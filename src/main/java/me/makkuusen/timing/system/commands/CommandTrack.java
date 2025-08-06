package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChain;
import me.makkuusen.timing.system.*;

import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.gui.TrackGui;
import me.makkuusen.timing.system.permissions.PermissionTrack;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.theme.messages.Warning;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.timetrial.TimeTrialDateComparator;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;
import me.makkuusen.timing.system.timetrial.TimeTrialSession;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.*;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

@CommandAlias("track|t")
public class CommandTrack extends BaseCommand {

    @Default
    @CommandPermission("%permissiontrack_menu")
    public static void onTrack(Player player) {
        new TrackGui(TSDatabase.getPlayer(player.getUniqueId())).show(player);
    }

    @Subcommand("tp")
    @CommandCompletion("@track @region")
    @CommandPermission("%permissiontrack_tp")
    public static void onTrackTp(Player player, Track track, @Optional String region) {
        if (!track.getSpawnLocation().isWorldLoaded()) {
            Text.send(player, Error.WORLD_NOT_LOADED);
            return;
        }

        if (region != null) {
            var rg = region.split("-");
            if (rg.length != 2) {
                Text.send(player, Error.SYNTAX);
                return;
            }
            String name = rg[0];
            String index = rg[1];

            var trackRegion = getRegion(track, name.toUpperCase(), index);

            if (trackRegion != null) {
                player.teleport(trackRegion.getSpawnLocation());
                Text.send(player, Success.TELEPORT_TO_TRACK, "%track%", trackRegion.getRegionType().name() + " : " + trackRegion.getRegionIndex());
                return;
            }

            var trackLocation = getTrackLocation(track, name, index);

            if (trackLocation != null) {
                player.teleport(trackLocation.getLocation());
                Text.send(player, Success.TELEPORT_TO_TRACK, "%track%", trackLocation.getLocationType().name() + " : " + trackLocation.getIndex());
                return;
            }
            Text.send(player, Error.FAILED_TELEPORT);
        } else {
            player.teleport(track.getSpawnLocation());
            Text.send(player, Success.TELEPORT_TO_TRACK, "%track%", track.getDisplayName());
        }
    }

