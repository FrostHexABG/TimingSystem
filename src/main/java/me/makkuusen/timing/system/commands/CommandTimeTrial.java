package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.api.events.TimeTrialTeleportEvent;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.gui.TimeTrialGui;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

@CommandAlias("timetrial|tt")
public class CommandTimeTrial extends BaseCommand {

    @Default
    @CommandCompletion("@track")
    @CommandPermission("%permissiontimetrial_menu")
    public static void onTimeTrial(CommandSender sender, @Optional Track track) {
        Player player = null;
        if (sender instanceof BlockCommandSender blockCommandSender) {
            Location location = blockCommandSender.getBlock().getLocation();
            double closest = -1;

            for (Player tmp : Bukkit.getOnlinePlayers()) {
                if (tmp.getWorld().equals(location.getWorld())) {

                    double distance = tmp.getLocation().distance(location);

                    if (distance < closest || closest == -1) {
                        player = tmp;
                        closest = distance;
                    }
                }
            }

            if (player == null) {
                return;
            }
        } else if (sender instanceof Player p) {
            player = p;
        } else {
            Text.send(sender, Error.ONLY_PLAYERS);
            return;
        }

        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
        }

        if (track == null) {
            var tPlayer = TSDatabase.getPlayer(player.getUniqueId());
            new TimeTrialGui(tPlayer).show(player);
        } else {
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }

            if (!track.isOpen() && !(player.hasPermission("timingsystem.packs.trackadmin"))) {
                Text.send(player, Error.TRACK_IS_CLOSED);
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

            if (player.getGameMode() != GameMode.SPECTATOR) { // Issue #12
                ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
                var eventTimeTrialTeleport = new TimeTrialTeleportEvent(tPlayer.getPlayer(), track);
                Bukkit.getServer().getPluginManager().callEvent(eventTimeTrialTeleport);
            } else {
                player.teleportAsync(track.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }
}