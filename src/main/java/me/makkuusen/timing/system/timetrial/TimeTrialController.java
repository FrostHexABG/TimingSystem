package me.makkuusen.timing.system.timetrial;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.events.TimeTrialAttemptEvent;
import me.makkuusen.timing.system.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class TimeTrialController {

    public static HashMap<UUID, TimeTrial> timeTrials = new HashMap<>();
    public static HashMap<UUID, Long> elytraProtection = new HashMap<>();
    public static HashMap<UUID, TimeTrialSession> timeTrialSessions = new HashMap<>();
    public static HashMap<UUID, Track> lastTimeTrialTrack = new HashMap<>();

    public static void playerLeavingMap(UUID uuid) {
        if (!TimeTrialController.timeTrials.containsKey(uuid)) {
            return;
        }
        var timeTrial = TimeTrialController.timeTrials.get(uuid);
        var time = ApiUtilities.getRoundedToTick(timeTrial.getTimeSinceStart(TimingSystem.currentTime));
        if (time > 1000) {
            var attempt = timeTrial.getTrack().getTimeTrials().newAttempt(time, uuid);
            var eventTimeTrialAttempt = new TimeTrialAttemptEvent(Bukkit.getPlayer(uuid), attempt);
            Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialAttempt);
        }
        TimeTrialController.timeTrials.remove(uuid);
        me.makkuusen.timing.system.boatutils.BoatUtilsManager.clearPlayerModes(uuid);
    }

    public static void playerCancelMap(Player player) {
        if (!TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
            return;
        }
        var timeTrial = TimeTrialController.timeTrials.get(player.getUniqueId());
        var time = ApiUtilities.getRoundedToTick(timeTrial.getTimeSinceStart(TimingSystem.currentTime));
        if (time > 1000) {
            var attempt = timeTrial.getTrack().getTimeTrials().newAttempt(time, player.getUniqueId());
            var eventTimeTrialAttempt = new TimeTrialAttemptEvent(player, attempt);
            Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialAttempt);
        }
        ApiUtilities.msgConsole(player.getName() + " has cancelled run on " + TimeTrialController.timeTrials.get(player.getUniqueId()).getTrack().getDisplayName());
        TimeTrialController.timeTrials.remove(player.getUniqueId());
        me.makkuusen.timing.system.boatutils.BoatUtilsManager.clearPlayerModes(player.getUniqueId());
    }
}
