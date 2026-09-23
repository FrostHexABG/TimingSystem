package me.makkuusen.timing.system.track;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.MySQLDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.gui.TrackPageGui;
import me.makkuusen.timing.system.timetrial.TimeTrialAttempt;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;
import me.makkuusen.timing.system.tplayer.TPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Getter
public class TimeTrials {

    // One best finish per player. Full history and checkpoint splits stay in the database
    // until a command or an active time trial asks for them.
    private Map<TPlayer, List<TimeTrialFinish>> timeTrialFinishes = new HashMap<>();
    private final Map<UUID, Integer> finishCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> finishTimeSums = new ConcurrentHashMap<>();
    private final Set<Integer> suppressedFinishIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> historyClearedAfter = new ConcurrentHashMap<>();
    private long trackHistoryClearedAt = Long.MIN_VALUE;
    private int totalFinishes = 0;

    // Lightweight aggregates instead of storing every TimeTrialAttempt.
    private final Map<TPlayer, Integer> playerAttemptCounts = new ConcurrentHashMap<>();
    private final Map<TPlayer, Long> playerAttemptSums = new ConcurrentHashMap<>();
    private int totalAttempts = 0;

    private List<TPlayer> cachedPositions = new ArrayList<>();
    @Getter
    private long totalTimeSpent = 0;
    private final int trackId;

    private static int attemptCacheGeneration = 0;
    private static final Set<UUID> attemptStatsLoaded = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> attemptLoadPending = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<Runnable>> attemptLoadCallbacks = new ConcurrentHashMap<>();

    public TimeTrials(int trackId) {
        this.trackId = trackId;
    }

    public static void clearAttemptStatCache() {
        attemptCacheGeneration++;
        attemptStatsLoaded.clear();
        attemptLoadPending.clear();
    }

    public static boolean hasAttemptStats(UUID uuid) {
        return uuid != null && attemptStatsLoaded.contains(uuid);
    }

    public static void loadAttemptStatsAsync(UUID uuid) {
        loadAttemptStatsAsync(uuid, null);
    }

