package me.makkuusen.timing.system.ping;

import me.makkuusen.timing.system.TimingSystem;
import org.bukkit.entity.Player;

/**
 * Latency compensation for the moments a driver has no control over.
 * <p>
 * The unit that matters is the whole round trip, not half of it, because two separate trips sit
 * inside every one of these moments. Take a reset: the player enters the region, that movement takes
 * half a trip to reach the server, and the boat the server hands back takes another half trip to
 * reach the client. The driver is stuck for both. Timing does not save them either, since a lap is
 * measured between two region crossings that are each stamped half a trip late, so the halves cancel
 * and what is left in the lap is the full trip lost to the reset.
 * <p>
 * A reaction start costs double that again: the lights go out, the driver's answer comes back, and
 * the release has to travel out to them once more, and on top of that everything they do afterwards
 * is only seen by the server half a trip later.
 * <p>
 * Compensation is floored to whole ticks, which is as fine as a scheduled task can be, and capped at
 * the configured max ping so one bad connection cannot drag a whole grid with it.
 */
public final class PingBalancer {

    private static final int MS_PER_TICK = 50;

    private PingBalancer() {
    }

    /**
     * @return the player's round trip time, capped at the configured maximum, 0 when they are offline
     */
    public static int getRoundTripMs(Player player) {
        if (player == null) {
            return 0;
        }
        return Math.min(Math.max(player.getPing(), 0), TimingSystem.configuration.getPingBalancingMaxPing());
    }

    /**
     * How early a server side change has to happen for the client to act on it in step with everyone
     * else: the countdown numbers, and the release that frees the boat.
     */
    public static int getLatencyTicks(Player player) {
        return toTicks(getRoundTripMs(player));
    }

    /**
     * Lead-in for a sequence compensated with {@link #getLatencyTicks}, so even the worst connection
     * has room to be served early instead of being clamped to now.
     */
    public static int getMaxLatencyTicks() {
        return toTicks(TimingSystem.configuration.getPingBalancingMaxPing());
    }

    /**
     * How early a reaction cue has to go out, which is twice {@link #getLatencyTicks} because the
     * driver's answer has to cross the link as well as the cue and the release.
     */
    public static int getReactionCueTicks(Player player) {
        return toTicks(getRoundTripMs(player) * 2);
    }

    /**
     * Lead-in for a reaction sequence, see {@link #getMaxLatencyTicks()}.
     */
    public static int getMaxReactionCueTicks() {
        return toTicks(TimingSystem.configuration.getPingBalancingMaxPing() * 2);
    }

    /**
     * Extra wait that levels out how long a reset takes from the driver's point of view: a client
     * that hears about the teleport late waits less, so every driver loses the same time to it.
     *
     * @return 0 when reset balancing is switched off in the config
     */
    public static int getResetPaddingTicks(Player player) {
        if (!TimingSystem.configuration.isPingBalancingOnResetRegions()) {
            return 0;
        }
        return Math.max(getMaxLatencyTicks() - getLatencyTicks(player), 0);
    }

    private static int toTicks(int milliseconds) {
        return milliseconds / MS_PER_TICK;
    }
}
