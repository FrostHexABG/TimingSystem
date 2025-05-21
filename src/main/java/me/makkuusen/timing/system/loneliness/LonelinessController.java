package me.makkuusen.timing.system.loneliness;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.makkuusen.timing.system.api.events.BoatSpawnEvent;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.tplayer.TPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.Plugin;

import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.api.events.TimeTrialStartEvent;
import me.makkuusen.timing.system.timetrial.TimeTrialController;



public class LonelinessController implements Listener {

    private static Plugin plugin = null;
    private static final Set<UUID> ghostedPlayers = ConcurrentHashMap.newKeySet();

    public LonelinessController(Plugin plugin) {
        LonelinessController.plugin = plugin;
    }
    
    public static void updatePlayersVisibility(Player player) {

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isInsideVehicle()) {
                showAllOthers(player);
                return;
            }

            if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
                hideAllOthers(player);
                return;
            }

            // All non heat related code should be above this line
            var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
            if (!maybeDriver.isPresent()) {
                return;
            }

            Driver driver = maybeDriver.get();
            Heat heat = driver.getHeat();

            // Driver is not participating
            if (driver.getState() == DriverState.DISQUALIFIED || driver.getState() == DriverState.SETUP || driver.getState() == DriverState.FINISHED) {
                return;
            }

            if (heat.getLonely()) {
                hideAllOthers(player);
                return;
            }

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }

                // Do not hide players in the same non loneliness heat, unless they are ghosted
                maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(p.getUniqueId());
                if (maybeDriver.isPresent() && maybeDriver.get().getHeat().getId() == heat.getId() && !ghostedPlayers.contains(p.getUniqueId())) {
                    if (p.isInsideVehicle() && (p.getVehicle() instanceof Boat || p.getVehicle() instanceof ChestBoat)) {
                        player.showEntity(plugin, p.getVehicle());
                    }

                    player.showEntity(plugin, p);
                    continue;
                }

                if (p.isInsideVehicle() && p.getVehicle() instanceof Boat || p.getVehicle() instanceof ChestBoat) {
                    player.hideEntity(plugin, p.getVehicle());
                }

                player.hideEntity(plugin, p);
            }
        }, 5L);
    }

    private static void showAllOthers(Player player) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            if (p.isInsideVehicle() && p.getVehicle() instanceof Boat || p.getVehicle() instanceof ChestBoat) {
                player.showEntity(plugin, p.getVehicle());
            }

            player.showEntity(plugin, p);
        }
    }

    private static void hideAllOthers(Player player) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            if (p.isInsideVehicle() && p.getVehicle() instanceof Boat || p.getVehicle() instanceof ChestBoat) {
                player.hideEntity(plugin, p.getVehicle());
            }

            player.hideEntity(plugin, p);
        }
    }

    public static void updatePlayerVisibility(Player player) {
         Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                // if p is not in a boat there is no situation where they should not see player
                if (!(p.getVehicle() instanceof Boat) && !(p.getVehicle() instanceof ChestBoat)) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.showEntity(plugin, player.getVehicle());
                    }

                    p.showEntity(plugin, player);
                    continue;
                }

                if (TimeTrialController.timeTrials.containsKey(p.getUniqueId())) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.hideEntity(plugin, player.getVehicle());
                    }

                    p.hideEntity(plugin, player);
                    continue;
                }

                var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(p.getUniqueId());
                if (!maybeDriver.isPresent()) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.showEntity(plugin, player.getVehicle());
                    }

                    p.showEntity(plugin, player);
                    continue;
                }

                Driver d = maybeDriver.get();
                Heat heat = d.getHeat();

                // Driver is not participating
                if (d.getState() == DriverState.DISQUALIFIED || d.getState() == DriverState.SETUP || d.getState() == DriverState.FINISHED) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.showEntity(plugin, player.getVehicle());
                    }

                    p.showEntity(plugin, player);
                    continue;
                }

                maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());

                if (!maybeDriver.isPresent()) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.hideEntity(plugin, player.getVehicle());
                    }

                    p.hideEntity(plugin, player);
                    continue;
                }

                if (maybeDriver.get().getHeat().getId() != heat.getId()) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.hideEntity(plugin, player.getVehicle());
                    }

                    p.hideEntity(plugin, player);
                    continue;
                }

                if (heat.getLonely()) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.hideEntity(plugin, player.getVehicle());
                    }

                    p.hideEntity(plugin, player);
                    continue;
                }

                if (ghostedPlayers.contains(player.getUniqueId())) {
                    if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                        p.hideEntity(plugin, player.getVehicle());
                    }

                    p.hideEntity(plugin, player);
                    continue;
                }

                if (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) {
                    p.showEntity(plugin, player.getVehicle());
                }

                p.showEntity(plugin, player);
            }
         }, 5L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updatePlayerVisibility(player);
    }

   @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getVehicle() instanceof Boat || event.getVehicle() instanceof ChestBoat) {
            if (event.getEntered() instanceof Player) {
                Player player = (Player) event.getEntered();
                updatePlayerVisibility(player);
                updatePlayersVisibility(player);
            }
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getVehicle() instanceof Boat || event.getVehicle() instanceof ChestBoat) {
            if (event.getExited() instanceof Player) {
                Player player = (Player) event.getExited();
                updatePlayersVisibility(player);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        updatePlayerVisibility(player);
        updatePlayersVisibility(player);
    }

    @EventHandler
    public void onPlayerStartTimeTrial(TimeTrialStartEvent event) {
        Player player = event.getPlayer();

        updatePlayersVisibility(player);
    }

    @EventHandler
    public void onBoatSpawn(BoatSpawnEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        updatePlayersVisibility(player);
        updatePlayerVisibility(player);
    }

    public static boolean isGhosted(UUID player) {
        return ghostedPlayers.contains(player);
    }

    public static void ghost(UUID player) {
        ghostedPlayers.add(player);
        updatePlayerVisibility(plugin.getServer().getPlayer(player));
    }

    public static boolean unghost(UUID player) {
        if (ghostedPlayers.contains(player)) {
            ghostedPlayers.remove(player);
            updatePlayerVisibility(plugin.getServer().getPlayer(player));
            return true;
        }
        return false;
    }
}