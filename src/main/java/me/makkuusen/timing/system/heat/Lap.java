package me.makkuusen.timing.system.heat;

import co.aikar.idb.DbRow;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

@Getter
@Setter
public class Lap implements Comparable<Lap> {

    private TPlayer player;
    private int heatId;
    private Track track;
    private Instant lapEnd;
    private Instant lapStart;
    private boolean pitted;
    private ArrayList<Instant> checkpoints = new ArrayList<>();

    public Lap(Driver driver, Track track) {
        this.heatId = driver.getHeat().getId();
        this.player = driver.getTPlayer();
        this.track = track;
        this.lapStart = TimingSystem.currentTime;
        this.pitted = false;
    }

    public Lap(Driver driver, Track track, Location from, Location to, TrackRegion region) {
        this.heatId = driver.getHeat().getId();
        this.player = driver.getTPlayer();
        this.track = track;
        Instant preciseTime = TimingSystem.currentTime;

        if (from != null && to != null && region != null) {
            double proportion = calculateRegionEntryProportion(from, to, region);
            long tickDurationNanos = 50_000_000L; // 50ms in nanoseconds (1 tick = 50ms)
            long adjustmentNanos = (long) ((1.0 - proportion) * tickDurationNanos);
            preciseTime = preciseTime.minusNanos(adjustmentNanos);
        }

        this.lapStart = preciseTime;
        this.pitted = false;
    }

    public Lap(DbRow data) {
        player = TSDatabase.getPlayer(data.getString("uuid"));
        heatId = data.getInt("heatId");
        track = TrackDatabase.getTrackById(data.getInt("trackId")).orElse(null);
        lapStart = Instant.ofEpochMilli(data.getLong("lapStart"));
        lapEnd = data.getLong("lapEnd") == null ? null : Instant.ofEpochMilli(data.getLong("lapEnd"));
        pitted = data.get("pitted") instanceof Boolean ? data.get("pitted") : data.get("pitted").equals(1);

    }

    public long getLapTime() {
        if (lapEnd == null || lapStart == null) {
            return -1;
        }
        long lapTime = Duration.between(lapStart, lapEnd).toMillis();
        return ApiUtilities.getRoundedToTick(lapTime);
    }

    public long getPreciseLapTime() {
        if (lapEnd == null || lapStart == null) {
            return -1;
        }
        return Duration.between(lapStart, lapEnd).toMillis();
    }

    public int getNextCheckpoint() {
        if (track.getNumberOfCheckpoints() >= checkpoints.size()) {
            return checkpoints.size() + 1;
        }
        return checkpoints.size();
    }

    public boolean hasPassedAllCheckpoints() {
        return checkpoints.size() == track.getNumberOfCheckpoints();
    }

    public void passNextCheckpoint(Instant timeStamp) {
        checkpoints.add(timeStamp);
    }

    public void passNextCheckpoint(org.bukkit.Location from, org.bukkit.Location to, me.makkuusen.timing.system.track.regions.TrackRegion checkpoint) {
        // Calculate precise timing by finding the exact point where player enters the checkpoint
        java.time.Instant preciseTime = me.makkuusen.timing.system.TimingSystem.currentTime;
        double proportion = calculateRegionEntryProportion(from, to, checkpoint);
        long tickDurationNanos = 50_000_000L; // 50ms in nanoseconds (1 tick = 50ms)
        long adjustmentNanos = (long) ((1.0 - proportion) * tickDurationNanos);
        preciseTime = preciseTime.minusNanos(adjustmentNanos);
        
        checkpoints.add(preciseTime);
    }

    public int getLatestCheckpoint() {
        return checkpoints.size();
    }

    public Instant getCheckpointTime(int checkpoint) {
        if (checkpoints.isEmpty() || checkpoint == 0) {
            return lapStart;
        }
        return checkpoints.get(checkpoint - 1);
    }

    @Override
    public int compareTo(@NotNull Lap lap) {
        return Long.compare(getPreciseLapTime(), lap.getPreciseLapTime());
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
