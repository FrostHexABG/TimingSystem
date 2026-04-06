package me.makkuusen.timing.system.boatutils;

import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.tplayer.TPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

public class NocolManager {
    private static final short PACKET_ID_NOCOL = 27;
    private static final short PACKET_ID_COLLISION_FILTER = 31;
    private static final short NOCOL_MODE_VANILLA = 0;
    private static final short NOCOL_MODE_NO_COLLISION_BOATS_PLAYERS = 1;
    private static final short NOCOL_MODE_NO_COLLISION_ANY = 2;
    private static final short NOCOL_MODE_FILTERED_COLLISION = 3;
    private static final short NOCOL_MODE_FILTERED_COLLISION_PLUS = 4;
    
    // Legacy constants for backward compatibility
    private static final short NOCOL_MODE_ON_ID = NOCOL_MODE_NO_COLLISION_ANY;
    private static final short NOCOL_MODE_OFF_ID = NOCOL_MODE_VANILLA;

    private static void sendShortAndShortPacket(Player player, short packetId, short value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeShort(value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            TimingSystem.getPlugin().getLogger().log(Level.SEVERE,
                    "Failed to serialize and send packet " + packetId + " for player " + player.getName(), e);
        }
    }

    private static void sendShortAndStringPacket(Player player, short packetId, String value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            CustomBoatUtilsMode.writeString(out, value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
            TimingSystem.getPlugin().getLogger().info("Sent collision filter packet " + packetId + " with value '" + value + "' to " + player.getName());
        } catch (IOException e) {
            TimingSystem.getPlugin().getLogger().log(Level.SEVERE,
                    "Failed to serialize and send packet " + packetId + " for player " + player.getName(), e);
        }
    }

    public static boolean playerCanUseNocol(Player player) {
        if (player == null) return false;
        TPlayer tplayer = TimingSystemAPI.getTPlayer(player.getUniqueId());
        if (!tplayer.hasBoatUtils()) return false;
        return (tplayer.getBoatUtilsVersion() >= 10);
    }

    public static boolean playerCanUseFilteredCollision(Player player) {
        if (player == null) return false;
        TPlayer tplayer = TimingSystemAPI.getTPlayer(player.getUniqueId());
        if (!tplayer.hasBoatUtils()) return false;
        return (tplayer.getBoatUtilsVersion() >= 16);
    }

    public static void setCollisionMode(Player player, boolean shouldCollide) {
        if (!playerCanUseNocol(player)) return;

        if (shouldCollide) {
            sendShortAndShortPacket(player, PACKET_ID_NOCOL, NOCOL_MODE_OFF_ID);
        } else {
            sendShortAndShortPacket(player, PACKET_ID_NOCOL, NOCOL_MODE_ON_ID);
        }
    }

    public static void setLowCollisionMode(Player player) {
        if (!playerCanUseFilteredCollision(player)) {
            return;
        }

        sendShortAndStringPacket(player, PACKET_ID_COLLISION_FILTER, "minecraft:player,minecraft:villager,minecraft:wandering_trader");
        sendShortAndShortPacket(player, PACKET_ID_NOCOL, NOCOL_MODE_FILTERED_COLLISION);

    }
}
