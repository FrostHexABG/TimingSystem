package me.makkuusen.timing.system.heat;

import co.aikar.taskchain.TaskChain;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class GridManager {

    private final HashMap<UUID, ArmorStand> armorStands = new HashMap<>();
    private final boolean qualy;

    public GridManager(boolean qualy) {
        this.qualy = qualy;
    }

    public void putDriverOnGrid(Driver driver, Track track) {
        boolean qualyGrid = qualy && !track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).isEmpty();
        Player player = driver.getTPlayer().getPlayer();
        if (player != null) {
            Location grid;
            if (qualyGrid) {
                if (driver.getHeat().getLonely()) {
                    grid = track.getTrackLocations().getLocation(TrackLocation.Type.QUALYGRID, 1).get().getLocation();
                } else {
                    grid = track.getTrackLocations().getLocation(TrackLocation.Type.QUALYGRID, driver.getStartPosition()).get().getLocation();
                }
            } else {
                if (driver.getHeat().getLonely()) {
                    grid = track.getTrackLocations().getLocation(TrackLocation.Type.GRID, 1).get().getLocation();
                } else {
                    grid = track.getTrackLocations().getLocation(TrackLocation.Type.GRID, driver.getStartPosition()).get().getLocation();
                }
            }
            if (grid != null) {
                teleportPlayerToGrid(player, grid, track);
            }
        }
        driver.setState(DriverState.LOADED);
        LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
        LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
    }

    private void teleportPlayerToGrid(Player player, Location location, Track track) {
        location.setPitch(player.getLocation().getPitch());
        if (!location.isWorldLoaded()) {
            return;
        }
        if (player.getVehicle() != null && player.getVehicle() instanceof Boat boat) {
            boat.remove();
        }
        player.teleport(location);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        ArmorStand ar = (ArmorStand) location.getWorld().spawnEntity(location.clone().add(0, -1.45, 0), EntityType.ARMOR_STAND);
        ar.setCanMove(false);
        ar.setGravity(false);
        ar.setVisible(false);
        Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> {
            TimeTrialController.lastTimeTrialTrack.put(player.getUniqueId(), track);
            Boat boat = ApiUtilities.spawnBoatAndAddPlayerWithBoatUtils(player, location, track, false);
            ar.addPassenger(boat);
            armorStands.put(player.getUniqueId(), ar);
        }, 2);
    }

    public void startDriversWithDelay(long startDelayMS, boolean setStartTime, List<Driver> startPositions) {
        TaskChain<?> chain = TimingSystem.newChain();
        for (Driver driver : startPositions) {
            chain.sync(() -> {
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
            });
            if (startDelayMS > 0) {
                //Start delay in ms divided by 50ms to get ticks
                chain.delay((int) (startDelayMS / 50));
            }
        }
        chain.execute();
    }

    private void startPlayerFromGrid(UUID uuid) {
        ArmorStand ar = armorStands.get(uuid);
        if (ar != null) {
            ar.remove();
        }
    }

    public void clearArmorstands() {
        armorStands.values().stream().forEach(armorStand -> armorStand.remove());
    }
}
