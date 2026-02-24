package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.TrackTagManager;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.permissions.PermissionTimingSystem;
import me.makkuusen.timing.system.theme.TSColor;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandAlias("timingsystem|ts")
public class CommandTimingSystem extends BaseCommand {
    @Subcommand("tag create")
    @CommandCompletion("<tag>")
    @CommandPermission("%permissiontimingsystem_tag_create")
    public static void onCreateTag(CommandSender commandSender, String value) {
        if (!value.matches("[A-Za-zÅÄÖåäöØÆøæ0-9]+")) {
            Text.send(commandSender, Error.INVALID_NAME);
            return;
        }

        if (TrackTagManager.createTrackTag(value)) {
            Text.send(commandSender, Success.CREATED_TAG, "%tag%", value);
            return;
        }

        Text.send(commandSender, Error.FAILED_TO_CREATE_TAG);
    }

    @Subcommand("tag color")
    @CommandCompletion("@trackTag <hexcolorcode>")
    @CommandPermission("%permissiontimingsystem_tag_set_color")
    public static void onSetTagColor(CommandSender commandSender, TrackTag tag, String color) {
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        if (TextColor.fromHexString(color) == null) {
            Text.send(commandSender, Error.COLOR_FORMAT);
            return;
        }

        tag.setColor(Objects.requireNonNull(TextColor.fromHexString(color)));
        Text.send(commandSender, Success.SAVED);

    }

