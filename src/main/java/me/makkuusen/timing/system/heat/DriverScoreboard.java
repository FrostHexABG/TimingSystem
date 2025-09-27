package me.makkuusen.timing.system.heat;

import me.makkuusen.timing.system.api.events.BoatSpawnEvent;
import me.makkuusen.timing.system.api.events.driver.DriverScoreboardTitleUpdateEvent;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Theme;
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

public class DriverScoreboard {
    TPlayer tPlayer;
    Driver driver;
    Heat heat;

    public DriverScoreboard(TPlayer tPlayer, Driver driver) {
        this.tPlayer = tPlayer;
        this.driver = driver;
        heat = driver.getHeat();
        setTitle();
    }

    public void setTitle() {
        String eventName;
        if (tPlayer.getSettings().getCompactScoreboard() && heat.getEvent().getDisplayName().length() > 8) {
            eventName = heat.getEvent().getDisplayName().substring(0, 8);
        } else {
            eventName = heat.getEvent().getDisplayName();
        }

        tPlayer.setScoreBoardTitle(Component.text(heat.getName() + " | " + eventName).color(ScoreboardUtils.getPrimaryColor(tPlayer.getTheme())).decorate(TextDecoration.BOLD));

        DriverScoreboardTitleUpdateEvent titleUpdateEvent = new DriverScoreboardTitleUpdateEvent(tPlayer.getPlayer(), eventName);
        Bukkit.getServer().getPluginManager().callEvent(titleUpdateEvent);
    }

    public void removeScoreboard() {
        tPlayer.clearScoreboard();
    }

    public void setDriverLines() {
        setTitle();
        setLines();
    }

    public void setLines() {
        List<Component> lines;
        int pos = driver.getPosition();
        if (pos > 12 && heat.getLivePositions().size() > 15) {
            lines = individualScoreboard();
        } else {
            lines = normalScoreboard();
        }
        tPlayer.setScoreBoardLines(lines);
    }

    public List<Component> individualScoreboard() {
        List<Component> lines = new ArrayList<>();
        int count = 0;
        int last = Math.min(driver.getPosition() + 7, heat.getDrivers().size());
        int first = last - 14;
        last = first + TimingSystem.configuration.getScoreboardMaxRows();
        for (Driver driver : heat.getLivePositions()) {
            count++;
            if (count < first) {
                continue;
            } else if (count > last) {
                break;
            }
            lineDecider(lines, driver);
        }
        return lines;
    }

    public List<Component> normalScoreboard() {
        List<Component> lines = new ArrayList<>();
        int count = 0;
        int last = TimingSystem.configuration.getScoreboardMaxRows();
        for (Driver driver : heat.getLivePositions()) {
            count++;
            if (count > last) {
                break;
            }
            lineDecider(lines, driver);
        }
        return lines;
    }

    private void lineDecider(List<Component> lines, Driver driver) {
        if (heat.getRound() instanceof QualificationRound) {
            lines.add(getDriverRowQualification(driver, this.driver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme()));
        } else if (isGhosted(driver.getTPlayer().getUniqueId())) {
            Component row = getDriverRowFinal(driver, this.driver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme());
            row = Component.text("👻 ").append(row);
            lines.add(row.color(TextColor.color(0xAAAAAA)));
        } else {
            lines.add(getDriverRowFinal(driver, this.driver, tPlayer.getSettings().getCompactScoreboard(), tPlayer.getTheme()));
        }
    }

    private Component getDriverRowFinal(Driver driver, Driver comparingDriver, boolean compact, Theme theme) {
        if (driver.getLaps().isEmpty()) {
            return ScoreboardUtils.getDriverLineRace(driver, driver.getPosition(), compact, theme);
        }
        long timeDiff;

        if (driver.isDisqualified()) {
            return ScoreboardUtils.getDriverLineRaceDNF(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        if (driver.getTPlayer().getPlayer() == null) {
            return ScoreboardUtils.getDriverLineRaceOffline(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }
        Location playerLoc = driver.getTPlayer().getPlayer().getLocation();

        if (driver.isInPit(playerLoc)) {
            return ScoreboardUtils.getDriverLineRaceInPit(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        if (driver.getPosition() < comparingDriver.getPosition()) {

            if (comparingDriver.isFinished()) {
                timeDiff = Duration.between(driver.getEndTime(), comparingDriver.getEndTime()).toMillis();
                return ScoreboardUtils.getDriverLineNegativeRaceGap(timeDiff, driver, driver.getPits(), driver.getPosition(), compact, theme);
            }

            if (!comparingDriver.getLaps().isEmpty() && comparingDriver.getCurrentLap() != null) {
                Instant timeStamp = comparingDriver.getTimeStamp(comparingDriver.getLaps().size(), comparingDriver.getCurrentLap().getLatestCheckpoint());
                Instant fasterTimeStamp = driver.getTimeStamp(comparingDriver.getLaps().size(), comparingDriver.getCurrentLap().getLatestCheckpoint());
                timeDiff = Duration.between(fasterTimeStamp, timeStamp).toMillis();
                if (timeDiff < 0) {
                    return ScoreboardUtils.getDriverLineRaceGap(timeDiff * -1, driver, driver.getPits(), driver.getPosition(), compact, theme);
                }
                return ScoreboardUtils.getDriverLineNegativeRaceGap(timeDiff, driver, driver.getPits(), driver.getPosition(), compact, theme);
            }
            return ScoreboardUtils.getDriverLineRace(driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        if (driver.getPosition() > comparingDriver.getPosition()) {
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

            if (timeDiff == 0) {
                return ScoreboardUtils.getDriverLineEqualRaceGap(driver, driver.getPits(), driver.getPosition(), compact, theme);
            }

            return ScoreboardUtils.getDriverLineRaceGap(timeDiff, driver, driver.getPits(), driver.getPosition(), compact, theme);
        }

        return ScoreboardUtils.getDriverLineRace(driver, driver.getPits(), driver.getPosition(), compact, theme);
    }

    private Component getDriverRowQualification(Driver driver, Driver comparingDriver, boolean compact, Theme theme) {
        if (driver.getBestLap().isEmpty()) {
            return ScoreboardUtils.getDriverLine(driver, driver.getPosition(), compact, theme);
        }

        if (comparingDriver.getBestLap().isEmpty()) {
            return ScoreboardUtils.getDriverLineQualyTime(driver.getBestLap().get().getLapTime(), driver, driver.getPosition(), compact, theme);
        }

        if (comparingDriver.equals(driver)) {
            return ScoreboardUtils.getDriverLineQualyTime(driver.getBestLap().get().getLapTime(), driver, driver.getPosition(), compact, theme);
        }

        long timeDiff = driver.getBestLap().get().getLapTime() - comparingDriver.getBestLap().get().getLapTime();
        if (timeDiff < 0) {
            return ScoreboardUtils.getDriverLineNegativeQualyGap(timeDiff * -1, driver, driver.getPosition(), compact, theme);
        }

        if (timeDiff == 0) {
            return ScoreboardUtils.getDriverLineEqualQualyGap(timeDiff, driver, driver.getPosition(), compact, theme);
        }

        return ScoreboardUtils.getDriverLineQualyGap(timeDiff, driver, driver.getPosition(), compact, theme);
    }
}


