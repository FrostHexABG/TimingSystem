package me.makkuusen.timing.system.timetrial;


import co.aikar.idb.DbRow;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.theme.Theme;
import net.kyori.adventure.text.Component;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TimeTrialFinish implements Comparator<TimeTrialFinish> {

    @Getter
    private final int id;
    private final int trackId;
    private final UUID uuid;
    @Getter
    private final long date;
    @Getter
    private final long time;
    private Map<Integer, Long> checkpointTimes = new HashMap<>();
    private volatile boolean checkpointsRequested;

    public TimeTrialFinish(DbRow data) {
        this.id = data.getInt("id");
        this.trackId = data.getInt("trackId");
        this.uuid = data.getString("uuid") == null ? null : UUID.fromString(data.getString("uuid"));
        this.date = data.getInt("date");
        this.time = data.getInt("time");
    }

    public void updateCheckpointTimes(Map<Integer, Long> checkpointTimes) {
        this.checkpointTimes = checkpointTimes;
    }

    public void insertCheckpoints(Map<Integer, Long> checkpointTimes) {
        this.checkpointTimes = checkpointTimes;
        checkpointTimes.keySet().forEach(key -> TimingSystem.getTrackDatabase().createCheckpointFinish(id, key, checkpointTimes.get(key)));
    }

    public @Nullable Long getCheckpointTime(int checkpoint) {
        return checkpointTimes.get(checkpoint);
    }

    public Set<Integer> getCheckpointKeys() {
        return checkpointTimes.keySet();
    }

    public boolean hasCheckpointTimes() {
        return !checkpointTimes.isEmpty();
    }

    public boolean isCheckpointsRequested() {
        return checkpointsRequested;
    }

    public void markCheckpointsRequested() {
        checkpointsRequested = true;
    }

    public TPlayer getPlayer() {
        return TSDatabase.getPlayer(uuid);
    }

    public int getTrack() {
        return trackId;
    }

    public Component getDeltaToOther(TimeTrialFinish other, Theme theme, int latestCheckpoint) {
        if (latestCheckpoint > 0) {
            if (other.hasCheckpointTimes() && other.getCheckpointTime(latestCheckpoint) != null) {
                if (other.getDate() > TrackDatabase.getTrackById(getTrack()).get().getDateChanged()) {
                    var otherCheckpoint = other.getCheckpointTime(latestCheckpoint);
                    var currentCheckpoint = getCheckpointTime(latestCheckpoint);
                    if (otherCheckpoint < currentCheckpoint) {
                        return Component.text(" +" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - otherCheckpoint)).color(theme.getError());
                    } else if (otherCheckpoint == currentCheckpoint) {
                        return Component.text(" =" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - otherCheckpoint)).color(theme.getWarning());
                    }else {
                        return Component.text(" -" + ApiUtilities.formatAsPersonalGap(otherCheckpoint - currentCheckpoint)).color(theme.getSuccess());
                    }
                }
            }
        }
        return Component.empty();
    }


    @Override
    public int compare(TimeTrialFinish f1, TimeTrialFinish f2) {
        int result = Long.compare(f1.getTime(), f2.getTime());
        if (result == 0) {
            return Long.compare(f1.getDate(), f2.getDate());
        }
        return result;
    }

    public int compareTo(TimeTrialFinish rf) {
        int result = Long.compare(getTime(), rf.getTime());
        if (result == 0) {
            return Long.compare(getDate(), rf.getDate());
        }
        return result;
    }

    public int compareDate(TimeTrialFinish rf) {
        return Long.compare(getDate(), rf.getDate());
    }

    @Override
    public boolean equals(Object rf) {
        if (rf instanceof TimeTrialFinish timeTrialFinish) {
            return timeTrialFinish.getDate() == getDate() && timeTrialFinish.getPlayer() == getPlayer();
        }
        return false;
    }
}
