package me.makkuusen.timing.system.database;

import co.aikar.idb.*;
import co.aikar.taskchain.TaskChain;
import lombok.Getter;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public interface EventDatabase {
    @Getter
    Set<Event> events = new HashSet<>();
    Set<Heat> heats = new HashSet<>();
    HashMap<UUID, Event> playerSelectedEvent = new HashMap<>();
    HashMap<UUID, Driver> playerInRunningHeat = new HashMap<>();

    Event createEvent(UUID uuid, String name);

    Round createRound(Event event, RoundType roundType, int roundNumber);

    Heat createHeat(Round round, int heatIndex);

    void createSign(TPlayer tPlayer, Event event, Subscriber.Type type);

    void removeSign(UUID uuid, int eventId, Subscriber.Type type);

    DbRow createDriver(UUID uuid, Heat heat, int startPosition);

    void createLap(Lap lap);

    List<DbRow> selectEvents() throws SQLException;

    List<DbRow> selectRounds(int eventId) throws SQLException;

    List<DbRow> selectHeats(int roundId) throws SQLException;

    List<DbRow> selectSigns(int eventId) throws SQLException;

    List<DbRow> selectDrivers(int heatId) throws SQLException;

    List<DbRow> selectLaps(int heatId, String uuid) throws SQLException;

    <T> void remove(T thing);

    void setHasFinishedLoading(boolean b);

    boolean hasFinishedLoading();

    void eventSet(long eventId, String column, String value);

    void eventSet(long eventId, String column, Boolean value);

    void eventSet(long eventID, String column, Integer value);

    void roundSet(long roundId, String column, String value);

    void heatSet(long heatId, String column, String value);

    void heatSet(long heatId, String column, Integer value);

    void heatSet(long heatId, String column, Long value);

    void heatSet(long heatId, String column, Boolean value);

    void driverSet(long driverId, String column, Integer value);

    void driverSet(long driverId, String column, Long value);

    DbRow teamHeatEntryNew(int heatId, int teamId, int startPosition);

    List<DbRow> selectTeamHeatEntries(int heatId) throws SQLException;

    void teamHeatEntrySet(long teamHeatEntryId, String column, String value);

    void teamHeatEntrySet(long teamHeatEntryId, String column, Integer value);

    void teamHeatEntrySet(long teamHeatEntryId, String column, Long value);

    void teamHeatEntrySet(long teamHeatEntryId, String column, Boolean value);

    void teamHeatEntryRemove(long teamHeatEntryId);

    void createTeamLap(int teamHeatEntryId, int lapNumber, long lapStart, Long lapEnd, UUID driverUUID, boolean pitted);

    List<DbRow> selectTeamLaps(int teamHeatEntryId) throws SQLException;


    static Set<Event> getEvents() {
        return events;
    }

    static void initSynchronize() {
        TaskChain<?> chain = TimingSystem.newChain();
        TimingSystem.getPlugin().getLogger().warning("Start loading events");
        chain.async(() -> {
            try {
                EventLoader.load();
            } catch (SQLException e) {
                TimingSystem.getPlugin().getLogger().warning("Failed to sync events");
            }
        }).execute((finished) -> {
            heats.stream().filter(Heat::isActive).forEach(Heat::resetHeat);
            TimingSystem.getEventDatabase().setHasFinishedLoading(true);
            TimingSystem.getPlugin().getLogger().warning("Finished loading events");
        });
    }

    static void initHeat(Round round, DbRow heatData, List<DbRow> driversData, Map<String, List<DbRow>> lapsData) {
        Heat heat = new Heat(heatData, round);
        heats.add(heat);
        round.addHeat(heat);

        for (DbRow driverData : driversData) {
            initDriver(heat, driverData, lapsData.getOrDefault(driverData.getString("uuid"), List.of()));
        }
    }

    static void initDriver(Heat heat, DbRow driverData, List<DbRow> lapsData) {
        Driver driver = new Driver(driverData);
        heat.addDriver(driver);
        List<Lap> laps = new ArrayList<>();

        for (DbRow lapData : lapsData) {
            laps.add(new Lap(lapData));
        }
        driver.setLaps(laps);
    }

    static void setPlayerSelectedEvent(UUID uuid, Event event) {
        playerSelectedEvent.put(uuid, event);
    }

    static Optional<Event> getPlayerSelectedEvent(UUID uuid) {
        if (playerSelectedEvent.containsKey(uuid)) {
            return Optional.of(playerSelectedEvent.get(uuid));
        }
        return Optional.empty();
    }

    static Optional<Driver> getClosestDriverForSpectator(Player player) {
        Optional<Driver> closest = Optional.empty();
        double distance = -1;
        for (Driver driver : playerInRunningHeat.values()) {
            if (driver.getTPlayer().getPlayer() != null && driver.getHeat().getEvent().getSpectators().get(player.getUniqueId()) != null) {
                if (player.getLocation().getWorld() != driver.getTPlayer().getPlayer().getWorld()) {
                    continue;
                }
                if (driver.isFinished()) {
                    continue;
                }
                if (closest.isEmpty()) {
                    closest = Optional.of(driver);
                    distance = player.getLocation().distance(driver.getTPlayer().getPlayer().getLocation());
                } else {
                    if (player.getLocation().distance(driver.getTPlayer().getPlayer().getLocation()) < distance) {
                        closest = Optional.of(driver);
                        distance = player.getLocation().distance(driver.getTPlayer().getPlayer().getLocation());
                    }
                }
            }
        }
        return closest;
    }

    static Optional<Event> getEvent(String name) {
        return events.stream().filter(event -> event.getDisplayName().equalsIgnoreCase(name)).findFirst();
    }

    static Optional<Event> getEvent(int id) {
        return events.stream().filter(event -> event.getId() == id).findFirst();
    }

    static Optional<Heat> getHeat(int id) {
        return heats.stream().filter(heat -> heat.getId() == id).findFirst();
    }

    static List<Heat> getHeats() {
        return heats.stream().toList();
    }

    static Optional<Event> eventNew(UUID uuid, String name) {
        if (!TimingSystem.getEventDatabase().hasFinishedLoading() || getEvent(name).isPresent())
            return Optional.empty();
        Event event = TimingSystem.getEventDatabase().createEvent(uuid, name);
        if(event == null)
            return Optional.empty();
        if (events.add(event))
            setPlayerSelectedEvent(uuid, event);
        return Optional.of(event);
    }

    static boolean roundNew(Event event, RoundType roundType, int roundNumber) {
        Round round = TimingSystem.getEventDatabase().createRound(event, roundType, roundNumber);
        if(round == null)
            return false;
        event.eventSchedule.addRound(round);
        return true;
    }

    static Optional<Heat> heatNew(Round round, int heatNumber) {
        Heat heat = TimingSystem.getEventDatabase().createHeat(round, heatNumber);
        if(heat == null)
            return Optional.empty();
        heats.add(heat);
        round.addHeat(heat);
        return Optional.of(heat);
    }

    static boolean heatDriverNew(UUID uuid, Heat heat, int startPosition) {
        if (heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED)
            return false;
        DbRow row = TimingSystem.getEventDatabase().createDriver(uuid, heat, startPosition);
        if(row == null)
            return false;
        heat.addDriver(new Driver(row));
        return true;
    }

    static boolean heatDriverNewLate(UUID uuid, Heat heat, int startPosition) {
        if (heat.getHeatState() != HeatState.RACING)
            return false;
        DbRow row = TimingSystem.getEventDatabase().createDriver(uuid, heat, startPosition);
        if(row == null)
            return false;
        heat.addDriver(new Driver(row));
        return true;
    }

    static Subscriber subscriberNew(TPlayer tPlayer, Event event, Subscriber.Type type) {
        TimingSystem.getEventDatabase().createSign(tPlayer, event, type);
        return new Subscriber(tPlayer);
    }

    static void lapNew(Lap lap) {
        TimingSystem.getEventDatabase().createLap(lap);
    }

    static List<String> getEventsAsStrings() {
        List<String> eventStrings = new ArrayList<>();
        events.forEach(event -> eventStrings.add(event.toString()));
        return eventStrings;
    }

    static List<String> getRoundsAsStrings(UUID uuid) {
        var maybeEvent = getPlayerSelectedEvent(uuid);
        if (maybeEvent.isEmpty()) {
            return List.of();
        }
        List<String> roundList = new ArrayList<>();
        maybeEvent.get().eventSchedule.getRounds().forEach(round -> roundList.add(round.getName()));
        return roundList;

    }

    static List<String> getHeatsAsStrings(UUID uuid) {
        var maybeEvent = getPlayerSelectedEvent(uuid);
        if (maybeEvent.isEmpty()) {
            return List.of();
        }
        List<String> roundList = new ArrayList<>();
        maybeEvent.get().eventSchedule.getRounds().forEach(round -> roundList.addAll(round.getRawHeats()));
        return roundList;

    }

    static void addPlayerToRunningHeat(Driver driver) {
        if (playerInRunningHeat.get(driver.getTPlayer().getUniqueId()) != null) {
            return;
        }
        playerInRunningHeat.put(driver.getTPlayer().getUniqueId(), driver);
    }

    static boolean removeEvent(Event event) {
        if (event.hasRunningHeat()) {
            return false;
        }
        List<UUID> uuids = new ArrayList<>(playerSelectedEvent.keySet());
        for (UUID uuid : uuids) {
            if (playerSelectedEvent.get(uuid).equals(event)) {
                playerSelectedEvent.remove(uuid);
            }
        }
        events.remove(event);
        TimingSystem.getEventDatabase().remove(event);
        return true;
    }

    static void removeEventHard(Event event) {
        if (event.hasRunningHeat()) {
            event.getRunningHeat().get().finishHeat();
        }
        List<UUID> uuids = new ArrayList<>(playerSelectedEvent.keySet());
        for (UUID uuid : uuids) {
            if (playerSelectedEvent.get(uuid).equals(event)) {
                playerSelectedEvent.remove(uuid);
            }
        }
        events.remove(event);
        TimingSystem.getEventDatabase().remove(event);
    }

    static boolean removeHeat(Heat heat) {
        if (heat.getRound().removeHeat(heat)) {
            heats.remove(heat);
            TimingSystem.getEventDatabase().remove(heat);
            return true;
        }
        return false;
    }

    static boolean removeRound(Round round) {
        if (round.getEvent().getEventSchedule().removeRound(round)) {
            TimingSystem.getEventDatabase().remove(round);
            return true;
        }
        return false;
    }

    static void removePlayerFromRunningHeat(UUID uuid) {
        playerInRunningHeat.remove(uuid);
    }

    static Optional<Driver> getDriverFromRunningHeat(UUID uuid) {
        if (playerInRunningHeat.get(uuid) != null) {
            return Optional.of(playerInRunningHeat.get(uuid));
        }
        return Optional.empty();
    }
}
