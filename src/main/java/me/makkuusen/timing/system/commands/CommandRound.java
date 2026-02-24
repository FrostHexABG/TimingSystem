package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventResults;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.theme.messages.TextButton;
import me.makkuusen.timing.system.theme.messages.Warning;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@CommandAlias("round")
public class CommandRound extends BaseCommand {
    @Default
    @Subcommand("list")
    @CommandPermission("%permissionround_list")
    public static void onRounds(Player player, @Optional Event event) {
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
        Text.send(player, Info.ROUNDS_TITLE, "%event%", event.getDisplayName());
        event.eventSchedule.listRounds(theme).forEach(player::sendMessage);
    }

    @Subcommand("create")
    @CommandCompletion("@roundType")
    @CommandPermission("%permissionround_create")
    public static void onCreate(Player player, RoundType roundType, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }

        if (event.getTrack() == null) {
            Text.send(player, Error.TRACK_NOT_FOUND_FOR_EVENT);
            return;
        }
        if (event.getTrack().isStage() && roundType.equals(RoundType.QUALIFICATION)) {
            Text.send(player, Error.QUALIFICATION_NOT_SUPPORTED);
            return;
        }

        if (EventDatabase.roundNew(event, roundType, event.getEventSchedule().getRounds().size() + 1)) {
            Text.send(player, Success.CREATED_ROUND, "%round%", roundType.name());
            return;
        }
        Text.send(player, Error.FAILED_TO_CREATE_ROUND);
    }

    @Subcommand("delete")
    @CommandCompletion("@round")
    @CommandPermission("%permissionround_delete")
    public static void onDelete(Player player, Round round) {
        if (EventDatabase.removeRound(round)) {
            Text.send(player, Success.REMOVED_ROUND, "%round%", round.getDisplayName());
            return;
        }
        Text.send(player, Error.FAILED_TO_REMOVE_ROUND);
    }

    @Subcommand("info")
    @CommandCompletion("@round")
    @CommandPermission("%permissionround_info")
    public static void onRoundInfo(Player player, Round round) {
        Theme theme = TSDatabase.getPlayer(player).getTheme();
        player.sendMessage(Component.space());
        player.sendMessage(theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/round info " + round.getName())).append(Component.space()).append(theme.getTitleLine(Component.text(round.getDisplayName()).color(theme.getSecondary()).append(Component.space()).append(theme.getParenthesized(round.getState().name())))).append(Component.space()).append(theme.getBrackets(Text.get(player, TextButton.VIEW_EVENT), theme.getButton()).clickEvent(ClickEvent.runCommand("/event info " + round.getEvent().getDisplayName())).hoverEvent(theme.getClickToViewHoverEvent(player))));

        var heatsMessage = Text.get(player, Info.ROUND_INFO_HEATS);

        if (player.hasPermission("timingsystem.packs.eventadmin")) {
            heatsMessage = heatsMessage.append(theme.tab()).append(theme.getAddButton(Text.get(player, TextButton.ADD_HEAT)).clickEvent(ClickEvent.runCommand("/heat create " + round.getName())).hoverEvent(theme.getClickToAddHoverEvent(player)));
        }
        player.sendMessage(heatsMessage);

        for (Heat heat : round.getHeats()) {

            var message = theme.tab().append(Component.text(heat.getName()).color(theme.getSecondary())).append(theme.tab()).append(theme.getViewButton(player).clickEvent(ClickEvent.runCommand("/heat info " + heat.getName())).hoverEvent(theme.getClickToViewHoverEvent(player)));

            if (player.hasPermission("timingsystem.packs.eventadmin")) {
                message = message.append(Component.space()).append(theme.getRemoveButton().clickEvent(ClickEvent.suggestCommand("/heat delete " + heat.getName())));
            }

            player.sendMessage(message);
        }
    }


    @Subcommand("finish")
    @CommandPermission("%permissionround_finish")
    public static void onRoundFinish(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }

        var maybeRound = event.eventSchedule.getRound();
        if (maybeRound.isPresent()) {
            if (maybeRound.get().finish(event)) {
                Text.send(player, Success.ROUND_FINISHED);
            } else {
                Text.send(player, Error.FAILED_TO_FINISH_ROUND);
            }
        } else {
            Text.send(player, Error.FAILED_TO_FINISH_ROUND);
        }
    }

    @Subcommand("results")
    @CommandCompletion("@round")
    @CommandPermission("%permissionround_results")
    public static void onRoundResults(Player player, Round round, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        List<Driver> results = EventResults.generateRoundResults(round.getHeats());

        if (!results.isEmpty()) {
            Theme theme = TSDatabase.getPlayer(player).getTheme();
            Text.send(player, Info.ROUND_RESULT_TITLE, "%round%", String.valueOf(round.getRoundIndex()));
            int pos = 1;
            if (round instanceof FinalRound) {
                for (Driver d : results) {
                    Text.send(player, Broadcast.HEAT_RESULT_ROW, "%pos%", String.valueOf(d.getPosition() ), "%player%", d.getTPlayer().getName(), "%laps%", String.valueOf(d.getLaps().size()), "%time%", ApiUtilities.formatAsTime(d.getFinishTime()));
                }
            } else {
                for (Driver d : results) {
                    player.sendMessage(theme.primary(pos++ + ".").append(Component.space()).append(theme.highlight(d.getTPlayer().getName())).append(theme.hyphen()).append(theme.highlight(d.getBestLap().isPresent() ? ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime()) : "0")));
                }
            }
        } else {
            Text.send(player, Error.ROUND_NOT_FINISHED);
        }
    }

    @Subcommand("removeDriversFromRound")
    @CommandPermission("%permissionround_removedrivers")
    public static void onRemoveDriversFromHeats(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        java.util.Optional<Round> maybeRound;
        if (event.eventSchedule.getCurrentRound() == null && !event.eventSchedule.getRounds().isEmpty()) {
            maybeRound = event.eventSchedule.getRound(1);
        } else {
            maybeRound = event.eventSchedule.getRound();
        }
        if (maybeRound.isPresent()) {
            Round round = maybeRound.get();

            for (Heat h : round.getHeats()) {
                if (h.getHeatState() != HeatState.SETUP) {
                    Text.send(player, Error.FAILED_TO_REMOVE_DRIVERS);
                    return;
                }

                List<Driver> drivers = new ArrayList<>(h.getDrivers().values());
                for (Driver d : drivers) {
                    h.removeDriver(d);
                }
            }
            Text.send(player, Success.REMOVED_DRIVERS);
        }
    }


    @Subcommand("fillheats")
    @CommandCompletion("random|sorted all|signed|reserves")
    @CommandPermission("%permissionround_fillheats")
    public static void onFillHeats(Player player, String sort, String group) {
        Event event;
        var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
        if (maybeEvent.isPresent()) {
            event = maybeEvent.get();
        } else {
            Text.send(player, Error.NO_EVENT_SELECTED);
            return;
        }

        boolean random = sort.equalsIgnoreCase("random");

        if (event.getState() != Event.EventState.SETUP) {
            Text.send(player, Error.EVENT_ALREADY_STARTED);
            return;
        }

        java.util.Optional<Round> maybeRound;
        if (event.eventSchedule.getCurrentRound() == null && !event.eventSchedule.getRounds().isEmpty()) {
            maybeRound = event.eventSchedule.getRound(1);
        } else {
            maybeRound = event.eventSchedule.getRound();
        }
        if (maybeRound.isPresent()) {
            Round round = maybeRound.get();
            var heats = round.getHeats();
            int numberOfDrivers = event.getSubscribers().size();

            int numberOfSlots = heats.stream().mapToInt(Heat::getMaxDrivers).sum();

            LinkedList<TPlayer> tPlayerList = new LinkedList<>();
            LinkedList<TPlayer> excludedList = new LinkedList<>();

            List<TPlayer> listOfSubscribers = new ArrayList<>();
            if (group.equalsIgnoreCase("all")) {
                listOfSubscribers.addAll(event.getSubscribers().values().stream().map(Subscriber::getTPlayer).toList());
                int reserveSlots = numberOfSlots - numberOfDrivers;
                var reserves = event.getReserves().values().stream().map(Subscriber::getTPlayer).collect(Collectors.toList());
                if (reserveSlots > 0 && !event.getReserves().values().isEmpty()) {
                    List<TPlayer> list;
                    if (!random) {
                        list = getSortedList(reserves, event.getTrack());
                    } else {
                        list = getRandomList(reserves);
                    }
                    int count = Integer.min(reserveSlots, event.getReserves().values().size());
                    for (int i = 0; i < count; i++) {
                        listOfSubscribers.add(list.remove(0));
                    }
                    excludedList.addAll(list);
                } else {
                    excludedList.addAll(reserves);
                }
            } else {
                var subscriberMap = group.equalsIgnoreCase("signed") ? event.getSubscribers() : event.getReserves();
                listOfSubscribers.addAll(subscriberMap.values().stream().map(Subscriber::getTPlayer).toList());
            }
            Collections.shuffle(listOfSubscribers);

            if (!random) {
                tPlayerList.addAll(getSortedList(listOfSubscribers, event.getTrack()));
            } else {
                tPlayerList.addAll(getRandomList(listOfSubscribers));
            }

            for (Heat heat : heats) {
                Text.send(player,Success.ADDING_DRIVERS, "%heat%", heat.getName());
                int size = heat.getMaxDrivers() - heat.getDrivers().size();
                for (int i = 0; i < size; i++) {
                    if (tPlayerList.isEmpty()) {
                        break;
                    }
                    heatAddDriver(player, tPlayerList.pop(), heat, random);
                }
            }

            tPlayerList.addAll(excludedList);

            if (!tPlayerList.isEmpty()) {
                Text.send(player, Warning.DRIVERS_LEFT_OUT);
                StringBuilder message = new StringBuilder();
                message.append(tPlayerList.pop().getName());

                while (!tPlayerList.isEmpty()) {
                    message.append(", ").append(tPlayerList.pop().getName());
                }
                Theme theme = TSDatabase.getPlayer(player).getTheme();
                player.sendMessage(theme.warning(message.toString()));
            }
        } else {
            Text.send(player, Error.ROUND_NOT_FOUND);
        }
    }

    public static List<TPlayer> getSortedList(List<TPlayer> players, Track track) {
        List<TPlayer> tPlayerList = new ArrayList<>();
        List<TimeTrialFinish> driversWithBestTimes = track.getTimeTrials().getTopList().stream().filter(tt -> players.contains(tt.getPlayer())).toList();
        for (var finish : driversWithBestTimes) {
            tPlayerList.add(finish.getPlayer());
        }

        for (var subscriber : players) {
            if (!tPlayerList.contains(subscriber)) {
                tPlayerList.add(subscriber);
            }
        }

        return tPlayerList;
    }

    public static List<TPlayer> getRandomList(List<TPlayer> players) {
        List<TPlayer> tPlayerList = new ArrayList<>();
        for (var subscriber : players) {
            if (!tPlayerList.contains(subscriber)) {
                tPlayerList.add(subscriber);
            }
        }
        Collections.shuffle(tPlayerList);
        return tPlayerList;
    }


    public static void heatAddDriver(Player sender, TPlayer tPlayer, Heat heat, boolean random) {
        if (heat.getMaxDrivers() <= heat.getDrivers().size()) {
            return;
        }

        for (Heat h : heat.getRound().getHeats()) {
            if (h.getDrivers().get(tPlayer.getUniqueId()) != null) {
                return;
            }
        }

        if (EventDatabase.heatDriverNew(tPlayer.getUniqueId(), heat, heat.getDrivers().size() + 1)) {
            var bestTime = heat.getEvent().getTrack().getTimeTrials().getBestFinish(tPlayer);
            Theme theme = TSDatabase.getPlayer(sender).getTheme();
            sender.sendMessage(theme.primary(heat.getDrivers().size() + ":").append(Component.space()).append(theme.highlight(tPlayer.getName())).append(theme.hyphen()).append(theme.highlight(bestTime == null ? "(-)" : ApiUtilities.formatAsTime(bestTime.getTime()))));
        }

    }

}
