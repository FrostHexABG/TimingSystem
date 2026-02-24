package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.ReadyCheckManager;
import me.makkuusen.timing.system.participant.Streaker;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.theme.messages.*;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.event.EventResults;
import me.makkuusen.timing.system.heat.CollisionMode;
import me.makkuusen.timing.system.heat.DriverSwapHandler;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.heat.TeamHeatEntry;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@CommandAlias("heat")
public class CommandHeat extends BaseCommand {

    @Default
    @Subcommand("list")
    @CommandPermission("%permissionheat_list")
    public static void onHeats(Player player, @Optional Event event) {
        if (event == null) {
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(player.getUniqueId());
            if (maybeEvent.isPresent()) {
                event = maybeEvent.get();
            } else {
                Text.send(player, Error.NO_EVENT_SELECTED);
                return;
            }
        }
        Text.send(player, Info.HEATS_TITLE, "%event%", event.getDisplayName());
        var messages = event.eventSchedule.getHeatList(TSDatabase.getPlayer(player).getTheme());
        messages.forEach(player::sendMessage);
    }

    @Subcommand("info")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_info")
    public static void onHeatInfo(Player player, Heat heat) {
        Theme theme = TSDatabase.getPlayer(player).getTheme();
        player.sendMessage(Component.empty());
        player.sendMessage(theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/heat info " + heat.getName())).append(Component.space()).append(theme.getTitleLine(Component.text(heat.getName()).color(theme.getSecondary()).append(Component.space()).append(theme.getParenthesized(heat.getHeatState().name()).append(Component.space()).append(theme.getBrackets(Text.get(player, TextButton.VIEW_EVENT), theme.getButton()).clickEvent(ClickEvent.runCommand("/event info " + heat.getEvent().getDisplayName())).hoverEvent(theme.getClickToViewHoverEvent(player)))))));

        Component load = theme.getBrackets(Text.get(player, Word.LOAD), NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/heat load " + heat.getName())).hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_LOAD)));
        Component reset = theme.getBrackets(Text.get(player, Word.RESET), NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/heat reset " + heat.getName())).hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_RESET)));
        Component start = theme.getBrackets(Text.get(player, Word.START), NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/heat start " + heat.getName())).hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_START)));
        Component finish = theme.getBrackets(Text.get(player, Word.FINISH), NamedTextColor.GRAY).clickEvent(ClickEvent.runCommand("/heat finish " + heat.getName())).hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_START)));

        if (player.hasPermission("timingsystem.packs.eventadmin") && heat.getHeatState() != HeatState.FINISHED) {
            player.sendMessage(load.append(Component.space()).append(reset).append(Component.space()).append(start).append(Component.space()).append(finish));
        }

        if (heat.getTimeLimit() != null) {
            var message = Text.get(player, Info.HEAT_INFO_TIME_LIMIT);

            if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                message = message.append(theme.getEditButton(player, (heat.getTimeLimit() / 1000) + "s", theme).clickEvent(ClickEvent.suggestCommand("/heat set timelimit " + heat.getName() + " ")));
            } else {
                message = message.append(theme.highlight((heat.getTimeLimit() / 1000) + "s"));
            }
            player.sendMessage(message);
        }
        if (heat.getStartDelay() != null) {
            var message = Text.get(player, Info.HEAT_INFO_START_DELAY);

            if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                message = message.append(theme.getEditButton(player, (heat.getStartDelay()) + "ms", theme).clickEvent(ClickEvent.suggestCommand("/heat set startdelay " + heat.getName() + " ")));
            } else {
                message = message.append(theme.highlight((heat.getStartDelay()) + "ms"));
            }
            player.sendMessage(message);
        }

