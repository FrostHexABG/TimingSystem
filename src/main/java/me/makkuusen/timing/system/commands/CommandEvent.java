package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.permissions.PermissionEvent;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Hover;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.theme.messages.TextButton;
import me.makkuusen.timing.system.theme.messages.Warning;
import me.makkuusen.timing.system.theme.messages.Word;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@CommandAlias("event")
public class CommandEvent extends BaseCommand {

    @Default
    @Description("Active events")
    @CommandPermission("%permissionevent_list")
    public static void onActiveEvents(CommandSender commandSender) {
        if (commandSender instanceof Player player) {
            Event event;
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
                onInfo(player, event);
                return;
            }
        }

        onListEvents(commandSender);
    }

    @Subcommand("list")
    @CommandPermission("%permissionevent_list")
    public static void onListEvents(CommandSender commandSender) {
        var list = EventDatabase.events.stream().filter(Event::isActive).sorted(Comparator.comparingLong(Event::getDate)).toList();
        commandSender.sendMessage(Component.empty());
        Text.send(commandSender, Info.ACTIVE_EVENTS_TITLE);
        Theme theme = Theme.getTheme(commandSender);
        for (Event event : list) {
            String nameDisplay;
            if (event.getUuid().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
                nameDisplay = "Admin";
            } else {
                TPlayer tPlayer = TSDatabase.getPlayer(event.getUuid());
                if (tPlayer != null) {
                    String playerNameDisplay = tPlayer.getNameDisplay();
                    if (playerNameDisplay != null) {
                        nameDisplay = playerNameDisplay;
                    } else {
                        nameDisplay = "{[ERROR] TPlayer.getNameDisplay() was null. Maybe the database hasn't finished loading yet?}";
                    }
                } else {
                    nameDisplay = "{[ERROR] TPlayer was null. Maybe the database hasn't finished loading yet?}";
                }
            }
            commandSender.sendMessage(theme.highlight(event.getDisplayName()).clickEvent(ClickEvent.runCommand("/event info " + event.getDisplayName())).hoverEvent(HoverEvent.showText(Text.get(commandSender, Hover.CLICK_TO_SELECT))).append(Component.space()).append(theme.getParenthesized(event.getState().name())).append(theme.primary(" - ")).append(theme.primary(ApiUtilities.niceDate(event.getDate()))).append(Component.space()).append(theme.primary(">")).append(Component.space()).append(theme.highlight(nameDisplay)));
        }
    }

    @Subcommand("start")
    @CommandPermission("%permissionevent_start")
    public static void onStart(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        if (event.start()) {
            Text.send(player, Success.EVENT_STARTED);
            Event finalEvent = event;
            event.getSpectators().values().forEach(spectator -> EventDatabase.setPlayerSelectedEvent(spectator.getTPlayer().getUniqueId(), finalEvent));
            return;
        }
        Text.send(player, Error.FAILED_TO_START_EVENT);
    }

    @Subcommand("finish")
    @CommandPermission("%permissionevent_finish")
    public static void onFinish(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        if (event.finish()) {
            Text.send(player, Success.EVENT_FINISHED);
            return;
        }
        Text.send(player, Error.FAILED_TO_FINISH_EVENT);
    }

    @Subcommand("info")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_info")
    public static void onInfo(CommandSender sender, Event event) {
        if (sender instanceof Player player) {
            EventDatabase.setPlayerSelectedEvent(player.getUniqueId(), event);
        }

        Theme theme = Theme.getTheme(sender);

        sender.sendMessage("");
        sender.sendMessage(theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/event info " + event.getDisplayName()))
                .append(Component.space())
                .append(theme.getTitleLine(Component.text(event.getDisplayName()).color(theme.getSecondary())
                        .append(Component.space())
                        .append(theme.getParenthesized(event.getState().name()))
                        .append(Component.space())
                        .append(theme.getParenthesized(String.valueOf(event.getId())))
                )));

        Component trackMessage;
        if (event.getTrack() == null) {
            trackMessage = Text.get(sender,Info.EVENT_INFO_TRACK).append(Component.text("-").color(theme.getSecondary()));

        } else {
            trackMessage = Text.get(sender,Info.EVENT_INFO_TRACK).append(Component.text(event.getTrack().getDisplayName()).color(theme.getSecondary())).append(Component.space()).append(theme.getViewButton(sender).clickEvent(ClickEvent.runCommand("/track info " + event.getTrack().getCommandName())).hoverEvent(theme.getClickToViewHoverEvent(sender)));

        }
        if (sender.hasPermission("timingsystem.packs.eventadmin") && event.getTrack() == null) {
            trackMessage = Text.get(sender,Info.EVENT_INFO_TRACK).append(theme.getBrackets("-").clickEvent(ClickEvent.suggestCommand("/event set track ")).hoverEvent(HoverEvent.showText(Text.get(sender, Hover.CLICK_TO_EDIT))));
        } else if (sender.hasPermission("timingsystem.packs.eventadmin")) {
            trackMessage = Text.get(sender,Info.EVENT_INFO_TRACK).append(theme.getBrackets(event.getTrack().getDisplayName()).clickEvent(ClickEvent.suggestCommand("/event set track ")).hoverEvent(HoverEvent.showText(Text.get(sender, Hover.CLICK_TO_EDIT)))).append(Component.space()).append(theme.getViewButton(sender).clickEvent(ClickEvent.runCommand("/track info " + event.getTrack().getCommandName())).hoverEvent(theme.getClickToViewHoverEvent(sender)));
        }
        sender.sendMessage(trackMessage);

        var signsMessage = Text.get(sender, Info.EVENT_INFO_SIGNS);
        Component open = Text.get(sender, Word.OPEN);
        Component closed = Text.get(sender, Word.CLOSED);

        if (sender.hasPermission(PermissionEvent.INFO.getNode())) {
            if (event.isOpenSign()) {
                signsMessage = signsMessage.append(theme.getBrackets(open)
                        .clickEvent(ClickEvent.runCommand("/event set signs closed"))
                        .hoverEvent(HoverEvent.showText(Text.get(sender, Hover.CLICK_TO_CLOSE))));
            } else {
                signsMessage = signsMessage.append(theme.getBrackets(closed).clickEvent(ClickEvent.runCommand("/event set signs open")).hoverEvent(HoverEvent.showText(Text.get(sender, Hover.CLICK_TO_OPEN))));
            }
        } else {
            signsMessage = event.isOpenSign() ? signsMessage.append(open.color(theme.getSecondary())) : signsMessage.append(closed.color(theme.getSecondary()));
        }

        sender.sendMessage(signsMessage);

        sender.sendMessage(Text.get(sender, Info.EVENT_INFO_SIGNED_DRIVERS).append(Component.space()).append(Component.text(event.getSubscribers().size() + "+" + event.getReserves().size()).color(theme.getSecondary())).append(Component.space()).append(theme.getViewButton(sender).clickEvent(ClickEvent.runCommand("/event signs " + event.getDisplayName())).hoverEvent(theme.getClickToViewHoverEvent(sender))));

        var roundsMessage = Text.get(sender, Info.EVENT_INFO_ROUNDS, "%total%", String.valueOf(event.eventSchedule.getRounds().size()));
        if (sender.hasPermission("timingsystem.packs.eventadmin")) {
            roundsMessage = roundsMessage.append(theme.tab()).append(theme.getAddButton(Text.get(sender, TextButton.ADD_ROUND)).clickEvent(ClickEvent.suggestCommand("/round create ")).hoverEvent(theme.getClickToAddHoverEvent(sender)));
        }

        sender.sendMessage(roundsMessage);

        for (Round round : event.eventSchedule.getRounds()) {

            boolean currentRound = round.getRoundIndex().equals(event.getEventSchedule().getCurrentRound()) && event.getState() != Event.EventState.FINISHED;
            var roundMessage = (currentRound ? theme.arrow() : theme.tab()).append(Component.text(round.getDisplayName() + ":").color(theme.getPrimary()));

            if (sender.hasPermission("timingsystem.packs.eventadmin") && round.getState() != Round.RoundState.FINISHED) {
                roundMessage = roundMessage.append(Component.space()).append(theme.getAddButton(Text.get(sender, TextButton.ADD_HEAT)).clickEvent(ClickEvent.runCommand("/heat create " + round.getName())).hoverEvent(theme.getClickToAddHoverEvent(sender)));

                if (currentRound) {
                    roundMessage = roundMessage.append(theme.getBrackets(Text.get(sender, Word.FINISH), NamedTextColor.GRAY).clickEvent(ClickEvent.suggestCommand("/round finish")).hoverEvent(HoverEvent.showText(Text.get(sender, Hover.CLICK_TO_FINISH))));
                }

                if (round.getState() != Round.RoundState.RUNNING) {
                    roundMessage = roundMessage.append(Component.space().append(theme.getRemoveButton().clickEvent(ClickEvent.suggestCommand("/round delete " + round.getName()))));
                }
            }

            sender.sendMessage(roundMessage);

            for (Heat heat : round.getHeats()) {
                var heatName = Component.text(heat.getName()).color(theme.getSecondary());
                heatName = heat.getHeatState() == HeatState.FINISHED ? heatName.decorate(TextDecoration.ITALIC) : heatName;
                var heatMessage = theme.tab().append(theme.tab()).append(heatName).append(theme.tab()).append(theme.getViewButton(sender).clickEvent(ClickEvent.runCommand("/heat info " + heat.getName())).hoverEvent(theme.getClickToViewHoverEvent(sender)));

                if (!heat.isFinished() && sender.hasPermission("timingsystem.packs.eventadmin")) {
                    heatMessage = heatMessage.append(Component.space()).append(theme.getRemoveButton().clickEvent(ClickEvent.suggestCommand("/heat delete " + heat.getName())));
                }
                sender.sendMessage(heatMessage);
            }
        }    }

    @Subcommand("create")
    @CommandCompletion("<name> @track")
    @CommandPermission("%permissionevent_create")
    public static void onCreate(Player player, @Single String name, @Optional Track track) {
        if (name.equalsIgnoreCase("QuickRace") || name.equalsIgnoreCase("RaceLobby")) {
            Text.send(player, Error.INVALID_NAME);
            return;
        }
        var maybeEvent = EventDatabase.eventNew(player.getUniqueId(), name);
        if (maybeEvent.isPresent()) {
            Text.send(player, Success.CREATED_EVENT, "%event%", name);
            if (track != null) {
                maybeEvent.get().setTrack(track);
            }
            return;
        }
        Text.send(player,Error.FAILED_TO_CREATE_EVENT);
    }

    @Subcommand("delete")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_delete")
    public static void onRemove(Player player, Event event) {
        if (EventDatabase.removeEvent(event)) {
            Text.send(player, Success.REMOVED_EVENT, "%event%", event.getDisplayName());
            return;
        }
        Text.send(player, Error.FAILED_TO_REMOVE_EVENT);
    }

    @Subcommand("select")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_select")
    public static void onSelectEvent(Player player, Event event) {
        EventDatabase.setPlayerSelectedEvent(player.getUniqueId(), event);
        Text.send(player, Success.EVENT_SELECTED);
    }

    @Subcommand("set track")
    @CommandCompletion("@track")
    @CommandPermission("%permissionevent_set_track")
    public static void onSetTrack(Player player, Track track) {
        Event event;
        var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
        if (maybeEvent.isPresent()) {
            event = maybeEvent.get();
        } else {
            Text.send(player, Error.NO_EVENT_SELECTED);
            return;
        }
        event.setTrack(track);
        Text.send(player, Success.TRACK_SELECTED, "%track%", track.getDisplayName());
    }

    @Subcommand("set signs")
    @CommandCompletion("open|closed")
    @CommandPermission("%permissionevent_set_signs")
    public static void setOpen(Player player, String open) {
        Event event;
        var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
        if (maybeEvent.isPresent()) {
            event = maybeEvent.get();
        } else {
            Text.send(player, Error.NO_EVENT_SELECTED);
            return;
        }

        if (open.equalsIgnoreCase("open")) {
            event.setOpenSign(true);
            Text.send(player, Success.SIGNS_NOW_OPEN);

        } else {
            event.setOpenSign(false);
            Text.send(player, Success.SIGNS_NOW_CLOSED);
        }
    }

    @Subcommand("spectate")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_spectate")
    public static void onSpectate(Player player, Event event) {
        if (event.isSpectating(player.getUniqueId())) {
            event.removeSpectator(player.getUniqueId());
            Text.send(player, Warning.NO_LONGER_SPECTATING, "%event%", event.getDisplayName());
        } else {
            event.addSpectator(player.getUniqueId());
            EventDatabase.setPlayerSelectedEvent(player.getUniqueId(), event);
            Text.send(player, Success.SPECTATING, "%event%", event.getDisplayName());
        }
    }

    @Subcommand("sign")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_sign")
    public static void onSignUp(Player player, Event event, @Optional String name) {
        if (name != null) {
            TPlayer tPlayer = TSDatabase.getPlayer(name);
            if (tPlayer == null) {
                Text.send(player, Error.PLAYER_NOT_FOUND);
                return;
            }

            if (event.isSubscribing(tPlayer.getUniqueId())) {
                if (event.getState() != Event.EventState.SETUP) {
                    Text.send(player, Error.EVENT_ALREADY_STARTED);
                    return;
                }
                event.removeSubscriber(tPlayer.getUniqueId());
                Text.send(player, Warning.PLAYER_NO_LONGER_SIGNED, "%player%", tPlayer.getName(), "%event%", event.getDisplayName());
            } else {
                if (!event.isOpenSign()) {
                    if (!player.hasPermission("timingsystem.packs.eventadmin")) {
                        Text.send(player, Error.NOT_NOW);
                        return;
                    }
                }

                if (event.isReserving(tPlayer.getUniqueId())) {
                    event.removeReserve(tPlayer.getUniqueId());
                }
                event.addSubscriber(tPlayer);
                EventDatabase.setPlayerSelectedEvent(tPlayer.getUniqueId(), event);
                Text.send(player, Success.PLAYER_SIGNED, "%player%", tPlayer.getName(), "%event%", event.getDisplayName());
                EventAnnouncements.broadcastPlayerSigned(tPlayer.getName(), event);
            }
            return;
        }

        TPlayer tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        if (event.isSubscribing(player.getUniqueId())) {
            if (event.getState() != Event.EventState.SETUP) {
                Text.send(player, Error.EVENT_ALREADY_STARTED);
                return;
            }
            event.removeSubscriber(player.getUniqueId());
            Text.send(player, Warning.NO_LONGER_SIGNED, "%event%", event.getDisplayName());
        } else {
            if (!event.isOpenSign()) {
                if (!player.hasPermission("timingsystem.packs.eventadmin")) {
                    Text.send(player, Error.NOT_NOW);
                    return;
                }
            }

            if (event.isReserving(player.getUniqueId())) {
                event.removeReserve(player.getUniqueId());
            }
            event.addSubscriber(tPlayer);
            EventDatabase.setPlayerSelectedEvent(player.getUniqueId(), event);
            Text.send(player, Success.SIGNED, "%event%", event.getDisplayName());
            EventAnnouncements.broadcastPlayerSigned(player.getName(), event);
        }
    }


    @Subcommand("signs")
    @CommandPermission("%permissionevent_listsigns")
    public static void onListSigns(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }

        Theme theme = TSDatabase.getPlayer(player).getTheme();

        int count = 1;
        player.sendMessage(Component.empty());
        var message = Text.get(player, Info.SIGNS_TITLE, "%event%", event.getDisplayName());

        if (player.hasPermission("timingsystem.packs.eventadmin") || player.hasPermission(PermissionEvent.SIGNOTHERS.getNode())) {
            message = message.append(Component.space()).append(theme.getAddButton().clickEvent(ClickEvent.suggestCommand("/event sign " + event.getDisplayName() + " ")));
        }

        player.sendMessage(message);
        player.sendMessage(Component.empty());

        if (event.getTrack() != null) {
            var sortedList = CommandRound.getSortedList(event.getSubscribers().values().stream().map(Subscriber::getTPlayer).collect(Collectors.toList()), event.getTrack());
            for (TPlayer tPlayer : sortedList) {
                var bestTime = event.getTrack().getTimeTrials().getBestFinish(tPlayer);
                player.sendMessage(theme.primary(count++ + ":").append(Component.space()).append(theme.highlight(tPlayer.getName())).append(theme.hyphen()).append(theme.highlight(bestTime == null ? "(-)" : ApiUtilities.formatAsTime(bestTime.getTime()))));
            }
        } else {
            for (Subscriber s : event.getSubscribers().values()) {
                player.sendMessage(theme.primary(count++ + ":").append(Component.space()).append(theme.highlight(s.getTPlayer().getName())));
            }
        }

        if (event.getReserves().values().isEmpty()) {
            return;
        }

        count = 1;
        message = Text.get(player, Info.RESERVES_TITLE, "%event%", event.getDisplayName());

        if (player.hasPermission("timingsystem.packs.eventadmin") || player.hasPermission(PermissionEvent.SIGNOTHERS.getNode())) {
            message = message.append(Component.space()).append(theme.getAddButton().clickEvent(ClickEvent.suggestCommand("/event reserve " + event.getDisplayName() + " ")));
        }

        player.sendMessage(message);

        if (event.getTrack() != null) {
            var sortedList = CommandRound.getSortedList(event.getReserves().values().stream().map(Subscriber::getTPlayer).collect(Collectors.toList()), event.getTrack());
            for (TPlayer tPlayer : sortedList) {
                var bestTime = event.getTrack().getTimeTrials().getBestFinish(tPlayer);
                player.sendMessage(theme.primary(count++ + ":").append(Component.space()).append(theme.highlight(tPlayer.getName())).append(theme.hyphen()).append(theme.highlight(bestTime == null ? "(-)" : ApiUtilities.formatAsTime(bestTime.getTime())))

                );
            }
        } else {
            for (Subscriber s : event.getReserves().values()) {
                player.sendMessage(theme.primary(count++ + ":").append(Component.space()).append(theme.highlight(s.getTPlayer().getName())));
            }
        }
    }

    @Subcommand("reserve")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_reserve")
    public static void onReserve(Player player, Event event, @Optional String name) {
        if (name != null) {
            if (!player.hasPermission(PermissionEvent.SIGNOTHERS.getNode())) {
                Text.send(player, Error.PERMISSION_DENIED);
                return;
            }

            TPlayer tPlayer = TSDatabase.getPlayer(name);
            if (tPlayer == null) {
                Text.send(player, Error.PLAYER_NOT_FOUND);
                return;
            }

            if (event.isReserving(tPlayer.getUniqueId())) {
                if (event.getState() != Event.EventState.SETUP) {
                    Text.send(player, Error.EVENT_ALREADY_STARTED);
                    return;
                }
                event.removeReserve(tPlayer.getUniqueId());

                Text.send(player, Warning.PLAYER_NO_LONGER_RESERVE, "%player%", tPlayer.getName(), "%event%", event.getDisplayName());
            } else {
                if (event.isSubscribing(tPlayer.getUniqueId())) {
                    event.removeSubscriber(tPlayer.getUniqueId());
                }
                event.addReserve(tPlayer);
                EventDatabase.setPlayerSelectedEvent(tPlayer.getUniqueId(), event);
                Text.send(player, Success.PLAYER_RESERVE, "%player%", tPlayer.getName(), "%event%", event.getDisplayName());
                EventAnnouncements.broadcastPlayerSignedReserve(tPlayer.getName(), event);
            }
            return;
        }
        var tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        if (event.isReserving(player.getUniqueId())) {
            if (event.getState() != Event.EventState.SETUP) {
                Text.send(player, Error.EVENT_ALREADY_STARTED);
                return;
            }
            event.removeReserve(player.getUniqueId());
            Text.send(player, Warning.NO_LONGER_RESERVE, "%event%", event.getDisplayName());
        } else {
            if (event.isSubscribing(player.getUniqueId())) {
                Text.send(player, Error.ALREADY_SIGNED, "%event%", event.getDisplayName());
                return;
            }
            event.addReserve(tPlayer);
            EventDatabase.setPlayerSelectedEvent(player.getUniqueId(), event);
            Text.send(player, Success.RESERVE, "%event%", event.getDisplayName());
            EventAnnouncements.broadcastPlayerSignedReserve(player.getName(), event);
        }
    }

    @Subcommand("countdown start")
    @CommandCompletion("<h/m/s> <label>")
    @CommandPermission("%permissionevent_countdown")
    public static void onCountdown(Player player, String time, @Optional String label) {
        Event event;
        var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
        if (maybeEvent.isPresent()) {
            event = maybeEvent.get();
        } else {
            Text.send(player, Error.NO_EVENT_SELECTED);
            return;
        }
        Integer timeLimit = ApiUtilities.parseDurationToMillis(time);
        if (timeLimit == null) {
            Text.send(player, Error.TIME_FORMAT);
            return;
        }


        event.eventCountdown.startCountdown(timeLimit/1000, label);

    }
    @Subcommand("countdown stop")
    @CommandCompletion("@event")
    @CommandPermission("%permissionevent_countdown")
    public static void onCountdown(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        if (event.eventCountdown.isActive()) {
            event.eventCountdown.stopCountdown();
        }
    }

    @Subcommand("broadcast clicktosign")
    @CommandPermission("%permissionevent_broadcast_clicktosign")
    public static void onSendSignUp(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (event.isSubscribing(p.getUniqueId())) {
                continue;
            }

            if (!event.isOpenSign()) {
                if (!player.hasPermission(PermissionEvent.SIGN.getNode()) || !player.hasPermission("timingsystem.packs.eventadmin")) {
                    continue;
                }
            }
            p.sendMessage(Component.empty());
            p.sendMessage(Text.get(p, Broadcast.CLICK_TO_SIGN, "%event%", event.getDisplayName()).clickEvent(ClickEvent.runCommand("/event sign " + event.getDisplayName())));
            p.sendMessage(Component.empty());
        }
    }

    @Subcommand("broadcast clicktoreserve")
    @CommandPermission("%permissionevent_broadcast_clicktoreserve")
    public static void onSendReserve(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (event.isReserving(p.getUniqueId()) || event.isSubscribing(p.getUniqueId())) {
                continue;
            }
            p.sendMessage(Component.empty());
            p.sendMessage(Text.get(p, Broadcast.CLICK_TO_RESERVE, "%event%", event.getDisplayName()).clickEvent(ClickEvent.runCommand("/event reserve " + event.getDisplayName())));
            p.sendMessage(Component.empty());
        }
    }
}
