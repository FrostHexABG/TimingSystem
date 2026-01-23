package me.makkuusen.timing.system;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import me.makkuusen.timing.system.api.events.driver.DriverPassCheckpointEvent;
import me.makkuusen.timing.system.boatutils.BoatUtilsManager;
import me.makkuusen.timing.system.boatutils.BoatUtilsMode;
import me.makkuusen.timing.system.commands.CommandRace;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.drs.PushToPass;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.network.UUIDFetcher;
import me.makkuusen.timing.system.network.UUIDFetcherCallback;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.timetrial.TimeTrial;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.editor.TrackEditor;
import me.makkuusen.timing.system.track.options.TrackOption;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.time.Instant;
import java.util.*;

import static me.makkuusen.timing.system.commands.CommandReset.performInHeatReset;
import static me.makkuusen.timing.system.loneliness.DeltaGhostingController.checkDeltas;

public class TSListener implements Listener {

    static TimingSystem plugin;
    static Set<UUID> inPits = new HashSet<>();

    @EventHandler
    public void onTick(ServerTickStartEvent e) {
        TimingSystem.currentTime = Instant.now();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {

        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {

            TPlayer TPlayer = TSDatabase.getPlayer(event.getUniqueId(), event.getName());

            if (TPlayer == null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Your player profile could not be loaded. Notify the server owner!"));
            }
        }
    }

    @EventHandler
    void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        tPlayer.setPlayer(player);

        if (!tPlayer.getName().equals(player.getName())) {
            // Update name
            tPlayer.setName(player.getName());
        }

        // Issue #51 - Check for other TPlayers that have an identical name.
        // If there is another player with the same name, but not the same UUID. We need to update that TPlayers name.
        for (TPlayer toCheck : TimingSystem.players.values()) {
            if (toCheck == null) {
                plugin.getLogger().warning("[DuplicateUsernameCheck] Variable toCheck was null. That shouldn't be possible. Check for possible data corruption in table ts_players.");
                continue;
            }

            if (toCheck.getName().equalsIgnoreCase(player.getName()) && !toCheck.getUniqueId().equals(player.getUniqueId())) {
                plugin.getLogger().warning("[DuplicateUsernameCheck] Existing TPlayer " + toCheck.getUniqueId().toString() + " has the same username as " + player.getUniqueId().toString() + ".");
                // Check the current username for toCheck. Afterward, update their username
                UUIDFetcher.getAsyncFromMojangAndRunCallback(toCheck.getUniqueId().toString(), new UUIDFetcherCallback() {
                    @Override
                    public void runSync(Optional<UUID> uuid, Optional<String> lastName, String originalMcUsernameOrUuid) {
                        if (uuid.isPresent() && lastName.isPresent()) {
                            TPlayer toUpdate = TSDatabase.getPlayer(uuid.get());
                            toUpdate.setName(lastName.get());
                            plugin.getLogger().info("[DuplicateUsernameCheck] Existing TPlayer " + uuid.get().toString() + "'s name was updated to " + lastName.get() + ".");
                        }
                    }
                });
            }
        }

        Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> {
            if (player.isInsideVehicle() && (player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat) && TimeTrialController.lastTimeTrialTrack.containsKey(player.getUniqueId())) {
                Track track = TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId());
                boolean sameAsLastTrack = TimeTrialController.lastTimeTrialTrack.containsKey(player.getUniqueId()) && TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId()).getId() == track.getId();
                BoatUtilsManager.sendBoatUtilsModePluginMessage(player, track.getBoatUtilsMode(), TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId()), sameAsLastTrack);
                ApiUtilities.teleportPlayerAndSpawnBoat(player, TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId()), player.getLocation().add(0,1,0));
            } else {
                BoatUtilsManager.sendBoatUtilsModePluginMessage(player, BoatUtilsMode.VANILLA, null, true);
            }
        }, 3);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        TimeTrialController.playerLeavingMap(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {

        var maybeDriver = EventDatabase.getDriverFromRunningHeat(event.getPlayer().getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().getState() == DriverState.LOADED) {
                event.setCancelled(true);
                return;
            }
            //Don't care about removing BU effects or playerLeavingMap if driver is an active race.
            return;
        }

        if (event.getCause().equals(PlayerTeleportEvent.TeleportCause.PLUGIN) || event.getCause().equals(PlayerTeleportEvent.TeleportCause.COMMAND) || event.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL) || event.getCause().equals(PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) || event.getCause().equals(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)) {
            TimeTrialController.playerLeavingMap(event.getPlayer().getUniqueId());
            if (ApiUtilities.hasBoatUtilsEffects(event.getPlayer())) {
                ApiUtilities.removeBoatUtilsEffects(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        TimeTrialController.playerLeavingMap(e.getPlayer().getUniqueId());
        BoatUtilsManager.clearPlayerModes(e.getPlayer().getUniqueId());

        if (TimeTrialController.timeTrialSessions.containsKey(e.getPlayer().getUniqueId()) && e.getReason() != PlayerQuitEvent.QuitReason.TIMED_OUT) {
            var ttSession = TimeTrialController.timeTrialSessions.get(e.getPlayer().getUniqueId());
            ttSession.clearScoreboard();
            TimeTrialController.timeTrialSessions.remove(e.getPlayer().getUniqueId());
        }

        //Remove driver from loaded heats.
        Heat heat = CommandRace.heat;
        if (heat == null) {
            return;
        }

        if (CommandRace.heat.getDrivers().containsKey(e.getPlayer().getUniqueId())) {
            if (heat.getHeatState() != HeatState.LOADED) {
                return;
            }
            heat.resetHeat();
            if (heat.removeDriver(heat.getDrivers().get(e.getPlayer().getUniqueId()))) {
                heat.getEvent().removeSpectator(e.getPlayer().getUniqueId());
            }
            heat.loadHeat();
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player && e.getVehicle() instanceof Boat boat && (boat.getPersistentDataContainer().has(Objects.requireNonNull(NamespacedKey.fromString("spawned", plugin))) || boat.getEntitySpawnReason().equals(CreatureSpawnEvent.SpawnReason.COMMAND)) && !e.getVehicle().getPassengers().isEmpty() && e.getVehicle().getPassengers().get(0) instanceof Player) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player && event.getVehicle() instanceof Boat boat && (boat.getPersistentDataContainer().has(Objects.requireNonNull(NamespacedKey.fromString("spawned", plugin))) || boat.getEntitySpawnReason().equals(CreatureSpawnEvent.SpawnReason.COMMAND))) {
            if (!player.isSneaking()) {
                event.setCancelled(true);
                return;
            }
            var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
            if (maybeDriver.isPresent()) {
                Driver driver = maybeDriver.get();
                
                if (driver.getHeat().getPushToPass() != null && driver.getHeat().getPushToPass() 
                    && driver.getState() == DriverState.RUNNING) {
                    PushToPass.togglePushToPass(player);
                    event.setCancelled(true);
                    return;
                }
                
                if (driver.getState() == DriverState.LOADED || driver.getState() == DriverState.STARTING || driver.getState() == DriverState.RUNNING || driver.getState() == DriverState.RESET || driver.getState() == DriverState.LAPRESET) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!boat.getPassengers().isEmpty()) {
                for (Entity e : boat.getPassengers()){
                    if (e instanceof Villager) {
                        e.remove();
                    }
                }
            }
            Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), () -> event.getVehicle().remove(), 4);
        }

        if (event.getExited() instanceof Villager villager && event.getVehicle() instanceof Boat boat && (boat.getPersistentDataContainer().has(Objects.requireNonNull(NamespacedKey.fromString("spawned", plugin))) || boat.getEntitySpawnReason().equals(CreatureSpawnEvent.SpawnReason.COMMAND))) {
            villager.remove();
        }

        if (event.getExited() instanceof Player player) {

            var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
            if (maybeDriver.isPresent()) {
                return;
            }

            if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
                Track track = TimeTrialController.timeTrials.get(player.getUniqueId()).getTrack();
                if (track.getTrackOptions().hasOption(TrackOption.FORCE_BOAT)) {
                    TimeTrialController.playerLeavingMap(player.getUniqueId());
                    if (ApiUtilities.hasBoatUtilsEffects(player)) {
                        ApiUtilities.removeBoatUtilsEffects(player);
                    }
                }
            } else {
                if (ApiUtilities.hasBoatUtilsEffects(player)) {
                    ApiUtilities.removeBoatUtilsEffects(player);
                }
            }

        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        var maybeDriver = EventDatabase.getDriverFromRunningHeat(event.getPlayer().getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().getState() == DriverState.LOADED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockPlaceEvent event) {
        var maybeDriver = EventDatabase.getDriverFromRunningHeat(event.getPlayer().getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().getState() == DriverState.LOADED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        var maybeDriver = EventDatabase.getDriverFromRunningHeat(event.getPlayer().getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().getState() == DriverState.LOADED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        var maybeDriver = EventDatabase.getDriverFromRunningHeat(event.getPlayer().getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().getState() == DriverState.LOADED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
            if (maybeDriver.isPresent()) {
                if (maybeDriver.get().getState() == DriverState.LOADED) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
            if (maybeDriver.isPresent()) {
                if (maybeDriver.get().getState() == DriverState.LOADED) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event.getVehicle() instanceof Boat boat && event.getVehicle().hasMetadata("spawned")) {
            if (!boat.getPassengers().isEmpty()) {
                for (Entity e : boat.getPassengers()) {
                    if (e instanceof Villager) {
                        e.remove();
                    }
                }
            }
            boat.remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityRemoveEvent(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Boat boat && event.getEntity().hasMetadata("spawned")) {
            if (!boat.getPassengers().isEmpty()) {
                for (Entity e : boat.getPassengers()) {
                    if (e instanceof Villager) {
                        e.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityRemoveEvent(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Boat boat && event.getEntity().hasMetadata("spawned")) {
            if (!boat.getPassengers().isEmpty()) {
                for (Entity e : boat.getPassengers()) {
                    if (e instanceof Villager) {
                        e.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player && event.getEntity().getVehicle() != null && event.getEntity().getVehicle() instanceof Boat && event.getEntity().getVehicle().hasMetadata("spawned")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        PlayerRegionData.instanceOf(player).remove();
    }

    @EventHandler
    public static void onPlayerFishEvent(PlayerFishEvent e) {
        if (e.getHook().getHookedEntity() instanceof Player hooked) {
            if (TimeTrialController.timeTrials.containsKey(hooked.getUniqueId())) {
                Text.send(e.getPlayer(), Error.NO_HOOKING_OTHERS);
                e.setCancelled(true);
                return;
            }
        }

        if (e.getCaught() instanceof Player player) {
            if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
                Text.send(e.getPlayer(), Error.NO_FISHING_OTHERS);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerMoveEventRemoveElytra(PlayerMoveEvent e) {

        if (!TimeTrialController.timeTrials.containsKey(e.getPlayer().getUniqueId())) {
            Player player = e.getPlayer();
            var elytra = player.getInventory().getChestplate();
            if (elytra == null || elytra.getItemMeta() == null || !elytra.getItemMeta().hasCustomModelData()) {
                return;
            }
            if (!player.isGliding() && elytra.getItemMeta().getCustomModelData() == 747) {
                if (TimeTrialController.elytraProtection.get(player.getUniqueId()) != null) {
                    if (TimingSystem.currentTime.getEpochSecond() > TimeTrialController.elytraProtection.get(player.getUniqueId())) {
                        player.getInventory().setChestplate(null);
                        TimeTrialController.elytraProtection.remove(player.getUniqueId());
                    }
                } else {
                    player.getInventory().setChestplate(null);
                }
            }
        }
    }

    @EventHandler
    public static void onPlayerSwitchGameModeEvent(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
            Text.send(player, Error.NO_GAME_MODE_CHANGE);
            TimeTrialController.playerLeavingMap(player.getUniqueId());
        }
    }

    @EventHandler
    public static void onPlayerMoveEvent(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
            TimeTrial timeTrial = TimeTrialController.timeTrials.get(player.getUniqueId());
            Track track = timeTrial.getTrack();
            if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType().equals(Material.ELYTRA) && track.getTrackOptions().hasOption(TrackOption.NO_ELYTRA)) {
                Text.send(player, Error.NO_ELYTRA);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (player.getGameMode().equals(GameMode.CREATIVE) && track.getTrackOptions().hasOption(TrackOption.NO_CREATIVE)) {
                Text.send(player, Error.NO_CREATIVE);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (!player.isGliding() && track.getTrackOptions().hasOption(TrackOption.FORCE_ELYTRA)) {
                Text.send(player, Error.STOPPED_FLYING);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (!player.getActivePotionEffects().isEmpty() && track.getTrackOptions().hasOption(TrackOption.NO_POTION_EFFECTS)) {
                Text.send(player, Error.NO_POTION_EFFECTS);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (player.isRiptiding() && track.getTrackOptions().hasOption(TrackOption.NO_RIPTIDE)) {
                Text.send(player, Error.NO_RIPTIDE);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (player.getInventory().getBoots() != null && player.getInventory().getBoots().containsEnchantment(Enchantment.SOUL_SPEED) && track.getTrackOptions().hasOption(TrackOption.NO_SOUL_SPEED)) {
                Text.send(player, Error.NO_SOUL_SPEED);
                TimeTrialController.playerLeavingMap(player.getUniqueId());
            } else if (!BoatUtilsManager.isPlayerUsingCorrectMode(player, track)) {
                Text.send(player, Error.WRONG_BOAT_UTILS_MODE);
                ApiUtilities.removeBoatUtilsEffects(player);
                timeTrial.playerResetMap();
            }

        }
    }

    @EventHandler
    public void onRegionEnter(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!TrackEditor.playerTrackVisualisation.contains(player.getUniqueId())) {
            return;
        }
        var track = TrackEditor.getPlayerTrackSelection(player.getUniqueId());
        if (track == null) {
            return;
        }

        for (TrackRegion region : track.getTrackRegions().getRegions()) {
            if (region.contains(player.getLocation())) {
                player.sendActionBar(Component.text(region.getRegionType() + " : " + region.getRegionIndex()).color(NamedTextColor.GREEN));
                return;
            }
        }
    }

    @EventHandler
    public void onRegionEnterV2(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockY() != e.getTo().getBlockY() || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            Player player = e.getPlayer();

            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }

            var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
            if (maybeDriver.isPresent()) {
                handleHeat(maybeDriver.get(), e);
                return;
            }

            if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
                handleTimeTrials(e);
                // don't need to check for starting new track
                return;
            }

            // Check for starting new tracks
            Iterator<TrackRegion> regions = TrackDatabase.getTrackStartRegions().iterator();
            while (true) {
                Integer regionId;
                TrackRegion region;
                do {
                    label:
                    do {
                        while (regions.hasNext()) {
                            region = regions.next();
                            regionId = region.getId();
                            if (region.contains(player.getLocation())) {
                                continue label;
                            }
                            // Leaving Region
                            PlayerRegionData.instanceOf(player).getEntered().remove(regionId);
                        }

                        return;
                    } while (!player.getWorld().getName().equals(region.getWorldName()));
                } while (PlayerRegionData.instanceOf(player).getEntered().contains(regionId));

                //Entering region
                var maybeTrack = TrackDatabase.getTrackById(region.getTrackId());
                if (maybeTrack.isPresent()) {
                    Track track_ = maybeTrack.get();

                    if (track_.isTimeTrial()) {
                        Instant now = TimingSystem.currentTime;
                        
                        double proportion = calculateRegionEntryProportion(e.getFrom(), e.getTo(), region);
                        long tickDurationNanos = 50_000_000L; // 50ms in nanoseconds (1 tick = 50ms)
                        long adjustmentNanos = (long) ((1.0 - proportion) * tickDurationNanos);
                        now = now.minusNanos(adjustmentNanos);
                        
                        TimeTrial timeTrial = new TimeTrial(track_, TSDatabase.getPlayer(player.getUniqueId()), now);
                        timeTrial.playerStartingTimeTrial();
                        TimeTrialController.elytraProtection.remove(player.getUniqueId());
                        TimeTrialController.lastTimeTrialTrack.put(player.getUniqueId(), track_);
                    }
                }
                PlayerRegionData.instanceOf(player).getEntered().add(regionId);
            }
        }
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        TPlayer TPlayer = TSDatabase.getPlayer(event.getPlayer());
        // Set to offline
        TPlayer.setPlayer(null);
        TPlayer.clearScoreboard();
        BoatUtilsManager.clearPlayerModes(event.getPlayer().getUniqueId());
    }

    static void handleTimeTrials(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        TimeTrial timeTrial = TimeTrialController.timeTrials.get(player.getUniqueId());
        // Check for ending current map.
        var track = timeTrial.getTrack();

        var startRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.START);
        var endRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.END);

        if (startRegions.isEmpty()) {
            return;
        }

        if (endRegions.isEmpty()) {
            for (var r : startRegions) {
                if (r.contains(player.getLocation())) {
                    if (timeTrial.getLatestCheckpoint() != 0) {
                        timeTrial.playerRestartMap(e.getFrom(), e.getTo(), r);
                        return;
                    }
                }
            }
        } else {
            for (var r : endRegions) {
                if (r.contains(player.getLocation())) {
                    if (timeTrial.getLatestCheckpoint() != 0) {
                        timeTrial.playerEndedMap(e.getFrom(), e.getTo(), r);
                        return;
                    }
                }
            }
        }


        for (TrackRegion r : track.getTrackRegions().getRegions(TrackRegion.RegionType.RESET)) {
            if (r.contains(player.getLocation())) {
                if (!track.getTrackOptions().hasOption(TrackOption.RESET_TO_LATEST_CHECKPOINT) && !track.getTrackOptions().hasOption(TrackOption.RESET_TO_RESET_REGION_SPAWN)) {
                    timeTrial.playerResetMap();
                } else if (track.getTrackOptions().hasOption(TrackOption.RESET_TO_RESET_REGION_SPAWN)) {
                    ApiUtilities.teleportPlayerAndSpawnBoat(player, track, r.getSpawnLocation());
                } else {
                    var maybeRegion = track.getTrackRegions().getRegion(TrackRegion.RegionType.CHECKPOINT, timeTrial.getLatestCheckpoint());
                    if (maybeRegion.isEmpty()) {
                        timeTrial.playerResetMap();
                        return;
                    }
                    ApiUtilities.teleportPlayerAndSpawnBoat(player, track, maybeRegion.get().getSpawnLocation());
                }
                return;
            }
        }

        if (!timeTrial.isLagStart() && !track.getTrackRegions().getRegions(TrackRegion.RegionType.LAGSTART).isEmpty()) {
            for (TrackRegion region : track.getTrackRegions().getRegions(TrackRegion.RegionType.LAGSTART)) {
                if (region.contains(player.getLocation())) {
                    timeTrial.setLagStartTrue();
                    timeTrial.playerPassingLagStart();
                    if (ApiUtilities.getRoundedToTick(timeTrial.getTimeSinceStart(TimingSystem.currentTime)) == 0) {
                        Text.send(player, Error.LAG_DETECTED);
                        plugin.getLogger().warning(player.getName() + " failed lagstart on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrial.getTimeSinceStart(TimingSystem.currentTime)));
                        timeTrial.playerResetMap();
                        return;
                    } else {
                        ApiUtilities.msgConsole(player.getName() + " passed lagstart on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrial.getTimeSinceStart(TimingSystem.currentTime)));
                    }
                }
            }
        }

        if (!timeTrial.isLagEnd() && !track.getTrackRegions().getRegions(TrackRegion.RegionType.LAGEND).isEmpty()) {
            for (TrackRegion region : track.getTrackRegions().getRegions(TrackRegion.RegionType.LAGEND)) {
                if (region.contains(player.getLocation())) {
                    timeTrial.setLagEnd(true);
                    timeTrial.playerPassingLagEnd();
                    if (!timeTrial.isLagStart()) {
                        Text.send(player, Error.LAG_DETECTED);
                        plugin.getLogger().warning(player.getName() + " failed lagend on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrial.getTimeSinceStart(TimingSystem.currentTime)));
                        timeTrial.playerResetMap();
                        return;
                    } else if (TimingSystem.currentTime.toEpochMilli() == timeTrial.getLagStart().toEpochMilli()) {
                        Text.send(player, Error.LAG_DETECTED);
                        plugin.getLogger().warning(player.getName() + " failed lagend on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrial.getTimeSinceStart(TimingSystem.currentTime)));
                        timeTrial.playerResetMap();
                        return;
                    } else {
                        ApiUtilities.msgConsole(player.getName() + " passed lagend on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrial.getTimeSinceStart(TimingSystem.currentTime)));
                    }
                }
            }
        }

        // Check for next checkpoint in current map
        int nextCheckpoint = timeTrial.getNextCheckpoint();
        if (nextCheckpoint == timeTrial.getLatestCheckpoint()) {
            return;
        }
        for (TrackRegion checkpoint : track.getTrackRegions().getCheckpoints(nextCheckpoint)) {
            if (checkpoint.contains(player.getLocation())){
                timeTrial.playerPassingNextCheckpoint(e.getFrom(), e.getTo(), checkpoint);
            }
        }
    }

    private static void handleHeat(Driver driver, PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Heat heat = driver.getHeat();

        if (!heat.getHeatState().equals(HeatState.RACING)) {
            return;
        }

        if (driver.isFinished()) {
            return;
        }
        var track = heat.getEvent().getTrack();

        var startRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.START);
        if (startRegions.isEmpty()) {
            return;
        }

        for (var r : startRegions) {
            if (r.contains(player.getLocation())) {
                if (driver.getState() == DriverState.STARTING) {
                    driver.start();
                    heat.updatePositions();
                    
                    if (heat.isBoatSwitchingEnabled()) {
                        var maybeTeamEntry = heat.getTeamEntryByPlayer(player.getUniqueId());
                        if (maybeTeamEntry.isPresent()) {
                            var teamEntry = maybeTeamEntry.get();
                            teamEntry.updateRaceProgress(1, 0);
                        }
                    }
                    
                    ApiUtilities.msgConsole("Starting : " + player.getName() + " in " + heat.getName());
                    if (heat.getGhostingDelta() != null) {
                        checkDeltas(driver);
                    }
                    return;
                } else if (driver.getState() == DriverState.RESET) {
                    driver.resetQualyLap();
                    heat.updatePositions();
                    return;
                } else if (driver.getState() == DriverState.LAPRESET) {
                    driver.lapReset();
                    heat.updatePositions();
                    return;
                } else if (driver.getCurrentLap() != null && driver.getCurrentLap().getLatestCheckpoint() != 0) {
                    if (!driver.getCurrentLap().hasPassedAllCheckpoints()) {
                        int checkpoint = driver.getCurrentLap().getLatestCheckpoint();
                        performInHeatReset(driver);
                        Text.send(driver.getTPlayer().getPlayer(), Error.MISSED_CHECKPOINTS);

                        return;
                    }
                    heat.passLap(driver, e.getFrom(), e.getTo(), r);
                    heat.updatePositions();
                    
                    if (heat.isBoatSwitchingEnabled()) {
                        var maybeTeamEntry = heat.getTeamEntryByPlayer(player.getUniqueId());
                        if (maybeTeamEntry.isPresent()) {
                            var teamEntry = maybeTeamEntry.get();
                            teamEntry.updateRaceProgress(driver.getLaps().size(), 0);
                        }
                    }
                    
                    if (heat.getGhostingDelta() != null) {
                        checkDeltas(driver);
                    }
                    return;
                }
            }
        }

        if (track.isStage()) {
            for (var r : track.getTrackRegions().getRegions(TrackRegion.RegionType.END)) {
                if (r.contains(player.getLocation())) {
                    if (driver.getCurrentLap() != null) {
                        if (!(driver.getCurrentLap().hasPassedAllCheckpoints())) {
                            int checkpoint = driver.getCurrentLap().getLatestCheckpoint();
                            performInHeatReset(driver);
                            Text.send(driver.getTPlayer().getPlayer(), Error.MISSED_CHECKPOINTS);
                            return;
                        }
                    }
                    heat.passLap(driver, e.getFrom(), e.getTo(), r);
                    heat.updatePositions();
                    
                    if (heat.isBoatSwitchingEnabled()) {
                        var maybeTeamEntry = heat.getTeamEntryByPlayer(player.getUniqueId());
                        if (maybeTeamEntry.isPresent()) {
                            var teamEntry = maybeTeamEntry.get();
                            teamEntry.updateRaceProgress(driver.getLaps().size(), 0);
                        }
                    }
                    
                    return;
                }
            }
        }


        if (driver.getState() == DriverState.RUNNING) {
            Lap lap = driver.getCurrentLap();

            if (lap == null) {
                return;
            }

            if (driver.getHeat().getRound() instanceof FinalRound) {
                // Check for pitstop
                for (var r : track.getTrackRegions().getRegions(TrackRegion.RegionType.PIT)) {
                    if (r.contains(player.getLocation())) {
                        if (driver.passPit()) {
                            heat.updatePositions();
                            break;
                        }
                    }
                }
            }

            // Check for reset
            for (TrackRegion r : track.getTrackRegions().getRegions(TrackRegion.RegionType.RESET)) {
                if (r.contains(player.getLocation())) {
                    if (track.getTrackOptions().hasOption(TrackOption.RESET_TO_RESET_REGION_SPAWN)) {
                        ApiUtilities.teleportPlayerAndSpawnBoat(player, track, r.getSpawnLocation());
                        return;
                    }
                    // var maybeRegion = track.getTrackRegions().getRegion(TrackRegion.RegionType.CHECKPOINT, lap.getLatestCheckpoint());

                    // TrackRegion region;
                    // region = maybeRegion.orElseGet(() -> track.getTrackRegions().getStart().get());
                    performInHeatReset(driver);
                    return;
                }
            }

            // Update if in pit
            var inPitRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.INPIT);
            for (TrackRegion trackRegion : inPitRegions) {
                if (trackRegion.contains(player.getLocation()) && !inPits.contains(player.getUniqueId())) {
                    inPits.add(player.getUniqueId());
                    heat.updatePositions();
                    if (driver.getHeat().getPushToPass() != null && driver.getHeat().getPushToPass()) {
                        PushToPass.handleInpitEntry(player);
                    }
                } else if (!trackRegion.contains(player.getLocation()) && inPits.contains(player.getUniqueId())) {
                    inPits.remove(player.getUniqueId());
                    heat.updatePositions();
                }
            }

            // Check for next checkpoint in current map
            if (lap.hasPassedAllCheckpoints()) {
                return;
            }

            var maybeCheckpoint = track.getTrackRegions().getRegions(TrackRegion.RegionType.CHECKPOINT).stream().filter(trackRegion -> trackRegion.contains(player.getLocation())).findFirst();
            if (maybeCheckpoint.isPresent() && maybeCheckpoint.get().getRegionIndex() == lap.getNextCheckpoint()) {
                lap.passNextCheckpoint(e.getFrom(), e.getTo(), maybeCheckpoint.get());
                heat.updatePositions();

                if (heat.isBoatSwitchingEnabled()) {
                    var maybeTeamEntry = heat.getTeamEntryByPlayer(player.getUniqueId());
                    if (maybeTeamEntry.isPresent()) {
                        var teamEntry = maybeTeamEntry.get();
                        teamEntry.updateRaceProgress(driver.getLaps().size(), maybeCheckpoint.get().getRegionIndex());
                    }
                }

                if (heat.getGhostingDelta() != null) {
                    checkDeltas(driver);
                }

                // Calculate precise time for the event
                java.time.Instant preciseTime = me.makkuusen.timing.system.TimingSystem.currentTime;
                double proportion = calculateRegionEntryProportion(e.getFrom(), e.getTo(), maybeCheckpoint.get());
                long tickDurationNanos = 50_000_000L;
                long adjustmentNanos = (long) ((1.0 - proportion) * tickDurationNanos);
                preciseTime = preciseTime.minusNanos(adjustmentNanos);
                
                new DriverPassCheckpointEvent(driver, lap, maybeCheckpoint.get(), preciseTime).callEvent();
            } else if (maybeCheckpoint.isPresent() && maybeCheckpoint.get().getRegionIndex() > lap.getNextCheckpoint()) {
                if (!track.getTrackOptions().hasOption(TrackOption.NO_RESET_ON_FUTURE_CHECKPOINT)) {
                    performInHeatReset(driver);
                    Text.send(driver.getTPlayer().getPlayer(), Error.MISSED_CHECKPOINTS);
                }
            }
        }
    }

    /**
     * Calculates the proportion of the movement vector where the player enters the region
     * using binary search with 15 iterations for precision.
     * 
     * @param from The starting location of the movement
     * @param to The ending location of the movement  
     * @param region The region being entered
     * @return A value between 0.0 and 1.0 representing how far through the movement the entry occurred
     */
    private static double calculateRegionEntryProportion(org.bukkit.Location from, org.bukkit.Location to, TrackRegion region) {
        double low = 0.0;
        double high = 1.0;
        
        // Binary search with 15 iterations for precision
        for (int i = 0; i < 15; i++) {
            double mid = (low + high) / 2.0;
            
            // Calculate the interpolated position at the midpoint
            org.bukkit.Location midLocation = interpolateLocation(from, to, mid);
            
            if (region.contains(midLocation)) {
                // If the midpoint is inside the region, the entry point is earlier in the movement
                high = mid;
            } else {
                // If the midpoint is outside the region, the entry point is later in the movement
                low = mid;
            }
        }
        
        // Return the final proportion (closer to the actual entry point)
        return (low + high) / 2.0;
    }
    
    /**
     * Interpolates between two locations based on a proportion value.
     * 
     * @param from The starting location
     * @param to The ending location
     * @param proportion A value between 0.0 and 1.0
     * @return The interpolated location
     */
    private static org.bukkit.Location interpolateLocation(org.bukkit.Location from, org.bukkit.Location to, double proportion) {
        double x = from.getX() + (to.getX() - from.getX()) * proportion;
        double y = from.getY() + (to.getY() - from.getY()) * proportion;
        double z = from.getZ() + (to.getZ() - from.getZ()) * proportion;
        
        return new org.bukkit.Location(from.getWorld(), x, y, z);
    }
}