    private static TrackRegion getRegion(Track track, String name, String index) {
        try {
            var regionType = TrackRegion.RegionType.valueOf(name);
            var regionIndex = Integer.parseInt(index);

            var trackRegion = track.getTrackRegions().getRegion(regionType, regionIndex);
            return trackRegion.orElse(null);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TrackLocation getTrackLocation(Track track, String name, String index) {
        try {
            var locationType = TrackLocation.Type.valueOf(name);
            var regionIndex = Integer.parseInt(index);

            var trackLocation = track.getTrackLocations().getLocation(locationType, regionIndex);
            return trackLocation.orElse(null);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Subcommand("info")
    @CommandCompletion("@track @players")
    @CommandPermission("%permissiontrack_info")
    public static void onInfo(CommandSender commandSender, Track track, @Optional String name) {
        TPlayer tPlayer;
        tPlayer = TSDatabase.getPlayer(name);
        if (name != null && commandSender.hasPermission(PermissionTrack.VIEW_PLAYERSTATS.getNode())) {
            if (tPlayer == null) {
                Text.send(commandSender, Error.PLAYER_NOT_FOUND);
                return;
            }
            sendPlayerStatsInfo(commandSender, tPlayer, track);
            return;
        }

        sendTrackInfo(commandSender, track);

    }

    private static void sendPlayerStatsInfo(CommandSender commandSender, TPlayer tPlayer, Track track) {
        var timeTrials = track.getTimeTrials();
        commandSender.sendMessage(Component.empty());
        Text.send(commandSender, Info.PLAYER_STATS_TITLE, "%player%", tPlayer.getName(), "%track%", track.getDisplayName());
        Text.send(commandSender, Info.PLAYER_STATS_POSITION, "%pos%", (timeTrials.getCachedPlayerPosition(tPlayer) == -1 ? "(-)" : String.valueOf(timeTrials.getCachedPlayerPosition(tPlayer))));
        if (timeTrials.getBestFinish(tPlayer) == null) {
            Text.send(commandSender, Info.PLAYER_STATS_BEST_LAP, "%time%", "(-)");
        } else {
            Text.send(commandSender, Info.PLAYER_STATS_BEST_LAP, "%time%", ApiUtilities.formatAsTime(timeTrials.getBestFinish(tPlayer).getTime()));
        }
        Text.send(commandSender, Info.PLAYER_STATS_FINISHES, "%size%", String.valueOf(timeTrials.getPlayerTotalFinishes(tPlayer)));
        Text.send(commandSender, Info.PLAYER_STATS_ATTEMPTS, "%size%", String.valueOf(timeTrials.getPlayerTotalAttempts(tPlayer)));
        Text.send(commandSender, Info.PLAYER_STATS_TIME_SPENT, "%size%", ApiUtilities.formatAsTimeSpent(timeTrials.getPlayerTotalTimeSpent(tPlayer)));
    }

    public static void sendTrackInfo(CommandSender commandSender, Track track) {
        commandSender.sendMessage(Component.empty());
        Text.send(commandSender, Info.TRACK_TITLE, "%name%", track.getDisplayName(), "%id%", String.valueOf(track.getId()));
        if (track.isOpen()) {
            Text.send(commandSender, Info.TRACK_OPEN);
        } else {
            Text.send(commandSender, Info.TRACK_CLOSED);
        }
        Text.send(commandSender, Info.TRACK_TIMETRIAL, "%timetrial%", track.isTimeTrial() ? "Enabled" : "Disabled");
        Text.send(commandSender, Info.TRACK_TYPE, "%type%", track.getTypeAsString());
        Text.send(commandSender, Info.TRACK_DATE_CREATED, "%date%", ApiUtilities.niceDate(track.getDateCreated()), "%owner%", track.getOwner().getName());
        Text.send(commandSender, Info.TRACK_OPTIONS, "%options%", track.getTrackOptions().listOfOptions());
        Text.send(commandSender, Info.TRACK_BOATUTILS_MODE, "%mode%", track.getBoatUtilsMode().name());
        Text.send(commandSender, Info.TRACK_CHECKPOINTS, "%size%", String.valueOf(track.getTrackRegions().getRegions(TrackRegion.RegionType.CHECKPOINT).size()));
        if (!track.getTrackLocations().getLocations(TrackLocation.Type.GRID).isEmpty()) {
            Text.send(commandSender, Info.TRACK_GRIDS, "%size%", String.valueOf(track.getTrackLocations().getLocations(TrackLocation.Type.GRID).size()));
        }
        if (!track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).isEmpty()) {
            Text.send(commandSender, Info.TRACK_QUALIFICATION_GRIDS, "%size%", String.valueOf(track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).size()));
        }
        Text.send(commandSender, Info.TRACK_RESET_REGIONS, "%size%", String.valueOf(track.getTrackRegions().getRegions(TrackRegion.RegionType.RESET).size()));
        Text.send(commandSender, Info.TRACK_SPAWN_LOCATION, "%location%", ApiUtilities.niceLocation(track.getSpawnLocation()));

        Text.send(commandSender, Info.TRACK_WEIGHT, "%size%", String.valueOf(track.getWeight()));
        Component tags = Component.empty();
        boolean notFirst = false;

        List<TrackTag> trackTags;
        if (commandSender.hasPermission("timingsystem.packs.trackadmin")) {
            trackTags = track.getTrackTags().get();
        } else {
            trackTags = track.getTrackTags().getDisplayTags();
        }
        for (TrackTag tag : trackTags) {
            if (notFirst) {
                tags = tags.append(Component.text(", ").color(Theme.getTheme(commandSender).getSecondary()));
            }
            tags = tags.append(Component.text(tag.getValue()).color(tag.getColor()));
            notFirst = true;
        }
        commandSender.sendMessage(Text.get(commandSender, Info.TRACK_TAGS).append(tags));

        Component contributors = Component.empty();
        if (!track.getContributors().isEmpty()) {
            contributors = contributors.append(Component.text(track.getContributors().get(0).getName()));

            for (TPlayer tp : track.getContributors().subList(1, track.getContributors().size())) {
                contributors = contributors.append(Component.text(", ", Theme.getTheme(commandSender).getSecondary())).append(Component.text(tp.getName(), Theme.getTheme(commandSender).getSecondary()));
            }
        }

        commandSender.sendMessage(Text.get(commandSender, Info.TRACK_CONTRIBUTORS).append(contributors));
    }

    @Subcommand("regions")
    @CommandCompletion("@track")
    @CommandPermission("%permissiontrack_view_regions")
    public static void onRegions(CommandSender sender, Track track) {
        Text.send(sender, Info.REGIONS_TITLE, "%track%", track.getDisplayName());

        Theme theme = Theme.getTheme(sender);

        for (var regionType : TrackRegion.RegionType.values()) {
            for (TrackRegion trackRegion : track.getTrackRegions().getRegions(regionType)) {

                String regionText = trackRegion.getRegionType().name() + "-" + trackRegion.getRegionIndex();
                sender.sendMessage(theme.arrow().append(Component.text(regionText).clickEvent(ClickEvent.runCommand("/t tp " + track.getCommandName() + " " + regionText))));
            }
        }
    }

    @Subcommand("locations")
    @CommandCompletion("@track")
    @CommandPermission("%permissiontrack_view_locations")
    public static void onLocations(CommandSender sender, Track track) {
        Text.send(sender, Info.LOCATIONS_TITLE, "%track%", track.getDisplayName());

        Theme theme = Theme.getTheme(sender);

        for (var locationType : TrackLocation.Type.values()) {
            for (TrackLocation trackLocation : track.getTrackLocations().getLocations(locationType)) {
                String locationText = trackLocation.getLocationType().name() + "-" + trackLocation.getIndex();
                sender.sendMessage(theme.arrow().append(Component.text(locationText).clickEvent(ClickEvent.runCommand("/t tp " + track.getCommandName() + " " + locationText))));
            }
        }
    }

    @Subcommand("here")
    @CommandPermission("%permissiontrack_view_here")
    public static void onHere(Player player) {
        if(!player.hasPermission(PermissionTrack.VIEW_HERE.getNode())) {
            Text.send(player, Error.PERMISSION_DENIED);
            return;
        }
        boolean inRegion = false;
        for (Track track : TrackDatabase.tracks) {
            for (TrackRegion region : track.getTrackRegions().getRegions()) {
                if (region.contains(player.getLocation())) {
                    inRegion = true;
                    player.sendMessage(Component.text(track.getDisplayName() + " - " + region.getRegionType() + " : " + region.getRegionIndex()).color(Theme.getTheme(player).getSecondary()));
                }
            }
        }

        if (!inRegion) {
            Text.send(player, Error.REGIONS_NOT_FOUND);
        }
    }

    @Subcommand("session")
    @CommandCompletion("@track")
    @CommandPermission("%permissiontrack_session_timetrial")
    public static void toggleSession(Player player, @Optional Track track) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
        }

        if (TimeTrialController.timeTrialSessions.containsKey(player.getUniqueId())) {
            var ttSession = TimeTrialController.timeTrialSessions.get(player.getUniqueId());
            ttSession.clearScoreboard();
            TimeTrialController.timeTrialSessions.remove(player.getUniqueId());
            if (track == null) {
                Text.send(player, Success.SESSION_ENDED);
                return;
            }
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }

            if (track.getId() != ttSession.getTrack().getId()) {
                var newSession = new TimeTrialSession(TSDatabase.getPlayer(player.getUniqueId()), track);
                newSession.updateScoreboard();
                TimeTrialController.timeTrialSessions.put(player.getUniqueId(), newSession);
                Text.send(player, Success.SESSION_STARTED, "%track%", track.getDisplayName());
                ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
                return;
            }
            Text.send(player, Success.SESSION_ENDED);
            return;
        }

