package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.gui.SettingsGui;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.tplayer.TPlayer;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.makkuusen.timing.system.database.EventDatabase.playerInRunningHeat;

@CommandAlias("settings|s")
public class CommandSettings extends BaseCommand {

    @Default
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onSettings(Player player) {
        new SettingsGui(TSDatabase.getPlayer(player.getUniqueId())).show(player);
    }

    @Subcommand("shortname")
    @CommandCompletion("<shortname>")
    @CommandPermission("%permissiontimingsystem_settings_shortname")
    public static void onShortName(Player player, @Single String shortName) {
        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        int maxLength = 4;
        int minLength = 3;

        if (shortName.length() < minLength || shortName.length() > maxLength) {
            Text.send(player, Error.INVALID_NAME);
            return;
        }

        if (!shortName.matches("[A-Za-z0-9]+")) {
            Text.send(player, Error.INVALID_NAME);
            return;
        }

        tPlayer.getSettings().setShortName(shortName);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("verbose")
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onVerbose(Player player) {
        var tPlayer = TSDatabase.getPlayer(player);
        tPlayer.getSettings().toggleVerbose();
        Text.send(player, tPlayer.getSettings().isVerbose() ? Success.CHECKPOINTS_ANNOUNCEMENTS_ON : Success.CHECKPOINTS_ANNOUNCEMENTS_OFF);
    }

    @Subcommand("lonely")
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onLonely(Player player) {
        var tPlayer = TSDatabase.getPlayer(player);

        for (Driver driver : playerInRunningHeat.values()) {
            if (driver.getTPlayer().getUniqueId().equals(player.getUniqueId())) {
                Text.send(player, Error.PERMISSION_DENIED);
                return;
            }
        }

        tPlayer.getSettings().toggleLonely();
        Text.send(player, tPlayer.getSettings().isLonely() ? Success.LONELY_ON : Success.LONELY_OFF);
    }

    @Subcommand("sound")
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onTTSound(Player player) {
        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        tPlayer.getSettings().toggleSound();
        Text.send(player, tPlayer.getSettings().isSound() ? Success.SOUND_ON : Success.SOUND_OFF);
    }

    @Subcommand("compactScoreboard")
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onCompactScoreboard(Player player) {
        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        tPlayer.getSettings().toggleCompactScoreboard();
        Text.send(player, tPlayer.getSettings().isCompactScoreboard() ? Success.COMPACT_SCOREBOARD_ON : Success.COMPACT_SCOREBOARD_OFF);
    }

    @Subcommand("override")
    @CommandPermission("%permissiontimingsystem_settings_override")
    public static void onOverride(Player player) {
        var tPlayer = TSDatabase.getPlayer(player);
        tPlayer.getSettings().toggleOverride();
        Text.send(player, tPlayer.getSettings().isOverride() ? Success.OVERRIDE_ON : Success.OVERRIDE_OFF);
    }


    @Subcommand("color")
    @CommandCompletion("<hexcolorcode>")
    @CommandPermission("%permissiontimingsystem_settings")
    public static void onColor(Player player, String hex) {
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        if (isValidHexCode(hex)) {
            var tPlayer = TSDatabase.getPlayer(player);
            tPlayer.getSettings().setHexColor(hex);
            player.sendMessage(Text.get(player, Success.COLOR_UPDATED).color(tPlayer.getSettings().getTextColor()));
            return;
        }
        Text.send(player, Error.COLOR_FORMAT);
    }

    public static boolean isValidHexCode(String str) {
        // Regex to check valid hexadecimal color code.
        String regex = "^#([A-Fa-f0-9]{6})$";

        // Compile the ReGex
        Pattern p = Pattern.compile(regex);

        // If the string is empty
        // return false
        if (str == null) {
            return false;
        }

        // Pattern class contains matcher() method
        // to find matching between given string
        // and regular expression.
        Matcher m = p.matcher(str);

        // Return if the string
        // matched the ReGex
        return m.matches();
    }
}
