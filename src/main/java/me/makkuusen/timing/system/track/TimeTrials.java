package me.makkuusen.timing.system.track;

import co.aikar.idb.DB;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.timetrial.TimeTrialAttempt;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.UUID;

@Getter
public class TimeTrials {

    // Lightweight aggregates instead of storing every TimeTrialAttempt (saves significant RAM for busy servers).
    // Per-player attempt data is loaded on-demand (synchronously, fast indexed query) when stats/time-spent are requested.
    // Track-wide total attempt count is pre-loaded cheaply via COUNT at startup.
    private final Map<TPlayer, Integer> playerAttemptCounts = new ConcurrentHashMap<>();
    private final Map<TPlayer, Long> playerAttemptSums = new ConcurrentHashMap<>();
    private int totalAttempts = 0;

    private Map<TPlayer, List<TimeTrialFinish>> timeTrialFinishes = new HashMap<>();
    private List<TPlayer> cachedPositions = new ArrayList<>();
    @Getter
    private long totalTimeSpent = 0;
    private final int trackId;
    public TimeTrials(int trackId) {
        this.trackId = trackId;
    }

    public void setTotalAttempts(int total) {
        this.totalAttempts = total;
    }

    public void addFinish(TimeTrialFinish timeTrialFinish) {
        if (timeTrialFinishes.get(timeTrialFinish.getPlayer()) == null) {
            List<TimeTrialFinish> list = new ArrayList<>();
            list.add(timeTrialFinish);
            timeTrialFinishes.put(timeTrialFinish.getPlayer(), list);
            return;
        }
        if (timeTrialFinishes.get(timeTrialFinish.getPlayer()).contains(timeTrialFinish)) {
            return;
        }
        timeTrialFinishes.get(timeTrialFinish.getPlayer()).add(timeTrialFinish);
    }

