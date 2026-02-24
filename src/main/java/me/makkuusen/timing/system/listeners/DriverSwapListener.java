package me.makkuusen.timing.system.listeners;

import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.heat.DriverSwapHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class DriverSwapListener implements Listener {

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player clicker = event.getPlayer();
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(clicker.getUniqueId());
        if (maybeDriver.isEmpty()) {
            return;
        }

        if (DriverSwapHandler.handleRightClickSwap(clicker, target)) {
            event.setCancelled(true);
        }
    }
}
