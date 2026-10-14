package me.makkuusen.timing.system.ping;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingRegistry {
    private static final int SAMPLES_KEPT = 5;
    private static final long FRESH_FOR_MS = 30000;
    private static final Map<UUID, Samples> samples = new ConcurrentHashMap<>();
    private static volatile boolean available = false;

    private PingRegistry() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void setAvailable(boolean available) {
        PingRegistry.available = available;
    }

    public static void record(UUID uuid, int roundTripMs) {
        if (roundTripMs < 0) {
            return;
        }
        samples.computeIfAbsent(uuid, key -> new Samples()).add(roundTripMs);
    }

    public static Integer getPing(UUID uuid) {
        Samples player = samples.get(uuid);
        if (player == null) {
            return null;
        }
        return player.lowest();
    }

    public static void remove(UUID uuid) {
        samples.remove(uuid);
    }

    public static void clear() {
        samples.clear();
    }

    private static final class Samples {

        private final Deque<Integer> recent = new ArrayDeque<>(SAMPLES_KEPT);
        private long updated;

        private synchronized void add(int roundTripMs) {
            if (recent.size() == SAMPLES_KEPT) {
                recent.removeFirst();
            }
            recent.addLast(roundTripMs);
            updated = System.currentTimeMillis();
        }

        private synchronized Integer lowest() {
            if (recent.isEmpty() || System.currentTimeMillis() - updated > FRESH_FOR_MS) {
                return null;
            }
            return Collections.min(recent);
        }
    }
}
