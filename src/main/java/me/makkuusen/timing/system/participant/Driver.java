package me.makkuusen.timing.system.participant;

import co.aikar.idb.DbRow;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.events.driver.*;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.heat.DriverScoreboard;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class Driver extends Participant implements Comparable<Driver> {

    private int id;
    private Heat heat;
    private Integer position;
    private int startPosition;
    private int pits;
    private Instant startTime;
    private Instant endTime;
    private DriverState state;
    private DriverScoreboard scoreboard;
    private boolean disqualified = false;
    private List<Lap> laps = new ArrayList<>();

    public Driver(DbRow data) {
        super(data);
        id = data.get("id");
        heat = EventDatabase.getHeat(data.getInt("heatId")).get();
        position = data.getInt("position");
        startPosition = data.getInt("startPosition");
        startTime = data.getLong("startTime") == null ? null : Instant.ofEpochMilli(data.getLong("startTime"));
        endTime = data.getLong("endTime") == null ? null : Instant.ofEpochMilli(data.getLong("endTime"));
        pits = data.getInt("pitstops");
        state = isFinished() ? DriverState.FINISHED : DriverState.SETUP;
    }

    public void updateScoreboard() {
        if (getTPlayer().getPlayer() == null) {
            if (scoreboard != null) {
                scoreboard.removeScoreboard();
                scoreboard = null;
            }
            return;
        }
        if (disqualified) {
            return;
        }
        if (scoreboard == null) {
            scoreboard = new DriverScoreboard(getTPlayer(), this);
        }
        scoreboard.setDriverLines();
    }

    public void finish() {
        finishLap();
        setEndTime(TimingSystem.currentTime);
        state = DriverState.FINISHED;

        DriverFinishHeatEvent e = new DriverFinishHeatEvent(this);
        e.callEvent();
    }

    public void disqualify() {
        state = DriverState.FINISHED;
        disqualified = true;

        DriverDisqualifyEvent e = new DriverDisqualifyEvent(this);
        e.callEvent();
    }

    public void start() {
        state = DriverState.RUNNING;
        newLap();

        DriverStartEvent e = new DriverStartEvent(this);
        e.callEvent();
    }

    public void passLap() {
        finishLap();
        newLap();
    }

    public void passLap(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        finishLap(from, to, region);
        newLap();
    }

    public void passResetLap() {
        finishLap();
    }

    public boolean passPit() {
        if (!getCurrentLap().isPitted()) {
            setPits(pits + 1);
            EventAnnouncements.broadcastPit(getHeat(), this, pits);
            getCurrentLap().setPitted(true);

            DriverPassPitEvent e = new DriverPassPitEvent(this, getCurrentLap(), pits);
            e.callEvent();

            return true;
        }
        return false;
    }

    private void finishLap() {
        var oldBest = getBestLap();
        getCurrentLap().setLapEnd(TimingSystem.currentTime);
        
        // Check if fastest lap driver still exists in heat (may have been swapped out)
        boolean isFastestLap = false;
        if (heat.getFastestLapUUID() == null) {
            isFastestLap = true;
        } else {
            Driver fastestDriver = heat.getDrivers().get(heat.getFastestLapUUID());
            if (fastestDriver == null) {
                // Fastest lap driver was removed (swapped out), this is now the fastest
                isFastestLap = true;
            } else {
                var fastestLap = fastestDriver.getBestLap();
                isFastestLap = fastestLap.map(lap -> getCurrentLap().getPreciseLapTime() < lap.getPreciseLapTime() ||
                        getCurrentLap().equals(lap)).orElse(true);
            }
        }

        if (isFastestLap) {
            EventAnnouncements.broadcastFastestLap(heat, this, getCurrentLap(), oldBest);
            heat.setFastestLapUUID(getTPlayer().getUniqueId());
        } else {
            if (heat.getRound() instanceof QualificationRound) {
                EventAnnouncements.broadcastQualifyingLap(heat, this, getCurrentLap(), oldBest);
            } else {
                EventAnnouncements.broadcastLapTime(heat, this, getCurrentLap().getPreciseLapTime());
            }
        }

        DriverFinishLapEvent e = new DriverFinishLapEvent(this, getCurrentLap(), isFastestLap);
        e.callEvent();

        ApiUtilities.msgConsole(getTPlayer().getName() + " finished lap in: " + ApiUtilities.formatAsTime(getCurrentLap().getPreciseLapTime()));
    }

    private void finishLap(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        var oldBest = getBestLap();
        
        // Calculate precise timing by finding the exact point where player enters the finish region
        java.time.Instant preciseEndTime = me.makkuusen.timing.system.TimingSystem.currentTime;
        if (from != null && to != null && region != null) {
            double proportion = calculateRegionEntryProportion(from, to, region);
            long tickDurationNanos = 50_000_000L; // 50ms in nanoseconds (1 tick = 50ms)
            long adjustmentNanos = (long) ((1.0 - proportion) * tickDurationNanos);
            preciseEndTime = preciseEndTime.minusNanos(adjustmentNanos);
        }
        
        getCurrentLap().setLapEnd(preciseEndTime);
        boolean isFastestLap = heat.getFastestLapUUID() == null || getCurrentLap().getPreciseLapTime() < heat.getDrivers().get(heat.getFastestLapUUID()).getBestLap().get().getPreciseLapTime() || getCurrentLap().equals(heat.getDrivers().get(heat.getFastestLapUUID()).getBestLap().get());

        if (isFastestLap) {
            EventAnnouncements.broadcastFastestLap(heat, this, getCurrentLap(), oldBest);
            heat.setFastestLapUUID(getTPlayer().getUniqueId());
        } else {
            if (heat.getRound() instanceof QualificationRound) {
                EventAnnouncements.broadcastQualifyingLap(heat, this, getCurrentLap(), oldBest);
            } else {
                EventAnnouncements.broadcastLapTime(heat, this, getCurrentLap().getPreciseLapTime());
            }
        }

        DriverFinishLapEvent e = new DriverFinishLapEvent(this, getCurrentLap(), isFastestLap);
        e.callEvent();

        ApiUtilities.msgConsole(getTPlayer().getName() + " finished lap in: " + ApiUtilities.formatAsTime(getCurrentLap().getPreciseLapTime()));
    }

    public void resetQualyLap() {
        laps.remove(laps.size() - 1);
        state = DriverState.RUNNING;
        newLap();
    }

    public void lapReset() {
        state = DriverState.RUNNING;
        newLap();
    }

    public void reset() {
        state = DriverState.SETUP;
        setEndTime(null);
        setStartTime(null);
        laps = new ArrayList<>();
        setPosition(startPosition);
        removeScoreboard();
        scoreboard = null;
        setPits(0);
    }

    public void removeScoreboard() {
        if (scoreboard != null) {
            scoreboard.removeScoreboard();
        }
    }

    public boolean isFinished() {
        return endTime != null;
    }

    public boolean isRunning() {
        if (disqualified) {
            return false;
        }
        return state == DriverState.RUNNING || state == DriverState.LOADED || state == DriverState.STARTING;
    }

    public boolean isInPit(Location playerLoc) {
        var inPitRegions = heat.getEvent().getTrack().getTrackRegions().getRegions(TrackRegion.RegionType.INPIT);
        for (TrackRegion trackRegion : inPitRegions) {
            if (trackRegion.contains(playerLoc)) {
                return true;
            }
        }
        return false;
    }

    private void newLap() {
        laps.add(new Lap(this, heat.getEvent().getTrack()));
        DriverNewLapEvent e = new DriverNewLapEvent(this, getCurrentLap());
        e.callEvent();
    }

    public long getFinishTime() {
        if(endTime == null) return 0;
        return Duration.between(startTime, endTime).toMillis();
    }

    public void setPosition(int position) {
        this.position = position;
        TimingSystem.getEventDatabase().driverSet(id, "position", position);
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
        TimingSystem.getEventDatabase().driverSet(id, "startPosition", startPosition);
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        TimingSystem.getEventDatabase().driverSet(id, "startTime", startTime == null ? null : startTime.toEpochMilli());
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
        TimingSystem.getEventDatabase().driverSet(id, "endTime", endTime == null ? null : endTime.toEpochMilli());
    }

    public void setPits(int pits) {
        this.pits = pits;
        TimingSystem.getEventDatabase().driverSet(id, "pitstops", pits);
    }

    public void setState(DriverState state) {
        this.state = state;
    }

    public @Nullable Lap getCurrentLap() {
        if (laps.isEmpty()) {
            return null;
        } else {
            return laps.get(laps.size() - 1);
        }
    }

    public void removeUnfinishedLap() {
        if (!laps.isEmpty() && getCurrentLap().getLapEnd() == null) {
            laps.remove(getCurrentLap());
        }
    }

    public Optional<Lap> getBestLap() {
        if (getLaps().isEmpty()) {
            return Optional.empty();
        }
        if (getLaps().get(0).getPreciseLapTime() == -1) {
            return Optional.empty();
        }
        Lap bestLap = getLaps().get(0);
        for (Lap lap : getLaps()) {
            if (lap.getPreciseLapTime() != -1 && lap.getPreciseLapTime() < bestLap.getPreciseLapTime()) {
                bestLap = lap;
            }
        }
        return Optional.of(bestLap);
    }


    public void onShutdown() {
        if (scoreboard != null) {
            scoreboard.removeScoreboard();
        }
    }

    public Instant getTimeStamp(int lap, int checkpoint) {
        var heat = getHeat();
        if (lap > heat.getTotalLaps()) {
            return getLaps().get(heat.getTotalLaps() - 1).getLapEnd();
        }

        return getLaps().get(lap - 1).getCheckpointTime(checkpoint);
    }

    public long getTimeGap(Driver comparingDriver) {

        if (heat.getRound() instanceof QualificationRound) {
            if (getBestLap().isEmpty()) {
                return 0;
            }

            if (comparingDriver.getBestLap().isEmpty()) {
                return 0;
            }

            if (comparingDriver.equals(this)) {
                return 0;
            }

            // returns time-difference
            return getBestLap().get().getPreciseLapTime() - comparingDriver.getBestLap().get().getPreciseLapTime();
        } else {

            if (getLaps().isEmpty()) {
                return 0;
            }

            long timeDiff;
            if (getPosition() < comparingDriver.getPosition()) {
                if (comparingDriver.isFinished()) {
                    return Duration.between(getEndTime(), comparingDriver.getEndTime()).toMillis();
                }

                if (!comparingDriver.getLaps().isEmpty() && comparingDriver.getCurrentLap() != null) {
                    Instant timeStamp = comparingDriver.getTimeStamp(comparingDriver.getLaps().size(), comparingDriver.getCurrentLap().getLatestCheckpoint());
                    Instant fasterTimeStamp = getTimeStamp(comparingDriver.getLaps().size(), comparingDriver.getCurrentLap().getLatestCheckpoint());
                    timeDiff = Duration.between(fasterTimeStamp, timeStamp).toMillis();
                    return timeDiff;
                }
            }

            if (getPosition() > comparingDriver.getPosition()) {
                if (isFinished()) {
                    return Duration.between(comparingDriver.getEndTime(), getEndTime()).toMillis();
                }
                Instant timeStamp = getTimeStamp(getLaps().size(), getCurrentLap().getLatestCheckpoint());
                Instant fasterTimeStamp = comparingDriver.getTimeStamp(getLaps().size(), getCurrentLap().getLatestCheckpoint());
                timeDiff = Duration.between(fasterTimeStamp, timeStamp).toMillis();
                return timeDiff;
            }
            return 0;
        }
    }


    @Override
    public int compareTo(@NotNull Driver o) {
        if (heat.getRound() instanceof QualificationRound) {
            return compareToQualification(o);
        } else {
            return compareToFinaldriver(o);
        }
    }

    private int compareToQualification(Driver o) {
        var bestLap = getBestLap();
        var oBestLap = o.getBestLap();
        if (bestLap.isEmpty() && oBestLap.isEmpty()) {
            return 0;
        } else if (bestLap.isPresent() && oBestLap.isEmpty()) {
            return -1;
        } else if (bestLap.isEmpty()) {
            return 1;
        }

        var lapTime = bestLap.get().getPreciseLapTime();
        var oLapTime = oBestLap.get().getPreciseLapTime();
        if (lapTime < oLapTime) {
            return -1;
        } else if (lapTime > oLapTime) {
            return 1;
        }

        return 0;
    }

    private int compareToFinaldriver(Driver o) {
        if (isFinished() && !o.isFinished()) {
            return -1;
        } else if (!isFinished() && o.isFinished()) {
            return 1;
        } else if (isFinished() && o.isFinished()) {
            // Make sure a disqualified driver don't rank better on endtime with fewer laps.
            if (getLaps().size() < o.getLaps().size()) {
                return 1;
            } else {
                return getEndTime().compareTo(o.getEndTime());
            }
        }

        if (getLaps().size() > o.getLaps().size()) {
            return -1;
        } else if (getLaps().size() < o.getLaps().size()) {
            return 1;
        }

        if (getLaps().isEmpty()) {
            return 0;
        }

        Lap lap = getCurrentLap();
        Lap oLap = o.getCurrentLap();

        if (lap.getLatestCheckpoint() > oLap.getLatestCheckpoint()) {
            return -1;
        } else if (lap.getLatestCheckpoint() < oLap.getLatestCheckpoint()) {
            return 1;
        }

        if (lap.getLatestCheckpoint() == 0) {
            return 0;
        } else if (lap.getLatestCheckpoint() == 0) {
            return lap.getLapStart().compareTo(oLap.getLapStart());
        }

        Instant last = lap.getCheckpointTime(lap.getLatestCheckpoint());
        Instant oLast = oLap.getCheckpointTime(lap.getLatestCheckpoint());
        return last.compareTo(oLast);
    }

    /**
     * Calculates the proportion of the movement vector where the player enters the region
     * using binary search with 15 iterations for precision.
     * 
     * @param from The starting location of the movement
     * @param to The ending location of the movement  
     * @param region The region being entered
     * @return A value between 0.0 and 1.0 representing how far through the movement the entry occurred
     */
    private static double calculateRegionEntryProportion(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        double low = 0.0;
        double high = 1.0;
        
        // Binary search with 15 iterations for precision
        for (int i = 0; i < 15; i++) {
            double mid = (low + high) / 2.0;
            
            // Calculate the interpolated position at the midpoint
            org.bukkit.Location midLocation = interpolateLocation(from, to, mid);
            
            if (region.contains(midLocation)) {
                // If the midpoint is inside the region, the entry point is earlier in the movement
                high = mid;
            } else {
                // If the midpoint is outside the region, the entry point is later in the movement
                low = mid;
            }
        }
        
        // Return the final proportion (closer to the actual entry point)
        return (low + high) / 2.0;
    }
    
    /**
     * Interpolates between two locations based on a proportion value.
     * 
     * @param from The starting location
     * @param to The ending location
     * @param proportion A value between 0.0 and 1.0
     * @return The interpolated location
     */
    private static org.bukkit.Location interpolateLocation(org.bukkit.Location from, org.bukkit.Location to, double proportion) {
        double x = from.getX() + (to.getX() - from.getX()) * proportion;
        double y = from.getY() + (to.getY() - from.getY()) * proportion;
        double z = from.getZ() + (to.getZ() - from.getZ()) * proportion;
        
        return new org.bukkit.Location(from.getWorld(), x, y, z);
    }
}
