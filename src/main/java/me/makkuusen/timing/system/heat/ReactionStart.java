package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.ping.PingBalancer;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the light sequence of a reaction start and releases drivers from the grid as they shift.
 * Red and amber are fixed length, green comes after a random wait so the start cannot be timed.
 * Shifting before green is a jump start: the driver is held on the grid for the configured penalty
 * on top of the wait for green, which is why they lose the time rather than gain it. The same
 * penalty doubles as the deadline for reacting at all, so a driver that never shifts is let off the
 * grid with it rather than being left sitting there.
 * <p>
 * The whole sequence is ping balanced. It sits behind a lead-in and each driver is shown the lights
 * early by {@link PingBalancer#getReactionCueTicks}, so that an equal reaction turns into an equal
 * getaway whatever the connection. Green therefore opens per driver rather than for the grid at
 * once: a driver whose client is further behind may legitimately answer earlier in server time,
 * because their release still has to travel back out to them before the boat moves.
 */
public class ReactionStart {

    public enum Phase {
        RED, AMBER, GREEN
    }

    private static final int RED_TICKS = 30;
    private static final int AMBER_MIN_TICKS = 20;
    private static final int AMBER_MAX_TICKS = 80;

    private final Heat heat;
    private final Set<UUID> onGrid = new HashSet<>();
    private final Set<UUID> greenOpen = new HashSet<>();
    private final Set<UUID> released = new HashSet<>();
    private final Set<UUID> jumped = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    public ReactionStart(Heat heat) {
        this.heat = heat;
    }

    public void start() {
        for (Driver driver : heat.getDrivers().values()) {
            onGrid.add(driver.getTPlayer().getUniqueId());
        }

        int redTick = PingBalancer.getMaxReactionCueTicks();
        int amberTick = redTick + RED_TICKS;
        int greenTick = amberTick + ThreadLocalRandom.current().nextInt(AMBER_MIN_TICKS, AMBER_MAX_TICKS + 1);

        EventAnnouncements.broadcastStartLights(heat, Phase.RED, redTick);
        EventAnnouncements.broadcastStartLights(heat, Phase.AMBER, amberTick);
        EventAnnouncements.broadcastStartLights(heat, Phase.GREEN, greenTick);

        for (UUID uuid : onGrid) {
            schedule(Math.max(greenTick - PingBalancer.getLatencyTicks(getPlayer(uuid)), 0), () -> greenOpen.add(uuid));
        }
        schedule(greenTick, heat::startHeat);
    }

    /**
     * Called from {@link Heat#startHeat()} once the green lights are on. Drivers release themselves
     * from here on, until the penalty runs out and everyone still on the grid is let go: the ones
     * that jumped, and the ones that never shifted at all.
     */
    public void openGrid() {
        for (UUID uuid : onGrid) {
            Driver driver = heat.getDrivers().get(uuid);
            if (driver != null && !driver.isDisqualified()) {
                driver.setState(DriverState.STARTING);
            }
        }
        int penaltyTicks = TimingSystem.configuration.getReactionStartPenalty() / 50;
        for (UUID uuid : onGrid) {
            Driver driver = heat.getDrivers().get(uuid);
            if (driver == null) {
                continue;
            }
            schedule(Math.max(penaltyTicks - PingBalancer.getLatencyTicks(getPlayer(uuid)), 0), () -> release(driver));
        }
    }

    /**
     * @return true when the shift was consumed by the reaction start, false when the driver is not
     * waiting on the grid and the shift should be handled as usual
     */
    public boolean onReleaseAttempt(Driver driver) {
        UUID uuid = driver.getTPlayer().getUniqueId();
        // Drivers that joined after the lights went on were never held, so they start as usual
        if (!onGrid.contains(uuid) || released.contains(uuid)) {
            return false;
        }
        if (greenOpen.contains(uuid)) {
            if (jumped.contains(uuid)) {
                // Still serving the penalty, the release is on a timer instead
                return true;
            }
            release(driver);
            return true;
        }
        if (jumped.add(uuid)) {
            announceJumpStart(driver);
        }
        return true;
    }

    public void stop() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (UUID uuid : onGrid) {
            if (!released.add(uuid)) {
                continue;
            }
            heat.getGridManager().releaseDriverFromGrid(uuid);
            // Qualifying clocks a driver from their own release, so one that never shifted has none yet
            Driver driver = heat.getDrivers().get(uuid);
            if (driver != null && driver.getStartTime() == null) {
                driver.setStartTime(heat.getStartTime() == null ? TimingSystem.currentTime : heat.getStartTime());
            }
        }
    }

    private void release(Driver driver) {
        UUID uuid = driver.getTPlayer().getUniqueId();
        if (!released.add(uuid)) {
            return;
        }
        heat.getGridManager().releaseDriverFromGrid(uuid);
        if (heat.getRound() instanceof QualificationRound) {
            driver.setStartTime(TimingSystem.currentTime);
        }
        if (!driver.isDisqualified()) {
            driver.setState(DriverState.STARTING);
        }
        Player player = driver.getTPlayer().getPlayer();
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1, 1);
        }
    }

    private void announceJumpStart(Driver driver) {
        Player player = driver.getTPlayer().getPlayer();
        if (player == null) {
            return;
        }
        String penalty = ApiUtilities.formatAsTime(TimingSystem.configuration.getReactionStartPenalty());
        Text.send(player, Broadcast.REACTION_JUMP_START, "%time%", penalty);
        EventAnnouncements.sendJumpStartTitle(player, penalty);
    }

    private Player getPlayer(UUID uuid) {
        Driver driver = heat.getDrivers().get(uuid);
        return driver == null ? null : driver.getTPlayer().getPlayer();
    }

    private void schedule(int delayInTicks, Runnable runnable) {
        tasks.add(Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), runnable, Math.max(delayInTicks, 0)));
    }
}
