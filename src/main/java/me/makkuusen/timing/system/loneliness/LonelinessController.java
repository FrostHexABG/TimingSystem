package me.makkuusen.timing.system.loneliness;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.events.BoatSpawnEvent;
import me.makkuusen.timing.system.heat.CollisionMode;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.Plugin;

import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.api.events.TimeTrialStartEvent;

import static me.makkuusen.timing.system.boatutils.NocolManager.*;

public class LonelinessController implements Listener {

    private static Plugin plugin = null;
    private static final Set<UUID> ghostedPlayers = ConcurrentHashMap.newKeySet();
    private static final long VISIBILITY_UPDATE_DELAY = 1L;

    public LonelinessController(Plugin plugin) {
        LonelinessController.plugin = plugin;
    }

    public static void updatePlayersVisibility(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Shows all players when not in a boat
            if (!player.isInsideVehicle()) {
                showAllOthers(player);
                return;
            }

            Optional<Driver> maybeDriver = getDriverFromRunningHeat(player);
            Heat streakingHeat = getStreakingHeat(player);
            boolean canUseNocol = playerCanUseNocol(player);
            boolean lonelinessDisabled = !TimingSystemAPI.getTPlayer(player.getUniqueId()).getSettings().isLonely();

            // Player is not in a heat as driver or streaker and is in a boat
            if (!maybeDriver.isPresent() && streakingHeat == null) {
                if (canUseNocol && lonelinessDisabled) {
                    // Show all drivers, activate nocol
                    showAllOthers(player);
                    setCollisionMode(player, false);
                } else {
                    // Hide all players
                    hideAllOthers(player);
                }
                return;
            }

            Heat heat;
            boolean isDriver = maybeDriver.isPresent();
            
            if (isDriver) {
                Driver driver = maybeDriver.get();
                heat = driver.getHeat();
            } else {
                // Player is a streaker
                heat = streakingHeat;
            }

            if (heat.getCollisionMode() == CollisionMode.DISABLED) {
                // Collision disabled heat (formerly loneliness)
                if (canUseNocol && lonelinessDisabled) {
                    // Show only heat players, activate nocol
                    showHeatPlayersOnly(player, heat);
                    setCollisionMode(player, false);
                } else {
                    // Hide all players (collision disabled effect) - but streakers should still see heat players
                    if (isDriver) {
                        hideAllOthers(player);
                    } else {
                        // Streakers always see heat players even in collision disabled mode
                        showHeatPlayersOnly(player, heat);
                    }
                }
            } else if (heat.getCollisionMode() == CollisionMode.LOW) {
                // Low collision mode - filtered collision for compatible clients, high collision for others
                showHeatPlayersOnly(player, heat);
                if (playerCanUseFilteredCollision(player)) {
                    // Use filtered collision - no collision with players/villagers but boats still collide
                    TimingSystem.getPlugin().getLogger().info("Applying LOW collision mode (filtered) for player " + player.getName());
                    setLowCollisionMode(player);
                } else {
                    // Fallback to high collision mode for incompatible clients
                    TimingSystem.getPlugin().getLogger().info("Applying LOW collision mode fallback (vanilla) for player " + player.getName());
                    setCollisionMode(player, true);
                }
            } else {
                // High collision mode - full vanilla collisions
                showHeatPlayersOnly(player, heat);
                setCollisionMode(player, true);
            }
        }, VISIBILITY_UPDATE_DELAY);
    }

    private static void showPlayerAndCustomBoat(Player player, Player boatOwner) {
        if (boatOwner.isInsideVehicle()
                && (boatOwner.getVehicle() instanceof Boat || boatOwner.getVehicle() instanceof ChestBoat)) {
            if (TimingSystem.configuration.isFrostHexAddOnEnabled()
                    && !boatOwner.getVehicle().getPassengers().isEmpty()) {
                for (Entity e : boatOwner.getVehicle().getPassengers()) {
                    if (e instanceof Villager) {
                        player.showEntity(plugin, e);
                    }
                }
            }
            player.showEntity(plugin, boatOwner.getVehicle());
        }
        player.showEntity(plugin, boatOwner);
    }

    private static void hidePlayerAndCustomBoat(Player player, Player boatOwner) {
        if (boatOwner.isInsideVehicle()
                && (boatOwner.getVehicle() instanceof Boat || boatOwner.getVehicle() instanceof ChestBoat)) {
            if (TimingSystem.configuration.isFrostHexAddOnEnabled()
                    && !boatOwner.getVehicle().getPassengers().isEmpty()) {
                for (Entity e : boatOwner.getVehicle().getPassengers()) {
                    if (e instanceof Villager) {
                        player.hideEntity(plugin, e);
                    }
                }
            }
            player.hideEntity(plugin, boatOwner.getVehicle());
        }
        player.hideEntity(plugin, boatOwner);
    }

    private static void showAllOthers(Player player) {
        if (playerCanUseNocol(player))
            setCollisionMode(player, true);
        for (Player otherPlayer : getOtherOnlinePlayers(player)) {
            showPlayerAndCustomBoat(player, otherPlayer);
        }
    }

    private static void hideAllOthers(Player player) {
        setCollisionMode(player, true); // Ensure vanilla collision when hiding players
        for (Player otherPlayer : getOtherOnlinePlayers(player)) {
            hidePlayerAndCustomBoat(player, otherPlayer);
        }
    }

    private static void showHeatPlayersOnly(Player player, Heat heat) {
        for (Player otherPlayer : getOtherOnlinePlayers(player)) {
            boolean shouldShow = false;
            
            // Check if player is a driver or streaker
            if (heat.getDrivers().containsKey(otherPlayer.getUniqueId()) || 
                heat.getStreakers().containsKey(otherPlayer.getUniqueId())) {
                shouldShow = true;
            }
            
            // In boat switching heats, also show all team members
            if (!shouldShow && heat.isBoatSwitchingEnabled()) {
                // Check if player is in any team in this heat
                var teamEntry = heat.getTeamEntryByPlayer(otherPlayer.getUniqueId());
                if (teamEntry.isPresent()) {
                    shouldShow = true;
                }
            }
            
            boolean isManuallyGhosted = ghostedPlayers.contains(otherPlayer.getUniqueId());
            boolean isDeltaGhosted = false;
            
            Driver viewingDriver = heat.getDrivers().get(player.getUniqueId());
            Driver otherDriver = heat.getDrivers().get(otherPlayer.getUniqueId());
            if (viewingDriver != null && otherDriver != null) {
                isDeltaGhosted = DeltaGhostingController.isDeltaGhosted(viewingDriver, otherDriver);
            }
            
            if (shouldShow && !isManuallyGhosted && !isDeltaGhosted) {
                showPlayerAndCustomBoat(player, otherPlayer);
            } else {
                hidePlayerAndCustomBoat(player, otherPlayer);
            }
        }
    }

    private static Optional<Driver> getDriverFromRunningHeat(Player player) {
        return TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
    }

    private static Heat getStreakingHeat(Player player) {
        // Check all running heats to see if player is a streaker
        for (Heat heat : TimingSystemAPI.getRunningHeats()) {
            if (heat.getStreakers().containsKey(player.getUniqueId())) {
                return heat;
            }
        }
        return null;
    }

    private static boolean isDriverNotParticipating(Driver driver) {
        DriverState state = driver.getState();
        return state == DriverState.DISQUALIFIED ||
                state == DriverState.SETUP ||
                state == DriverState.FINISHED;
    }

    private static Iterable<? extends Player> getOtherOnlinePlayers(Player excludePlayer) {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(excludePlayer.getUniqueId()))
                .toList();
    }

    private static boolean isPlayerInBoat(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Boat || vehicle instanceof ChestBoat;
    }

    private static void processPlayerVisibilityForOther(Player targetPlayer, Player viewingPlayer) {
        if (!isPlayerInBoat(viewingPlayer)) {
            showPlayerAndCustomBoat(viewingPlayer, targetPlayer);
            return;
        }

        boolean canUseNocol = playerCanUseNocol(viewingPlayer);
        boolean lonelinessDisabled = !TimingSystemAPI.getTPlayer(viewingPlayer.getUniqueId()).getSettings().isLonely();

        if (canUseNocol && lonelinessDisabled) {
            showPlayerAndCustomBoat(viewingPlayer, targetPlayer);
        } else {
            hidePlayerAndCustomBoat(viewingPlayer, targetPlayer);
        }

        Optional<Driver> viewingMaybeDriver = getDriverFromRunningHeat(viewingPlayer);
        Heat viewingStreakingHeat = getStreakingHeat(viewingPlayer);
        
        if (!viewingMaybeDriver.isPresent() && viewingStreakingHeat == null) return;

        Heat viewingHeat;
        boolean viewingIsDriver = viewingMaybeDriver.isPresent();
        
        if (viewingIsDriver) {
            Driver viewingDriver = viewingMaybeDriver.get();
            viewingHeat = viewingDriver.getHeat();
            if (isDriverNotParticipating(viewingDriver)) return;
        } else {
            // Viewing player is a streaker
            viewingHeat = viewingStreakingHeat;
        }

        Optional<Driver> targetMaybeDriver = getDriverFromRunningHeat(targetPlayer);
        boolean targetIsStreaker = viewingHeat.getStreakers().containsKey(targetPlayer.getUniqueId());
        boolean targetIsTeamMember = false;
        
        // In boat switching heats, check if target is a team member
        if (viewingHeat.isBoatSwitchingEnabled()) {
            var targetTeamEntry = viewingHeat.getTeamEntryByPlayer(targetPlayer.getUniqueId());
            targetIsTeamMember = targetTeamEntry.isPresent();
        }

        // If target is neither a driver, streaker, nor team member in the viewing player's heat, hide them
        if (!targetMaybeDriver.isPresent() && !targetIsStreaker && !targetIsTeamMember) {
            hidePlayerAndCustomBoat(viewingPlayer, targetPlayer);
            return;
        }

        // If target is a driver but in a different heat, hide them
        if (targetMaybeDriver.isPresent() && targetMaybeDriver.get().getHeat().getId() != viewingHeat.getId()) {
            hidePlayerAndCustomBoat(viewingPlayer, targetPlayer);
            return;
        }

        boolean isManuallyGhosted = ghostedPlayers.contains(targetPlayer.getUniqueId());
        boolean isDeltaGhosted = false;
        
        if (viewingIsDriver && targetMaybeDriver.isPresent()) {
            Driver viewingDriver = viewingMaybeDriver.get();
            Driver targetDriver = targetMaybeDriver.get();
            isDeltaGhosted = DeltaGhostingController.isDeltaGhosted(viewingDriver, targetDriver);
        }
        
        if (isManuallyGhosted || isDeltaGhosted) {
            hidePlayerAndCustomBoat(viewingPlayer, targetPlayer);
            return;
        }

        if (viewingHeat.getCollisionMode() == CollisionMode.DISABLED) {
            if (canUseNocol && lonelinessDisabled) {
                showPlayerAndCustomBoat(viewingPlayer, targetPlayer);
            } else {
                // In collision disabled mode, streakers should still see heat players
                if (!viewingIsDriver) {
                    // Viewing player is a streaker - always show heat players
                    showPlayerAndCustomBoat(viewingPlayer, targetPlayer);
                } else {
                    // Viewing player is a driver - hide in collision disabled mode
                    hidePlayerAndCustomBoat(viewingPlayer, targetPlayer);
                }
            }
            return;
        }

        showPlayerAndCustomBoat(viewingPlayer, targetPlayer);
    }

    public static void updatePlayerVisibility(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player otherPlayer : getOtherOnlinePlayers(player)) {
                processPlayerVisibilityForOther(player, otherPlayer);
            }
        }, VISIBILITY_UPDATE_DELAY);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TimingSystemAPI.getTPlayer(player.getUniqueId()).getSettings().setLonely(false);
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