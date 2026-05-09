package me.makkuusen.timing.system.listeners;

import com.Zrips.CMI.events.CMIAfkEnterEvent;
import com.Zrips.CMI.events.CMIAfkLeaveEvent;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Created to solve bug caused by CMI on BRWC. Issue #176
 * @author Justin Brubaker
 */
public class CMIListener implements Listener {

    @EventHandler
    public void onCMIAfkEnter(CMIAfkEnterEvent e) {
        TimeTrialController.playerLeavingMap(e.getPlayer().getUniqueId());
        if (ApiUtilities.hasBoatUtilsEffects(e.getPlayer())) {
            ApiUtilities.removeBoatUtilsEffects(e.getPlayer());
        }
    }

    @EventHandler
    public void onCMIAFKLeave(CMIAfkLeaveEvent e) {
        TimeTrialController.playerLeavingMap(e.getPlayer().getUniqueId());
        if (ApiUtilities.hasBoatUtilsEffects(e.getPlayer())) {
            ApiUtilities.removeBoatUtilsEffects(e.getPlayer());
        }
    }

}
