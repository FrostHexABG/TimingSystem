package me.makkuusen.timing.system.track;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.LeaderboardManager;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.MySQLDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.gui.TrackPageGui;
import me.makkuusen.timing.system.timetrial.TimeTrialAttempt;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;
import me.makkuusen.timing.system.tplayer.TPlayer;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public class TimeTrials {

    private static final int NEXT_BEST_ATTEMPTS = 3;
    private static final int NEXT_BEST_RETRY_TICKS = 20;

    // One best finish per player. Full history and checkpoint splits stay in the database
    // until a command or an active time trial asks for them.
    private volatile Map<TPlayer, List<TimeTrialFinish>> timeTrialFinishes = new HashMap<>();
    private final Map<UUID, Integer> finishCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> finishTimeSums = new ConcurrentHashMap<>();
    private final Set<Integer> suppressedFinishIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> historyClearedAfter = new ConcurrentHashMap<>();
    private final Map<Integer, NotedAfterClear> visibleAfterClear = new ConcurrentHashMap<>();
    private final List<TimeTrialFinish> finishesBeforeIndex = new ArrayList<>();
    private volatile long trackHistoryClearedAt = Long.MIN_VALUE;
    private volatile boolean finishIndexApplied = false;
    private int totalFinishes = 0;

    private List<TimeTrialFinish> stashedFinishes = List.of();
    private boolean stashedTrackCleared;
    private Map<UUID, Long> stashedPlayerClears = Map.of();

    // Lightweight aggregates instead of storing every TimeTrialAttempt.
    private final Map<TPlayer, Integer> playerAttemptCounts = new ConcurrentHashMap<>();
    private final Map<TPlayer, Long> playerAttemptSums = new ConcurrentHashMap<>();
    private int totalAttempts = 0;

    private List<TPlayer> cachedPositions = new ArrayList<>();
    @Getter
    private long totalTimeSpent = 0;
    private final int trackId;

    private static int attemptCacheGeneration = 0;
    private static volatile boolean trackAttemptTotalsLoaded = false;
    private static final Set<UUID> attemptStatsLoaded = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> attemptLoadPending = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<Runnable>> attemptLoadCallbacks = new ConcurrentHashMap<>();
    private static final List<PendingAttempt> pendingAttempts = Collections.synchronizedList(new ArrayList<>());

    public TimeTrials(int trackId) {
        this.trackId = trackId;
    }

    public static void clearAttemptStatCache() {
        attemptCacheGeneration++;
        attemptStatsLoaded.clear();
        attemptLoadPending.clear();
        trackAttemptTotalsLoaded = false;
        synchronized (pendingAttempts) {
            for (PendingAttempt pending : pendingAttempts) {
                pending.inTrackTotal = false;
                pending.inPlayerStats = false;
                pending.addedToTrackTotal = false;
                pending.addedToPlayerStats = false;
            }
        }
    }

    public static boolean hasAttemptStats(UUID uuid) {
        return uuid != null && attemptStatsLoaded.contains(uuid);
    }

    public static void loadAttemptStatsAsync(UUID uuid) {
        loadAttemptStatsAsync(uuid, null);
    }

    /**
     * One grouped query for every track this player has attempted.
     * {@code onComplete} runs on the main thread after the cache is filled, or after a failed load.
     */
    public static void loadAttemptStatsAsync(UUID uuid, Runnable onComplete) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> loadAttemptStatsAsync(uuid, onComplete));
            return;
        }
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
                        return null;
                    }
                })
                .syncLast(rows -> {
                    if (rows == null) {
                        attemptLoadPending.remove(uuid);
                        runAttemptLoadCallbacks(uuid);
                        return;
                    }
                    if (generation != attemptCacheGeneration) {
                        attemptLoadPending.remove(uuid);
                        loadAttemptStatsAsync(uuid, null);
                        return;
                    }
                    TPlayer tPlayer = TSDatabase.getPlayer(uuid);
                    if (tPlayer == null) {
                        attemptLoadPending.remove(uuid);
                        runAttemptLoadCallbacks(uuid);
                        return;
                    }
                    Map<Integer, long[]> stats = new HashMap<>();
                    for (DbRow row : rows) {
                        stats.put(row.getInt("trackId"), new long[]{
                                MySQLDatabase.asInt(row.get("cnt")),
                                MySQLDatabase.asLong(row.get("s")),
                                MySQLDatabase.asLong(row.get("maxId"))
                        });
                    }
                    for (Track track : TrackDatabase.tracks) {
                        long[] row = stats.get(track.getId());
                        int count = row == null ? 0 : (int) row[0];
                        long sum = row == null ? 0L : row[1];
                        long maxId = row == null ? 0L : row[2];
                        track.getTimeTrials().applyAttemptStats(tPlayer, count, sum, maxId);
                    }
                    attemptStatsLoaded.add(uuid);
                    attemptLoadPending.remove(uuid);
                    if (tPlayer.getPlayer() != null && tPlayer.getOpenGui() instanceof TrackPageGui page) {
                        TrackPageGui.openNewTrackPage(page, tPlayer, page.getTitle());
                    }
                    runAttemptLoadCallbacks(uuid);
                })
                .execute();
    }

    private static void runAttemptLoadCallbacks(UUID uuid) {
        List<Runnable> callbacks = attemptLoadCallbacks.remove(uuid);
        if (callbacks == null) {
            return;
        }
        for (Runnable callback : callbacks) {
            callback.run();
        }
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

    public Set<Integer> copySuppressedFinishIds() {
        return Set.copyOf(suppressedFinishIds);
    }

    public void stashAndClearFinishCache() {
        stashedFinishes = new ArrayList<>(finishesBeforeIndex);
        stashedTrackCleared = trackHistoryClearedAt != Long.MIN_VALUE;
        stashedPlayerClears = new HashMap<>(historyClearedAfter);
        timeTrialFinishes = new HashMap<>();
        finishCounts.clear();
        finishTimeSums.clear();
        totalFinishes = 0;
    }

    public void replayStashedFinishes(long snapshotMaxId, Set<Integer> suppressedAtSnapshot, Set<Integer> suppressedCounted) {
        finishIndexApplied = true;
        Map<Integer, TimeTrialFinish> localFinishes = new LinkedHashMap<>();
        for (TimeTrialFinish finish : stashedFinishes) {
            if (finish != null) {
                localFinishes.put(finish.getId(), finish);
            }
        }
        for (TimeTrialFinish finish : finishesBeforeIndex) {
            if (finish != null) {
                localFinishes.putIfAbsent(finish.getId(), finish);
            }
        }
        finishesBeforeIndex.clear();
        stashedFinishes = List.of();

        boolean trackCleared = stashedTrackCleared;
        Map<UUID, Long> playerClears = stashedPlayerClears == null ? Map.of() : stashedPlayerClears;
        stashedPlayerClears = Map.of();
        Set<UUID> zeroed = new HashSet<>();
        if (trackCleared) {
            timeTrialFinishes = new HashMap<>();
            finishCounts.clear();
            finishTimeSums.clear();
            totalFinishes = 0;
        } else {
            for (UUID uuid : playerClears.keySet()) {
                clearPlayerFinishCache(uuid);
                zeroed.add(uuid);
            }
        }

        for (TimeTrialFinish finish : localFinishes.values()) {
            TPlayer player = finish.getPlayer();
            if (player == null) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            boolean playerZeroed = trackCleared || zeroed.contains(uuid);
            if (!isVisible(finish)) {
                if (!playerZeroed && countedInSnapshot(finish.getId(), snapshotMaxId, suppressedAtSnapshot, suppressedCounted)) {
                    subtractFinish(finish);
                }
                continue;
            }
            if (playerZeroed || finish.getId() > snapshotMaxId) {
                absorbFinish(finish);
            } else {
                keepCheckpointCopy(finish);
            }
        }

        for (UUID uuid : new ArrayList<>(finishCounts.keySet())) {
            int count = finishCounts.getOrDefault(uuid, 0);
            if (count <= 0) {
                continue;
            }
            TPlayer player = TSDatabase.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            TimeTrialFinish best = getBestFinish(player);
            if (best != null && !isVisible(best)) {
                boolean playerZeroed = trackCleared || zeroed.contains(uuid);
                if (!playerZeroed && countedInSnapshot(best.getId(), snapshotMaxId, suppressedAtSnapshot, suppressedCounted)) {
                    subtractFinish(best);
                } else {
                    timeTrialFinishes.remove(player);
                }
            }
            if (getBestFinish(player) == null && finishCounts.getOrDefault(uuid, 0) > 0) {
                refreshNextBest(player);
            }
        }
    }

    public static long[] takePendingAttempts(int trackId, UUID uuid, long maxId, boolean forTrackTotal) {
        long count = 0;
        long sum = 0;
        synchronized (pendingAttempts) {
            Iterator<PendingAttempt> attempts = pendingAttempts.iterator();
            while (attempts.hasNext()) {
                PendingAttempt pending = attempts.next();
                if (pending.trackId != trackId) {
                    continue;
                }
                if (uuid != null && !uuid.equals(pending.uuid)) {
                    continue;
                }
                if (forTrackTotal ? pending.inTrackTotal : pending.inPlayerStats) {
                    continue;
                }
                boolean countIt = false;
                if (!pending.failed) {
                    if (!pending.insertId.isDone()) {
                        countIt = true;
                    } else if (!pending.insertId.isCompletedExceptionally()) {
                        Long id = pending.insertId.getNow(null);
                        countIt = id == null || id > maxId;
                    }
                }
                if (countIt) {
                    count++;
                    sum += pending.time;
                    if (forTrackTotal) {
                        pending.addedToTrackTotal = true;
                    } else {
                        pending.addedToPlayerStats = true;
                    }
                }
                if (forTrackTotal) {
                    pending.inTrackTotal = true;
                } else {
                    pending.inPlayerStats = true;
                }
                if (pending.inTrackTotal && pending.inPlayerStats) {
                    attempts.remove();
                }
            }
        }
        return new long[]{count, sum};
    }

    public static void markTrackAttemptTotalsLoaded() {
        trackAttemptTotalsLoaded = true;
    }

    public void addFinish(TimeTrialFinish timeTrialFinish) {
        TPlayer player = timeTrialFinish.getPlayer();
        if (player == null) {
            return;
        }
        if (!finishIndexApplied) {
            finishesBeforeIndex.add(timeTrialFinish);
        }
        noteFinishCreatedAfterClear(timeTrialFinish);
        if (absorbFinish(timeTrialFinish)) {
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

    private void revertPlayerAttempt(TPlayer player, long time) {
        if (player == null) {
            return;
        }
        playerAttemptCounts.compute(player, (ignored, count) -> count == null || count <= 1 ? 0 : count - 1);
        playerAttemptSums.compute(player, (ignored, sum) -> sum == null || sum <= time ? 0L : sum - time);
    }

    private void revertAttemptTotal() {
        if (totalAttempts > 0) {
            totalAttempts--;
        }
    }

    public TimeTrialAttempt newAttempt(long time, UUID uuid) {
        long date = ApiUtilities.getTimestamp();
        CompletableFuture<Long> insertId = TimingSystem.getTrackDatabase().createAttempt(trackId, uuid, date, time);
        PendingAttempt pending = new PendingAttempt(trackId, uuid, time, insertId);
        synchronized (pendingAttempts) {
            pendingAttempts.add(pending);
        }
        TimeTrialAttempt timeTrialAttempt = new TimeTrialAttempt(trackId, uuid, date, time);
        TPlayer tPlayer = timeTrialAttempt.getPlayer();
        if (tPlayer != null) {
            incrementAttempt(tPlayer, time);
        }
        insertId.whenComplete((id, error) -> {
            if (error != null) {
                pending.failed = true;
                Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> reconcileFailedAttempt(pending));
            }
        });
        return timeTrialAttempt;
    }

    private void applyAttemptStats(TPlayer player, int count, long sum, long maxId) {
        if (player == null) {
            return;
        }
        long[] extra = takePendingAttempts(trackId, player.getUniqueId(), maxId, false);
        playerAttemptCounts.put(player, count + (int) extra[0]);
        playerAttemptSums.put(player, sum + extra[1]);
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
        if (!finishIndexApplied) {
            return null;
        }
        if (best == null && getPlayerTotalFinishes(player) <= 0) {
            return null;
        }
        try {
            UUID uuid = player.getUniqueId();
            long cutoff = visibilityCutoff(uuid);
            DbRow row = TimingSystem.getTrackDatabase().selectBestFinishUntil(
                    trackId,
                    uuid,
                    untilDate,
                    cutoff,
                    sameSecondIds(uuid, cutoff),
                    Set.copyOf(suppressedFinishIds)
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
                        return null;
                    }
                })
                .syncLast(rows -> {
                    if (rows == null) {
                        best.clearCheckpointsRequested();
                        return;
                    }
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
        if (uuid == null || limit <= 0) {
            return List.of();
        }
        List<TimeTrialFinish> page = collectVisiblePage(limit, Math.max(0, offset),
                (batch, dbOffset) -> TimingSystem.getTrackDatabase().selectPlayerFinishPage(trackId, uuid, batch, dbOffset),
                this::isVisible);
        attachCheckpoints(page);
        return page;
    }

    public static List<TimeTrialFinish> loadPlayerHistoryPage(UUID uuid, Collection<Integer> trackIds, int limit, int offset) throws SQLException {
        if (uuid == null || limit <= 0 || trackIds == null || trackIds.isEmpty()) {
            return List.of();
        }
        List<TimeTrialFinish> page = collectVisiblePage(limit, Math.max(0, offset),
                (batch, dbOffset) -> TimingSystem.getTrackDatabase().selectPlayerFinishPageForTracks(trackIds, uuid, batch, dbOffset),
                finish -> {
                    Track track = TrackDatabase.getTrackById(finish.getTrack()).orElse(null);
                    return track != null && track.getTimeTrials().isVisible(finish);
                });
        attachCheckpoints(page);
        return page;
    }

    private static List<TimeTrialFinish> collectVisiblePage(int limit, int offset, FinishPageQuery query, Predicate<TimeTrialFinish> visible) throws SQLException {
        List<TimeTrialFinish> page = new ArrayList<>();
        int skipped = 0;
        int dbOffset = 0;
        int batch = offset > Integer.MAX_VALUE - limit ? limit : Math.min(500, Math.max(limit, offset + limit));
        if (batch <= 0) {
            batch = limit;
        }
        while (page.size() < limit) {
            List<DbRow> rows = query.fetch(batch, dbOffset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            dbOffset += rows.size();
            for (DbRow row : rows) {
                TimeTrialFinish finish = new TimeTrialFinish(row);
                if (!visible.test(finish)) {
                    continue;
                }
                if (skipped < offset) {
                    skipped++;
                    continue;
                }
                page.add(finish);
                if (page.size() == limit) {
                    break;
                }
            }
            if (rows.size() < batch) {
                break;
            }
        }
        return page;
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
        if (!survivesClear(finish.getDate(), finish.getId(), trackHistoryClearedAt)) {
            return false;
        }
        TPlayer player = finish.getPlayer();
        if (player == null) {
            return false;
        }
        Long clearedAt = historyClearedAfter.get(player.getUniqueId());
        if (clearedAt == null) {
            return true;
        }
        return survivesClear(finish.getDate(), finish.getId(), clearedAt);
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
        TimeTrialFinish current = getBestFinish(player);
        if (current != null && next.compareTo(current) >= 0) {
            return;
        }
        setBestFinish(next);
        getTopList();
    }

    public void refreshNextBest(TPlayer player) {
        scheduleNextBestLookup(player, 0);
    }

    public DbRow queryNextBest(UUID uuid) throws SQLException {
        long cutoff = visibilityCutoff(uuid);
        return TimingSystem.getTrackDatabase().selectNextBestFinish(trackId, uuid, cutoff, sameSecondIds(uuid, cutoff), Set.copyOf(suppressedFinishIds));
    }

    public void deleteAllFinishes(TPlayer player) {
        UUID uuid = player.getUniqueId();
        int removed = finishCounts.getOrDefault(uuid, 0);
        totalFinishes = Math.max(0, totalFinishes - removed);
        finishCounts.put(uuid, 0);
        finishTimeSums.put(uuid, 0L);
        timeTrialFinishes.remove(player);
        visibleAfterClear.entrySet().removeIf(entry -> uuid.equals(entry.getValue().uuid));
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
        visibleAfterClear.clear();
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

    private boolean absorbFinish(TimeTrialFinish timeTrialFinish) {
        TPlayer player = timeTrialFinish.getPlayer();
        if (player == null) {
            return false;
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
            return true;
        }
        return false;
    }

    private void subtractFinish(TimeTrialFinish finish) {
        TPlayer player = finish.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int remaining = finishCounts.merge(uuid, -1, Integer::sum);
        if (remaining <= 0) {
            finishCounts.put(uuid, 0);
        }
        long remainingSum = finishTimeSums.merge(uuid, -finish.getTime(), Long::sum);
        if (remainingSum < 0) {
            finishTimeSums.put(uuid, 0L);
        }
        if (totalFinishes > 0) {
            totalFinishes--;
        }
        TimeTrialFinish cached = getBestFinish(player);
        if (cached != null && cached.getId() == finish.getId()) {
            timeTrialFinishes.remove(player);
        }
    }

    private void clearPlayerFinishCache(UUID uuid) {
        int snapshotCount = finishCounts.getOrDefault(uuid, 0);
        totalFinishes = Math.max(0, totalFinishes - snapshotCount);
        finishCounts.put(uuid, 0);
        finishTimeSums.put(uuid, 0L);
        timeTrialFinishes.entrySet().removeIf(entry -> entry.getKey() != null && uuid.equals(entry.getKey().getUniqueId()));
    }

    private void keepCheckpointCopy(TimeTrialFinish local) {
        if (!local.hasCheckpointTimes() && !local.isCheckpointsRequested()) {
            return;
        }
        TPlayer player = local.getPlayer();
        TimeTrialFinish cached = getBestFinish(player);
        if (cached != null && cached.getId() == local.getId()) {
            setBestFinish(local);
        }
    }

    private void noteFinishCreatedAfterClear(TimeTrialFinish finish) {
        TPlayer player = finish.getPlayer();
        if (player == null) {
            return;
        }
        long date = finish.getDate();
        UUID uuid = player.getUniqueId();
        boolean sameSecond = date == trackHistoryClearedAt;
        Long clearedAt = historyClearedAfter.get(uuid);
        if (clearedAt != null && date == clearedAt) {
            sameSecond = true;
        }
        if (sameSecond) {
            visibleAfterClear.put(finish.getId(), new NotedAfterClear(date, uuid));
        }
    }

    private boolean survivesClear(long date, int id, long cutoff) {
        if (date > cutoff) {
            return true;
        }
        if (date < cutoff) {
            return false;
        }
        NotedAfterClear noted = visibleAfterClear.get(id);
        return noted != null && noted.date == date;
    }

    private Set<Integer> sameSecondIds(UUID uuid, long cutoff) {
        if (uuid == null || cutoff == Long.MIN_VALUE || visibleAfterClear.isEmpty()) {
            return Set.of();
        }
        Set<Integer> ids = new HashSet<>();
        for (Map.Entry<Integer, NotedAfterClear> entry : visibleAfterClear.entrySet()) {
            NotedAfterClear noted = entry.getValue();
            if (noted.date == cutoff && uuid.equals(noted.uuid)) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private static boolean countedInSnapshot(int id, long snapshotMaxId, Set<Integer> suppressedAtSnapshot, Set<Integer> suppressedCounted) {
        if (id > snapshotMaxId) {
            return false;
        }
        if (suppressedAtSnapshot != null && suppressedAtSnapshot.contains(id)) {
            return suppressedCounted != null && suppressedCounted.contains(id);
        }
        return true;
    }

    private void scheduleNextBestLookup(TPlayer player, int attempt) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long cutoff = visibilityCutoff(uuid);
        Set<Integer> included = sameSecondIds(uuid, cutoff);
        Set<Integer> excluded = Set.copyOf(suppressedFinishIds);
        TimingSystem.newChain()
                .asyncFirst(() -> readNextBest(uuid, cutoff, included, excluded))
                .syncLast(lookup -> applyNextBestLookup(player, lookup, attempt))
                .execute();
    }

    private NextBestLookup readNextBest(UUID uuid, long cutoff, Set<Integer> included, Set<Integer> excluded) {
        try {
            DbRow row = TimingSystem.getTrackDatabase().selectNextBestFinish(trackId, uuid, cutoff, included, excluded);
            return new NextBestLookup(true, row);
        } catch (SQLException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to load the next best finish on track " + trackId + ": " + e.getMessage());
            return new NextBestLookup(false, null);
        }
    }

    private void applyNextBestLookup(TPlayer player, NextBestLookup lookup, int attempt) {
        if (lookup == null || !lookup.ok) {
            if (attempt + 1 < NEXT_BEST_ATTEMPTS) {
                Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> scheduleNextBestLookup(player, attempt + 1), NEXT_BEST_RETRY_TICKS);
                return;
            }
            TimingSystem.getPlugin().getLogger().warning("Failed to load the next best finish on track " + trackId + " after " + NEXT_BEST_ATTEMPTS + " attempts");
            updateLeaderboard();
            return;
        }
        if (lookup.row != null) {
            promoteBestFinish(player, new TimeTrialFinish(lookup.row));
        }
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        TrackDatabase.getTrackById(trackId).ifPresent(LeaderboardManager::updateFastestTimeLeaderboard);
    }

    private static void reconcileFailedAttempt(PendingAttempt pending) {
        synchronized (pendingAttempts) {
            Track track = TrackDatabase.getTrackById(pending.trackId).orElse(null);
            boolean revertTotal = pending.addedToTrackTotal || (track != null && trackAttemptTotalsLoaded && !pending.inTrackTotal);
            if (revertTotal && track != null) {
                track.getTimeTrials().revertAttemptTotal();
                pending.addedToTrackTotal = false;
            }
            boolean revertPlayer = pending.addedToPlayerStats || (attemptStatsLoaded.contains(pending.uuid) && !pending.inPlayerStats);
            if (revertPlayer && track != null) {
                track.getTimeTrials().revertPlayerAttempt(TSDatabase.getPlayer(pending.uuid), pending.time);
                pending.addedToPlayerStats = false;
            }
            pending.inTrackTotal = true;
            pending.inPlayerStats = true;
            pendingAttempts.remove(pending);
        }
    }

    @FunctionalInterface
    private interface FinishPageQuery {
        List<DbRow> fetch(int limit, int offset) throws SQLException;
    }

    private static final class NotedAfterClear {
        private final long date;
        private final UUID uuid;

        private NotedAfterClear(long date, UUID uuid) {
            this.date = date;
            this.uuid = uuid;
        }
    }

    private static final class PendingAttempt {
        private final int trackId;
        private final UUID uuid;
        private final long time;
        private final CompletableFuture<Long> insertId;
        private volatile boolean failed;
        private boolean inTrackTotal;
        private boolean inPlayerStats;
        private boolean addedToTrackTotal;
        private boolean addedToPlayerStats;

        private PendingAttempt(int trackId, UUID uuid, long time, CompletableFuture<Long> insertId) {
            this.trackId = trackId;
            this.uuid = uuid;
            this.time = time;
            this.insertId = insertId;
        }
    }

    private static final class NextBestLookup {
        private final boolean ok;
        private final DbRow row;

        private NextBestLookup(boolean ok, DbRow row) {
            this.ok = ok;
            this.row = row;
        }
    }
}
