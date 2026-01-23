package me.makkuusen.timing.system.drs;

import lombok.Getter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PushToPass {
    
    private static final Map<UUID, PushToPassData> pushToPassPlayers = new HashMap<>();
    private static final Map<UUID, Long> toggleCooldowns = new HashMap<>();
    private static final short PACKET_ID_SET_FORWARD_ACCELERATION = 11;
    private static final long TOGGLE_COOLDOWN_MS = 500;
    
    /**
     * Activates push to pass for a player if they have charge available
     */
    public static void activatePushToPass(Player player) {
        UUID playerId = player.getUniqueId();
        PushToPassData data = pushToPassPlayers.get(playerId);
        
        if (data == null || data.isActive()) {
            return;
        }
        
        data.updateCharge();
        
        if (data.getChargePercent() <= 0) {
            return;
        }
        
        data.setActive(true);
        double forwardAccel = TimingSystem.configuration.getPushToPassForwardAccel();
        sendForwardAccelerationPacket(player, (float) forwardAccel);
        
        updateDriverScoreboard(playerId);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
    }
    
    /**
     * Deactivates push to pass for a player
     */
    public static void deactivatePushToPass(Player player) {
        UUID playerId = player.getUniqueId();
        PushToPassData data = pushToPassPlayers.get(playerId);
        
        if (data == null || !data.isActive()) {
            return;
        }
        
        data.updateCharge();
        data.setActive(false);
        
        DrsManager.resetToTrackSettings(player);
        updateDriverScoreboard(playerId);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
    }
    
    /**
     * Toggles push to pass on/off
     */
    public static void togglePushToPass(Player player) {
        UUID playerId = player.getUniqueId();
        PushToPassData data = pushToPassPlayers.get(playerId);
        
        if (data == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        Long lastToggle = toggleCooldowns.get(playerId);
        if (lastToggle != null && (currentTime - lastToggle) < TOGGLE_COOLDOWN_MS) {
            return;
        }
        
        toggleCooldowns.put(playerId, currentTime);
        
        if (data.isActive()) {
            deactivatePushToPass(player);
        } else {
            activatePushToPass(player);
        }
    }
    
    /**
     * Initializes push to pass for a player (called when heat starts)
     */
    public static void initializePushToPass(UUID playerId) {
        int startingCharge = TimingSystem.configuration.getPushToPassStartingCharge();
        PushToPassData data = new PushToPassData(startingCharge);
        pushToPassPlayers.put(playerId, data);
        
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            data.addPlayer(player);
        }
    }
    
    /**
     * Cleans up push to pass data for a player
     */
    public static void cleanupPlayer(UUID playerId) {
        PushToPassData data = pushToPassPlayers.remove(playerId);
        toggleCooldowns.remove(playerId);
        if (data != null) {
            if (data.isActive()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    DrsManager.resetToTrackSettings(player);
                }
            }
            data.cleanup();
        }
    }
    
    /**
     * Gets the current charge percentage for a player (0-100)
     */
    public static double getChargePercent(UUID playerId) {
        PushToPassData data = pushToPassPlayers.get(playerId);
        if (data == null) {
            return 0;
        }
        data.updateCharge();
        return data.getChargePercent();
    }
    
    /**
     * Checks if push to pass is currently active for a player
     */
    public static boolean isPushToPassActive(UUID playerId) {
        PushToPassData data = pushToPassPlayers.get(playerId);
        return data != null && data.isActive();
    }
    
    /**
     * Updates charge for all active players (should be called periodically)
     */
    public static void updateAllCharges() {
        for (Map.Entry<UUID, PushToPassData> entry : pushToPassPlayers.entrySet()) {
            PushToPassData data = entry.getValue();
            data.updateCharge();
            double newCharge = data.getChargePercent();
            
            if (data.isActive() && newCharge <= 0) {
                UUID playerId = entry.getKey();
                Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        deactivatePushToPass(player);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                });
            }
        }
    }
    
    private static void updateDriverScoreboard(UUID playerId) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(playerId);
        if (maybeDriver.isPresent()) {
            Driver driver = maybeDriver.get();
            Heat heat = driver.getHeat();
            heat.getDrivers().values().forEach(Driver::updateScoreboard);
        }
    }
    
    private static void sendForwardAccelerationPacket(Player player, float acceleration) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(PACKET_ID_SET_FORWARD_ACCELERATION);
            out.writeFloat(acceleration);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            TimingSystem.getPlugin().getLogger().warning("Failed to send Push to Pass forward acceleration packet to " + player.getName());
            e.printStackTrace();
        }
    }

    @Getter
    private static class PushToPassData {
        private double chargePercent;
        private boolean active;
        private Instant lastUpdate;
        private BossBar bossBar;
        
        public PushToPassData(double startingCharge) {
            this.chargePercent = Math.max(0, Math.min(100, startingCharge));
            this.active = false;
            this.lastUpdate = Instant.now();
            this.bossBar = Bukkit.createBossBar("Push to Pass", BarColor.GREEN, BarStyle.SOLID);
            this.bossBar.setProgress(startingCharge / 100.0);
        }
        
        public void setActive(boolean active) {
            this.active = active;
            this.lastUpdate = Instant.now();
            updateBossBar();
        }
        
        public void addPlayer(Player player) {
            if (bossBar != null && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        
        public void cleanup() {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
        }
        
        private void updateBossBar() {
            if (bossBar == null) {
                return;
            }
            
            // Update progress (0.0 to 1.0)
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, chargePercent / 100.0)));
            
            if (active) {
                bossBar.setColor(BarColor.PINK);
                bossBar.setTitle(String.format("Push to Pass: ACTIVE (%.0f%%)", chargePercent));
            } else {
                if (chargePercent >= 67) {
                    bossBar.setColor(BarColor.GREEN);
                } else if (chargePercent >= 33) {
                    bossBar.setColor(BarColor.YELLOW);
                } else {
                    bossBar.setColor(BarColor.RED);
                }
                bossBar.setTitle(String.format("Push to Pass: %.0f%%", chargePercent));
            }
        }
        
        /**
         * Updates the charge based on time elapsed since last update
         */
        public void updateCharge() {
            Instant now = Instant.now();
            long elapsedMillis = now.toEpochMilli() - lastUpdate.toEpochMilli();
            
            if (elapsedMillis <= 0) {
                return;
            }
            
            if (active) {
                // Draining
                int maxUseTime = TimingSystem.configuration.getPushToPassMaxUseTime();
                double drainRate = 100.0 / maxUseTime; // percent per millisecond
                chargePercent -= drainRate * elapsedMillis;
                if (chargePercent < 0) {
                    chargePercent = 0;
                }
            } else {
                // Charging
                int fullChargeTime = TimingSystem.configuration.getPushToPassFullChargeTime();
                double chargeRate = 100.0 / fullChargeTime; // percent per millisecond
                double oldCharge = chargePercent;
                chargePercent += chargeRate * elapsedMillis;
                if (chargePercent > 100) {
                    chargePercent = 100;
                }

                if (oldCharge < 100 && chargePercent >= 100) {
                    for (Player player : bossBar.getPlayers()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                }
            }
            
            lastUpdate = now;
            updateBossBar();
        }
    }
}
