package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.theme.Theme;
import net.kyori.adventure.text.Component;

import java.time.Duration;

public class QualifyHeat {

    public static boolean passQualyLap(Driver driver) {
        return passQualyLap(driver, null, null, null);
    }

    public static boolean passQualyLap(Driver driver, org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        if (driver.getHeat().getHeatState() != HeatState.RACING) {
            return false;
        }
        if (timeIsOver(driver) || lapsAreOver(driver)) {
            driver.finish(from, to, region);
            driver.getHeat().updatePositions();
            driver.fireFinishEvent();
            EventAnnouncements.sendFinishSound(driver);
            EventAnnouncements.sendFinishTitleQualification(driver);
            EventAnnouncements.broadcastFinishQualification(driver.getHeat(), driver);
            if (driver.getHeat().noDriversRunning()) {
                driver.getHeat().finishHeat();
            }
            return true;
        }

        if (driver.getHeat().getLapReset()) {
            driver.passResetLap(from, to, region);
            driver.setState(DriverState.LAPRESET);
            driver.teleportForReset(driver.getHeat().getEvent().getTrack().getSpawnLocation());
            return true;
        }

        driver.passLap(from, to, region);
        return true;
    }

    public static Component getBestLapDelta(Theme theme, Lap finishedLap, Lap personalBest) {
        if (finishedLap != null && personalBest != null) {
            if (personalBest.getPreciseLapTime() < finishedLap.getPreciseLapTime()) {
                return Component.text(" +" + ApiUtilities.formatAsPersonalGap(finishedLap.getPreciseLapTime() - personalBest.getPreciseLapTime())).color(theme.getError());
            } else if (personalBest.getPreciseLapTime() == finishedLap.getPreciseLapTime()){
                return Component.text(" =" + ApiUtilities.formatAsPersonalGap(personalBest.getPreciseLapTime() - finishedLap.getPreciseLapTime())).color(theme.getWarning());
            }else {
                return Component.text(" -" + ApiUtilities.formatAsPersonalGap(personalBest.getPreciseLapTime() - finishedLap.getPreciseLapTime())).color(theme.getSuccess());
            }
        }

        return Component.empty();
    }

    public static String getBestLapCheckpointDelta(Driver driver, int latestCheckpoint) {
        if (latestCheckpoint > 0) {
            if (driver.getBestLap().isPresent() && driver.getBestLap().get().getCheckpointTime(latestCheckpoint) != null) {
                var bestCheckpoint = Duration.between(driver.getBestLap().get().getLapStart(), driver.getBestLap().get().getCheckpointTime(latestCheckpoint)).toMillis();
                var currentCheckpoint = Duration.between(driver.getCurrentLap().getLapStart(), driver.getCurrentLap().getCheckpointTime(latestCheckpoint)).toMillis();
                if (bestCheckpoint < currentCheckpoint) {
                    return " " + "&e+" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - bestCheckpoint);
                } else if (bestCheckpoint == currentCheckpoint) {
                    return " " + "&w=" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - bestCheckpoint);
                }else {
                    return " " + "&s-" + ApiUtilities.formatAsPersonalGap(bestCheckpoint - currentCheckpoint);
                }
            }
        }

        return "";
    }

    public static boolean timeIsOver(Driver driver) {
        Integer timeLimit = driver.getHeat().getTimeLimit();
        if (timeLimit == null || driver.getStartTime() == null) {
            return false;
        }
        return Duration.between(driver.getStartTime(), TimingSystem.currentTime).toMillis() > timeLimit;
    }

    /**
     * A qualifying lap count limits how many laps each driver gets, the same way the time limit
     * limits how long they get. Both are stopping cases, whichever comes first.
     */
    public static boolean lapsAreOver(Driver driver) {
        Integer totalLaps = driver.getHeat().getTotalLaps();
        return totalLaps != null && totalLaps <= driver.getLaps().size();
    }
}
