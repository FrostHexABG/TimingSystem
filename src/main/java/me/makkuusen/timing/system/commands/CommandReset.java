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
import org.bukkit.Location;
import org.bukkit.entity.Player;

import static me.makkuusen.timing.system.heat.QualifyHeat.timeIsOver;

@CommandAlias("reset|re")
public class CommandReset extends BaseCommand {

    @Default
    @CommandPermission("%permissiontimingsystem_reset")
    public static void onReset(Player player) {
        TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId())
                .ifPresentOrElse(
                        driver -> handleDriverReset(player, driver),
                        () -> ApiUtilities.resetPlayerTimeTrial(player));
    }

    private static void handleDriverReset(Player player, Driver driver) {
        if (canResetDriver(driver)) {
            performInHeatReset(driver);
        } else {
            Text.send(player, Error.NOT_NOW);
        }
    }

    private static boolean canResetDriver(Driver driver) {
        if (driver.getHeat().getRound() instanceof QualificationRound) {
            return canResetInQualification(driver);
        } else if (driver.getHeat().getRound() instanceof FinalRound) {
            return canResetInFinal(driver);
        }
        return false;
    }

    private static boolean canResetInQualification(Driver driver) {
        if (!isValidStateForQualificationReset(driver.getState())) {
            return false;
        }

        if (driver.getHeat().getReset()) {
            return driver.getState() == DriverState.RUNNING && !timeIsOver(driver);
        }

        return true;
    }

    private static boolean canResetInFinal(Driver driver) {
        return (driver.getState() == DriverState.RUNNING || driver.getState() == DriverState.STARTING)
                && !isPlayerInPit(driver);
    }

    private static boolean isValidStateForQualificationReset(DriverState state) {
        return state == DriverState.RUNNING ||
                state == DriverState.LAPRESET ||
                state == DriverState.RESET ||
                state == DriverState.STARTING;
    }

    private static boolean isPlayerInPit(Driver driver) {
        return driver.getHeat().getEvent().getTrack().getTrackRegions()
                .getRegions(TrackRegion.RegionType.INPIT)
                .stream()
                .anyMatch(region -> region.contains(driver.getTPlayer().getPlayer().getLocation()));
    }

    public static void performInHeatReset(Driver driver) {
        if (driver.getHeat().getRound() instanceof QualificationRound &&
                driver.getHeat().getReset() &&
                driver.getState() == DriverState.RUNNING) {
            resetToTrackSpawn(driver);
            driver.setState(DriverState.RESET);
            return;
        }

        if (driver.getState() == DriverState.RUNNING) {
            resetToCheckpoint(driver);
        } else if (driver.getState() == DriverState.STARTING) {
            resetToGrid(driver);
        } else if (driver.getState() == DriverState.RESET) {
            resetToTrackSpawn(driver);
        }
    }

    private static void resetToCheckpoint(Driver driver) {
        int latestCheckpoint = driver.getCurrentLap().getLatestCheckpoint();
        Location resetLocation = latestCheckpoint == 0
                ? getStartLineLocation(driver)
                : getCheckpointLocation(driver, latestCheckpoint);

        teleportPlayerToLocation(driver, resetLocation);
    }

    private static void resetToGrid(Driver driver) {
        Track track = driver.getHeat().getEvent().getTrack();
        Location gridLocation = driver.getHeat().getRound() instanceof QualificationRound
                ? getLastQualificationGridLocation(track)
                : getLastRaceGridLocation(track);

        teleportPlayerToLocation(driver, gridLocation);
    }

    private static void resetToTrackSpawn(Driver driver) {
        Location spawnLocation = driver.getHeat().getEvent().getTrack().getSpawnLocation();
        teleportPlayerToLocation(driver, spawnLocation);
    }

    private static Location getStartLineLocation(Driver driver) {
        return driver.getHeat().getEvent().getTrack()
                .getTrackRegions()
                .getRegions(TrackRegion.RegionType.START)
                .get(0)
                .getSpawnLocation();
    }

    private static Location getCheckpointLocation(Driver driver, int checkpoint) {
        return driver.getHeat().getEvent().getTrack()
                .getTrackRegions()
                .getCheckpoints(checkpoint)
                .get(0)
                .getSpawnLocation();
    }

    private static Location getLastQualificationGridLocation(Track track) {
        var qualyGrids = track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID);
        return qualyGrids.get(qualyGrids.size() - 1).getLocation();
    }

    private static Location getLastRaceGridLocation(Track track) {
        var raceGrids = track.getTrackLocations().getLocations(TrackLocation.Type.GRID);
        return raceGrids.get(raceGrids.size() - 1).getLocation();
    }

    private static void teleportPlayerToLocation(Driver driver, Location location) {
        ApiUtilities.teleportPlayerAndSpawnBoat(
                driver.getTPlayer().getPlayer(),
                driver.getHeat().getEvent().getTrack(),
                location
        );
    }
}