package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.api.events.driver.DriverScoreboardTitleUpdateEvent;
import me.makkuusen.timing.system.api.events.driver.SpectatorScoreboardTitleUpdateEvent;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Spectator;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static me.makkuusen.timing.system.loneliness.LonelinessController.isGhosted;

public class SpectatorScoreboard {

    private final Heat heat;

    public SpectatorScoreboard(Heat heat) {
        this.heat = heat;
    }

    public void updateScoreboard() {
        for (Spectator spec : heat.getEvent().getSpectators().values()) {
            Driver driver = heat.getDrivers().get(spec.getTPlayer().getUniqueId());
            if (driver == null) {
                updateScoreBoard(spec);
            } else if (driver.isDisqualified()) {
                updateScoreBoard(spec);
            }
        }
    }

    private void updateScoreBoard(Spectator spec) {
        if (spec.getTPlayer().getPlayer() != null) {
            spec.getTPlayer().initScoreboard();
            List<Component> lines;
            lines = normalScoreboard(spec.getTPlayer());
            setTitle(spec.getTPlayer());
            spec.getTPlayer().setScoreBoardLines(lines);
        }
    }

    public void setTitle(TPlayer tPlayer) {
        String eventName;
        if (tPlayer.getSettings().getCompactScoreboard() && heat.getEvent().getDisplayName().length() > 8) {
            eventName = heat.getEvent().getDisplayName().substring(0, 8);
        } else {
            eventName = heat.getEvent().getDisplayName();
        }

        tPlayer.setScoreBoardTitle(Component.text(heat.getName() + " | " + eventName).color(ScoreboardUtils.getPrimaryColor(tPlayer.getTheme())).decorate(TextDecoration.BOLD));

        SpectatorScoreboardTitleUpdateEvent titleUpdateEvent = new SpectatorScoreboardTitleUpdateEvent(tPlayer.getPlayer(), eventName);
        Bukkit.getServer().getPluginManager().callEvent(titleUpdateEvent);
    }

    public void removeScoreboards() {
        for (Spectator spec : heat.getEvent().getSpectators().values()) {
            spec.getTPlayer().clearScoreboard();
        }
    }

    public List<Component> normalScoreboard(TPlayer tPlayer) {
        List<Component> lines = new ArrayList<>();
        int count = 0;
        int last = TimingSystem.configuration.getScoreboardMaxRows();
        Driver prevDriver = null;
        boolean compareToFirst = true;
        for (Driver driver : heat.getLivePositions()) {
            count++;
            if (count > last) {
                break;
            }
            if (heat.getRound() instanceof QualificationRound) {
                lines.add(getDriverRowQualification(driver, prevDriver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme()));
                if (compareToFirst) {
                    prevDriver = driver;
                    compareToFirst = false;
                }
            } else if (isGhosted(driver.getTPlayer().getUniqueId())) {
                Component row = getDriverRowFinal(driver, prevDriver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme());
                row = Component.text("👻 ").append(row);
                lines.add(row.color(TextColor.color(0xAAAAAA)));
                prevDriver = driver;
            } else {
                lines.add(getDriverRowFinal(driver, prevDriver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme()));
                prevDriver = driver;
            }
        }
        return lines;
    }

    private Component getDriverRowFinal(Driver driver, Driver comparingDriver, boolean compact, Theme theme) {


        if (driver.isDisqualified()) {
            return ScoreboardUtils.getDriverLineRaceDNF(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        if (driver.getTPlayer().getPlayer() == null) {
            return ScoreboardUtils.getDriverLineRaceOffline(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        if (driver.getLaps().isEmpty()) {
            return ScoreboardUtils.getDriverLineRace(driver, driver.getPosition(), compact, theme);
        }
        long timeDiff;

        Location playerLoc = driver.getTPlayer().getPlayer().getLocation();

        var inPitRegions = heat.getEvent().getTrack().getTrackRegions().getRegions(TrackRegion.RegionType.INPIT);
        for (TrackRegion trackRegion : inPitRegions) {
            if (trackRegion.contains(playerLoc)) {
                return ScoreboardUtils.getDriverLineRaceInPit(driver, driver.getPits(), driver.getPosition(), compact, theme);
            }
        }

        if (comparingDriver == null) {
            return ScoreboardUtils.getDriverLineRaceLaps(driver.getLaps().size(), driver, driver.getPits(), driver.getPosition(), compact, theme);
        }
        if (driver.isFinished()) {
            timeDiff = Duration.between(comparingDriver.getEndTime(), driver.getEndTime()).toMillis();
            return ScoreboardUtils.getDriverLineRaceGap(timeDiff, driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        Instant timeStamp = driver.getTimeStamp(driver.getLaps().size(), driver.getCurrentLap().getLatestCheckpoint());
        Instant fasterTimeStamp = comparingDriver.getTimeStamp(driver.getLaps().size(), driver.getCurrentLap().getLatestCheckpoint());
        timeDiff = Duration.between(fasterTimeStamp, timeStamp).toMillis();
        if (timeDiff < 0) {
            return ScoreboardUtils.getDriverLineNegativeRaceGap(timeDiff * -1, driver, driver.getPits(), driver.getPosition(), compact, theme);
        }
        return ScoreboardUtils.getDriverLineRaceGap(timeDiff, driver, driver.getPits(), driver.getPosition(), compact, theme);
    }

    private Component getDriverRowQualification(Driver driver, Driver comparingDriver, boolean compact, Theme theme) {
        if (driver.getBestLap().isEmpty()) {
            return ScoreboardUtils.getDriverLine(driver, driver.getPosition(), compact, theme);
        }

        if (comparingDriver == null) {
            return ScoreboardUtils.getDriverLineQualyTime(driver.getBestLap().get().getLapTime(), driver, driver.getPosition(), compact, theme);
        }

        long timeDiff = driver.getBestLap().get().getLapTime() - comparingDriver.getBestLap().get().getLapTime();
        if (timeDiff < 0) {
            return ScoreboardUtils.getDriverLineNegativeQualyGap(timeDiff * -1, driver, driver.getPosition(), compact, theme);
        } else if (timeDiff == 0) {
            return ScoreboardUtils.getDriverLineEqualQualyGap(timeDiff, driver, driver.getPosition(), compact, theme);
        }
        return ScoreboardUtils.getDriverLineQualyGap(timeDiff, driver, driver.getPosition(), compact, theme);
    }
}
