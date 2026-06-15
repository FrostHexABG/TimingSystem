package me.makkuusen.timing.system.track;

import co.aikar.idb.DB;
import co.aikar.taskchain.TaskChain;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.timetrial.TimeTrialAttempt;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Getter
public class TimeTrials {

    // Lightweight aggregates instead of storing every TimeTrialAttempt (saves significant RAM for busy servers).
    // Per-player attempt data is loaded on-demand (async) when stats/time-spent are requested for a specific player.
    // Track-wide total attempt count is pre-loaded cheaply via COUNT at startup.
    private final Map<TPlayer, Integer> playerAttemptCounts = new HashMap<>();
    private final Map<TPlayer, Long> playerAttemptSums = new HashMap<>();
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
        return timeTrialFinishes.containsKey(tPlayer) || playerAttemptCounts.containsKey(tPlayer);
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
        if (!playerAttemptCounts.containsKey(tPlayer)) {
            loadPlayerAttemptStatsAsync(tPlayer);
            return 0;
        }
        return playerAttemptCounts.getOrDefault(tPlayer, 0);
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
        if (tPlayer != null && !playerAttemptSums.containsKey(tPlayer)) {
            loadPlayerAttemptStatsAsync(tPlayer);
        }
        long time = 0L;

        if (tPlayer != null && playerAttemptSums.containsKey(tPlayer)) {
            time += playerAttemptSums.get(tPlayer);
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
     * Asynchronously loads (or refreshes) the attempt count and time sum for a specific player on this track.
     * Populates the lightweight caches used by getPlayerTotalAttempts / getPlayerTotalTimeSpent / hasPlayed.
     * This avoids holding *all* historical attempts in RAM; data is fetched when first needed for a player.
     */
    public void loadPlayerAttemptStatsAsync(TPlayer tPlayer) {
        if (tPlayer == null || playerAttemptCounts.containsKey(tPlayer)) {
            return;
        }
        TaskChain<?> chain = TimingSystem.newChain();
        chain.async(() -> {
            try {
                UUID uuid = tPlayer.getUniqueId();
                int count = TimingSystem.getTrackDatabase().getPlayerAttemptCount(trackId, uuid);
                long sum = TimingSystem.getTrackDatabase().getPlayerAttemptSum(trackId, uuid);
                Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> {
                    playerAttemptCounts.put(tPlayer, count);
                    playerAttemptSums.put(tPlayer, sum);
                    // totalAttempts is populated at startup via cheap COUNT(*) per track (see loadAttemptTotals)
                    // and kept up-to-date by incrementAttempt on newAttempt(). No adjustment here.
                });
            } catch (SQLException e) {
                TimingSystem.getPlugin().getLogger().warning("Failed async load of attempt stats for track " + trackId + ": " + e.getMessage());
            }
        }).execute();
    }

}
