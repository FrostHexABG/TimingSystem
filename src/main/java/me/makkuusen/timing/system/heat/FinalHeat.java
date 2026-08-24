package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;


public class FinalHeat {

    public static boolean passLap(Driver driver) {
        return passLap(driver, null, null, null);
    }

    public static boolean passLap(Driver driver, Location from, Location to, TrackRegion region) {
        if (driver.getHeat().getHeatState() != HeatState.RACING) {
            return false;
        }

        if (lapsAreOver(driver) && pitsAreDone(driver)) {
            finishDriver(driver, from, to, region);
            if (driver.getHeat().noDriversRunning()) {
                driver.getHeat().finishHeat();
            }
            return true;
        }
        driver.passLap(from, to, region);
        return true;
    }

    /**
     * A final without a lap count is only stopped by its time limit, so the lap count can never be over.
     */
    private static boolean lapsAreOver(Driver driver) {
        Integer totalLaps = driver.getHeat().getTotalLaps();
        return totalLaps != null && totalLaps <= driver.getLaps().size();
    }

    private static boolean pitsAreDone(Driver driver) {
        Integer totalPits = driver.getHeat().getTotalPits();
        return totalPits == null || totalPits <= driver.getPits();
    }

    private static void finishDriver(Driver driver, Location from, Location to, TrackRegion region) {
        driver.finish(from, to, region);
        driver.getHeat().updatePositions();
        driver.fireFinishEvent();
        EventAnnouncements.sendFinishSound(driver);
        EventAnnouncements.sendFinishTitle(driver);
        EventAnnouncements.broadcastFinish(driver.getHeat(), driver, driver.getFinishTime());

        Player player = driver.getTPlayer().getPlayer();
        if (player == null) {
            return;
        }

        // Resolve location on the main thread before going async
        Location finishTp = driver.getHeat().getEvent().getTrack().getTrackLocations()
                .getFinishTp(driver.getPosition())
                .or(() -> driver.getHeat().getEvent().getTrack().getTrackLocations().getFinishTp())
                .orElse(null);

        if (finishTp != null) {
            ApiUtilities.removePlayerFromBoat(player);
            player.teleportAsync(finishTp);
        }
    }
}