        if (heat.getTotalLaps() != null) {
            var message = Text.get(player, Info.HEAT_INFO_LAPS);

            if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                message = message.append(theme.getEditButton(player, String.valueOf(heat.getTotalLaps()), theme).clickEvent(ClickEvent.suggestCommand("/heat set laps " + heat.getName() + " ")));
            } else {
                message = message.append(theme.highlight(String.valueOf(heat.getTotalLaps())));
            }
            player.sendMessage(message);
        }
        if (heat.getTotalPits() != null) {
            var message = Text.get(player, Info.HEAT_INFO_PITS);

            if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                message = message.append(theme.getEditButton(player, String.valueOf(heat.getTotalPits()), theme).clickEvent(ClickEvent.suggestCommand("/heat set pits " + heat.getName() + " ")));
            } else {
                message = message.append(theme.highlight(String.valueOf(heat.getTotalPits())));
            }
            player.sendMessage(message);
        }

        var maxDriversMessage = Text.get(player, Info.HEAT_INFO_MAX_DRIVERS);

        if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
            maxDriversMessage = maxDriversMessage.append(theme.getEditButton(player, String.valueOf(heat.getMaxDrivers()), theme).clickEvent(ClickEvent.suggestCommand("/heat set maxdrivers " + heat.getName() + " ")));
        } else {
            maxDriversMessage = maxDriversMessage.append(theme.highlight(String.valueOf(heat.getMaxDrivers())));
        }
        player.sendMessage(maxDriversMessage);

        var collisionModeMessage = Text.get(player, Info.HEAT_INFO_COLLISION_MODE);

        if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
            collisionModeMessage = collisionModeMessage.append(theme.getEditButton(player, heat.getCollisionMode().name().toLowerCase(), theme).clickEvent(ClickEvent.suggestCommand("/heat set collision " + heat.getName() + " ")));
        } else {
            collisionModeMessage = collisionModeMessage.append(theme.highlight(heat.getCollisionMode().name().toLowerCase()));
        }
        player.sendMessage(collisionModeMessage);

        var drsMessage = Component.text("DRS: ").color(theme.getPrimary());

        if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
            String drsValue = (heat.getDrs() != null && heat.getDrs()) ? "true" : "false";
            drsMessage = drsMessage.append(theme.getEditButton(player, drsValue, theme).clickEvent(ClickEvent.suggestCommand("/heat set drs " + heat.getName() + " ")));
        } else {
            String drsValue = (heat.getDrs() != null && heat.getDrs()) ? "enabled" : "disabled";
            drsMessage = drsMessage.append(theme.highlight(drsValue));
        }
        player.sendMessage(drsMessage);

        var pushToPassMessage = Component.text("Push to Pass: ").color(theme.getPrimary());

        if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
            String p2pValue = (heat.getPushToPass() != null && heat.getPushToPass()) ? "true" : "false";
            pushToPassMessage = pushToPassMessage.append(theme.getEditButton(player, p2pValue, theme).clickEvent(ClickEvent.suggestCommand("/heat set pushtopass " + heat.getName() + " ")));
        } else {
            String p2pValue = (heat.getPushToPass() != null && heat.getPushToPass()) ? "enabled" : "disabled";
            pushToPassMessage = pushToPassMessage.append(theme.highlight(p2pValue));
        }
        player.sendMessage(pushToPassMessage);

        if (heat.getFastestLapUUID() != null) {
            Driver d = heat.getDrivers().get(heat.getFastestLapUUID());
            player.sendMessage(Text.get(player, Info.HEAT_INFO_FASTEST_LAP, "%time%", ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime()), "%player%", d.getTPlayer().getName()));
        }

        var driverMessage = Text.get(player, Info.HEAT_INFO_DRIVERS);

        if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
            driverMessage = driverMessage.append(Component.space()).append(theme.getAddButton().clickEvent(ClickEvent.suggestCommand("/heat add " + heat.getName() + " ")))
                .append(Component.space())
                .append(theme.getBrackets(Component.text("+ Team"), NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.suggestCommand("/heat add team " + heat.getName() + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to add a team"))));
        }

        player.sendMessage(driverMessage);

        boolean boatSwitchingEnabled = heat.getBoatSwitching() != null && heat.getBoatSwitching();
        
        if (boatSwitchingEnabled && !heat.getTeamEntries().isEmpty()) {
            for (TeamHeatEntry teamEntry : heat.getTeamEntries().values()) {
                TPlayer activeDriver = teamEntry.getActiveDriver();
                String activeDriverName = activeDriver != null ? activeDriver.getName() : "none";
                String teamName = teamEntry.getTeam() != null ? teamEntry.getTeam().getDisplayName() : "Unknown Team";
                
                var message = theme.tab().append(Component.text(teamEntry.getStartPosition() + ": " + teamName + " (Active: " + activeDriverName + ")").color(NamedTextColor.WHITE));

                if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                    message = message.append(theme.tab()).append(theme.getRemoveButton().clickEvent(ClickEvent.suggestCommand("/heat delete driver " + heat.getName() + " " + activeDriverName)));
                }

                player.sendMessage(message);
            }
        } else {
            for (Driver d : heat.getStartPositions()) {
                var message = theme.tab().append(Component.text(d.getStartPosition() + ": " + d.getTPlayer().getName()).color(NamedTextColor.WHITE));

                if (!heat.isFinished() && player.hasPermission("timingsystem.packs.eventadmin")) {
                    message = message.append(theme.tab()).append(theme.getMoveButton().clickEvent(ClickEvent.suggestCommand("/heat set driverposition " + heat.getName() + " " + d.getTPlayer().getName() + " ")).hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_EDIT_POSITION)))).append(Component.space()).append(theme.getRemoveButton().clickEvent(ClickEvent.suggestCommand("/heat delete driver " + heat.getName() + " " + d.getTPlayer().getName())));
                }

                player.sendMessage(message);
            }
        }
    }

    @Subcommand("start")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_start")
    public static void onHeatStart(Player player, Heat heat) {
        if (heat.startCountdown()) {
            Text.send(player, Success.HEAT_COUNTDOWN_STARTED);
            return;
        }
        Text.send(player, Error.FAILED_TO_START_HEAT);
    }

    @Subcommand("readycheck")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_readycheck")
    public static void onReadyCheckOpen(Player player, Heat heat) {
        if (ReadyCheckManager.isReadyCheckInProgress(player)) {
            ReadyCheckManager.getReadyCheck(player).openGUIToInitiator();
        } else {
            ReadyCheckManager.createReadyCheck(player, heat).openGUIToInitiator();
        }
    }

    @Subcommand("readycheck end")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_readycheck")
    public static void onReadyCheckEnd(Player player) {
        if (ReadyCheckManager.isReadyCheckInProgress(player)) {
            ReadyCheckManager.getReadyCheck(player).end();
        }
    }

    @Subcommand("finish")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_finish")
    public static void onHeatFinish(Player player, Heat heat) {
        if (heat.finishHeat()) {
            Text.send(player, Success.HEAT_FINISHED);
            return;
        }
        Text.send(player, Error.FAILED_TO_FINISH_HEAT);
    }

    @Subcommand("load")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_load")
    public static void onHeatLoad(Player player, Heat heat) {
        var state = heat.getHeatState();
        if (state != HeatState.SETUP) {
            if (!heat.resetHeat()) {
                Text.send(player, Error.FAILED_TO_RESET_HEAT);
                return;
            }
        }

        if (heat.loadHeat()) {
            if (state == HeatState.SETUP) {
                EventAnnouncements.broadcastSpectate(heat.getEvent());
            }
            Text.send(player, Success.HEAT_LOADED);
            return;
        }
        Text.send(player, Error.FAILED_TO_LOAD_HEAT);

    }

    @Subcommand("reset")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_reset")
    public static void onHeatReset(Player player, Heat heat) {
        if (heat.resetHeat()) {
            EventAnnouncements.broadcastReset(heat);
            Text.send(player, Success.HEAT_RESET);
            return;
        }
        Text.send(player, Error.FAILED_TO_RESET_HEAT);
    }

    @Subcommand("delete")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_remove")
    public static void onHeatRemove(Player player, Heat heat) {
        if (EventDatabase.removeHeat(heat)) {
            Text.send(player, Success.REMOVED_HEAT, "%heat%", heat.getName());
            return;
        }
        Text.send(player, Error.FAILED_TO_REMOVE_HEAT);
    }

    @Subcommand("swap")
    @CommandPermission("%permissionheat_driver_swap")
    @Description("Take over for your team's offline driver")
    public static void onDriverSwap(Player player) {
        DriverSwapHandler.handleOfflineReplacement(player);
    }

    @Subcommand("create")
    @CommandCompletion("@round")
    @CommandPermission("%permissionheat_create")
    public static void onHeatCreate(Player player, Round round, @Optional Event event) {
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
        round.createHeat(round.getHeats().size() + 1);
        Text.send(player, Success.CREATED_HEAT, "%round%", round.getDisplayName());
    }

    @Subcommand("set laps")
    @CommandCompletion("@heat <laps>")
    @CommandPermission("%permissionheat_set_laps")
    public static void onHeatSetLaps(Player player, Heat heat, Integer laps) {
        heat.setTotalLaps(laps);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set pits")
    @CommandCompletion("@heat <pits>")
    @CommandPermission("%permissionheat_set_laps")
    public static void onHeatSetPits(Player player, Heat heat, Integer pits) {
        if (heat.getRound() instanceof QualificationRound) {
            Text.send(player, Error.CAN_NOT);
        } else {
            heat.setTotalPits(pits);
            Text.send(player, Success.SAVED);
        }
    }

    @Subcommand("set startdelay")
    @CommandCompletion("@heat <h/m/s>")
    @CommandPermission("%permissionheat_set_startdelay")
    public static void onHeatStartDelay(Player player, Heat heat, String startDelay) {
        Integer delay = ApiUtilities.parseDurationToMillis(startDelay);
        if (delay == null) {
            Text.send(player, Error.TIME_FORMAT);
            return;
        }
        heat.setStartDelayInTicks(delay);
        Text.send(player, Success.SAVED);

    }

    @Subcommand("set timelimit")
    @CommandCompletion("@heat <h/m/s>")
    @CommandPermission("%permissionheat_set_timelimit")
    public static void onHeatSetTime(Player player, Heat heat, String time) {
        Integer timeLimit = ApiUtilities.parseDurationToMillis(time);
        if (timeLimit == null) {
            Text.send(player, Error.TIME_FORMAT);
            return;
        }
        heat.setTimeLimit(timeLimit);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set maxdrivers")
    @CommandCompletion("@heat <max>")
    @CommandPermission("%permissionheat_set_maxdrivers")
    public static void onHeatMaxDrivers(Player player, Heat heat, Integer maxDrivers) {
        heat.setMaxDrivers(maxDrivers);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set collision")
    @CommandCompletion("@heat high|low|disabled")
    @CommandPermission("%permissionheat_set_collision")
    public static void onHeatSetCollision(Player player, Heat heat, String collisionMode) {
        try {
            CollisionMode mode = CollisionMode.valueOf(collisionMode.toUpperCase());
            heat.setCollisionMode(mode);
            Text.send(player, Success.SAVED);
        } catch (IllegalArgumentException e) {
            Text.send(player, Error.GENERIC);
        }
    }

    @Subcommand("set drs")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_drs")
    public static void onHeatSetDrs(Player player, Heat heat, Boolean drs) {
        heat.setDrs(drs);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set drsdowntime")
    @CommandCompletion("@heat <laps>")
    @CommandPermission("%permissionheat_set_drsdowntime")
    public static void onHeatSetDrsDowntime(Player player, Heat heat, Integer laps) {
        heat.setDrsDowntime(laps);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set pushtopass|p2p")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_pushtopass")
    public static void onHeatSetPushToPass(Player player, Heat heat, Boolean pushToPass) {
        heat.setPushToPass(pushToPass);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set lonely")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_lonely")
    @Deprecated
    public static void onHeatSetLonely(Player player, Heat heat, Boolean lonely) {
        heat.setLonely(lonely);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set reset")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_reset")
    public static void onHeatSetReset(Player player, Heat heat, Boolean reset) {
        heat.setReset(reset);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set lapreset")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_lapreset")
    public static void onHeatSetLapReset(Player player, Heat heat, Boolean lapReset) {
        heat.setLapReset(lapReset);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set ghostingDelta")
    @CommandCompletion("@heat <false/h/m/s>")
    @CommandPermission("%permissionheat_set_ghostingdelta")
    public static void onHeatGhostingDelta(Player player, Heat heat, String time) {
        if (time.equalsIgnoreCase("false")) {
            heat.setGhostingDelta(null);
            Text.send(player, Success.SAVED);
            return;
        }

        Integer timeLimit = ApiUtilities.parseDurationToMillis(time);
        if (timeLimit == null) {
            Text.send(player, Error.TIME_FORMAT);
            return;
        }
        heat.setGhostingDelta(timeLimit);
        Text.send(player, Success.SAVED);
    }

    @Subcommand("set boatSwitching")
    @CommandCompletion("@heat true|false")
    @CommandPermission("%permissionheat_set_boatswitching")
    public static void onHeatBoatSwitching(Player player, Heat heat, Boolean boatSwitching) {
        if (boatSwitching) {
            if (!heat.getDrivers().isEmpty()) {
                player.sendMessage(Component.text("Cannot enable boat switching: heat already has individual drivers. Remove all drivers first or create a new heat.", NamedTextColor.RED));
                return;
            }
            
            if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
                player.sendMessage(Component.text("Cannot enable boat switching: heat has already started or finished.", NamedTextColor.RED));
                return;
            }
        } else {
            if (!heat.getTeamEntries().isEmpty()) {
                player.sendMessage(Component.text("Cannot disable boat switching: heat already has team entries. Remove all teams first or create a new heat.", NamedTextColor.RED));
                return;
            }
            
            if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED) {
                player.sendMessage(Component.text("Cannot disable boat switching: heat has already started or finished.", NamedTextColor.RED));
                return;
            }
        }
        
        heat.setBoatSwitching(boatSwitching);
        player.sendMessage(Component.text("Boat switching " + (boatSwitching ? "enabled" : "disabled") + " for heat " + heat.getName() + ".", NamedTextColor.GREEN));
    }

    @Subcommand("set driverposition")
    @CommandCompletion("@heat @players <[+/-]pos>")
    @CommandPermission("%permissionheat_set_driverposition")
    public static void onHeatSetDriverPosition(Player sender, Heat heat, String playerName, String position) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        if (heat.getDrivers().get(tPlayer.getUniqueId()) == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        Driver driver = heat.getDrivers().get(tPlayer.getUniqueId());
        if (heat.isRacing()) {
            Text.send(sender, Error.HEAT_ALREADY_STARTED);
            return;
        }
        if (getParsedIndex(position) == null) {
            Text.send(sender, Error.NUMBER_FORMAT);
            return;
        }
        int parsedIndex = Objects.requireNonNull(getParsedIndex(position));
        int pos;
        if (getParsedRemoveFlag(position)) {
            pos = driver.getStartPosition() - parsedIndex;
        } else if (getParsedAddFlag(position)) {
            pos = driver.getStartPosition() + parsedIndex;
        } else {
            pos = parsedIndex;
        }

        if (pos > heat.getDrivers().size()) {
            Text.send(sender, Error.CAN_NOT);
            return;
        }

        if (pos < 1) {
            Text.send(sender, Error.CAN_NOT);
            return;
        }

        if (pos == driver.getStartPosition()) {
            Text.send(sender, Error.CAN_NOT);
            return;
        }


        if (heat.setDriverPosition(driver, pos)) {
            Text.send(sender, Success.DRIVER_NEW_START_POSITION, "%driver%", driver.getTPlayer().getName(), "%pos%", String.valueOf(pos));
            if (heat.getHeatState() == HeatState.LOADED) {
                heat.reloadHeat();
            }
            return;
        }
        Text.send(sender, Error.GENERIC);

    }

    @Subcommand("set reversegrid")
    @CommandCompletion("@heat <%>")
    @CommandPermission("%permissionheat_set_reversegrid")
    public static void onReverseGrid(Player player, Heat heat, @Optional Integer percentage) {
        if (percentage == null) {
            percentage = 100;
        }
        heat.reverseGrid(percentage);
        if (heat.getHeatState() == HeatState.LOADED) {
            heat.reloadHeat();
        }
        Text.send(player, Success.HEAT_REVERSED_GRID, "%percent%", String.valueOf(percentage));
    }

    @Subcommand("add streaker")
    @CommandCompletion("@heat @players")
    @CommandPermission("%permissionheat_add_streaker")
    public static void onHeatAddStreaker(Player sender, Heat heat, String playerName) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }

        if (heat.isStreaking(tPlayer.getUniqueId())) {
            Text.send(sender, Error.PLAYER_ALREADY_IN_ROUND);
            return;
        }

        heat.addStreaker(tPlayer.getUniqueId());
        Text.send(sender, Success.ADDED_DRIVER, "%player%", tPlayer.getName());
    }

    @Subcommand("add")
    @CommandCompletion("@heat @players")
    @CommandPermission("%permissionheat_add_driver")
    public static void onHeatAddDriver(Player sender, Heat heat, String playerName) {
        if (!heat.getRound().getRoundIndex().equals(heat.getEvent().getEventSchedule().getCurrentRound()) && heat.getRound().getRoundIndex() != 1) {
            Text.send(sender, Error.ADD_DRIVER_FUTURE_ROUND);
            return;
        }

        if (heat.isBoatSwitchingEnabled()) {
            sender.sendMessage(Component.text("Cannot add individual drivers to a boat switching heat. Use '/heat add team <team>' instead.", NamedTextColor.RED));
            return;
        }

        if (heat.getMaxDrivers() <= heat.getDrivers().size()) {
            Text.send(sender, Error.HEAT_FULL);
            return;
        }

        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }

        for (Heat h : heat.getRound().getHeats()) {
            if (h.getDrivers().get(tPlayer.getUniqueId()) != null) {
                Text.send(sender, Error.PLAYER_ALREADY_IN_ROUND);
                return;
            }
        }

        if (EventDatabase.heatDriverNew(tPlayer.getUniqueId(), heat, heat.getDrivers().size() + 1)) {
            Text.send(sender, Success.ADDED_DRIVER);
            if (heat.getHeatState() == HeatState.LOADED) {
                heat.addDriverToGrid(heat.getDrivers().get(tPlayer.getUniqueId()));
            }
            return;
        }

        Text.send(sender, Error.FAILED_TO_ADD_DRIVER);
    }

    @Subcommand("add team")
    @CommandCompletion("@heat @teams")
    @CommandPermission("%permissionheat_add_driver")
    public static void onHeatAddTeam(Player sender, Heat heat, Team team) {
        if (!heat.getRound().getRoundIndex().equals(heat.getEvent().getEventSchedule().getCurrentRound()) && heat.getRound().getRoundIndex() != 1) {
            Text.send(sender, Error.ADD_DRIVER_FUTURE_ROUND);
            return;
        }

        if (team.isEmpty()) {
            sender.sendMessage(Component.text("Team has no players.", NamedTextColor.RED));
            return;
        }

        boolean boatSwitchingEnabled = heat.isBoatSwitchingEnabled();
        
        if (!boatSwitchingEnabled) {
            sender.sendMessage(Component.text("Cannot add teams to this heat. Boat switching is not enabled. Use '/heat add <player>' to add individual players instead.", NamedTextColor.RED));
            return;
        }

        if (heat.getTeamEntry(team.getId()).isPresent()) {
            sender.sendMessage(Component.text("Team " + team.getDisplayName() + " is already in this heat.", NamedTextColor.RED));
            return;
        }

        if (heat.getMaxDrivers() < heat.getTeamEntries().size() + 1) {
            sender.sendMessage(Component.text("Not enough space in heat. Heat is full.", NamedTextColor.RED));
            return;
        }

        for (TPlayer teamPlayer : team.getPlayers()) {
            var existingTeamEntry = heat.getTeamEntryByPlayer(teamPlayer.getUniqueId());
            if (existingTeamEntry.isPresent()) {
                var existingTeam = existingTeamEntry.get().getTeam();
                String existingTeamName = existingTeam != null ? existingTeam.getDisplayName() : "Unknown";
                sender.sendMessage(Component.text("Cannot add team: Player " + teamPlayer.getName() + 
                    " is already in this heat as part of team " + existingTeamName + ".", NamedTextColor.RED));
                return;
            }
        }

        int startPosition = heat.getTeamEntries().size() + 1;
        heat.addTeamToHeat(team, startPosition);
        
        TeamHeatEntry teamEntry = heat.getTeamEntry(team.getId()).orElse(null);
        if (teamEntry == null) {
            sender.sendMessage(Component.text("Failed to create team heat entry.", NamedTextColor.RED));
            return;
        }
        
        TPlayer activeDriver = teamEntry.getActiveDriver();
        if (activeDriver != null && !heat.getDrivers().containsKey(activeDriver.getUniqueId())) {
            EventDatabase.heatDriverNew(activeDriver.getUniqueId(), heat, startPosition);
        }
        
        if (heat.getHeatState() == HeatState.LOADED) {
            if (activeDriver != null && heat.getDrivers().containsKey(activeDriver.getUniqueId())) {
                heat.addDriverToGrid(heat.getDrivers().get(activeDriver.getUniqueId()));
                sender.sendMessage(Component.text("Added team " + team.getDisplayName() + 
                    " to heat. Active driver: " + activeDriver.getName() + 
                    " (placed on grid).", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Added team " + team.getDisplayName() + 
                    " to heat (no active driver to place on grid).", NamedTextColor.GREEN));
            }
        } else {
            String activeDriverName = activeDriver != null ? activeDriver.getName() : "none";
            sender.sendMessage(Component.text("Added team " + team.getDisplayName() + 
                " to heat. Active driver: " + activeDriverName + ".", NamedTextColor.GREEN));
        }
    }


    @Subcommand("delete streaker")
    @CommandCompletion("@heat @players")
    @CommandPermission("%permissionheat_removestreaker")
    public static void onHeatRemoveStreaker(Player sender, Heat heat, String playerName) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        if (!heat.isStreaking(tPlayer.getUniqueId())) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        
        heat.removeStreaker(tPlayer.getUniqueId());
        Text.send(sender, Success.DRIVER_REMOVED, "%player%", tPlayer.getName());
    }

    @Subcommand("delete team")
    @CommandCompletion("@heat @teams")
    @CommandPermission("%permissionheat_removedriver")
    public static void onHeatRemoveTeam(Player sender, Heat heat, Team team) {
        if (!heat.isBoatSwitchingEnabled()) {
            sender.sendMessage(Component.text("This heat does not have boat switching enabled.", NamedTextColor.RED));
            return;
        }

        if (!heat.getTeamEntry(team.getId()).isPresent()) {
            sender.sendMessage(Component.text("Team " + team.getDisplayName() + " is not in this heat.", NamedTextColor.RED));
            return;
        }

        if (heat.isRacing()) {
            sender.sendMessage(Component.text("Cannot remove team while heat is racing. Disqualify the active driver instead.", NamedTextColor.RED));
            return;
        }

        boolean needsReload = heat.getHeatState() == HeatState.LOADED;
        if (needsReload) {
            heat.resetHeat();
        }

        if (heat.removeTeamFromHeat(team.getId())) {
            sender.sendMessage(Component.text("Removed team " + team.getDisplayName() + " from heat.", NamedTextColor.GREEN));
            
            if (needsReload && !heat.getTeamEntries().isEmpty()) {
                heat.loadHeat();
            }
        } else {
            sender.sendMessage(Component.text("Failed to remove team from heat.", NamedTextColor.RED));
        }
    }

    @Subcommand("delete driver")
    @CommandCompletion("@heat @players")
    @CommandPermission("%permissionheat_removedriver")
    public static void onHeatRemoveDriver(Player sender, Heat heat, String playerName) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerName);
        if (tPlayer == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        if (heat.getDrivers().get(tPlayer.getUniqueId()) == null) {
            Text.send(sender, Error.PLAYER_NOT_FOUND);
            return;
        }
        if (heat.isRacing()) {
            if (heat.disqualifyDriver(heat.getDrivers().get(tPlayer.getUniqueId()))) {
                if (tPlayer.getPlayer() != null) {
                    if (tPlayer.getPlayer().getVehicle() != null && tPlayer.getPlayer().getVehicle() instanceof Boat boat) {
                        boat.remove();
                    }
                    Location loc = tPlayer.getPlayer().getBedSpawnLocation() == null ? tPlayer.getPlayer().getWorld().getSpawnLocation() : tPlayer.getPlayer().getBedSpawnLocation();
                    tPlayer.getPlayer().teleport(loc);
                }
                Text.send(sender, Success.DRIVER_DISQUALIFIED);
                return;
            }
           Text.send(sender, Error.FAILED_TO_DISQUALIFY_DRIVER);
        } else {
            boolean reload = false;
            if (heat.getHeatState() == HeatState.LOADED) {
                heat.resetHeat();
                reload = true;
            }
            if (heat.removeDriver(heat.getDrivers().get(tPlayer.getUniqueId()))) {
                boolean removeSpectator = true;
                for (Round round : heat.getEvent().getEventSchedule().getRounds()) {
                    for (Heat h : round.getHeats()) {
                        if (h.getDrivers().containsKey(tPlayer.getUniqueId())) {
                            removeSpectator = false;
                            break;
                        }
                    }
                }
                if (removeSpectator) {
                    heat.getEvent().removeSpectator(tPlayer.getUniqueId());
                }
                Text.send(sender, Success.DRIVER_REMOVED);
                if (reload) {
                    heat.loadHeat();
                }
                return;
            }
            Text.send(sender,Error.FAILED_TO_REMOVE_DRIVER);
        }
    }

    @Subcommand("quit")
    @CommandPermission("%permissionheat_quit")
    public static void onHeatDriverQuit(Player player) {
        if (EventDatabase.getDriverFromRunningHeat(player.getUniqueId()).isEmpty()) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        Driver driver = EventDatabase.getDriverFromRunningHeat(player.getUniqueId()).get();
        Heat heat = driver.getHeat();
        if (heat.getHeatState() == HeatState.LOADED) {
            heat.resetHeat();
            if (heat.removeDriver(heat.getDrivers().get(player.getUniqueId()))) {
                heat.getEvent().removeSpectator(player.getUniqueId());
            }
            heat.loadHeat();
        }

        if (driver.getState() == DriverState.LOADED && heat.getHeatState() != HeatState.LOADED) {
            Text.send(player, Error.NOT_NOW);
            return;
        }

        if (driver.getHeat().disqualifyDriver(driver)) {
            if (player.getVehicle() != null && player.getVehicle() instanceof Boat boat) {
                boat.remove();
            }
            Location loc = player.getBedSpawnLocation() == null ? player.getWorld().getSpawnLocation() : player.getBedSpawnLocation();
            player.teleport(loc);
            Text.send(player, Success.HEAT_ABORTED);
            return;
        }
        Text.send(player, Error.FAILED_TO_ABORT_HEAT);
    }

    @Subcommand("streakers")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_info")
    public static void onHeatStreakers(Player sender, Heat heat) {
        Text.send(sender, Info.STREAKER_MESSAGE_TITLE, "%heatname%", heat.getName());
        if (heat.getStreakers().isEmpty()) {
            Text.send(sender, Warning.NO_STREAKERS);
        } else {
            for (Streaker streaker : heat.getStreakers().values()) {
                Text.send(sender, Info.STREAKER_MESSAGE_INDIV, "%name%", streaker.getTPlayer().getName());
            }
        }
    }

    @Subcommand("add alldrivers")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_add_all")
    public static void onHeatAddDrivers(Player sender, Heat heat) {
        if (!heat.getRound().getRoundIndex().equals(heat.getEvent().getEventSchedule().getCurrentRound()) && heat.getRound().getRoundIndex() != 1) {
            Text.send(sender, Error.ADD_DRIVER_FUTURE_ROUND);
            return;
        }
        
        if (heat.isBoatSwitchingEnabled()) {
            sender.sendMessage(Component.text("Cannot add individual drivers to a boat switching heat. Use '/heat add team <team>' instead.", NamedTextColor.RED));
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (heat.getMaxDrivers() <= heat.getDrivers().size()) {
                Text.send(sender, Error.HEAT_FULL);
                return;
            }
            boolean inOtherHeat = false;
            for (Heat h : heat.getRound().getHeats()) {
                if (h.getDrivers().get(player.getUniqueId()) != null) {
                    inOtherHeat = true;
                    break;
                }
            }
            if (inOtherHeat) {
                continue;
            }
            if (heat.getDrivers().get(player.getUniqueId()) != null) {
                continue;
            }
            if (EventDatabase.heatDriverNew(player.getUniqueId(), heat, heat.getDrivers().size() + 1)) {
                continue;
            }
            if (heat.getHeatState() == HeatState.LOADED) {
                heat.addDriverToGrid(heat.getDrivers().get(player.getUniqueId()));
            }
        }
        Text.send(sender, Success.ADDED_ALL_DRIVERS);
    }


    @Subcommand("results")
    @CommandCompletion("@heat @players")
    @CommandPermission("%permissionheat_results")
    public static void onHeatResults(Player sender, Heat heat, @Optional String name) {
        Theme theme = TSDatabase.getPlayer(sender).getTheme();

        if (name != null) {
            TPlayer tPlayer = TSDatabase.getPlayer(name);
            if (tPlayer == null) {
                Text.send(sender, Error.PLAYER_NOT_FOUND);
                return;
            }
            if (heat.getDrivers().get(tPlayer.getUniqueId()) == null) {
                Text.send(sender, Error.PLAYER_NOT_FOUND);
                return;
            }
            Driver driver = heat.getDrivers().get(tPlayer.getUniqueId());
            Text.send(sender, Info.PLAYER_HEAT_RESULT_TITLE, "%player%", tPlayer.getName(), "%heat%", heat.getName());
            Text.send(sender, Info.PLAYER_HEAT_RESULT_POSITION, "%pos%", driver.getPosition().toString());
            Text.send(sender, Info.PLAYER_HEAT_RESULT_START_POSITION, "%pos%", String.valueOf(driver.getStartPosition()));

            var maybeBestLap = driver.getBestLap();
            maybeBestLap.ifPresent(lap -> Text.send(sender, Info.PLAYER_HEAT_RESULT_FASTEST_LAP, "%time%", ApiUtilities.formatAsTime(lap.getPreciseLapTime())));
            int count = 1;
            for (Lap l : driver.getLaps()) {
                String lap = "&2" + count + ": &1" + ApiUtilities.formatAsTime(l.getPreciseLapTime());
                if (l.equals(maybeBestLap.get())) {
                    lap += " &2(F)";
                }
                if (l.isPitted()) {
                    lap += " &2(P)";
                }
                sender.sendMessage(Text.get(sender, lap));
                count++;
            }
            return;
        }
        if (heat.getHeatState() == HeatState.FINISHED) {

            Text.send(sender, Info.HEAT_RESULT_TITLE, "%heat%", heat.getName());
            if (heat.getFastestLapUUID() != null) {
                Driver d = heat.getDrivers().get(heat.getFastestLapUUID());
                var bestLap = ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime());
                Text.send(sender, Info.HEAT_INFO_FASTEST_LAP, "%time%", bestLap, "%player%", d.getTPlayer().getName());
            }
            List<Driver> result = EventResults.generateHeatResults(heat);
            if (heat.getRound() instanceof FinalRound) {
                for (Driver d : result) {
                    Text.send(sender, Broadcast.HEAT_RESULT_ROW, "%pos%", String.valueOf(d.getPosition() ), "%player%", d.getTPlayer().getName(), "%laps%", String.valueOf(d.getLaps().size()), "%time%", ApiUtilities.formatAsTime(d.getFinishTime()));

                }
            } else {
                for (Driver d : result) {
                    sender.sendMessage(theme.primary(d.getPosition() + ".").append(Component.space()).append(theme.highlight(d.getTPlayer().getName())).append(theme.hyphen()).append(theme.highlight(d.getBestLap().isPresent() ? ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime()) : "0")));
                }
            }
        } else {
            Text.send(sender, Error.NOT_NOW);
        }
    }

    @Subcommand("sort tt")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_sort_tt")
    public static void onSortByTT(Player player, Heat heat) {
        if (heat.getHeatState() == HeatState.FINISHED) {
            Text.send(player, Error.NOT_NOW);
            return;
        }
        if (heat.isRacing()) {
            Text.send(player, Error.HEAT_ALREADY_STARTED);
            return;
        }
        if (heat.getStartPositions().isEmpty()) {
            Text.send(player, Error.NOT_NOW);
            return;
        }
        if (!Objects.equals(heat.getRound().getRoundIndex(), heat.getEvent().getEventSchedule().getCurrentRound()) && heat.getRound().getRoundIndex() != 1) {
            Text.send(player, Error.SORT_DRIVERS_FUTURE_ROUND);
            return;
        }

        List<TimeTrialFinish> driversWithBestTimes = heat.getEvent().getTrack().getTimeTrials().getTopList().stream().filter(tt -> heat.getDrivers().containsKey(tt.getPlayer().getUniqueId())).toList();
        List<Driver> allDrivers = new ArrayList<>(heat.getStartPositions());
        List<Driver> noTT = new ArrayList<>();

        int i = 1;
        for (Driver driver : allDrivers) {
            boolean match = false;
            for (TimeTrialFinish finish : driversWithBestTimes) {
                if (finish.getPlayer() == driver.getTPlayer()) {
                    heat.setDriverPosition(driver, driversWithBestTimes.indexOf(finish) + 1);
                    i++;
                    match = true;
                    break;
                }
            }
            if (!match) {
                noTT.add(driver);
            }
        }

        for (Driver driver : noTT) {
            heat.setDriverPosition(driver, i);
            i++;
        }

        if (heat.getHeatState() == HeatState.LOADED) {
            heat.reloadHeat();
        }

        Text.send(player, Success.HEAT_SORTED_BY_TIME);
    }

    @Subcommand("sort random")
    @CommandCompletion("@heat")
    @CommandPermission("%permissionheat_sort_random")
    public static void onSortByRandom(Player player, Heat heat) {
        if (heat.getHeatState() == HeatState.FINISHED) {
            Text.send(player, Error.NOT_NOW);
            return;
        }
        if (heat.isRacing()) {
            Text.send(player, Error.HEAT_ALREADY_STARTED);
            return;
        }
        if (heat.getStartPositions().isEmpty()) {
            Text.send(player, Error.NOT_NOW);
            return;
        }
        if (!Objects.equals(heat.getRound().getRoundIndex(), heat.getEvent().getEventSchedule().getCurrentRound()) && heat.getRound().getRoundIndex() != 1) {
            Text.send(player, Error.SORT_DRIVERS_FUTURE_ROUND);
            return;
        }

        List<Driver> randomDrivers = new ArrayList<>(heat.getStartPositions());
        Collections.shuffle(randomDrivers);

        for (int i = 0; i < randomDrivers.size(); i++) {
            heat.setDriverPosition(randomDrivers.get(i), i + 1);
        }

        if (heat.getHeatState() == HeatState.LOADED) {
            heat.reloadHeat();
        }

        Text.send(player, Success.HEAT_SORTED_BY_RANDOM);
    }

    private static boolean getParsedRemoveFlag(String index) {
        return index.startsWith("-");
    }

    private static boolean getParsedAddFlag(String index) {
        return index.startsWith("+");
    }

    private static Integer getParsedIndex(String index) {
        if (index.startsWith("-")) {
            index = index.substring(1);
        } else if (index.startsWith("+")) {
            index = index.substring(1);
        }
        try {
            return Integer.parseInt(index);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

