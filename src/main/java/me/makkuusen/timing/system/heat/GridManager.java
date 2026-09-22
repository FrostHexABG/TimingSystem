package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.ping.PingBalancer;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GridManager {

    private static final int DRIVERS_PER_TICK = 5;

    private final HashMap<UUID, ArmorStand> armorStands = new HashMap<>();
    private final Deque<Driver> pendingDrivers = new ArrayDeque<>();
    private final boolean qualy;
    private Consumer<Driver> pendingPlacement;
    private BukkitTask placementTask;

    public GridManager(boolean qualy) {
        this.qualy = qualy;
    }

    public void placeDriversInBatches(List<Driver> drivers, Consumer<Driver> placement) {
        cancelPendingPlacements();
        pendingPlacement = placement;
        pendingDrivers.addAll(drivers);
        placeNextBatch();
        if (pendingDrivers.isEmpty()) {
            return;
        }
        placementTask = Bukkit.getScheduler().runTaskTimer(TimingSystem.getPlugin(), () -> {
            placeNextBatch();
            if (pendingDrivers.isEmpty()) {
                cancelPendingPlacements();
            }
        }, 1, 1);
    }

    private void placeNextBatch() {
        for (int i = 0; i < DRIVERS_PER_TICK && !pendingDrivers.isEmpty(); i++) {
            pendingPlacement.accept(pendingDrivers.poll());
        }
    }

    public void placeRemainingDriversNow() {
        while (!pendingDrivers.isEmpty()) {
            pendingPlacement.accept(pendingDrivers.poll());
        }
        cancelPendingPlacements();
    }

    public void cancelPendingPlacements() {
        pendingDrivers.clear();
        if (placementTask != null) {
            placementTask.cancel();
            placementTask = null;
        }
    }

    public void putDriverOnGrid(Driver driver, Track track) {
        boolean qualyGrid = qualy && !track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).isEmpty();
        Player player = driver.getTPlayer().getPlayer();
        if (player != null) {
            TrackLocation.Type gridType = qualyGrid ? TrackLocation.Type.QUALYGRID : TrackLocation.Type.GRID;
            Location grid = getGridLocation(track, gridType, driver);
            if (grid != null) {
                teleportPlayerToGrid(player, grid, track);
            }
        }
        driver.setState(DriverState.LOADED);
        if (driver.getTPlayer().getPlayer() != null) {
            LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
            LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
        }
    }

    private static Location getGridLocation(Track track, TrackLocation.Type gridType, Driver driver) {
        List<TrackLocation> grids = track.getTrackLocations().getLocations(gridType);
        if (grids.isEmpty()) {
            return null;
        }
        // All drivers share the first grid in loneliness heats
        if (driver.getHeat().getLonely()) {
            return grids.get(0).getLocation();
        }
        // Drivers with a start position beyond the available grids share the last grid
        return track.getTrackLocations().getLocation(gridType, driver.getStartPosition())
                .map(TrackLocation::getLocation)
                .orElse(grids.get(grids.size() - 1).getLocation());
    }

    public void putLateDriverOnGrid(Driver driver, Track track) {
        boolean qualyGrid = qualy && !track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).isEmpty();
        Player player = driver.getTPlayer().getPlayer();
        if (player != null) {
            TrackLocation.Type gridType = qualyGrid ? TrackLocation.Type.QUALYGRID : TrackLocation.Type.GRID;
            Location grid = getGridLocation(track, gridType, driver);
            if (grid != null) {
                teleportPlayerToGrid(player, grid, track, false);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1, 1);
        }
        driver.setState(DriverState.STARTING);
        if (driver.getTPlayer().getPlayer() != null) {
            LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
            LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
        }
    }

    private void teleportPlayerToGrid(Player player, Location location, Track track) {
        teleportPlayerToGrid(player, location, track, true);
    }

    private void teleportPlayerToGrid(Player player, Location location, Track track, boolean holdOnGrid) {
        location.setPitch(player.getLocation().getPitch());
        if (!location.isWorldLoaded()) {
            return;
        }
        if (player.getVehicle() != null && player.getVehicle() instanceof Boat boat) {
            ApiUtilities.removePlayerFromBoat(player);
        }
        player.teleport(location);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        ArmorStand ar;
        if (holdOnGrid) {
            ar = (ArmorStand) location.getWorld().spawnEntity(location.clone().add(0, -1.45, 0), EntityType.ARMOR_STAND);
            ar.setCanMove(false);
            ar.setGravity(false);
            ar.setVisible(false);
        } else {
            ar = null;
        }
        Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> {
            TimeTrialController.lastTimeTrialTrack.put(player.getUniqueId(), track);
            Boat boat = ApiUtilities.spawnBoatAndAddPlayerWithBoatUtils(player, location, track, false);
            if (ar != null) {
                ar.addPassenger(boat);
                armorStands.put(player.getUniqueId(), ar);
            }
        }, 2);
    }

    public void startDriversWithDelay(long startDelayMS, boolean setStartTime, List<Driver> startPositions) {
        startDriversWithDelay(startDelayMS, setStartTime, startPositions, 0, null);
    }

    /**
     * A client only learns its boat is free once the removal has travelled to it, so a laggy driver
     * would pull away late through no fault of their own. Every release is held back by a lead-in
     * worth the largest compensation allowed and then brought forward again by however far each
     * client is behind, which lands them all on the same real moment.
     */
    public void startDriversWithDelay(long startDelayMS, boolean setStartTime, List<Driver> startPositions, int gridsPerRow, Integer rowStartDelayMS) {
        boolean rowDelayEnabled = gridsPerRow > 0 && rowStartDelayMS != null;
        int leadIn = PingBalancer.getMaxLatencyTicks();
        int cumulativeTicks = 0;
        int started = 0;
        for (Driver driver : startPositions) {
            int releaseInTicks = Math.max(leadIn + cumulativeTicks - PingBalancer.getLatencyTicks(driver.getTPlayer().getPlayer()), 0);
            Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> releaseDriver(driver, setStartTime), releaseInTicks);
            started++;
            boolean endOfRow = rowDelayEnabled && started % gridsPerRow == 0;
            long delayMS = endOfRow ? rowStartDelayMS : startDelayMS;
            if (delayMS > 0) {
                //Start delay in ms divided by 50ms to get ticks
                cumulativeTicks += (int) (delayMS / 50);
            }
        }
    }

    private void releaseDriver(Driver driver, boolean setStartTime) {
        startPlayerFromGrid(driver.getTPlayer().getUniqueId());
        if (setStartTime) {
            driver.setStartTime(TimingSystem.currentTime);
        }
        if (!driver.isDisqualified()) {
            driver.setState(DriverState.STARTING);
        }
        if (driver.getTPlayer().getPlayer() != null) {
            driver.getTPlayer().getPlayer().playSound(driver.getTPlayer().getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1, 1);
        }
    }

    /**
     * Lets a single driver off the grid, used by reaction starts where drivers release themselves.
     */
    public void releaseDriverFromGrid(UUID uuid) {
        startPlayerFromGrid(uuid);
    }

    private void startPlayerFromGrid(UUID uuid) {
        ArmorStand ar = armorStands.get(uuid);
        if (ar != null) {
            ar.remove();
        }
    }

    public void clearArmorstands() {
        cancelPendingPlacements();
        armorStands.values().stream().forEach(armorStand -> armorStand.remove());
    }
}
