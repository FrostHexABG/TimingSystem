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

        // Running the laps out only finishes a driver who has served their pit stops. A time limit
        // finishes them either way - outstanding stops cost them positions instead.
        if (timeIsOver(driver) || (lapsAreOver(driver) && pitsAreDone(driver))) {
            finishDriver(driver, from, to, region);
            if (driver.getHeat().noDriversRunning()) {
                driver.getHeat().finishHeat();
            }
            return true;
        }

        if (lapsAreOver(driver)) {
            // The flag has fallen for this driver, but pit stops are still outstanding so they are
            // not allowed to finish. They keep circulating to serve them, on laps that no longer
            // count towards their position.
            driver.markFlagLap();
        }
        driver.passLap(from, to, region);
        return true;
    }

    private static boolean timeIsOver(Driver driver) {
        Heat heat = driver.getHeat();
        return heat.getTimeLimitEnd() == TimeLimitEnd.ENDOFLAP && heat.isTimeLimitOver();
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
        announceFinish(driver);
    }

    /**
     * Tells a driver where they finished and moves them off the track. Used both when a driver takes
     * the flag out on track and when a time limit classifies the drivers who were still running.
     * The driver's position has to be settled before this is called.
     */
    public static void announceFinish(Driver driver) {
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
