package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@CommandAlias("timetrialrandom|ttrandom|ttr")
@CommandPermission("%permissiontimetrial_random")
public class CommandTimeTrialRandom extends BaseCommand {

    @Default
    @CommandCompletion("@trackTag")
    public static void onRandom(Player player, @Optional TrackTag trackTag) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
        }

        if (TrackDatabase.getOpenTracks().isEmpty()) {
            Text.send(player, Error.TRACKS_NOT_FOUND);
            return;
        }

        List<Track> tracks;

        if (trackTag != null) {
            tracks = TrackDatabase.getOpenTracks().stream().filter(track -> track.getTrackTags().hasTag(trackTag)).collect(Collectors.toList());
            if (tracks.isEmpty()) {
                Text.send(player, Error.TRACKS_NOT_FOUND);
                return;
            }
        } else {
            tracks = TrackDatabase.getOpenTracks();
        }
        teleportPlayerToRandomTrack(tracks, player);
    }

    @Subcommand("unfinished")
    @CommandCompletion("@trackTag")
    public static void onRandomUnfinished(Player player, @Optional TrackTag trackTag) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
        }

        if (TrackDatabase.getOpenTracks().isEmpty()) {
            Text.send(player, Error.TRACKS_NOT_FOUND);
            return;
        }

        List<Track> tracks;
        if (trackTag != null) {
            tracks = TrackDatabase.getOpenTracks().stream().filter(track -> track.getTrackTags().hasTag(trackTag)).collect(Collectors.toList());
            if (tracks.isEmpty()) {
                Text.send(player, Error.TRACKS_NOT_FOUND);
                return;
            }
        } else {
            tracks = TrackDatabase.getOpenTracks();
        }

        tracks = tracks.stream().filter(track -> track.getTimeTrials().getPlayerTotalFinishes(TSDatabase.getPlayer(player.getUniqueId())) < 1).collect(Collectors.toList());
        if (tracks.isEmpty()) {
            Text.send(player, Error.TRACKS_NOT_FOUND);
            return;
        }

        teleportPlayerToRandomTrack(tracks, player);
    }

    private static void teleportPlayerToRandomTrack(List<Track> tracks, Player player) {
        Track track = tracks.get(new Random().nextInt(tracks.size()));

        if (!track.getSpawnLocation().isWorldLoaded()) {
            Text.send(player, Error.WORLD_NOT_LOADED);
            return;
        }
        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());

        if (track.getTimeTrials().getCachedPlayerPosition(tPlayer) != -1) {
            Component message = Text.get(player, Success.TELEPORT_TO_TRACK, "%track%", track.getDisplayName());
            var leaderboardPosition = track.getTimeTrials().getCachedPlayerPosition(tPlayer);
            Component positionComponent = tPlayer.getTheme().getParenthesized(String.valueOf(leaderboardPosition));
            if (message != null) {
                player.sendMessage(message.append(Component.space()).append(positionComponent));
            }
        } else {
            Text.send(player, Success.TELEPORT_TO_TRACK, "%track%", track.getDisplayName());
        }

        ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation(), true);
    }

}