    @Subcommand("tag item")
    @CommandCompletion("@trackTag")
    @CommandPermission("%permissiontimingsystem_tag_set_item")
    public static void onSetTagItem(Player player, TrackTag tag) {
        var item = player.getInventory().getItemInMainHand();
        if (item.getItemMeta() == null) {
            Text.send(player, Error.ITEM_NOT_FOUND);
            return;
        }
        tag.setItem(item);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("tag weight")
    @CommandCompletion("@trackTag <value>")
    @CommandPermission("%permissiontimingsystem_tag_set_weight")
    public static void onSetTagItem(Player player, TrackTag tag, int weight) {
        tag.setWeight(weight);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("tag delete")
    @CommandCompletion("@trackTag <value>")
    @CommandPermission("%permissiontimingsystem_tag_delete")
    public static void onDeleteTag(Player player, TrackTag tag) {
        TrackTagManager.deleteTag(tag);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("scoreboard maxrows")
    @CommandCompletion("<value>")
    @CommandPermission("%permissiontimingsystem_scoreboard_set_maxrows")
    public static void onMaxRowsScoreboardChange(CommandSender sender, int rows) {
        TimingSystem.configuration.setScoreboardMaxRows(rows);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("scoreboard interval")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_scoreboard_set_interval")
    public static void onIntervalScoreboardChange(CommandSender sender, String value) {
        TimingSystem.configuration.setScoreboardInterval(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("drs mindelta")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_drs_set_mindelta")
    public static void onDrsMinDeltaChange(CommandSender sender, int value) {
        TimingSystem.configuration.setDrsMinDelta(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("drs maxdelta")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_drs_set_maxdelta")
    public static void onDrsMaxDeltaChange(CommandSender sender, int value) {
        TimingSystem.configuration.setDrsMaxDelta(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("drs duration")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_drs_set_duration")
    public static void onDrsDurationChange(CommandSender sender, int value) {
        TimingSystem.configuration.setDrsDuration(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("drs forwardaccel")
    @CommandCompletion("<value>")
    @CommandPermission("%permissiontimingsystem_drs_set_forwardaccel")
    public static void onDrsForwardAccelChange(CommandSender sender, double value) {
        TimingSystem.configuration.setDrsForwardAccel(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("pushtopass|p2p maxusetime")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_pushtopass_set_maxusetime")
    public static void onPushToPassMaxUseTimeChange(CommandSender sender, int value) {
        TimingSystem.configuration.setPushToPassMaxUseTime(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("pushtopass|p2p fullchargetime")
    @CommandCompletion("<value in ms>")
    @CommandPermission("%permissiontimingsystem_pushtopass_set_fullchargetime")
    public static void onPushToPassFullChargeTimeChange(CommandSender sender, int value) {
        TimingSystem.configuration.setPushToPassFullChargeTime(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("pushtopass|p2p forwardaccel")
    @CommandCompletion("<value>")
    @CommandPermission("%permissiontimingsystem_pushtopass_set_forwardaccel")
    public static void onPushToPassForwardAccelChange(CommandSender sender, double value) {
        TimingSystem.configuration.setPushToPassForwardAccel(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("pushtopass|p2p startingcharge")
    @CommandCompletion("<0-100>")
    @CommandPermission("%permissiontimingsystem_pushtopass_set_startingcharge")
    public static void onPushToPassStartingChargeChange(CommandSender sender, int value) {
        if (value < 0 || value > 100) {
            Text.send(sender, Error.GENERIC);
            return;
        }
        TimingSystem.configuration.setPushToPassStartingCharge(value);
        Text.send(sender, Success.SAVED);
    }

    @Subcommand("shortname")
    @CommandCompletion("<shortname> @players")
    @CommandPermission("%permissiontimingsystem_shortname_others")
    public static void onShortNameOthers(CommandSender sender, @Single String shortName, String playerName) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }

        int maxLength = 4;
        int minLength = 3;

        if (shortName.length() < minLength || shortName.length() > maxLength) {
            Text.send(sender, Error.INVALID_NAME);
            return;
        }

        if (!shortName.matches("[A-Za-z0-9]+")) {
            Text.send(sender, Error.INVALID_NAME);
            return;
        }

        tPlayer.getSettings().setShortName(shortName);
        Text.send(sender, Success.SAVED);
    }


    @Subcommand("hexcolor")
    @CommandCompletion("@tscolor <hexcolorcode>")
    @CommandPermission("%permissiontimingsystem_color_set_hex")
    public static void onColorChange(CommandSender sender, TSColor tsColor, String hex) {
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        TextColor color;
        Theme theme = Theme.getTheme(sender);
        if (isValidHexCode(hex)) {
            color = TextColor.fromHexString(hex);
            if (color == null) {
                Text.send(sender,Error.COLOR_FORMAT);
                return;
            }
            switch (tsColor) {
                case SECONDARY -> theme.setSecondary(color);
                case PRIMARY -> theme.setPrimary(color);
                case AWARD -> theme.setAward(color);
                case AWARD_SECONDARY -> theme.setAwardSecondary(color);
                case ERROR -> theme.setError(color);
                case BROADCAST -> theme.setBroadcast(color);
                case SUCCESS -> theme.setSuccess(color);
                case WARNING -> theme.setWarning(color);
                case TITLE -> theme.setTitle(color);
                case BUTTON -> theme.setButton(color);
                case BUTTON_ADD -> theme.setButtonAdd(color);
                case BUTTON_REMOVE -> theme.setButtonRemove(color);
                default -> {
                }
            }
            sender.sendMessage(Text.get(sender, Success.COLOR_UPDATED).color(color));
            return;
        }
        Text.send(sender,Error.COLOR_FORMAT);
    }

    @Subcommand("color")
    @CommandCompletion("@tscolor @namedColor")
    @CommandPermission("%permissiontimingsystem_color_set_named")
    public static void onNamedColorChange(CommandSender sender, TSColor tsColor, NamedTextColor color) {
        if(sender instanceof Player player) {
            if (!player.hasPermission(PermissionTimingSystem.COLOR_SET_NAMED.getNode())) {
                Text.send(player, Error.PERMISSION_DENIED);
                return;
            }
        }

        if (color == null) {
            Text.send(sender,Error.NO_HEX_COLOR_IN_TS_COLOR);
            return;
        }

        Theme theme = Theme.getTheme(sender);
        switch (tsColor) {
            case SECONDARY -> theme.setSecondary(color);
            case PRIMARY -> theme.setPrimary(color);
            case AWARD -> theme.setAward(color);
            case AWARD_SECONDARY -> theme.setAwardSecondary(color);
            case ERROR -> theme.setError(color);
            case BROADCAST -> theme.setBroadcast(color);
            case SUCCESS -> theme.setSuccess(color);
            case WARNING -> theme.setWarning(color);
            case TITLE -> theme.setTitle(color);
            case BUTTON -> theme.setButton(color);
            case BUTTON_ADD -> theme.setButtonAdd(color);
            case BUTTON_REMOVE -> theme.setButtonRemove(color);
            default -> {
            }
        }
        sender.sendMessage(Text.get(sender, Success.COLOR_UPDATED).color(color));
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
