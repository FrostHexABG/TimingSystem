package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.boatutils.BoatUtilsMode;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandAlias("race")
public class CommandRace extends BaseCommand {
    public static Event event;
    public static Round round;
    public static Heat heat;

    @Subcommand("start")
    @CommandPermission("%permissionrace_start")
    public static void onStart(Player player) {
        if (heat == null) {
            Text.send(player, Error.RACE_NOT_FOUND);

            return;
        }
        if (heat.startCountdown()) {
            Text.send(player, Success.HEAT_COUNTDOWN_STARTED);
            return;
        }
        Text.send(player, Error.FAILED_TO_START_HEAT);

    }

    @Subcommand("end")
    @CommandPermission("%permissionrace_end")
    public void onEnd(Player player) {
        if (event == null) {
            Text.send(player, Error.RACE_NOT_FOUND);
            return;
        }
        if (heat != null && heat.getHeatState() == HeatState.RACING) {
            heat.finishHeat();
        } else if (heat != null && heat.getHeatState() == HeatState.LOADED) {
            heat.resetHeat();
        }

        deleteEvent();
        Text.send(player, Success.RACE_FINISHED);

    }

    @Subcommand("create")
    @CommandCompletion("@track laps pits")
    @CommandPermission("%permissionrace_create")
    public void onCreate(Player player, Track track, @Optional Integer laps, @Optional Integer pits) {
        if (heat != null) {
            if (heat.isFinished()) {
                deleteEvent();
            } else {
                Text.send(player, Error.RACE_IN_PROGRESS, "%track%", heat.getEvent().getTrack().getDisplayName());
                return;
            }
        }

        if (!track.isOpen()) {
            Text.send(player, Error.TRACK_IS_CLOSED);
            return;
        }

        if (!track.getTrackRegions().hasRegion(TrackRegion.RegionType.START)) {
            Text.send(player, Error.GENERIC);
            return;
        }

        if (!track.getTrackLocations().hasLocation(TrackLocation.Type.GRID)) {
            Text.send(player, Error.GENERIC);
            return;
        }


        String name = "QuickRace";
        var maybeEvent = EventDatabase.eventNew(player.getUniqueId(), name);
        if (maybeEvent.isEmpty()) {
            Text.send(player, Error.GENERIC);
            return;
        }

        event = maybeEvent.get();
        event.setTrack(track);

        if (!EventDatabase.roundNew(event, RoundType.FINAL, 1)) {
            Text.send(player, Error.FAILED_TO_CREATE_ROUND);
            return;
        }

        var maybeRound = event.getEventSchedule().getRound(1);

        if (maybeRound.isEmpty()) {
            Text.send(player, Error.GENERIC);
            return;
        }

        round = maybeRound.get();

        round.createHeat(1);

        var maybeHeat = round.getHeat("R1F1");

        if (maybeHeat.isEmpty()) {
            Text.send(player, Error.FAILED_TO_CREATE_HEAT);
            return;
        }

        heat = maybeHeat.get();


        if (track.isStage()) {
            heat.setTotalLaps(1);
            heat.setTotalPits(0);
        } else {
            if (laps != null && laps > 0) {
                heat.setTotalLaps(laps);
            } else {
                heat.setTotalLaps(3);
            }

            if (pits != null && laps != null) {
                heat.setTotalPits(Math.min(laps, pits));
            } else {
                heat.setTotalPits(0);
            }
        }

        var state = heat.getHeatState();
        if (state != HeatState.SETUP) {
            if (!heat.resetHeat()) {
                Text.send(player, Error.FAILED_TO_RESET_HEAT);
                return;
            }
        }

        if (!heat.loadHeat()) {
            Text.send(player,Error.FAILED_TO_LOAD_HEAT);
            deleteEvent();
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (heat.getDrivers().containsKey(p.getUniqueId())) {
                continue;
            }
            p.sendMessage(Component.empty());
            p.sendMessage(Text.get(p, Broadcast.CLICK_TO_JOIN_RACE, "%track%", event.getTrack().getDisplayName(), "%laps%", String.valueOf(heat.getTotalLaps())).clickEvent(ClickEvent.runCommand("/race join")));
            p.sendMessage(Component.empty());
        }
    }

    @Subcommand("join")
    @CommandPermission("%permissionrace_join")
    public static void onClickToJoin(Player player) {
        if (heat == null) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        if (heat.getHeatState() != HeatState.LOADED) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        if (heat.getDrivers().get(player.getUniqueId()) != null) {
            Text.send(player, Error.ALREADY_SIGNED_RACE);
            return;
        }

        if (heat.getMaxDrivers() <= heat.getDrivers().size()) {
            Text.send(player, Error.RACE_FULL);
            return;
        }

        // Issue #46 - Don't allow players without boatutils to join boatutils tracks.
        Event raceEvent = heat.getEvent();
        if (raceEvent != null) {
            Track raceTrack = raceEvent.getTrack();
            if (raceTrack == null) return;
            TPlayer tPlayer = TimingSystemAPI.getTPlayer(player.getUniqueId());
            if (raceTrack.getBoatUtilsMode() != BoatUtilsMode.VANILLA) {
                if (tPlayer != null && (!tPlayer.hasBoatUtils() || tPlayer.getBoatUtilsVersion() < raceTrack.getBoatUtilsMode().getRequiredVersion())) {
                    Text.send(player, Error.BOAT_UTILS_NEEDED_FOR_RACE);
                    return;
                }
            }

            Integer customModeId = raceTrack.getCustomBoatUtilsModeId();
            if (customModeId != null) {
                CustomBoatUtilsMode bume = TimingSystem.getTrackDatabase().getCustomBoatUtilsModeFromId(customModeId);
                if (bume != null) {
                    if (tPlayer != null && (!tPlayer.hasBoatUtils() || tPlayer.getBoatUtilsVersion() < bume.getRequiredVersion())) {
                        Text.send(player, Error.BOAT_UTILS_NEEDED_FOR_RACE);
                        return;
                    }
                }
            }
        }

        if (EventDatabase.heatDriverNew(player.getUniqueId(), heat, heat.getDrivers().size() + 1)) {
            Text.send(player, Success.SIGNED_RACE);
            heat.addDriverToGrid(heat.getDrivers().get(player.getUniqueId()));
            return;
        }

        Text.send(player, Error.NOT_NOW);
    }

    @Subcommand("leave")
    @CommandPermission("%permissionrace_leave")
    public static void onLeave(Player player) {
        if (EventDatabase.getDriverFromRunningHeat(player.getUniqueId()).isEmpty()) {
            Text.send(player, Error.NOT_NOW);
            return;
        }
        Driver driver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId()).get();
        Heat heat = driver.getHeat();
        if (heat.getHeatState() == HeatState.LOADED) {
            heat.resetHeat();
            if (heat.removeDriver(heat.getDrivers().get(player.getUniqueId()))) {
                heat.getEvent().removeSpectator(player.getUniqueId());
            }
            heat.loadHeat();
        }

        if (driver.getState() == DriverState.LOADED && heat.getHeatState() != HeatState.LOADED) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        if (driver.getHeat().disqualifyDriver(driver)) {
            ApiUtilities.removePlayerFromBoat(player);
            Location loc = player.getBedSpawnLocation() == null ? player.getWorld().getSpawnLocation() : player.getBedSpawnLocation();
            player.teleport(loc);
            Text.send(player, Success.HEAT_ABORTED);
            return;
        }
        Text.send(player, Error.FAILED_TO_ABORT_HEAT);
    }

    private void deleteEvent() {
        EventDatabase.removeEventHard(event);
        event = null;
        round = null;
        heat = null;
    }
}
