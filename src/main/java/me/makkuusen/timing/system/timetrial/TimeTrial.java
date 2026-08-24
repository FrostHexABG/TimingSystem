package me.makkuusen.timing.system.timetrial;

import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.LeaderboardManager;
import me.makkuusen.timing.system.api.events.TimeTrialEarlyAttemptEvent;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.api.events.TimeTrialAttemptEvent;
import me.makkuusen.timing.system.api.events.TimeTrialFinishEvent;
import me.makkuusen.timing.system.api.events.TimeTrialStartEvent;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.medals.Medals;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeTrial {

    private final TPlayer tPlayer;
    @Getter
    private final Track track;
    private Instant startTime;
    private long startDelta;
    private ArrayList<Instant> checkpoints;
    @Getter
    private boolean lagStart = false;
    private Instant lagStartTime = null;
    @Getter
    private boolean lagEnd = false;
    @Getter
    private TimeTrialFinish bestFinish;


    public TimeTrial(Track track, TPlayer player, Instant startTime) {
        this.track = track;
        this.startTime = startTime;
        this.checkpoints = new ArrayList<>();
        this.bestFinish = track.getTimeTrials().getBestFinish(player);
        this.tPlayer = player;
    }


    public long getBestTime() {
        if (bestFinish == null) {
            return -1;
        }
        return bestFinish.getTime();
    }


    @SuppressWarnings("unused")
    //Used in third party plugin
    public void setBestFinish(TimeTrialFinish finish) {
        bestFinish = finish;
    }

    private boolean hasNotPassedAllCheckpoints() {
        return checkpoints.size() != track.getNumberOfCheckpoints();
    }

    public int getNextCheckpoint() {
        if (track.getNumberOfCheckpoints() >= checkpoints.size()) {
            return checkpoints.size() + 1;
        }
        return checkpoints.size();
    }

    public void passNextCheckpoint(Instant timeStamp) {
        checkpoints.add(timeStamp);
    }

    public int getLatestCheckpoint() {
        return checkpoints.size();
    }

    public long getCheckpointTime(int checkpoint) {
        if (checkpoints.isEmpty() || checkpoint == 0) {
            return 0;
        }
        return getTimeSinceStart(checkpoints.get(checkpoint - 1));
    }

    public List<Long> getCheckpointTimes() {
        List<Long> checkpointTimes = new ArrayList<>();
        checkpoints.forEach(checkpoint -> checkpointTimes.add(getTimeSinceStart(checkpoint)));
        return checkpointTimes;
    }

    public long getCurrentTime() {
        return getTimeSinceStart(TimingSystem.currentTime);
    }

    public long getTimeSinceStart(Instant time) {
        return Duration.between(startTime, time).toMillis();
    }

    public void setLagStartTrue() {
        this.lagStart = true;
        lagStartTime = TimingSystem.currentTime;
    }

    public Instant getLagStart() {
        return lagStartTime;
    }

    public void setLagEnd(boolean lagEnd) {
        this.lagEnd = lagEnd;
    }

    public boolean isLagEnd() {
        return lagEnd;
    }

    public boolean isLagStart() {
        return lagStart;
    }

    public void playerPassingLagStart() {
        Player player = tPlayer.getPlayer();
        if (tPlayer.getSettings().isVerbose() && (player.isOp() || player.hasPermission("timingsystem.packs.trackadmin"))) {
            Text.send(player, Info.TIME_TRIAL_LAG_START, "%time%", ApiUtilities.formatAsTime(ApiUtilities.getRoundedToTick(getTimeSinceStart(TimingSystem.currentTime))));
        }
    }

    public void playerPassingLagEnd() {
        Player player = tPlayer.getPlayer();
        if (tPlayer.getSettings().isVerbose() && (player.isOp() || player.hasPermission("timingsystem.packs.trackadmin"))) {
            Text.send(player, Info.TIME_TRIAL_LAG_END, "%time%", ApiUtilities.formatAsTime(ApiUtilities.getRoundedToTick(getTimeSinceStart(TimingSystem.currentTime))));
        }
    }

    public void playerPassingNextCheckpoint() {
        passNextCheckpoint(TimingSystem.currentTime);
        long timeSinceStart = ApiUtilities.getRoundedToTick(getTimeSinceStart(TimingSystem.currentTime));
        if (tPlayer.getSettings().isVerbose()) {
            Component delta = getBestLapDelta(tPlayer.getTheme(), getLatestCheckpoint());
            tPlayer.getPlayer().sendMessage(Text.get(tPlayer.getPlayer(), Info.TIME_TRIAL_CHECKPOINT, "%checkpoint%", String.valueOf(getLatestCheckpoint()), "%time%", ApiUtilities.formatAsTime(timeSinceStart)).append(delta));
        }
        ApiUtilities.msgConsole(tPlayer.getName() + " passed checkpoint " + getLatestCheckpoint() + " on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeSinceStart));
    }

    public void playerPassingNextCheckpoint(Location from, Location to, TrackRegion checkpoint) {
        Instant preciseTime = ApiUtilities.getPreciseRegionEntryTime(from, to, checkpoint);

        passNextCheckpoint(preciseTime);
        long timeSinceStart = getTimeSinceStart(preciseTime);
        if (tPlayer.getSettings().isVerbose()) {
            Component delta = getBestLapDelta(tPlayer.getTheme(), getLatestCheckpoint());
            tPlayer.getPlayer().sendMessage(Text.get(tPlayer.getPlayer(), Info.TIME_TRIAL_CHECKPOINT, "%checkpoint%", String.valueOf(getLatestCheckpoint()), "%time%", ApiUtilities.formatAsTime(timeSinceStart)).append(delta));
        }
        ApiUtilities.msgConsole(tPlayer.getName() + " passed checkpoint " + getLatestCheckpoint() + " on " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeSinceStart));
    }

    public void playerResetMap() {
        var timeTrial = TimeTrialController.timeTrials.get(tPlayer.getUniqueId());
        if (timeTrial == null) {
            return;
        }
        var time = ApiUtilities.getRoundedToTick(getTimeSinceStart(TimingSystem.currentTime));
        if (time > 1000) {
            var attempt = getTrack().getTimeTrials().newAttempt(time, tPlayer.getUniqueId());
            var eventTimeTrialAttempt = new TimeTrialAttemptEvent(tPlayer.getPlayer(), attempt);
            Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialAttempt);
        } else {
            var eventTimeTrialEarlyAttempt = new TimeTrialEarlyAttemptEvent(tPlayer.getPlayer(), timeTrial.getTrack());
            Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialEarlyAttempt);
        }
        TimeTrialController.timeTrials.remove(tPlayer.getUniqueId());
        ApiUtilities.teleportPlayerAndSpawnBoat(tPlayer.getPlayer(), track, track.getSpawnLocation(), false);
        ApiUtilities.msgConsole(tPlayer.getName() + " has been reset on " + track.getDisplayName());

    }

    public void playerStartingTimeTrial() {
        Player player = tPlayer.getPlayer();

        if (!tPlayer.getSettings().isTimeTrial()) {
            return;
        }

        if (!track.isOpen() && !tPlayer.getSettings().isOverride()) {
            return;
        }

        if (!player.isInsideVehicle() && track.isBoatTrack()) {
            return;
        }

        if (track.isBoatTrack() && !(player.getVehicle() instanceof Boat)) {
            return;
        }

        var eventTimeTrialStart = new TimeTrialStartEvent(tPlayer.getPlayer(), this);
        Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialStart);

        TimeTrialController.timeTrials.put(tPlayer.getUniqueId(), this);
        ApiUtilities.msgConsole(tPlayer.getName() + " started on " + track.getDisplayName());
    }

    public void playerEndedMap() {
        Instant endTime = TimingSystem.currentTime;
        Player player = tPlayer.getPlayer();

        if (validateFinish(player)) {
            long timeTrialTime = ApiUtilities.getRoundedToTick(getTimeSinceStart(endTime));
            saveAndAnnounceFinish(player, timeTrialTime);
            ApiUtilities.msgConsole(player.getName() + " finished " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrialTime));
        }
        TimeTrialController.timeTrials.remove(player.getUniqueId());
    }

    public void playerEndedMap(Location from, Location to, TrackRegion endRegion) {
        Instant preciseEndTime = ApiUtilities.getPreciseRegionEntryTime(from, to, endRegion);

        Player player = tPlayer.getPlayer();

        if (validateFinish(player)) {
            long timeTrialTime = getTimeSinceStart(preciseEndTime); // Use precise time without rounding
            saveAndAnnounceFinish(player, timeTrialTime);
            ApiUtilities.msgConsole(player.getName() + " finished " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrialTime));
        }
        TimeTrialController.timeTrials.remove(player.getUniqueId());
    }

    public void playerRestartMap() {
        Instant endTime = TimingSystem.currentTime;
        Player player = tPlayer.getPlayer();

        if (validateFinish(player)){
            long timeTrialTime = ApiUtilities.getRoundedToTick(getTimeSinceStart(endTime));
            saveAndAnnounceFinish(player, timeTrialTime);
            ApiUtilities.msgConsole(player.getName() + " finished " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrialTime));
        }

        if (!track.isOpen() && !tPlayer.getSettings().isOverride()) {
            TimeTrialController.timeTrials.remove(player.getUniqueId());
        } else {
            resetTimeTrial();
        }
    }

    public void playerRestartMap(Location from, Location to, TrackRegion startRegion) {
        Instant preciseEndTime = ApiUtilities.getPreciseRegionEntryTime(from, to, startRegion);

        Player player = tPlayer.getPlayer();

        if (validateFinish(player)){
            long timeTrialTime = getTimeSinceStart(preciseEndTime); // Use precise time without rounding
            saveAndAnnounceFinish(player, timeTrialTime);
            ApiUtilities.msgConsole(player.getName() + " finished " + track.getDisplayName() + " with a time of " + ApiUtilities.formatAsTime(timeTrialTime));
        }

        if (!track.isOpen() && !tPlayer.getSettings().isOverride()) {
            TimeTrialController.timeTrials.remove(player.getUniqueId());
        } else {
            resetTimeTrial();
        }
    }

    private boolean validateFinish(Player player) {
        if (hasNotPassedAllCheckpoints()) {
            Text.send(player, Error.MISSED_CHECKPOINTS);
            return false;
        }

        if (track.getTrackRegions().hasRegion(TrackRegion.RegionType.LAGSTART) && !lagStart) {
            Text.send(player, Error.LAG_DETECTED);
            return false;
        }

        if (track.getTrackRegions().hasRegion(TrackRegion.RegionType.LAGEND) && !lagEnd) {
            Text.send(player, Error.LAG_DETECTED);
            return false;
        }

        if (!player.isInsideVehicle() && track.isBoatTrack()) {
            return false;
        }

        return true;
    }

    private void resetTimeTrial() {
        ApiUtilities.msgConsole(tPlayer.getName() + " started on " + track.getDisplayName());
        this.startTime = TimingSystem.currentTime;
        this.checkpoints = new ArrayList<>();
        this.lagStart = false;
        this.lagEnd = false;
        this.lagStartTime = null;
    }

    private void saveAndAnnounceFinish(Player player, long timeTrialTime) {

        Component finishMessage;
        Component medalMessage = null;
        TimeTrialFinish finish;
        if (bestFinish == null) {
            //First finish
            finish = newBestFinish(player, timeTrialTime, -1);
            finishMessage = Text.get(player, Info.TIME_TRIAL_FIRST_FINISH,"%track%", track.getDisplayName(), "%time%", ApiUtilities.formatAsTime(timeTrialTime), "%pos%", String.valueOf(track.getTimeTrials().getPlayerTopListPosition(tPlayer)));
            finishMessage = tPlayer.getTheme().getCheckpointHovers(finish, finishMessage);
            if (TimingSystem.configuration.isMedalsAddOnEnabled()) {
                Medals prevMedal = track.getTrackMedals().getMedal(0);
                medalMessage = track.getTrackMedals().getMedalMessage(track.getTimeTrials(), player.hasResourcePack(), prevMedal, timeTrialTime, track.getDisplayName());
            }
        } else if (timeTrialTime < bestFinish.getTime()) {

            // Temporary fix to make TimingSystemTrackMerge integrate a little better.
            if (bestFinish.getTrack() != track.getId()) {
                var recordTrack = TimingSystemAPI.getTrackById(bestFinish.getTrack()).get();
                var oldPos = recordTrack.getTimeTrials().getCachedPlayerPosition(tPlayer);
                var oldFinish = bestFinish;
                finish = newBestFinish(player, timeTrialTime, oldFinish.getTime());
                finishMessage = Text.get(player, Info.TIME_TRIAL_NEW_RECORD, "%track%", track.getDisplayName(), "%time%", ApiUtilities.formatAsTime(timeTrialTime), "%delta%", ApiUtilities.formatAsPersonalGap(oldFinish.getTime() - timeTrialTime), "%oldPos%", oldPos.toString(), "%pos%", recordTrack.getTimeTrials().getPlayerTopListPosition(tPlayer).toString());
                finishMessage = tPlayer.getTheme().getCheckpointHovers(finish, oldFinish, finishMessage);
            } else {
                //New personal best
                var oldPos = track.getTimeTrials().getCachedPlayerPosition(tPlayer);
                var oldFinish = bestFinish;
                Medals prevMedal = Medals.NO_MEDAL;
                if (TimingSystem.configuration.isMedalsAddOnEnabled()) { prevMedal = track.getTrackMedals().getMedal(oldFinish.getTime()); }
                finish = newBestFinish(player, timeTrialTime, oldFinish.getTime());
                finishMessage = Text.get(player, Info.TIME_TRIAL_NEW_RECORD, "%track%", track.getDisplayName(), "%time%", ApiUtilities.formatAsTime(timeTrialTime), "%delta%", ApiUtilities.formatAsPersonalGap(oldFinish.getTime() - timeTrialTime), "%oldPos%", oldPos.toString(), "%pos%", track.getTimeTrials().getPlayerTopListPosition(tPlayer).toString());
                finishMessage = tPlayer.getTheme().getCheckpointHovers(finish, oldFinish, finishMessage);
                if (TimingSystem.configuration.isMedalsAddOnEnabled()) {
                    medalMessage = track.getTrackMedals().getMedalMessage(track.getTimeTrials(), player.hasResourcePack(), prevMedal, timeTrialTime, track.getDisplayName());
                }
            }
        } else {
            //Finish no improvement
            finish = callTimeTrialFinishEvent(player, timeTrialTime, bestFinish.getTime(), false);
            finishMessage = Text.get(player, Info.TIME_TRIAL_FINISH, "%track%", track.getDisplayName(), "%time%", ApiUtilities.formatAsTime(timeTrialTime), "%delta%", ApiUtilities.formatAsPersonalGap(timeTrialTime - bestFinish.getTime()));
            finishMessage = tPlayer.getTheme().getCheckpointHovers(finish, track.getTimeTrials().getBestFinish(tPlayer), finishMessage);
        }

        player.sendMessage(finishMessage);
        if (TimingSystem.configuration.isMedalsAddOnEnabled() && medalMessage != null) {
            player.sendMessage(medalMessage);
            if (TimingSystem.configuration.isMedalsShowEveryone()) {
                Medals medal = track.getTrackMedals().getMedal(timeTrialTime);
                if (track.getTrackMedals().isBroadcastWorthy(medal)) {
                    Component text = Component.text("§7" + player.getName() + " unlocked " + medal.getColor() + "§l" + medal.getName() + "§r§7 on " + track.getDisplayName() + "!").clickEvent(ClickEvent.runCommand("/tt " + track.getCommandName())).hoverEvent(HoverEvent.showText(Component.text("§fClick to teleport")));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p != player) {
                            p.sendMessage(text);
                        }
                    }
                }
            }
        }
    }

    private TimeTrialFinish newBestFinish(Player p, long mapTime, long oldTime) {
        var finish = callTimeTrialFinishEvent(p, mapTime, oldTime, true);
        this.bestFinish = track.getTimeTrials().getBestFinish(tPlayer);
        if (tPlayer.getSettings().isSound()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1, 1);
        }
        LeaderboardManager.updateFastestTimeLeaderboard(track);

        return finish;
    }

    private TimeTrialFinish callTimeTrialFinishEvent(Player player, long time, long oldBestTime, boolean newBestFinish) {
        var finish = track.getTimeTrials().newFinish(time, player.getUniqueId());
        Map<Integer, Long> checkpointTimes = new HashMap<>();
        for(int i = 1; i <= checkpoints.size(); i++) {
            checkpointTimes.put(i, getCheckpointTime(i));
        }
        if (newBestFinish) {
            //only store checkpoints on personal best
            finish.insertCheckpoints(checkpointTimes);
        } else {
            finish.updateCheckpointTimes(checkpointTimes);
        }

        TimeTrialFinishEvent eventTimeTrialFinish = new TimeTrialFinishEvent(player, this, finish, oldBestTime, newBestFinish);
        Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialFinish);
        return finish;
    }

    public Component getBestLapDelta(Theme theme, int latestCheckpoint) {
        if (latestCheckpoint > 0) {
            if (getBestFinish() != null && getBestFinish().hasCheckpointTimes() && getBestFinish().getCheckpointTime(latestCheckpoint) != null) {
                if (getBestFinish().getDate() > getTrack().getDateChanged()) {
                    var bestCheckpoint = getBestFinish().getCheckpointTime(latestCheckpoint);
                    var currentCheckpoint = getCheckpointTime(latestCheckpoint);
                    if (bestCheckpoint < currentCheckpoint) {
                        return Component.text(" +" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - bestCheckpoint)).color(theme.getError());
                    } else if (bestCheckpoint == currentCheckpoint) {
                        return Component.text(" =" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - bestCheckpoint)).color(theme.getWarning());
                    } else {
                        return Component.text(" -" + ApiUtilities.formatAsPersonalGap(bestCheckpoint - currentCheckpoint)).color(theme.getSuccess());
                    }
                }
            }
        }
        return Component.empty();
    }

}
