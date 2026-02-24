package me.makkuusen.timing.system.drs;

import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.boatutils.BoatUtilsManager;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.heat.CollisionMode;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DrsManager {
    
    private static final Map<UUID, DrsData> drsEnabledPlayers = new HashMap<>();
    private static final Map<Integer, Map<UUID, Instant>> drsDetectRegionPasses = new HashMap<>();
    private static final Map<Integer, Map<UUID, Instant>> drsEnabledInRegion = new HashMap<>();
    private static final Map<UUID, Integer> activeDrsPlayers = new HashMap<>();
    
    private static final short PACKET_ID_SET_FORWARD_ACCELERATION = 11;
    
    public static void activateDrs(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (activeDrsPlayers.containsKey(playerId)) {
            return;
        }
        
        double drsForwardAccel = getDrsForwardAccel();
        int drsDuration = getDrsDuration();
        
        sendForwardAccelerationPacket(player, (float) drsForwardAccel);
        
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
            TimingSystem.getPlugin(),
            () -> deactivateDrs(player),
            drsDuration / 50
        );
        
        activeDrsPlayers.put(playerId, taskId);

        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(playerId);
        if (maybeDriver.isPresent()) {
            Driver driver = maybeDriver.get();
            Heat heat = driver.getHeat();
            heat.getDrivers().values().forEach(Driver::updateScoreboard);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 2.0f);
    }

    public static void deactivateDrs(Player player) {
        UUID playerId = player.getUniqueId();
        
        Integer taskId = activeDrsPlayers.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        resetToTrackSettings(player);

        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(playerId);
        if (maybeDriver.isPresent()) {
            Driver driver = maybeDriver.get();
            Heat heat = driver.getHeat();
            heat.getDrivers().values().forEach(Driver::updateScoreboard);
        }
    }

    public static int getDrsDuration() {
        return TimingSystem.configuration.getDrsDuration();
    }

    public static double getDrsForwardAccel() {
        return TimingSystem.configuration.getDrsForwardAccel();
    }

    public static void playerPassedDrsDetect(Player player, int regionId) {
        UUID playerId = player.getUniqueId();
        Instant now = Instant.now();
        
        Map<UUID, Instant> regionPasses = drsDetectRegionPasses.computeIfAbsent(regionId, k -> new HashMap<>());
        Map<UUID, Instant> drsEnabledTimes = drsEnabledInRegion.computeIfAbsent(regionId, k -> new HashMap<>());
        
        int minDelta = TimingSystem.configuration.getDrsMinDelta();
        int maxDelta = TimingSystem.configuration.getDrsMaxDelta();
        
        boolean shouldEnableDrs = false;
        long closestTimeDiff = Long.MAX_VALUE;
        
        for (Map.Entry<UUID, Instant> entry : regionPasses.entrySet()) {
            UUID otherPlayerId = entry.getKey();
            Instant otherPlayerTime = entry.getValue();
            
            if (otherPlayerId.equals(playerId)) {
                continue;
            }
            
            long timeDiff = now.toEpochMilli() - otherPlayerTime.toEpochMilli();
            
            if (timeDiff >= minDelta && timeDiff <= maxDelta) {
                shouldEnableDrs = true;
                if (timeDiff < closestTimeDiff) {
                    closestTimeDiff = timeDiff;
                }
            }


            // drs chaining
            else if (timeDiff > 0 && timeDiff < minDelta) {
                if (drsEnabledTimes.containsKey(otherPlayerId)) {
                    Instant otherPlayerDrsTime = drsEnabledTimes.get(otherPlayerId);
                    long timeSinceDrsEnabled = now.toEpochMilli() - otherPlayerDrsTime.toEpochMilli();
                    if (timeSinceDrsEnabled <= maxDelta) {
                        shouldEnableDrs = true;
                        if (timeDiff < closestTimeDiff) {
                            closestTimeDiff = timeDiff;
                        }
                    }
                }
            }
        }
        
        regionPasses.put(playerId, now);
        
        if (shouldEnableDrs) {
            enableDrs(player);
            drsEnabledTimes.put(playerId, now);
        }
    }

    public static void playerPassedDrsActivate(Player player, int regionId) {
        UUID playerId = player.getUniqueId();
        
        if (hasDrsEnabled(playerId)) {
            activateDrs(player);
            disableDrs(player);
        }
    }

    private static void enableDrs(Player player) {
        UUID playerId = player.getUniqueId();
        if (!drsEnabledPlayers.containsKey(playerId)) {
            drsEnabledPlayers.put(playerId, new DrsData(Instant.now()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private static void disableDrs(Player player) {
        drsEnabledPlayers.remove(player.getUniqueId());
    }

    public static boolean hasDrsEnabled(UUID playerId) {
        return drsEnabledPlayers.containsKey(playerId);
    }

    public static boolean hasDrsActive(UUID playerId) {
        return activeDrsPlayers.containsKey(playerId);
    }
    
    public static void cleanupPlayer(UUID playerId) {
        drsEnabledPlayers.remove(playerId);
        
        Integer taskId = activeDrsPlayers.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        for (Map<UUID, Instant> regionPasses : drsDetectRegionPasses.values()) {
            regionPasses.remove(playerId);
        }
        
        for (Map<UUID, Instant> drsEnabled : drsEnabledInRegion.values()) {
            drsEnabled.remove(playerId);
        }
    }

    public static void cleanupOldDetections() {
        Instant now = Instant.now();
        int maxDelta = TimingSystem.configuration.getDrsMaxDelta();
        long cutoffTime = now.toEpochMilli() - (maxDelta * 2);
        
        for (Map<UUID, Instant> regionPasses : drsDetectRegionPasses.values()) {
            regionPasses.entrySet().removeIf(entry -> 
                entry.getValue().toEpochMilli() < cutoffTime
            );
        }
        
        for (Map<UUID, Instant> drsEnabled : drsEnabledInRegion.values()) {
            drsEnabled.entrySet().removeIf(entry -> 
                entry.getValue().toEpochMilli() < cutoffTime
            );
        }
    }

    private static void sendForwardAccelerationPacket(Player player, float acceleration) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(PACKET_ID_SET_FORWARD_ACCELERATION);
            out.writeFloat(acceleration);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to send DRS forward acceleration packet to " + player.getName());
            e.printStackTrace();
        }
    }
    
    public static void resetToTrackSettings(Player player) {
        Optional<Driver> maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            Heat heat = maybeDriver.get().getHeat();
            Track track = heat.getEvent().getTrack();
            if (track != null) {
                Integer customModeId = track.getCustomBoatUtilsModeId();
                if (customModeId != null) {
                    CustomBoatUtilsMode bume = TimingSystem.getTrackDatabase().getCustomBoatUtilsModeFromId(customModeId);
                    if (bume != null && bume.applyToPlayer(player)) {
                        BoatUtilsManager.playerCustomBoatUtilsModeId.put(player.getUniqueId(), customModeId);
                    } else {
                        CustomBoatUtilsMode.resetPlayer(player);
                        BoatUtilsManager.playerCustomBoatUtilsModeId.remove(player.getUniqueId());
                        var mode = track.getBoatUtilsMode();
                        BoatUtilsManager.sendBoatUtilsModePluginMessage(player, mode, track, false);
                    }
                } else {
                    CustomBoatUtilsMode.resetPlayer(player);
                    BoatUtilsManager.playerCustomBoatUtilsModeId.remove(player.getUniqueId());
                    var mode = track.getBoatUtilsMode();
                    BoatUtilsManager.sendBoatUtilsModePluginMessage(player, mode, track, false);
                }
                LonelinessController.updatePlayersVisibility(player);
                LonelinessController.updatePlayerVisibility(player);
            }
            return;
        }
        
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(0);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to reset BoatUtils for " + player.getName());
            e.printStackTrace();
        }
    }

    private static class DrsData {
        private final Instant enabledTime;
        
        public DrsData(Instant enabledTime) {
            this.enabledTime = enabledTime;
        }
        
        public Instant getEnabledTime() {
            return enabledTime;
        }
    }
}