    public TimeTrialFinish newFinish(long time, UUID uuid) {
        try {
            long date = ApiUtilities.getTimestamp();
            var finishId = DB.executeInsert("INSERT INTO `ts_finishes` (`trackId`, `uuid`, `date`, `time`, `isRemoved`) VALUES(" + trackId + ", '" + uuid + "', " + date + ", " + time + ", 0);");
            var dbRow = DB.getFirstRow("SELECT * FROM `ts_finishes` WHERE `id` = " + finishId + ";");
            TimeTrialFinish timeTrialFinish = new TimeTrialFinish(dbRow);

            addFinish(timeTrialFinish);
            return timeTrialFinish;
        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public void addAttempt(TimeTrialAttempt timeTrialAttempt) {
        // Legacy compatibility for any external direct adds (populates aggregates)
        TPlayer p = timeTrialAttempt.getPlayer();
        if (p != null) {
            incrementAttempt(p, timeTrialAttempt.getTime());
        }
    }

    private void incrementAttempt(TPlayer player, long time) {
        playerAttemptCounts.merge(player, 1, Integer::sum);
        playerAttemptSums.merge(player, time, Long::sum);
        totalAttempts++;
    }

    public TimeTrialAttempt newAttempt(long time, UUID uuid) {
        long date = ApiUtilities.getTimestamp();
        TimingSystem.getTrackDatabase().createAttempt(trackId, uuid, date, time);
        TimeTrialAttempt timeTrialAttempt = new TimeTrialAttempt(trackId, uuid, date, time);
        TPlayer tPlayer = timeTrialAttempt.getPlayer();
        if (tPlayer != null) {
            incrementAttempt(tPlayer, time);
        }
        return timeTrialAttempt;
    }

    public TimeTrialFinish getBestFinish(TPlayer player) {
        if (timeTrialFinishes.get(player) == null) {
            return null;
        }
        var times = timeTrialFinishes.get(player);
        List<TimeTrialFinish> ttTimes = new ArrayList<>(times);
        if (ttTimes.isEmpty()) {
            return null;
        }

        ttTimes.sort(new TimeTrialFinishComparator());
        return ttTimes.get(0);
    }

    //Used in ranked
    public TimeTrialFinish getBestFinish(TPlayer player, long untilDate) {
        if (timeTrialFinishes.get(player) == null) {
            return null;
        }
        var times = timeTrialFinishes.get(player).stream().filter(timeTrialFinish -> timeTrialFinish.getDate() <= untilDate).toList();
        List<TimeTrialFinish> ttTimes = new ArrayList<>(times);
        if (ttTimes.isEmpty()) {
            return null;
        }

        ttTimes.sort(new TimeTrialFinishComparator());
        return ttTimes.get(0);
    }


    public boolean hasPlayed(TPlayer tPlayer) {
        if (tPlayer == null) return false;
        Integer att = playerAttemptCounts.get(tPlayer);
        if (att == null) {
            ensurePlayerAttemptStatsLoaded(tPlayer);
            att = playerAttemptCounts.get(tPlayer);
        }
        boolean hasAttempts = (att != null && att > 0);
        return timeTrialFinishes.containsKey(tPlayer) || hasAttempts;
    }

    public void deleteBestFinish(TPlayer player, TimeTrialFinish bestFinish) {
        timeTrialFinishes.get(player).remove(bestFinish);
        TimingSystem.getTrackDatabase().removeFinish(bestFinish.getId());
    }

    public void deleteAllFinishes(TPlayer player) {
        timeTrialFinishes.remove(player);
        TimingSystem.getTrackDatabase().removeAllFinishes(trackId, player.getUniqueId());
    }

    public void deleteAllFinishes() {
        timeTrialFinishes = new HashMap<>();
        TimingSystem.getTrackDatabase().removeAllFinishes(trackId);
    }

    public Integer getPlayerTopListPosition(TPlayer TPlayer) {
        var topList = getTopList(-1);
        for (int i = 0; i < topList.size(); i++) {
            if (topList.get(i).getPlayer().equals(TPlayer)) {
                return ++i;
            }
        }
        return -1;
    }

    public Integer getCachedPlayerPosition(TPlayer tPlayer) {
        int pos = cachedPositions.indexOf(tPlayer);
        if (pos != -1) {
            pos++;
        }
        return pos;
    }

    public List<TimeTrialFinish> getTopList(int limit) {

        List<TimeTrialFinish> bestTimes = new ArrayList<>();
        for (TPlayer player : timeTrialFinishes.keySet()) {
            TimeTrialFinish bestFinish = getBestFinish(player);
            if (bestFinish != null) {
                bestTimes.add(bestFinish);
            }
        }
        bestTimes.sort(new TimeTrialFinishComparator());
        cachedPositions = new ArrayList<>();
        bestTimes.forEach(timeTrialFinish -> cachedPositions.add(timeTrialFinish.getPlayer()));

        if (limit == -1) {
            return bestTimes;
        }

        return bestTimes.stream().limit(limit).collect(Collectors.toList());
    }

    public List<TimeTrialFinish> getTopList() {
        return getTopList(-1);
    }

    public int getPlayerTotalFinishes(TPlayer tPlayer) {
        if (!timeTrialFinishes.containsKey(tPlayer)) {
            return 0;
        }
        return timeTrialFinishes.get(tPlayer).size();
    }

    public int getPlayerTotalAttempts(TPlayer tPlayer) {
        if (tPlayer == null) return 0;
        Integer count = playerAttemptCounts.get(tPlayer);
        if (count == null) {
            ensurePlayerAttemptStatsLoaded(tPlayer);
            count = playerAttemptCounts.get(tPlayer);
        }
        return count == null ? 0 : count;
    }

    public int getTotalFinishes() {
        int laps = 0;
        for (List<TimeTrialFinish> l : timeTrialFinishes.values()) {
            laps += l.size();
        }
        return laps;

    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public long getPlayerTotalTimeSpent(TPlayer tPlayer) {
        if (tPlayer != null) {
            Integer c = playerAttemptCounts.get(tPlayer);
            if (c == null) {
                ensurePlayerAttemptStatsLoaded(tPlayer);
            }
        }
        long time = 0L;

        if (tPlayer != null && playerAttemptSums.containsKey(tPlayer)) {
            time += playerAttemptSums.getOrDefault(tPlayer, 0L);
        }
        if (timeTrialFinishes.containsKey(tPlayer)) {
            for (TimeTrialFinish l : timeTrialFinishes.get(tPlayer)) {
                time += l.getTime();
            }
        }
        return time;
    }

    public void setTotalTimeSpent(long time) {
        totalTimeSpent = time;
    }

    /**
     * Ensures attempt stats for the given player on this track are loaded from the DB if not already cached.
     * Performs a fast indexed query on demand (synchronously).
     * This avoids holding *all* historical attempts in RAM while keeping stats accurate when accessed
     * (e.g. in GUIs, lore, commands, and hasPlayed checks).
     */
    private void ensurePlayerAttemptStatsLoaded(TPlayer tPlayer) {
        if (tPlayer == null) return;
        if (playerAttemptCounts.get(tPlayer) != null) return;

        int count = 0;
        long sum = 0L;
        try {
            UUID uuid = tPlayer.getUniqueId();
            count = TimingSystem.getTrackDatabase().getPlayerAttemptCount(trackId, uuid);
            sum = TimingSystem.getTrackDatabase().getPlayerAttemptSum(trackId, uuid);
        } catch (SQLException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to load attempt stats for track " + trackId + ": " + e.getMessage());
            // cache 0 to avoid repeated attempts
        }
        playerAttemptCounts.put(tPlayer, count);
        playerAttemptSums.put(tPlayer, sum);
    }

}
