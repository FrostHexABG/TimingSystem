package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.participant.Driver;
import org.bukkit.Location;
import org.bukkit.entity.Player;


public class FinalHeat {

    public static boolean passLap(Driver driver) {
        return passLap(driver, null, null, null);
    }

    public static boolean passLap(Driver driver, org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        if (driver.getHeat().getHeatState() != HeatState.RACING) {
            return false;
        }

        if (driver.getHeat().getTotalLaps() <= driver.getLaps().size() && driver.getHeat().getTotalPits() <= driver.getPits()) {
            finishDriver(driver);
            if (driver.getHeat().noDriversRunning()) {
                driver.getHeat().finishHeat();
            }
            return true;
        }
        driver.passLap(from, to, region);
        return true;
    }

    private static void finishDriver(Driver driver) {
        driver.finish();
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
