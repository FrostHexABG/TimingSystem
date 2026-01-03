package me.makkuusen.timing.system.participant;

import me.makkuusen.timing.system.ReadyCheckManager;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static me.makkuusen.timing.system.TimingSystem.getPlugin;

public class InHeatBoatExitMSG implements Listener {

    private static final Field DRIVER_HEAT_FIELD;
    private static final Field HEAT_STATE_FIELD;

    private final Set<UUID> cooldown = new HashSet<>();

    static {
        try {
            DRIVER_HEAT_FIELD = Driver.class.getDeclaredField("heat");
            DRIVER_HEAT_FIELD.setAccessible(true);

            HEAT_STATE_FIELD = Heat.class.getDeclaredField("heatState");
            HEAT_STATE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Unable to initialize reflection for InHeatBoatExitMSG", e);
        }
    }

    @EventHandler
    public void onBoatExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        if (!(event.getVehicle() instanceof org.bukkit.entity.Boat)) return;

        if (cooldown.contains(player.getUniqueId())) return;

        var maybeDriver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isEmpty()) return;

        try {
            Heat heat = (Heat) DRIVER_HEAT_FIELD.get(maybeDriver.get());
            HeatState state = (HeatState) HEAT_STATE_FIELD.get(heat);

            if (state != HeatState.RACING) return;
        } catch (IllegalAccessException e) {
            return;
        }

        if (ReadyCheckManager.isReadyCheckInProgress(player)) return;

        player.sendMessage(Component.text("§cAre you stuck? Use '§7§l/reset§r§c' to return to the track"));
        player.sendMessage(Component.text("§cTo leave the race, use '§7§l/race leave§r§c'"));

        cooldown.add(player.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                cooldown.remove(player.getUniqueId());
            }
        }.runTaskLater(getPlugin(), 40);
    }
}