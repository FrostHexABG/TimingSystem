package me.makkuusen.timing.system.ping;

import me.makkuusen.timing.system.TimingSystem;
import org.bukkit.entity.Player;

/**
 * Latency compensation for the moments a driver has no control over.
 * <p>
 * The rule is that a driver's boat should come free at the same real moment as everyone else's, and
 * nothing beyond that. Crossing a line is deliberately left alone: both the start and the end of a
 * lap are stamped half a trip late so the halves cancel, and a race that is clocked from its own
 * start still leaves a laggy driver marginally worse off at the flag. Paying that back would mean
 * letting them away early in real time, which is a head start, not a fix.
 * <p>
 * How much is owed depends on who set the moment off. A release is the server's own doing, so only
 * the trip out to the client is in the way. A reset is the driver's doing, so their entry has to
 * reach the server and the boat has to come back, and they sit still for both. A reaction start is
 * one trip worse again, because the cue has to reach them before they can answer it at all.
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
     * How early something the server decides has to happen for the client to see it at the intended
     * real moment: the countdown numbers, and the release that frees the boat.
     */
    public static int getOutboundTicks(Player player) {
        return toTicks(getRoundTripMs(player) / 2);
    }

    /**
     * Lead-in for a sequence compensated with {@link #getOutboundTicks}, so even the worst connection
     * has room to be served early instead of being clamped to now.
     */
    public static int getMaxOutboundTicks() {
        return toTicks(TimingSystem.configuration.getPingBalancingMaxPing() / 2);
    }

    /**
     * How early a reaction cue has to go out: one trip for the lights to arrive, one for the driver's
     * answer to come back, and one more for the release to reach them.
     */
    public static int getReactionCueTicks(Player player) {
        int roundTrip = getRoundTripMs(player);
        return toTicks(roundTrip + roundTrip / 2);
    }

    /**
     * Lead-in for a reaction sequence, see {@link #getMaxOutboundTicks()}.
     */
    public static int getMaxReactionCueTicks() {
        int maxPing = TimingSystem.configuration.getPingBalancingMaxPing();
        return toTicks(maxPing + maxPing / 2);
    }

    /**
     * Extra wait that levels out how long a reset takes from the driver's point of view. Both trips
     * are lost time here, so the whole round trip is owed: a client that hears about the teleport
     * late waits less, and every driver ends up stuck for the same length of time.
     *
     * @return 0 when reset balancing is switched off in the config
     */
    public static int getResetPaddingTicks(Player player) {
        if (!TimingSystem.configuration.isPingBalancingOnResetRegions()) {
            return 0;
        }
        return Math.max(toTicks(TimingSystem.configuration.getPingBalancingMaxPing()) - toTicks(getRoundTripMs(player)), 0);
    }

    private static int toTicks(int milliseconds) {
        return milliseconds / MS_PER_TICK;
    }
}
