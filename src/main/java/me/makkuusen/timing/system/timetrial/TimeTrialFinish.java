package me.makkuusen.timing.system.timetrial;


import co.aikar.idb.DbRow;
import com.google.common.collect.ContiguousSet;
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

    private static final long[] NO_CHECKPOINTS = new long[0];

    @Getter
    private final int id;
    private final int trackId;
    private final UUID uuid;
    private final int date;
    private final int time;
    private long[] checkpointArray = NO_CHECKPOINTS;
    private Map<Integer, Long> checkpointMap;

    public TimeTrialFinish(DbRow data) {
        this.id = data.getInt("id");
        this.trackId = data.getInt("trackId");
        this.uuid = data.getString("uuid") == null ? null : sharedUuid(UUID.fromString(data.getString("uuid")));
        this.date = data.getInt("date");
        this.time = data.getInt("time");
    }

    private static UUID sharedUuid(UUID uuid) {
        TPlayer player = TSDatabase.getPlayer(uuid);
        return player == null ? uuid : player.getUniqueId();
    }

    public long getDate() {
        return date;
    }

    public long getTime() {
        return time;
    }

    public void updateCheckpointTimes(Map<Integer, Long> checkpointTimes) {
        setCheckpointTimes(checkpointTimes);
    }

    public void updateCheckpointTimes(long[] checkpointTimes) {
        this.checkpointArray = checkpointTimes.length == 0 ? NO_CHECKPOINTS : checkpointTimes;
        this.checkpointMap = null;
    }

    public void insertCheckpoints(Map<Integer, Long> checkpointTimes) {
        setCheckpointTimes(checkpointTimes);
        checkpointTimes.keySet().forEach(key -> TimingSystem.getTrackDatabase().createCheckpointFinish(id, key, checkpointTimes.get(key)));
    }

    private void setCheckpointTimes(Map<Integer, Long> checkpointTimes) {
        long[] array = checkpointTimes.getClass() == HashMap.class ? toArray(checkpointTimes) : null;
        if (array == null) {
            this.checkpointArray = NO_CHECKPOINTS;
            this.checkpointMap = checkpointTimes;
        } else {
            updateCheckpointTimes(array);
        }
    }

    private static long[] toArray(Map<Integer, Long> checkpointTimes) {
        long[] array = new long[checkpointTimes.size()];
        for (int i = 0; i < array.length; i++) {
            Long checkpointTime = checkpointTimes.get(i + 1);
            if (checkpointTime == null) {
                return null;
            }
            array[i] = checkpointTime;
        }
        return array;
    }

    public @Nullable Long getCheckpointTime(int checkpoint) {
        if (checkpointMap != null) {
            return checkpointMap.get(checkpoint);
        }
        return checkpoint >= 1 && checkpoint <= checkpointArray.length ? Long.valueOf(checkpointArray[checkpoint - 1]) : null;
    }

    public Set<Integer> getCheckpointKeys() {
        return checkpointMap != null ? checkpointMap.keySet() : ContiguousSet.closedOpen(1, checkpointArray.length + 1);
    }

    public boolean hasCheckpointTimes() {
        return checkpointMap != null ? !checkpointMap.isEmpty() : checkpointArray.length > 0;
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
