package me.makkuusen.timing.system.ping;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import me.makkuusen.timing.system.ApiUtilities;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ProtocolLibPingMeasurer extends PacketAdapter {
    private static final int SAMPLE_INTERVAL_TICKS = 40;
    private static final long STALE_AFTER_NANOS = 60_000_000_000L;

    private final Map<UUID, Outstanding> outstanding = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();

    public ProtocolLibPingMeasurer(Plugin plugin) {
        super(plugin, ListenerPriority.MONITOR, PacketType.Play.Client.PONG);
    }

    public void start() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
        Bukkit.getScheduler().runTaskTimer(getPlugin(), this::sampleEveryone, SAMPLE_INTERVAL_TICKS, SAMPLE_INTERVAL_TICKS);
        PingRegistry.setAvailable(true);
        ApiUtilities.msgConsole("Ping balancing is measuring latency through ProtocolLib");
    }

    private void sampleEveryone() {
        outstanding.values().removeIf(sent -> System.nanoTime() - sent.sentNanos() > STALE_AFTER_NANOS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            int id = nextId.incrementAndGet();
            PacketContainer ping = new PacketContainer(PacketType.Play.Server.PING);
            ping.getIntegers().write(0, id);
            outstanding.put(player.getUniqueId(), new Outstanding(id, System.nanoTime()));
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, ping);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Outstanding sent = outstanding.get(player.getUniqueId());
        if (sent == null || event.getPacket().getIntegers().read(0) != sent.id()) {
            return;
        }
        outstanding.remove(player.getUniqueId());
        PingRegistry.record(player.getUniqueId(), (int) ((System.nanoTime() - sent.sentNanos()) / 1_000_000L));
    }

    private record Outstanding(int id, long sentNanos) {
    }
}
