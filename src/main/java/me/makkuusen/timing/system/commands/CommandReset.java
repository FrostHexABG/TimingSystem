package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import static me.makkuusen.timing.system.heat.QualifyHeat.timeIsOver;

@CommandAlias("reset|re")
public class CommandReset extends BaseCommand {

    @Default
    @CommandPermission("%permissiontimingsystem_reset")
    public static void onReset(Player player) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());

        if (maybeDriver.isEmpty()) {
            ApiUtilities.resetPlayerTimeTrial(player);
            return;
        }

        Driver driver = maybeDriver.get();

        if (!driver.isRunning()) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        var round = driver.getHeat().getRound();
        if (round instanceof QualificationRound && driver.getState() == DriverState.RUNNING) {
            if (timeIsOver(driver)) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
            if (driver.getHeat().getReset()) {
                ApiUtilities.teleportPlayerAndSpawnBoat(driver.getTPlayer().getPlayer(), driver.getHeat().getEvent().getTrack(), driver.getHeat().getEvent().getTrack().getSpawnLocation());
                driver.setState(DriverState.RESET);
            } else {
                boolean success = resetCheck(driver);
                if (!success) {
                    Text.send(player, Error.NOT_NOW);
                    return;
                }
            }
        } else if (round instanceof FinalRound) {
            resetCheck(driver);
        } else {
            Text.send(player, Error.NOT_NOW);
        }
    }

    private static boolean resetCheck(Driver driver) {
        if (driver.getState() == DriverState.RUNNING) {
            int latestCheckpoint = driver.getCurrentLap().getLatestCheckpoint();

            if (latestCheckpoint == 0) {
                Location startLineLocation = driver.getHeat().getEvent().getTrack().getTrackRegions().getRegions(TrackRegion.RegionType.START).get(0).getSpawnLocation();
                ApiUtilities.teleportPlayerAndSpawnBoat(driver.getTPlayer().getPlayer(), driver.getHeat().getEvent().getTrack(), startLineLocation);
                return true;
            }

            ApiUtilities.teleportPlayerAndSpawnBoat(driver.getTPlayer().getPlayer(), driver.getHeat().getEvent().getTrack(), driver.getHeat().getEvent().getTrack().getTrackRegions().getCheckpoints(latestCheckpoint).get(0).getSpawnLocation());
            return true;
        }

        if (driver.getState() == DriverState.STARTING) {
            Track track = driver.getHeat().getEvent().getTrack();
            int numGrids = track.getTrackLocations().getLocations(TrackLocation.Type.GRID).size();
            Location finalGridLocation = track.getTrackLocations().getLocations(TrackLocation.Type.GRID).get(numGrids - 1).getLocation();
            ApiUtilities.teleportPlayerAndSpawnBoat(driver.getTPlayer().getPlayer(), driver.getHeat().getEvent().getTrack(), finalGridLocation);
            return true;
        }

        return false;
    }
}