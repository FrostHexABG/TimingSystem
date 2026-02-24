package me.makkuusen.timing.system.event;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.heat.QualifyHeat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Participant;
import me.makkuusen.timing.system.participant.Spectator;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Broadcast;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class EventAnnouncements {

    private static void broadcastAnnouncement(Heat heat, Broadcast key, String... replacements) {

        for (Participant p : heat.getParticipants()) {
            if (p.getTPlayer().getPlayer() != null) {
                Text.send(p.getTPlayer().getPlayer(), key, replacements);
            }
        }
    }

    public static void broadcastSpectate(Event event) {
        Bukkit.getOnlinePlayers().stream().filter(player -> !event.getSpectators().containsKey(player.getUniqueId())).forEach(player -> {
            player.sendMessage(Component.empty());
            player.sendMessage(Text.get(player, Broadcast.CLICK_TO_SPECTATE_EVENT, "%event%", event.getDisplayName()).clickEvent(ClickEvent.runCommand("/event spectate " + event.getDisplayName())));
            player.sendMessage(Component.empty());
        });

    }

    public static void broadcastFinish(Heat heat, Driver driver, long time) {
        broadcastAnnouncement(heat, Broadcast.EVENT_PLAYER_FINISH, "%player%", driver.getTPlayer().getName(), "%position%", String.valueOf(driver.getPosition()), "%time%", ApiUtilities.formatAsTime(time));
    }

    public static void broadcastFinishQualification(Heat heat, Driver driver) {
        broadcastAnnouncement(heat, Broadcast.EVENT_PLAYER_FINISH_BASIC, "%player%", driver.getTPlayer().getName());
    }

    public static void broadcastPit(Heat heat, Driver driver, int pit) {
        broadcastAnnouncement(heat, Broadcast.EVENT_PLAYER_PIT, "%player%", driver.getTPlayer().getName(), "%pit%", String.valueOf(pit));
    }

    public static void broadcastFastestLap(Heat heat, Driver driver, Lap time, Optional<Lap> oldBest) {
        if (heat.getRound() instanceof QualificationRound) {
            for (Participant p : heat.getParticipants()) {
                if (p.getTPlayer().getPlayer() != null) {
                    Player player = p.getTPlayer().getPlayer();
                    Component delta = Component.empty();
                    if (oldBest.isPresent()) {
                        delta = QualifyHeat.getBestLapDelta(Theme.getTheme(player), time, oldBest.get());
                    }
                    player.sendMessage(Text.get(player, Broadcast.EVENT_PLAYER_FASTEST_LAP, "%player%", driver.getTPlayer().getName(), "%time%", ApiUtilities.formatAsTime(time.getPreciseLapTime())).append(delta));
                }
            }
        } else {
            broadcastAnnouncement(heat, Broadcast.EVENT_PLAYER_FASTEST_LAP, "%player%", driver.getTPlayer().getName(), "%time%", ApiUtilities.formatAsTime(time.getPreciseLapTime()));
        }
    }

    public static void broadcastQualifyingLap(Heat heat, Driver driver, Lap time, Optional<Lap> oldBest) {
        for (Participant p : heat.getParticipants()) {
            if (p.getTPlayer().getPlayer() == null) continue;
            Player player = p.getTPlayer().getPlayer();
            Component delta = Component.empty();
            if (oldBest.isPresent()) {
                delta = QualifyHeat.getBestLapDelta(Theme.getTheme(player), time, oldBest.get());
            }
            player.sendMessage(Text.get(player, Broadcast.EVENT_PLAYER_FINISHED_QUALIFICATION_LAP, "%player%", driver.getTPlayer().getName(), "%time%", ApiUtilities.formatAsTime(time.getPreciseLapTime())).append(delta));
        }
    }

    public static void broadcastLapTime(Heat heat, Driver driver, long time) {
        if (driver.getTPlayer().getPlayer() == null) return;
        Text.send(driver.getTPlayer().getPlayer(), Broadcast.EVENT_PLAYER_FINISHED_LAP, "%time%", ApiUtilities.formatAsTime(time));
        for(Participant p : heat.getParticipants().stream().filter(participant -> participant.getTPlayer().getPlayer() != null && participant.getTPlayer() != driver.getTPlayer() && participant.getTPlayer().getSettings().isSendFinalLaps()).toList()) {
            Text.send(p.getTPlayer().getPlayer(), Broadcast.EVENT_PLAYER_FINISHED_LAP_ANNOUNCE, "%player%", driver.getTPlayer().getName(), "%lap%", String.valueOf(driver.getLaps().size()), "%time%", ApiUtilities.formatAsTime(time));
        }
    }

    public static void broadcastQualificationResults(Event event, List<Driver> drivers) {
        for (Spectator s : event.getSpectators().values()) {
            Player player = s.getTPlayer().getPlayer();
            if (player != null) {
                Text.send(player, Broadcast.EVENT_RESULTS_QUALIFICATION, "%event%", event.getDisplayName());

                int pos = 1;
                for (Driver d : drivers) {
                    player.sendMessage(Text.get(player, "&1" + pos++ + ". &2" + d.getTPlayer().getName() + "&1 - &2" + (d.getBestLap().isPresent() ? ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime()) : "-")));
                }
            }
        }
    }

    public static void broadcastFinalResults(Event event, List<Driver> drivers) {
        for (Spectator s : event.getSpectators().values()) {
            if (s.getTPlayer().getPlayer() != null) {
                Player player = s.getTPlayer().getPlayer();
                Text.send(player, Broadcast.EVENT_RESULTS, "%event%", event.getDisplayName());
                int pos = 1;
                for (Driver d : drivers) {
                    if (d.isFinished()) {
                        Text.send(player, Broadcast.HEAT_RESULT_ROW, "%pos%", String.valueOf(pos++), "%player%", d.getTPlayer().getName(), "%laps%", String.valueOf(d.getLaps().size()), "%time%", ApiUtilities.formatAsTime(d.getFinishTime()));
                    } else {
                        player.sendMessage(Text.get(player, "&1" + pos++ + ". &2" + d.getTPlayer().getName()));
                    }
                }
            }
        }
    }

    public static void broadcastHeatResult(List<Driver> drivers, Heat heat) {
        for (Spectator s : heat.getEvent().getSpectators().values()) {
            if (s.getTPlayer().getPlayer() != null) {
                Player player = s.getTPlayer().getPlayer();
                Text.send(player, Broadcast.HEAT_RESULTS, "%heat%", heat.getName());
                int pos = 1;
                for (Driver d : drivers) {
                    if (heat.getRound() instanceof QualificationRound) {
                        player.sendMessage(Text.get(player, "&1" + pos++ + ". &2" + d.getTPlayer().getName() + "&1 - &2" + (d.getBestLap().isPresent() ? ApiUtilities.formatAsTime(d.getBestLap().get().getPreciseLapTime()) : "-")));
                    } else {
                        Text.send(player, Broadcast.HEAT_RESULT_ROW, "%pos%", String.valueOf(pos++), "%player%", d.getTPlayer().getName(), "%laps%", String.valueOf(d.getLaps().size()), "%time%", ApiUtilities.formatAsTime(d.getFinishTime()));
                    }
                }
            }
        }
    }

    public static void broadcastCountdown(Heat heat, Integer count) {
        for (Participant participant : heat.getParticipants()) {
            Player player = participant.getTPlayer().getPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 1, 1);
                Component mainTitle = Component.text(count, NamedTextColor.WHITE);
                Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(100));
                Title title = Title.title(mainTitle, Component.empty(), times);
                player.showTitle(title);
            }
        }
    }

    public static void broadcastReset(Heat heat) {
        for (Participant participant : heat.getParticipants()) {
            Player player = participant.getTPlayer().getPlayer();
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.MASTER, 1, 1);
                Component mainTitle = Text.get(player, Broadcast.HEAT_RESET);
                Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(100));
                Title title = Title.title(mainTitle, Component.empty(), times);
                player.showTitle(title);
            }

        }
    }

    public static void broadcastPlayerSigned(String name, Event event) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Only send to admins that have the event selected.
            EventDatabase.getPlayerSelectedEvent(p.getUniqueId()).ifPresent(e -> {
                if (e.getId() == event.getId() && p.hasPermission("timingsystem.packs.eventadmin")) {
                    Text.send(p, Broadcast.PLAYER_SIGNED_EVENT, "%player%", name);
                }
            });
        }
    }

    public static void broadcastPlayerSignedReserve(String name, Event event) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Only send to admins that have the event selected.
            EventDatabase.getPlayerSelectedEvent(p.getUniqueId()).ifPresent(e -> {
                if (e.getId() == event.getId() && p.hasPermission("timingsystem.packs.eventadmin")) {
                    Text.send(p, Broadcast.PLAYER_RESERVE_EVENT, "%player%", name);
                }
            });
        }
    }


    public static void sendFinishSound(Driver raceDriver) {
        if (raceDriver.getTPlayer().getPlayer() == null) {
            return;
        }
        Player player = raceDriver.getTPlayer().getPlayer();
        if (raceDriver.getTPlayer().getSettings().isSound()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1, 1);
        }
    }

    public static void sendFinishTitle(Driver driver) {
        if (driver.getTPlayer().getPlayer() == null) {
            return;
        }
        Player player = driver.getTPlayer().getPlayer();
        Component mainTitle = Text.get(player, Broadcast.HEAT_FINISH_TITLE_POS, "%pos%", String.valueOf( driver.getPosition()));
        Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(100));
        Title title = Title.title(mainTitle, Component.empty(), times);
        player.showTitle(title);
    }

    public static void sendFinishTitleQualification(Driver driver) {
        if (driver.getTPlayer().getPlayer() == null) {
            return;
        }
        Player player = driver.getTPlayer().getPlayer();
        Component mainTitle = Text.get(player, Broadcast.HEAT_FINISH_TITLE);
            Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(100));
        Title title = Title.title(mainTitle, Component.empty(), times);
        player.showTitle(title);
    }
}
