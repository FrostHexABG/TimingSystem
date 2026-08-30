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
    // True from the moment a reset/lap reset teleport is queued until the player has actually been moved.
    // Guards against the start region triggering again while the driver is still sitting on the start line.
    private boolean awaitingResetTeleport = false;
    // The lap a driver took the flag on while pit stops were still outstanding. Laps driven after
    // that one are only there to serve those stops, so they must not count towards a position.
    private Integer flagLap;
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
    }

    public void finish(Location from, Location to, TrackRegion region) {
        finishLap(from, to, region);
        setEndTime(ApiUtilities.getPreciseRegionEntryTime(from, to, region));
        state = DriverState.FINISHED;
    }

    public void finishWithoutLap() {
        removeUnfinishedLap();
        setEndTime(getLaps().isEmpty() ? TimingSystem.currentTime : getCurrentLap().getLapEnd());
        state = DriverState.FINISHED;
    }

    public void fireFinishEvent() {
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

    public void start(Location from, Location to, TrackRegion region) {
        state = DriverState.RUNNING;
        newLap(from, to, region);

        DriverStartEvent e = new DriverStartEvent(this);
        e.callEvent();
    }

    public void passLap() {
        finishLap();
        newLap();
    }

    public void passLap(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        finishLap(from, to, region);
        newLap(from, to, region);
    }

    public void passResetLap(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion region) {
        finishLap(from, to, region);
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
        
        getCurrentLap().setLapEnd(ApiUtilities.getPreciseRegionEntryTime(from, to, region));
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

    public void teleportForReset(Location location) {
        var player = getTPlayer().getPlayer();
        if (player == null) {
            return;
        }
        awaitingResetTeleport = true;
        ApiUtilities.teleportPlayerAndSpawnBoat(player, heat.getEvent().getTrack(), location, false, false, () -> awaitingResetTeleport = false);
    }

    public void resetQualyLap() {
        laps.remove(laps.size() - 1);
        state = DriverState.RUNNING;
        awaitingResetTeleport = false;
        newLap();
    }

    public void resetQualyLap(Location from, Location to, TrackRegion region) {
        laps.remove(laps.size() - 1);
        state = DriverState.RUNNING;
        awaitingResetTeleport = false;
        newLap(from, to, region);
    }

    public void lapReset() {
        state = DriverState.RUNNING;
        awaitingResetTeleport = false;
        newLap();
    }

    public void lapReset(Location from, Location to, TrackRegion region) {
        state = DriverState.RUNNING;
        awaitingResetTeleport = false;
        newLap(from, to, region);
    }

    public void reset() {
        state = DriverState.SETUP;
        awaitingResetTeleport = false;
        flagLap = null;
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

    private void newLap(Location from, Location to, TrackRegion region) {
        laps.add(new Lap(this, heat.getEvent().getTrack(), from, to, region));
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

    /**
     * Remembers the lap the flag fell on for a driver who is not allowed to finish yet because they
     * still owe pit stops. Only the first crossing counts, since that is where their race ended.
     */
    public void markFlagLap() {
        if (flagLap == null) {
            flagLap = laps.size();
        }
    }

    /**
     * How a driver's pit stops rank them, highest first. An expired time limit is the only thing
     * that classifies a driver who still owes stops - running the laps out does not finish them
     * until they are served - so that is the only point where the stops reorder anything. Up to
     * then every driver is taken to be on their way to serving the rest, which keeps the board
     * about racing rather than about pit counts.
     */
    private int getPitRank() {
        Integer totalPits = heat.getTotalPits();
        if (totalPits == null || totalPits <= 0 || !heat.isTimeLimitOver()) {
            return 0;
        }
        return Math.min(pits, totalPits);
    }

    /**
     * The lap count a driver's position in a final is judged on. The lap in progress counts, so a
     * driver still out on track ranks ahead of one who has already taken the flag on the same lap.
     * Laps driven after the flag fell do not count, which is what stops a driver who has to keep
     * circulating for an outstanding pit stop from gaining positions by it.
     */
    public int getPositionLaps() {
        int positionLaps = laps.size();
        if (flagLap != null && positionLaps > flagLap) {
            return flagLap;
        }
        return positionLaps;
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

    public @Nullable Instant getTimeStamp(int lap, int checkpoint) {
        if (getLaps().isEmpty()) {
            return null;
        }
        var heat = getHeat();
        // A heat without a lap count is bounded by the driver's own laps instead, and so is a driver
        // who never got that far - a time limit ends a race wherever the drivers happen to be.
        int lastLap = heat.getTotalLaps() == null ? getLaps().size() : Math.min(heat.getTotalLaps(), getLaps().size());
        if (lap > lastLap) {
            Lap lastKnownLap = getLaps().get(lastLap - 1);
            return lastKnownLap.getLapEnd() == null ? lastKnownLap.getCheckpointTime(lastKnownLap.getLatestCheckpoint()) : lastKnownLap.getLapEnd();
        }

        // A driver can be asked for a checkpoint they have not reached yet: a driver serving pit
        // stops after taking the flag keeps circulating past drivers who are classified ahead of
        // them. The last checkpoint they did pass is as far as they can be measured.
        Lap requestedLap = getLaps().get(lap - 1);
        return requestedLap.getCheckpointTime(Math.min(checkpoint, requestedLap.getLatestCheckpoint()));
    }

    /**
     * Whether a driver's place in the race is settled: they have either taken the flag or are only
     * still out on track to serve outstanding pit stops. The laps they drive from here do not count
     * towards a position, so neither do they count towards a gap.
     */
    private boolean isRaceProgressFrozen() {
        return isFinished() || flagLap != null;
    }

    /**
     * The lap a driver's gap to the rest of the field is measured on. A driver whose race is settled
     * is measured at the start of the lap they never raced, which is the moment they took the flag,
     * so a gap can still be taken against drivers who are out on track.
     */
    public int getGapLap() {
        return isRaceProgressFrozen() ? getPositionLaps() + 1 : getLaps().size();
    }

    /**
     * The checkpoint within {@link #getGapLap()} a driver's gap is measured at.
     */
    public int getGapCheckpoint() {
        Lap currentLap = getCurrentLap();
        if (isRaceProgressFrozen() || currentLap == null) {
            return 0;
        }
        return currentLap.getLatestCheckpoint();
    }

    /**
     * The time between two drivers in a race, measured at the point the driver behind has reached.
     * Either driver may have taken the flag already - a driver who finishes a lap or more down is
     * classified behind drivers who are still running - so this must not assume that a finished
     * driver is being compared against another finished one.
     */
    public static long getRaceGap(Driver driverAhead, Driver driverBehind) {
        // Two drivers who ran the race out to the flag are simply compared on when they took it.
        // A driver who kept circulating to serve pit stops is not, since their end time is a lap or
        // more later than the race they are classified on.
        if (driverAhead.isFinished() && driverBehind.isFinished() && driverAhead.flagLap == null && driverBehind.flagLap == null) {
            return Duration.between(driverAhead.getEndTime(), driverBehind.getEndTime()).toMillis();
        }

        int lap = driverBehind.getGapLap();
        int checkpoint = driverBehind.getGapCheckpoint();
        Instant behind = driverBehind.getTimeStamp(lap, checkpoint);
        Instant ahead = driverAhead.getTimeStamp(lap, checkpoint);
        if (behind == null || ahead == null) {
            return 0;
        }
        return Duration.between(ahead, behind).toMillis();
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

            if (getLaps().isEmpty() || comparingDriver.getLaps().isEmpty()) {
                return 0;
            }

            if (getPosition() < comparingDriver.getPosition()) {
                return getRaceGap(this, comparingDriver);
            }

            if (getPosition() > comparingDriver.getPosition()) {
                return getRaceGap(comparingDriver, this);
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
        // Outstanding pit stops come before anything raced for: a driver classified without them
        // drops behind everyone who served more of theirs, however the race itself went.
        int pitRank = getPitRank();
        int oPitRank = o.getPitRank();
        if (pitRank > oPitRank) {
            return -1;
        } else if (pitRank < oPitRank) {
            return 1;
        }

        // Laps come next, so a driver still out on track keeps the places they hold over drivers
        // who have already taken the flag a lap or more behind them.
        int positionLaps = getPositionLaps();
        int oPositionLaps = o.getPositionLaps();
        if (positionLaps > oPositionLaps) {
            return -1;
        } else if (positionLaps < oPositionLaps) {
            return 1;
        }

        // On the same lap the driver who has taken the flag is home, the other one is still on it.
        if (isFinished() != o.isFinished()) {
            return isFinished() ? -1 : 1;
        }

        if (isFinished()) {
            return getEndTime().compareTo(o.getEndTime());
        }

        if (getLaps().isEmpty() || o.getLaps().isEmpty()) {
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
        }

        Instant last = lap.getCheckpointTime(lap.getLatestCheckpoint());
        Instant oLast = oLap.getCheckpointTime(lap.getLatestCheckpoint());
        return last.compareTo(oLast);
    }

}