        if (track == null) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        if (!track.isOpen() && !player.hasPermission("timingsystem.packs.trackadmin")) {
            Text.send(player, Error.TRACK_IS_CLOSED);
            return;
        }

        TimeTrialSession ttSession = new TimeTrialSession(TSDatabase.getPlayer(player.getUniqueId()), track);
        ttSession.updateScoreboard();
        TimeTrialController.timeTrialSessions.put(player.getUniqueId(), ttSession);
        Text.send(player, Success.SESSION_STARTED, "%track%", track.getDisplayName());
        ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
    }


    @Subcommand("times")
    @CommandCompletion("@track <page>")
    @CommandPermission("%permissiontrack_view_times")
    public static void onTimes(CommandSender commandSender, Track track, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }
        Theme theme = Theme.getTheme(commandSender);
        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= track.getTimeTrials().getTopList().size()) {
            Text.send(commandSender, Error.PAGE_NOT_FOUND);
            return;
        }

        List<TimeTrialFinish> topList = track.getTimeTrials().getTopList();
        int topListSize = topList.size();

        Text.send(commandSender, Info.TRACK_TIMES_TITLE, "%track%", track.getDisplayName());
        for (int i = start; i < stop; i++) {
            if (i == topListSize) {
                break;
            }
            TimeTrialFinish finish = topList.get(i);
            commandSender.sendMessage(theme.getTimesRow(String.valueOf(i + 1), finish.getPlayer().getName(), ApiUtilities.formatAsTime(finish.getTime())));
        }

        int pageEnd = (int) Math.ceil(((double) topListSize) / ((double) itemsPerPage));
        commandSender.sendMessage(theme.getPageSelector(commandSender, pageStart, pageEnd, "/t times " + track.getCommandName()));
    }

    @Subcommand("mytimes")
    @CommandCompletion("@track <page>")
    @CommandPermission("%permissiontrack_view_mytimes")
    public static void onMyTimes(Player player, Track track, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }

        var tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        Theme theme = tPlayer.getTheme();
        List<TimeTrialFinish> allTimes = new ArrayList<>();
        if (track.getTimeTrials().getTimeTrialFinishes().containsKey(tPlayer)) {
            allTimes.addAll(track.getTimeTrials().getTimeTrialFinishes().get(tPlayer));
            allTimes.sort(new TimeTrialFinishComparator());
        }

        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= allTimes.size()) {
            Text.send(player, Error.PAGE_NOT_FOUND);
            return;
        }

        Text.send(player, Info.PLAYER_BEST_TIMES_TITLE, "%player%", player.getName(), "%track%", track.getDisplayName());

        for (int i = start; i < stop; i++) {
            if (i == allTimes.size()) {
                break;
            }
            TimeTrialFinish finish = allTimes.get(i);
            Component row = theme.getTimesRow(String.valueOf(i + 1), ApiUtilities.formatAsTime(finish.getTime()), ApiUtilities.niceDate(finish.getDate()));
            if (finish.hasCheckpointTimes()) {
                row = theme.getCheckpointHovers(finish, track.getTimeTrials().getBestFinish(tPlayer), row);
            }
            player.sendMessage(row);
        }

        int pageEnd = (int) Math.ceil(((double) allTimes.size()) / ((double) itemsPerPage));
        player.sendMessage(theme.getPageSelector(player, pageStart, pageEnd, "/t mytimes " + track.getCommandName()));
    }

    @Subcommand("alltimes")
    @CommandCompletion("@players <page>")
    @CommandPermission("%permissiontrack_view_alltimes")
    public static void onAllTimes(Player player, @Optional String name, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }
        TPlayer tPlayer;
        if (name != null) {

            if (!player.hasPermission("timingsystem.packs.trackadmin") && !player.hasPermission(PermissionTrack.VIEW_ALLTIMES.getNode())) {
                Text.send(player, Error.PERMISSION_DENIED);
                return;
            }
            tPlayer = TSDatabase.getPlayer(name);
            if (tPlayer == null) {
                Text.send(player, Error.PLAYER_NOT_FOUND);
                return;
            }
        } else {
            tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        }

        List<TimeTrialFinish> allTimes = new ArrayList<>();
        var tracks = TrackDatabase.getOpenTracks();
        for (Track t : tracks) {
            if (t.getTimeTrials().getTimeTrialFinishes().containsKey(tPlayer)) {
                allTimes.addAll(t.getTimeTrials().getTimeTrialFinishes().get(tPlayer));
            }
        }
        allTimes.sort(new TimeTrialDateComparator());

        Theme theme = TSDatabase.getPlayer(player).getTheme();
        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= allTimes.size()) {
            Text.send(player, Error.PAGE_NOT_FOUND);
            return;
        }

        Text.send(player, Info.PLAYER_RECENT_TIMES_TITLE, "%player%", tPlayer.getName());

        for (int i = start; i < stop; i++) {
            if (i == allTimes.size()) {
                break;
            }
            TimeTrialFinish finish = allTimes.get(i);
            Track track = TrackDatabase.getTrackById(finish.getTrack()).get();
            Component row = theme.getTimesRow(String.valueOf(i + 1), ApiUtilities.formatAsTime(finish.getTime()), track.getDisplayName(), ApiUtilities.niceDate(finish.getDate()));
            if (finish.hasCheckpointTimes()) {
                row = theme.getCheckpointHovers(finish, track.getTimeTrials().getBestFinish(tPlayer), row);
            }
            player.sendMessage(row);
        }
        int pageEnd = (int) Math.ceil(((double) allTimes.size()) / ((double) itemsPerPage));
        player.sendMessage(theme.getPageSelector(player, pageStart, pageEnd, "/t alltimes " + tPlayer.getName()));
    }

    @Subcommand("reload")
    @CommandPermission("%permissiontrack_reload")
    public static void onReload(CommandSender commandSender, @Optional String confirmText) {
        if(confirmText != null && confirmText.equals("confirm")) {
            TSDatabase.reload();
            Text.send(commandSender, Success.SAVED);
            return;
        }

        Text.send(commandSender, Warning.DANGEROUS_COMMAND, "%command%", "/track reload confirm");
    }

    @Subcommand("deletebesttime")
    @CommandCompletion("@track <playername>")
    @CommandPermission("%permissiontrack_delete_besttime")
    public static void onDeleteBestTime(CommandSender commandSender, Track track, String name) {
        TPlayer TPlayer = TSDatabase.getPlayer(name);
        if (TPlayer == null) {
            Text.send(commandSender, Error.PLAYER_NOT_FOUND);
            return;
        }

        TimeTrialFinish bestFinish = track.getTimeTrials().getBestFinish(TPlayer);
        if (bestFinish == null) {
            Text.send(commandSender, Error.NOTHING_TO_REMOVE);
            return;
        }
        track.getTimeTrials().deleteBestFinish(TPlayer, bestFinish);
        Text.send(commandSender, Success.REMOVED_BEST_FINISH, "%player%", TPlayer.getName(), "%track%", track.getDisplayName());
        LeaderboardManager.updateFastestTimeLeaderboard(track);
    }

    @Subcommand("deletealltimes")
    @CommandCompletion("@track <player>")
    @CommandPermission("%permissiontrack_delete_alltimes")
    public static void onDeleteAllTimes(CommandSender commandSender, Track track, @Optional String playerName) {
        if (playerName != null) {
            TPlayer tPlayer = TSDatabase.getPlayer(playerName);
            if (tPlayer == null) {
                Text.send(commandSender, Error.PLAYER_NOT_FOUND);
                return;
            }
            var message = Text.get(commandSender, Success.REMOVED_ALL_FINISHES).append(tPlayer.getTheme().success(" -> " + tPlayer.getNameDisplay()));
            commandSender.sendMessage(message);
            track.getTimeTrials().deleteAllFinishes(tPlayer);
            LeaderboardManager.updateFastestTimeLeaderboard(track);
            return;
        }
        track.getTimeTrials().deleteAllFinishes();
        Text.send(commandSender, Success.REMOVED_ALL_FINISHES);
        LeaderboardManager.updateFastestTimeLeaderboard(track);

    }

    @Subcommand("deleteallplayertimes")
    @CommandCompletion("<player>")
    @CommandPermission("%permissiontrack_delete_allplayertimes")
    public static void onDeleteAllPlayerTimes(CommandSender commandSender, String playerName) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(commandSender, Error.PLAYER_NOT_FOUND);
            return;
        }
        TaskChain<?> chain = TimingSystem.newChain();

        for (Track t : TimingSystemAPI.getTracks()) {
            if (t.getTimeTrials().hasPlayed(tPlayer)) {
                t.getTimeTrials().deleteAllFinishes(tPlayer);
                chain.sync(() -> LeaderboardManager.updateFastestTimeLeaderboard(t)).delay(LeaderboardManager.TICKS_BETWEEN_INDIVIDUAL_UPDATES);
            }
        }

        var message = Text.get(commandSender, Success.REMOVED_ALL_FINISHES);
        commandSender.sendMessage(message);
        chain.execute();
    }

    @Subcommand("updateleaderboards")
    @CommandPermission("%permissiontrack_updateleaderboards")
    public static void onUpdateLeaderboards(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(TimingSystem.getPlugin(), LeaderboardManager::updateAllFastestTimeLeaderboard);
        Text.send(player, Info.UPDATING_LEADERBOARDS);
    }
}
