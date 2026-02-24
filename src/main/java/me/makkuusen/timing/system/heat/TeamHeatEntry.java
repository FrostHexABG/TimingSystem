package me.makkuusen.timing.system.heat;

import co.aikar.idb.DbRow;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import org.bukkit.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TeamHeatEntry {
    
    private int id;
    private Heat heat;
    private Team team;
    private UUID activeDriverUUID;
    private int currentLap;
    private int currentCheckpoint;
    private Instant startTime;
    private Instant endTime;
    private Integer position;
    private int startPosition;
    private int pits;
    private List<Lap> laps;
    private boolean finished;
    private int lastSwapLap = -1;
    public TeamHeatEntry(DbRow data, Heat heat) {
        this.id = data.getInt("id");
        this.heat = heat;
        this.team = TimingSystem.getTeamDatabase().getTeam(data.getInt("teamId")).orElse(null);
        this.activeDriverUUID = data.getString("activeDriverUUID") == null ? null : UUID.fromString(data.getString("activeDriverUUID"));
        this.currentLap = data.getInt("currentLap");
        this.currentCheckpoint = data.getInt("currentCheckpoint");
        this.startTime = data.getLong("startTime") == null ? null : Instant.ofEpochMilli(data.getLong("startTime"));
        this.endTime = data.getLong("endTime") == null ? null : Instant.ofEpochMilli(data.getLong("endTime"));
        this.position = data.get("position") == null ? null : data.getInt("position");
        this.startPosition = data.getInt("startPosition");
        this.pits = data.getInt("pits");
        this.finished = data.get("finished") instanceof Boolean ? data.get("finished") : data.get("finished").equals(1);
        this.laps = new ArrayList<>();
    }

    public void setActiveDriver(UUID playerUUID) {
        if (!isPlayerInTeam(playerUUID)) {
            throw new IllegalArgumentException("Player is not in this team");
        }
        this.activeDriverUUID = playerUUID;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "activeDriverUUID", playerUUID.toString());
    }

    public TPlayer getActiveDriver() {
        if (activeDriverUUID == null) {
            return null;
        }
        return TSDatabase.getPlayer(activeDriverUUID);
    }

    public boolean isPlayerInTeam(UUID playerUUID) {
        if (team == null) {
            return false;
        }
        TPlayer player = TSDatabase.getPlayer(playerUUID);
        return player != null && team.hasPlayer(player);
    }

    public void swapDriver(UUID newDriverUUID) {
        setActiveDriver(newDriverUUID);
        this.lastSwapLap = this.currentLap;
    }
    
    public boolean hasSwappedThisLap() {
        return this.lastSwapLap == this.currentLap;
    }

    public Location getLastCheckpointLocation() {
        if (heat == null || heat.getEvent() == null || heat.getEvent().getTrack() == null) {
            return null;
        }

        var track = heat.getEvent().getTrack();
        var checkpointRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.CHECKPOINT);

        if (currentCheckpoint == 0 || checkpointRegions.isEmpty()) {
            var startRegion = track.getTrackRegions().getStart();
            if (startRegion.isPresent()) {
                return startRegion.get().getSpawnLocation();
            }
            return null;
        }

        int checkpointIndex = currentCheckpoint - 1;
        if (checkpointIndex >= 0 && checkpointIndex < checkpointRegions.size()) {
            return checkpointRegions.get(checkpointIndex).getSpawnLocation();
        }

        var startRegion = track.getTrackRegions().getStart();
        if (startRegion.isPresent()) {
            return startRegion.get().getSpawnLocation();
        }

        return null;
    }

    public void updateRaceProgress(int lap, int checkpoint) {
        this.currentLap = lap;
        this.currentCheckpoint = checkpoint;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "currentLap", lap);
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "currentCheckpoint", checkpoint);
    }

    public void incrementPits() {
        this.pits++;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "pits", pits);
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "finished", finished);
    }

    public void setPosition(Integer position) {
        this.position = position;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "position", position);
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "startTime", startTime == null ? null : startTime.toEpochMilli());
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
        TimingSystem.getEventDatabase().teamHeatEntrySet(id, "endTime", endTime == null ? null : endTime.toEpochMilli());
    }

    public java.util.Optional<Lap> getBestLap() {
        if (laps.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (laps.get(0).getLapTime() == -1) {
            return java.util.Optional.empty();
        }
        Lap bestLap = laps.get(0);
        for (Lap lap : laps) {
            if (lap.getLapTime() != -1 && lap.getLapTime() < bestLap.getLapTime()) {
                bestLap = lap;
            }
        }
        return java.util.Optional.of(bestLap);
    }
}