    /**
     * One grouped query for every track this player has attempted.
     * {@code onComplete} runs on the main thread after the cache is filled.
     */
    public static void loadAttemptStatsAsync(UUID uuid, Runnable onComplete) {
        if (uuid == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (attemptStatsLoaded.contains(uuid)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (onComplete != null) {
            attemptLoadCallbacks.compute(uuid, (id, callbacks) -> {
                if (callbacks == null) {
                    callbacks = Collections.synchronizedList(new ArrayList<>());
                }
                callbacks.add(onComplete);
                return callbacks;
            });
        }
        if (!attemptLoadPending.add(uuid)) {
            return;
        }
        int generation = attemptCacheGeneration;
        TimingSystem.newChain()
                .asyncFirst(() -> {
                    try {
                        return TimingSystem.getTrackDatabase().selectPlayerAttemptStats(uuid);
                    } catch (SQLException e) {
                        TimingSystem.getPlugin().getLogger().warning("Failed to load attempt stats for " + uuid + ": " + e.getMessage());
                        return List.<DbRow>of();
                    }
                })
                .syncLast(rows -> {
                    if (generation != attemptCacheGeneration) {
                        attemptLoadPending.remove(uuid);
                        loadAttemptStatsAsync(uuid, onComplete);
                        return;
                    }
                    Map<Integer, int[]> counts = new HashMap<>();
                    Map<Integer, Long> sums = new HashMap<>();
                    for (DbRow row : rows) {
                        int id = row.getInt("trackId");
                        counts.put(id, new int[]{MySQLDatabase.asInt(row.get("cnt"))});
                        sums.put(id, MySQLDatabase.asLong(row.get("s")));
                    }
                    TPlayer tPlayer = TSDatabase.getPlayer(uuid);
                    for (Track track : TrackDatabase.tracks) {
                        int count = counts.containsKey(track.getId()) ? counts.get(track.getId())[0] : 0;
                        long sum = sums.getOrDefault(track.getId(), 0L);
                        track.getTimeTrials().applyAttemptStats(tPlayer, count, sum);
                    }
                    attemptStatsLoaded.add(uuid);
                    attemptLoadPending.remove(uuid);
                    if (tPlayer != null && tPlayer.getPlayer() != null && tPlayer.getOpenGui() instanceof TrackPageGui page) {
                        TrackPageGui.openNewTrackPage(page, tPlayer, page.getTitle());
                    }
                    List<Runnable> callbacks = attemptLoadCallbacks.remove(uuid);
                    if (callbacks != null) {
                        for (Runnable callback : callbacks) {
                            callback.run();
                        }
                    }
                })
                .execute();
    }

    public void setTotalAttempts(int total) {
        this.totalAttempts = total;
    }

    public void setTotalFinishes(int total) {
        this.totalFinishes = total;
    }

    public void setFinishStats(UUID uuid, int count, long sum) {
        if (uuid == null) {
            return;
        }
        finishCounts.put(uuid, count);
        finishTimeSums.put(uuid, sum);
    }

    public void setBestFinish(TimeTrialFinish timeTrialFinish) {
        TPlayer player = timeTrialFinish.getPlayer();
        if (player == null) {
            return;
        }
        List<TimeTrialFinish> list = new ArrayList<>();
        list.add(timeTrialFinish);
        timeTrialFinishes.put(player, list);
    }

    public void addFinish(TimeTrialFinish timeTrialFinish) {
        TPlayer player = timeTrialFinish.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        finishCounts.merge(uuid, 1, Integer::sum);
        finishTimeSums.merge(uuid, timeTrialFinish.getTime(), Long::sum);
        totalFinishes++;

        List<TimeTrialFinish> existing = timeTrialFinishes.get(player);
        if (existing == null || existing.isEmpty() || timeTrialFinish.compareTo(existing.get(0)) < 0) {
            List<TimeTrialFinish> list = new ArrayList<>();
            list.add(timeTrialFinish);
            timeTrialFinishes.put(player, list);
            getTopList();
        }
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

    private void applyAttemptStats(TPlayer player, int count, long sum) {
        if (player == null) {
            return;
        }
        int existing = playerAttemptCounts.getOrDefault(player, 0);
        if (existing > count) {
            // A finish attempt was counted in memory while this snapshot was in flight.
            return;
        }
        playerAttemptCounts.put(player, count);
        playerAttemptSums.put(player, sum);
    }

    public TimeTrialFinish getBestFinish(TPlayer player) {
        if (player == null || timeTrialFinishes.get(player) == null) {
            return null;
        }
        var times = timeTrialFinishes.get(player);
        if (times.isEmpty()) {
            return null;
        }
        List<TimeTrialFinish> ttTimes = new ArrayList<>(times);
        ttTimes.sort(new TimeTrialFinishComparator());
        return ttTimes.get(0);
    }

    // Used by ranked. The stored best is also the best as of untilDate when it is old enough.
    public TimeTrialFinish getBestFinish(TPlayer player, long untilDate) {
        if (player == null) {
            return null;
        }
        TimeTrialFinish best = getBestFinish(player);
        if (best != null && best.getDate() <= untilDate && isVisible(best)) {
            return best;
        }
        try {
            DbRow row = TimingSystem.getTrackDatabase().selectBestFinishUntil(
                    trackId,
                    player.getUniqueId(),
                    untilDate,
                    visibilityCutoff(player.getUniqueId()),
                    suppressedFinishIds
            );
            if (row == null) {
                return null;
            }
            TimeTrialFinish finish = new TimeTrialFinish(row);
            return isVisible(finish) ? finish : null;
        } catch (SQLException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to load historical best for track " + trackId + ": " + e.getMessage());
            return null;
        }
    }

    public void prefetchBestCheckpointTimes(TPlayer player) {
        TimeTrialFinish best = getBestFinish(player);
        if (best == null || best.hasCheckpointTimes() || best.isCheckpointsRequested()) {
            return;
        }
        best.markCheckpointsRequested();
        int finishId = best.getId();
        TimingSystem.newChain()
                .asyncFirst(() -> {
                    try {
                        return TimingSystem.getTrackDatabase().selectCheckpointTimes(finishId);
                    } catch (SQLException e) {
                        TimingSystem.getPlugin().getLogger().warning("Failed to load checkpoints for finish " + finishId + ": " + e.getMessage());
                        return List.<DbRow>of();
                    }
                })
                .syncLast(rows -> {
                    TimeTrialFinish current = getBestFinish(player);
                    if (current == null || current.getId() != finishId) {
                        return;
                    }
                    Map<Integer, Long> checkpoints = new HashMap<>();
                    for (DbRow row : rows) {
                        checkpoints.put(row.getInt("checkpointIndex"), (long) row.getInt("time"));
                    }
                    current.updateCheckpointTimes(checkpoints);
                })
                .execute();
    }

    public List<TimeTrialFinish> loadPlayerHistoryPage(UUID uuid, int limit, int offset) throws SQLException {
        List<DbRow> rows = TimingSystem.getTrackDatabase().selectPlayerFinishPage(trackId, uuid, limit, offset);
        return withCheckpoints(rows);
    }

    public static List<TimeTrialFinish> loadPlayerHistoryPage(UUID uuid, Collection<Integer> trackIds, int limit, int offset) throws SQLException {
        List<DbRow> rows = TimingSystem.getTrackDatabase().selectPlayerFinishPageForTracks(trackIds, uuid, limit, offset);
        List<TimeTrialFinish> visible = new ArrayList<>();
        for (DbRow row : rows) {
            TimeTrialFinish finish = new TimeTrialFinish(row);
            Track track = TrackDatabase.getTrackById(finish.getTrack()).orElse(null);
            if (track != null && track.getTimeTrials().isVisible(finish)) {
                visible.add(finish);
            }
        }
        attachCheckpoints(visible);
        return visible;
    }

    private List<TimeTrialFinish> withCheckpoints(List<DbRow> rows) throws SQLException {
        List<TimeTrialFinish> visible = new ArrayList<>();
        for (DbRow row : rows) {
            TimeTrialFinish finish = new TimeTrialFinish(row);
            if (isVisible(finish)) {
                visible.add(finish);
            }
        }
        attachCheckpoints(visible);
        return visible;
    }

    private static void attachCheckpoints(List<TimeTrialFinish> finishes) throws SQLException {
        if (finishes.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (TimeTrialFinish finish : finishes) {
            ids.add(finish.getId());
        }
        List<DbRow> rows = TimingSystem.getTrackDatabase().selectCheckpointTimesForFinishes(ids);
        Map<Integer, Map<Integer, Long>> byFinish = new HashMap<>();
        for (DbRow row : rows) {
            byFinish.computeIfAbsent(row.getInt("finishId"), ignored -> new HashMap<>())
                    .put(row.getInt("checkpointIndex"), (long) row.getInt("time"));
        }
        for (TimeTrialFinish finish : finishes) {
            Map<Integer, Long> checkpoints = byFinish.get(finish.getId());
            if (checkpoints != null) {
                finish.updateCheckpointTimes(checkpoints);
            }
        }
    }

    public boolean isVisible(TimeTrialFinish finish) {
        if (finish == null || suppressedFinishIds.contains(finish.getId())) {
            return false;
        }
        if (finish.getDate() <= trackHistoryClearedAt) {
            return false;
        }
        TPlayer player = finish.getPlayer();
        if (player == null) {
            return false;
        }
        Long clearedAt = historyClearedAfter.get(player.getUniqueId());
        return clearedAt == null || finish.getDate() > clearedAt;
    }

    private long visibilityCutoff(UUID uuid) {
        long cutoff = trackHistoryClearedAt;
        Long clearedAt = historyClearedAfter.get(uuid);
        if (clearedAt != null && clearedAt > cutoff) {
            cutoff = clearedAt;
        }
        return cutoff;
    }

    public boolean hasPlayed(TPlayer tPlayer) {
        if (tPlayer == null) {
            return false;
        }
        if (timeTrialFinishes.containsKey(tPlayer)) {
            return true;
        }
        return getPlayerTotalAttempts(tPlayer) > 0;
    }

    public void deleteBestFinish(TPlayer player, TimeTrialFinish bestFinish) {
        List<TimeTrialFinish> list = timeTrialFinishes.get(player);
        if (list != null) {
            list.remove(bestFinish);
            if (list.isEmpty()) {
                timeTrialFinishes.remove(player);
            }
        }
        suppressedFinishIds.add(bestFinish.getId());
        UUID uuid = player.getUniqueId();
        int remaining = finishCounts.merge(uuid, -1, Integer::sum);
        if (remaining <= 0) {
            finishCounts.put(uuid, 0);
            timeTrialFinishes.remove(player);
        }
        long remainingSum = finishTimeSums.merge(uuid, -bestFinish.getTime(), Long::sum);
        if (remainingSum < 0) {
            finishTimeSums.put(uuid, 0L);
        }
        if (totalFinishes > 0) {
            totalFinishes--;
        }
        TimingSystem.getTrackDatabase().removeFinish(bestFinish.getId());
    }

    public void promoteBestFinish(TPlayer player, TimeTrialFinish next) {
        if (player == null || next == null || !isVisible(next)) {
            return;
        }
        setBestFinish(next);
        getTopList();
    }

    public DbRow queryNextBest(UUID uuid) throws SQLException {
        return TimingSystem.getTrackDatabase().selectNextBestFinish(trackId, uuid, visibilityCutoff(uuid), suppressedFinishIds);
    }

    public void deleteAllFinishes(TPlayer player) {
        UUID uuid = player.getUniqueId();
        int removed = finishCounts.getOrDefault(uuid, 0);
        totalFinishes = Math.max(0, totalFinishes - removed);
        finishCounts.put(uuid, 0);
        finishTimeSums.put(uuid, 0L);
        timeTrialFinishes.remove(player);
        historyClearedAfter.put(uuid, ApiUtilities.getTimestamp());
        getTopList();
        TimingSystem.getTrackDatabase().removeAllFinishes(trackId, uuid);
    }

    public void deleteAllFinishes() {
        timeTrialFinishes = new HashMap<>();
        finishCounts.clear();
        finishTimeSums.clear();
        totalFinishes = 0;
        historyClearedAfter.clear();
        trackHistoryClearedAt = ApiUtilities.getTimestamp();
        cachedPositions = new ArrayList<>();
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
        if (tPlayer == null) {
            return 0;
        }
        int count = finishCounts.getOrDefault(tPlayer.getUniqueId(), 0);
        if (count == 0 && timeTrialFinishes.containsKey(tPlayer)) {
            return 1;
        }
        return count;
    }

    public int getPlayerTotalAttempts(TPlayer tPlayer) {
        if (tPlayer == null) {
            return 0;
        }
        if (!attemptStatsLoaded.contains(tPlayer.getUniqueId())) {
            loadAttemptStatsAsync(tPlayer.getUniqueId());
        }
        return playerAttemptCounts.getOrDefault(tPlayer, 0);
    }

    public int getTotalFinishes() {
        return totalFinishes;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public long getPlayerTotalTimeSpent(TPlayer tPlayer) {
        if (tPlayer == null) {
            return 0L;
        }
        if (!attemptStatsLoaded.contains(tPlayer.getUniqueId())) {
            loadAttemptStatsAsync(tPlayer.getUniqueId());
        }
        long time = finishTimeSums.getOrDefault(tPlayer.getUniqueId(), 0L);
        time += playerAttemptSums.getOrDefault(tPlayer, 0L);
        return time;
    }

    public void setTotalTimeSpent(long time) {
        totalTimeSpent = time;
    }
}
