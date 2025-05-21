package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.theme.messages.Warning;
import me.makkuusen.timing.system.tplayer.TPlayer;
import org.bukkit.entity.Player;

import static me.makkuusen.timing.system.loneliness.LonelinessController.unghost;

@CommandAlias("unghost|ugh")
public class CommandUnghost extends BaseCommand {
    @Default
    @CommandCompletion("@players")
    @CommandPermission("%permissiontimingsystem_ghost")
    public static void onUnghost(Player player, String targetName) {
        Player target = player.getServer().getPlayer(targetName);
        TPlayer tPlayer = TSDatabase.getPlayer(target.getUniqueId());
        Boolean isGhosted = LonelinessController.isGhosted(target.getUniqueId());

        if (unghost(target.getUniqueId())) {
            Text.send(player, Success.GHOSTING_OFF);
            Text.send(target, Warning.GHOSTING_TARGET_OFF);
        } else {
            Text.send(player, Error.NOT_NOW);
        }
    }
}
