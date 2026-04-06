package me.makkuusen.timing.system.api;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static me.makkuusen.timing.system.commands.CommandRace.event;
import static me.makkuusen.timing.system.commands.CommandRace.round;
import static me.makkuusen.timing.system.commands.CommandRace.heat;

@SuppressWarnings("unused")
public class QuickRaceAPI {
    private QuickRaceAPI() {
    }

    /**
     * Creates a quickrace on the specified track with the number of laps and pits specified.
     * @param playerHost The player that the quickrace will be created on behalf of.
     * @param track The track which will be raced on.
     * @param laps The number of laps for the race. Forced to '1' if track is a stage.
     * @param pits The number of pits for the race. Forced to '0' if track is a stage.
     * @return Whether the creation is successful or not
     */
    public static boolean create(UUID playerHost, Track track, int laps, int pits) {
        if(getQuickRaceHeat().isPresent() && heat.isFinished()) deleteEvent();

        if(!track.isOpen() || !track.getTrackLocations().hasLocation(TrackLocation.Type.GRID) || !track.getTrackRegions().hasRegion(TrackRegion.RegionType.START)) return false;

        final String name = "QuickRace";
        Optional<Event> maybeEvent = EventDatabase.eventNew(playerHost, name);
        if(maybeEvent.isEmpty()) return false;
        event = maybeEvent.get();

        event.setTrack(track);
        if(!EventDatabase.roundNew(event, RoundType.FINAL, 1)) return false;

        Optional<Round> maybeRound = event.eventSchedule.getRound(1);
        if(maybeRound.isEmpty()) return false;
        round = maybeRound.get();

        round.createHeat(1);
        var maybeHeat = round.getHeat("R1F1");
        if(maybeHeat.isEmpty()) return false;
        heat = maybeHeat.get();

        laps = Math.max(1, laps);
        pits = Math.max(0, pits);

        if(track.isStage()) {
            heat.setTotalLaps(1);
            heat.setTotalPits(0);
        } else {
            heat.setTotalLaps(laps);
            heat.setTotalPits(pits);
        }

        HeatState state = heat.getHeatState();
        if (state != HeatState.SETUP && !heat.resetHeat()) return false;

        if (!heat.loadHeat()) {
            deleteEvent();
            return false;
        }
        return true;
    }

    /**
     * Sends a message to all players which adds them to the current quickrace.
     * @return True if the messages was sent successfully, false otherwise.
     */
    public static boolean sendJoinMessage() {
        if(getQuickRaceHeat().isEmpty()) return false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (heat.getDrivers().containsKey(p.getUniqueId())) continue;
            p.sendMessage(Component.empty());
            p.sendMessage(Text.get(p, Broadcast.CLICK_TO_JOIN_RACE, "%track%", event.getTrack().getDisplayName(), "%laps%", String.valueOf(heat.getTotalLaps())).clickEvent(ClickEvent.runCommand("/race join")));
            p.sendMessage(Component.empty());
        }
        return true;
    }

    /**
     * Sends a message to certain players which adds them to the current quickrace.
     * @param players A collection of players to be sent the join message.
     * @return True if the messages was sent successfully, false otherwise.
     */
    public static boolean sendJoinMessage(List<Player> players) {
        if(getQuickRaceHeat().isEmpty()) return false;
        for (Player p : players) {
            if (heat.getDrivers().containsKey(p.getUniqueId())) continue;
            p.sendMessage(Component.empty());
            p.sendMessage(Text.get(p, Broadcast.CLICK_TO_JOIN_RACE, "%track%", event.getTrack().getDisplayName(), "%laps%", String.valueOf(heat.getTotalLaps())).clickEvent(ClickEvent.runCommand("/race join")));
            p.sendMessage(Component.empty());
        }
        return true;
    }

    /**
     * Starts the current quickrace heat.
     * @return True if the heat was started successfully, false otherwise.
     */
    public static boolean start() {
        if(getQuickRaceHeat().isEmpty()) return false;
        return heat.startCountdown();
    }

    /**
     * Ends and deletes the quickrace.
     * @return True if the event was ended successfully, false otherwise.
     */
    public static boolean end() {
        if (event == null || heat == null) return false;
        if (heat.getHeatState() == HeatState.RACING) {
            heat.finishHeat();
        } else if (heat.getHeatState() == HeatState.LOADED) {
            heat.resetHeat();
        }

        deleteEvent();
        return true;
    }

    /**
     * Adds the specified player to the current quickrace.
     * @param player The player to be added to the quickrace.
     * @return True if  the player was added successfully, false otherwise.
     */
    public static boolean addPlayer(Player player) {
        if(getQuickRaceHeat().isEmpty()) return false;
        if(heat.getHeatState() != HeatState.LOADED) return false;
        if(heat.getDrivers().containsKey(player.getUniqueId())) return false;
        if(heat.getMaxDrivers() <= heat.getDrivers().size()) return false;
        if(EventDatabase.heatDriverNew(player.getUniqueId(), heat, heat.getStartPositions().size() + 1)) {
            heat.addDriverToGrid(heat.getDrivers().get(player.getUniqueId()));
            return true;
        }
        return false;
    }

    /**
     * Removes the specified player from the current quickrace.
     * @param player The player to be removed from the quickrace
     * @return True if  the player was removed successfully, false otherwise.
     */
    public static boolean removePlayer(Player player) {
        Optional<Driver> d = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
        if(d.isEmpty()) return false;

        if (heat.getHeatState() == HeatState.LOADED) {
            heat.resetHeat();
            if(heat.removeDriver(heat.getDrivers().get(player.getUniqueId()))) heat.getEvent().removeSpectator(player.getUniqueId());
            heat.loadHeat();
        }

        Driver driver = d.get();
        if(!driver.getHeat().disqualifyDriver(driver)) return false;
        ApiUtilities.removePlayerFromBoat(player);
        player.teleport(player.getBedSpawnLocation() == null ? player.getWorld().getSpawnLocation() : player.getBedSpawnLocation());
        return true;
    }

    /**
     * @return An optional of the quickrace event.
     */
    public static Optional<Event> getQuickRaceEvent() {
        return Optional.ofNullable(event);
    }

    /**
     * @return An optional of the quickrace heat.
     */
    public static Optional<Heat> getQuickRaceHeat() {
        return Optional.ofNullable(heat);
    }

    /**
     * @return True if the quickrace heat is currently active, false otherwise.
     */
    public static boolean quickRaceActive() {
        if(getQuickRaceHeat().isPresent()) return heat.isActive();
        return false;
    }

    private static void deleteEvent() {
        EventDatabase.removeEventHard(event);
        event = null;
        round = null;
        heat = null;
    }
}
