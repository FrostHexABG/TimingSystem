package me.makkuusen.timing.system.heat;

import co.aikar.idb.DbRow;
import co.aikar.taskchain.TaskChain;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.events.HeatFinishEvent;
import me.makkuusen.timing.system.api.events.driver.DriverPlacedOnGrid;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventAnnouncements;
import me.makkuusen.timing.system.event.EventResults;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.participant.Participant;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class Heat {

    private int id;
    private Event event;
    private Round round;
    private Integer heatNumber;
    private Instant startTime;
    private Instant endTime;
    private HeatState heatState;
    private HashMap<UUID, Driver> drivers = new HashMap<>();
    private GridManager gridManager;
    private List<Driver> startPositions = new ArrayList<>();
    private List<Driver> livePositions = new ArrayList<>();
    private UUID fastestLapUUID;
    private Integer timeLimit;
    private Integer totalLaps;
    private Integer totalPits;
    private Integer startDelay;
    private Integer maxDrivers;
    private Boolean lonely;
    private Boolean reset;
    private Boolean lapReset;
    private SpectatorScoreboard scoreboard;
    private Instant lastScoreboardUpdate = Instant.now();

    public Heat(DbRow data, Round round) {
        id = data.getInt("id");
        this.event = round.getEvent();
        this.round = round;
        heatState = HeatState.valueOf(data.getString("state"));
        heatNumber = data.getInt("heatNumber");
        startTime = data.getLong("startTime") == null ? null : Instant.ofEpochMilli(data.getLong("startTime"));
        endTime = data.getLong("endTime") == null ? null : Instant.ofEpochMilli(data.getLong("endTime"));
        timeLimit = data.get("timeLimit") == null ? null : data.getInt("timeLimit");
        totalLaps = data.get("totalLaps") == null ? null : data.getInt("totalLaps");
        totalPits = data.get("totalPitstops") == null ? null : data.getInt("totalPitstops");
        maxDrivers = data.get("maxDrivers") == null ? null : data.getInt("maxDrivers");
        lonely = data.get("lonely") instanceof Boolean ? data.get("lonely") : data.get("lonely").equals(1);
        reset = data.get("canReset") instanceof Boolean ? data.get("canReset") : data.get("canReset").equals(1);
        lapReset = data.get("lapReset") instanceof Boolean ? data.get("lapReset") : data.get("lapReset").equals(1);
        startDelay = data.get("startDelay") == null ? round instanceof FinalRound ? TimingSystem.configuration.getFinalStartDelayInMS() : TimingSystem.configuration.getQualyStartDelayInMS() : data.getInt("startDelay");
        fastestLapUUID = data.getString("fastestLapUUID") == null ? null : UUID.fromString(data.getString("fastestLapUUID"));
        gridManager = new GridManager(round instanceof QualificationRound);
    }

    public String getName() {
        if (round instanceof QualificationRound) {
            return "R" + round.getRoundIndex() + "Q" + getHeatNumber();
        } else {
            return "R" + round.getRoundIndex() + "F" + getHeatNumber();
        }
    }

    public boolean loadHeat() {
        if (event.getEventSchedule().getCurrentRound() != round.getRoundIndex()) {
            if (round.getRoundIndex() == 1) {
                event.start();
            } else {
                return false;
            }

        }

        if (getHeatState() != HeatState.SETUP) {
            return false;
        }

        if (getEvent().getTrack().getTrackLocations().getLocations(TrackLocation.Type.GRID).isEmpty()) {
            return false;
        }

        for (Driver driver : getStartPositions()) {
            setDriverOnGrid(driver);
        }

        updateStartingLivePositions();
        setHeatState(HeatState.LOADED);
        scoreboard = new SpectatorScoreboard(this);
        updateScoreboard();
        return true;
    }

    public void addDriverToGrid(Driver driver) {
        setDriverOnGrid(driver);
        updateStartingLivePositions();
        getStartPositions().forEach(Driver::updateScoreboard);
    }

    private void setDriverOnGrid(Driver driver) {
        DriverPlacedOnGrid event = new DriverPlacedOnGrid(driver, this);
        Bukkit.getServer().getPluginManager().callEvent(event);
        gridManager.putDriverOnGrid(driver, getEvent().getTrack());
        EventDatabase.addPlayerToRunningHeat(driver);
    }

    private void updateStartingLivePositions() {
        List<Driver> pos = new ArrayList<>(getStartPositions());
        setLivePositions(pos);
    }

    public void reloadHeat() {
        if (!resetHeat()) {
            return;
        }
        loadHeat();
    }

    public boolean startCountdown() {
        if (getHeatState() != HeatState.LOADED) {
            return false;
        }
        setHeatState(HeatState.STARTING);
        countdown(5);

        return true;
    }

    public boolean startCountdown(int length) {
        if (getHeatState() != HeatState.LOADED) {
            return false;
        }
        setHeatState(HeatState.STARTING);
        countdown(length);

        return true;
    }

    public void countdown(int count) {
        TaskChain<?> chain = TimingSystem.newChain();
        for (int i = count; i > 0; i--) {
            int finalI = i;
            chain.sync(() -> EventAnnouncements.broadcastCountdown(this, finalI)).delay(20);
        }
        chain.execute((finished) -> startHeat());
    }

    public void startHeat() {
        setHeatState(HeatState.RACING);
        updateScoreboard();
        setStartTime(TimingSystem.currentTime);
        if (round instanceof QualificationRound) {
            gridManager.startDriversWithDelay(getStartDelay(), true, getStartPositions());
            return;
        }
        getDrivers().values().forEach(driver -> driver.setStartTime(TimingSystem.currentTime));
        gridManager.startDriversWithDelay(getStartDelay(), false, getStartPositions());
    }

    public void passLap(Driver driver) {
        if (round instanceof QualificationRound) {
            QualifyHeat.passQualyLap(driver);
        } else {
            FinalHeat.passLap(driver);
        }
    }

    public boolean finishHeat() {
        if (getHeatState() != HeatState.RACING) {
            return false;
        }
        updatePositions();
        setHeatState(HeatState.FINISHED);
        setEndTime(TimingSystem.currentTime);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(TimingSystem.getPlugin(), () -> {
            getDrivers().values().forEach(Driver::removeScoreboard);
            scoreboard.removeScoreboards();
            ApiUtilities.msgConsole("CLEARED SCOREBOARDS");
        }, 60);

        getDrivers().values().forEach(driver -> {
            EventDatabase.removePlayerFromRunningHeat(driver.getTPlayer().getUniqueId());
            if (driver.getEndTime() == null) {
                driver.removeUnfinishedLap();
                if (!driver.getLaps().isEmpty()) {
                    driver.setEndTime(driver.getCurrentLap().getLapEnd());
                } else {
                    driver.setEndTime(TimingSystem.currentTime);
                }
                driver.setState(DriverState.FINISHED);
                LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
                if (!LonelinessController.unghost(driver.getTPlayer().getUniqueId())) {
                    LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
                }
            }
            LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
            if (!LonelinessController.unghost(driver.getTPlayer().getUniqueId())) {
                LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
            }
        });

        //Dump all laps to database
        getDrivers().values().forEach(driver -> driver.getLaps().forEach(EventDatabase::lapNew));

        var heatResults = EventResults.generateHeatResults(this);
        EventAnnouncements.broadcastHeatResult(heatResults, this);

        HeatFinishEvent finishEvent = new HeatFinishEvent(this);
        Bukkit.getServer().getPluginManager().callEvent(finishEvent);

        return true;
    }

    public boolean isFinished() {
        return heatState == HeatState.FINISHED;
    }

    public void updatePositions() {
        Collections.sort(getLivePositions());
        int pos = 1;
        for (Driver rd : getLivePositions()) {
            rd.setPosition(pos++);
        }
        updateScoreboard();
    }

    public boolean resetHeat() {
        if (getHeatState() == HeatState.FINISHED) {
            return false;
        }
        setHeatState(HeatState.SETUP);
        setStartTime(null);
        setEndTime(null);
        setFastestLapUUID(null);
        setLivePositions(new ArrayList<>());
        gridManager.clearArmorstands();
        getDrivers().values().forEach(driver -> {
            driver.reset();
            EventDatabase.removePlayerFromRunningHeat(driver.getTPlayer().getUniqueId());
            LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
            if (!LonelinessController.unghost(driver.getTPlayer().getUniqueId())) {
                LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
            }
        });
        if (scoreboard != null) {
            scoreboard.removeScoreboards();
        }
        ApiUtilities.msgConsole("CLEARED SCOREBOARDS");
        return true;
    }

    public Integer getMaxDrivers() {
        if (maxDrivers != null) {
            return maxDrivers;
        }
        if (round instanceof QualificationRound && !getEvent().getTrack().getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).isEmpty()) {
            return getEvent().getTrack().getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).size();
        }
        return getEvent().getTrack().getTrackLocations().getLocations(TrackLocation.Type.GRID).size();
    }

    public void setMaxDrivers(int maxDrivers) {
        this.maxDrivers = maxDrivers;
        TimingSystem.getEventDatabase().heatSet(id, "maxDrivers", maxDrivers);
    }

    public void addDriver(Driver driver) {
        drivers.put(driver.getTPlayer().getUniqueId(), driver);
        if (driver.getStartPosition() > 0) {
            startPositions.add(driver);
            startPositions.sort(Comparator.comparingInt(Driver::getStartPosition));
        }
        if (!event.getSpectators().containsKey(driver.getTPlayer().getUniqueId())) {
            event.addSpectator(driver.getTPlayer().getUniqueId());
        }
        if (!event.getSubscribers().containsKey(driver.getTPlayer().getUniqueId()) && !event.getReserves().containsKey(driver.getTPlayer().getUniqueId())) {
            event.addSubscriber(driver.getTPlayer());
        }
    }

    public boolean removeDriver(Driver driver) {
        if (driver.getHeat().getHeatState() != HeatState.SETUP && driver.getHeat().getHeatState() != HeatState.LOADED) {
            return false;
        }
        TimingSystem.getEventDatabase().driverSet(driver.getId(), "isRemoved", 1);
        drivers.remove(driver.getTPlayer().getUniqueId());
        startPositions.remove(driver);
        int startPos = driver.getStartPosition();
        for (Driver d : startPositions) {
            if (d.getStartPosition() > startPos) {
                d.setStartPosition(d.getStartPosition() - 1);
                d.setPosition(d.getPosition() - 1);
            }
        }
        LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
        if (!LonelinessController.unghost(driver.getTPlayer().getUniqueId())) {
            LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
        }
        return true;
    }

    public boolean setDriverPosition(Driver driver, int newStartPosition) {
        if (driver.getHeat().getHeatState() != HeatState.SETUP && driver.getHeat().getHeatState() != HeatState.LOADED) {
            return false;
        }

        int prevPos = driver.getStartPosition();
        for (Driver d : startPositions) {
            if (newStartPosition < prevPos) {
                if (d.getStartPosition() >= newStartPosition && d.getStartPosition() < prevPos) {
                    d.setStartPosition(d.getStartPosition() + 1);
                    d.setPosition(d.getPosition() + 1);
                }
            } else if (newStartPosition > prevPos) {
                if (d.getStartPosition() > prevPos && d.getStartPosition() <= newStartPosition) {
                    d.setStartPosition(d.getStartPosition() - 1);
                    d.setPosition(d.getPosition() - 1);
                }
            } else {
                return false;
            }
        }
        driver.setStartPosition(newStartPosition);
        driver.setPosition(newStartPosition);
        startPositions.sort(Comparator.comparingInt(Driver::getStartPosition));
        return true;
    }

    public boolean disqualifyDriver(Driver driver) {
        if (!driver.getHeat().isActive()) {
            return false;
        }
        driver.disqualify();
        driver.removeScoreboard();
        getEvent().removeSpectator(driver.getTPlayer().getUniqueId());
        EventDatabase.removePlayerFromRunningHeat(driver.getTPlayer().getUniqueId());
        if (noDriversRunning()) {
            finishHeat();
        }
        LonelinessController.updatePlayersVisibility(driver.getTPlayer().getPlayer());
        if (!LonelinessController.unghost(driver.getTPlayer().getUniqueId())) {
            LonelinessController.updatePlayerVisibility(driver.getTPlayer().getPlayer());
        }
        return true;
    }

    public List<Participant> getParticipants() {
        return new ArrayList<>(getEvent().getSpectators().values());
    }

    public void updateScoreboard() {
        if (Duration.between(lastScoreboardUpdate, TimingSystem.currentTime).toMillis() > TimingSystem.configuration.getScoreboardInterval()) {
            livePositions.forEach(Driver::updateScoreboard);
            scoreboard.updateScoreboard();
            lastScoreboardUpdate = TimingSystem.currentTime;
        }
    }

    public boolean noDriversRunning() {
        for (Driver d : getDrivers().values()) {
            if (d.isRunning()) {
                return false;
            }
        }
        return true;
    }

    public void setHeatState(HeatState state) {
        this.heatState = state;
        TimingSystem.getEventDatabase().heatSet(id, "state", state.name());
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        TimingSystem.getEventDatabase().heatSet(id, "startTime", startTime == null ? null : startTime.toEpochMilli());
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
        TimingSystem.getEventDatabase().heatSet(id, "endTime", endTime == null ? null : endTime.toEpochMilli());
    }

    public void setFastestLapUUID(UUID fastestLapUUID) {
        this.fastestLapUUID = fastestLapUUID;
        TimingSystem.getEventDatabase().heatSet(id, "fastestLapUUID", fastestLapUUID == null ? null : fastestLapUUID.toString());
    }

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
        TimingSystem.getEventDatabase().heatSet(getId(), "timeLimit", timeLimit);
    }

    public void setStartDelayInTicks(int startDelay) {
        this.startDelay = startDelay;
        TimingSystem.getEventDatabase().heatSet(getId(), "startDelay", startDelay);
    }

    public void setTotalLaps(int totalLaps) {
        this.totalLaps = totalLaps;
        TimingSystem.getEventDatabase().heatSet(getId(), "totalLaps", totalLaps);
    }

    public void setTotalPits(int totalPits) {
        this.totalPits = totalPits;
        TimingSystem.getEventDatabase().heatSet(getId(), "totalPitstops", totalPits);
    }

    public void setHeatNumber(int heatNumber) {
        this.heatNumber = heatNumber;
        TimingSystem.getEventDatabase().heatSet(getId(), "heatNumber", heatNumber);
    }

    public boolean isActive() {
        return getHeatState() == HeatState.LOADED || getHeatState() == HeatState.RACING || getHeatState() == HeatState.STARTING;
    }

    public boolean isRacing() {
        return getHeatState() == HeatState.RACING || getHeatState() == HeatState.STARTING;
    }

    public void onShutdown() {
        gridManager.clearArmorstands();
        if (scoreboard != null) {
            scoreboard.removeScoreboards();
        }
        drivers.values().forEach(Driver::onShutdown);
    }

    public void reverseGrid(Integer percentage) {
        if (getHeatState() != HeatState.SETUP && getHeatState() != HeatState.LOADED) {
            return;
        }
        int reverseSize = Math.min((getStartPositions().size() * percentage) / 100, getStartPositions().size());

        if (reverseSize == 0) {
            return;
        }

        for (Driver driver : getStartPositions()) {

            int driverPos = driver.getStartPosition();
            if (driverPos > reverseSize) {
                break;
            }
            int newPos = reverseSize - (driverPos - 1);
            driver.setStartPosition(newPos);
            driver.setPosition(newPos);
        }
        startPositions.sort(Comparator.comparingInt(Driver::getStartPosition));
        livePositions.sort(Comparator.comparingInt(Driver::getPosition));
    }
}
