package me.makkuusen.timing.system.database;

import co.aikar.idb.*;
import co.aikar.taskchain.TaskChain;
import lombok.Getter;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventSchedule;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    private static String sqlPlaceholders(int count) {
        if (count > 0) return String.join(", ", Collections.nCopies(count, "?"));
        else return "";
    }

    static void initSynchronize() {
        TaskChain<?> chain = TimingSystem.newChain();
        TimingSystem.getPlugin().getLogger().warning("Start loading events");
        chain.async(() -> {
            try {
                List<DbRow> eventRows = DB.getResults("SELECT * FROM ts_events WHERE isRemoved = 0 AND name NOT IN ('QuickRace', 'LobbyRace')");
                List<Integer> eventIds = eventRows.stream().map(r -> r.getInt("id")).toList();
                List<DbRow> signRows = DB.getResults("SELECT * FROM ts_events_signs WHERE eventId IN (" + sqlPlaceholders(eventIds.size()) + ")", eventIds.toArray());
                List<DbRow> roundRows = DB.getResults("SELECT * FROM ts_rounds WHERE isRemoved = 0 AND eventId IN (" + sqlPlaceholders(eventIds.size()) + ")", eventIds.toArray());
                List<Integer> roundIds = roundRows.stream().map(r -> r.getInt("id")).toList();
                List<DbRow> heatRows = DB.getResults("SELECT * FROM ts_heats WHERE isRemoved = 0 AND roundId IN (" + sqlPlaceholders(roundIds.size()) + ")", roundIds.toArray());
                List<Integer> heatIds = heatRows.stream().map(r -> r.getInt("id")).toList();
                List<DbRow> driverRows = DB.getResults("SELECT * FROM ts_drivers WHERE isRemoved = 0 AND heatId IN (" + sqlPlaceholders(heatIds.size()) + ")", heatIds.toArray());
                List<DbRow> lapRows = DB.getResults("SELECT * FROM ts_laps WHERE isRemoved = 0 AND heatId IN (" + sqlPlaceholders(heatIds.size()) + ")", heatIds.toArray());

                Map<Integer, List<DbRow>> signsByEvent = signRows.stream().collect(Collectors.groupingBy(r -> r.getInt("eventId")));
                Map<Integer, List<DbRow>> roundsByEvent = roundRows.stream().collect(Collectors.groupingBy(r -> r.getInt("eventId")));
                Map<Integer, List<DbRow>> heatsByRound = heatRows.stream().collect(Collectors.groupingBy(r -> r.getInt("roundId")));
                Map<Integer, List<DbRow>> driversByHeat = driverRows.stream().collect(Collectors.groupingBy(r -> r.getInt("heatId")));
                Map<Integer, Map<String, List<DbRow>>> lapsByHeat = lapRows.stream().collect(Collectors.groupingBy(r -> r.getInt("heatId"), Collectors.groupingBy(r -> r.getString("uuid"))));

                signRows.clear(); roundRows.clear(); heatRows.clear(); driverRows.clear(); lapRows.clear();
                for (DbRow eventData : eventRows) {
                    Event event = new Event(eventData);
                    events.add(event);
                    EventSchedule es = new EventSchedule();

                    for (DbRow signsData : signsByEvent.getOrDefault(event.getId(), List.of())) {
                        try {
                            var type = Subscriber.Type.valueOf(signsData.getString("type"));
                            if (type == Subscriber.Type.SUBSCRIBER) {
                                event.getSubscribers().put(UUID.fromString(signsData.get("uuid")), new Subscriber(signsData));
                            } else if (type == Subscriber.Type.RESERVE) {
                                event.getReserves().put(UUID.fromString(signsData.get("uuid")), new Subscriber(signsData));
                            }
                        } catch (IllegalArgumentException ignore) { }
                    }

                    for (DbRow roundData : roundsByEvent.getOrDefault(event.getId(), List.of())) {
                        Round round;
                        var type = RoundType.valueOf(roundData.getString("type"));
                        if (type == RoundType.FINAL) {
                            round = new FinalRound(roundData);
                        } else {
                            round = new QualificationRound(roundData);
                        }

                        for (DbRow heatData : heatsByRound.getOrDefault(round.getId(), List.of())) {
                            initHeat(round, heatData, driversByHeat.getOrDefault(heatData.getInt("id"), List.of()), lapsByHeat.getOrDefault(heatData.getInt("id"), Map.of()));
                        }

                        es.addRound(round);
                    }

                    es.setCurrentRound();
                    event.setEventSchedule(es);

                    List<DbRow> eventRounds = roundsByEvent.remove(event.getId());
                    if (eventRounds != null) {
                        for (DbRow roundRow : eventRounds) {
                            List<DbRow> roundHeats = heatsByRound.remove(roundRow.getInt("id"));
                            if (roundHeats != null) {
                                for (DbRow heatRow : roundHeats) {
                                    driversByHeat.remove(heatRow.getInt("id"));
                                    lapsByHeat.remove(heatRow.getInt("id"));
                                }
                            }
                        }
                    }
                    signsByEvent.remove(event.getId());
                }
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
        // Allow joining mid-heat if it's enabled and the round is qualification
        boolean canJoinMidHeat = heat.getJoinMidHeat() != null && heat.getJoinMidHeat() && 
                                 heat.getRound() instanceof me.makkuusen.timing.system.round.QualificationRound;
        
        if (!canJoinMidHeat && heat.getHeatState() != HeatState.SETUP && heat.getHeatState() != HeatState.LOADED)
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
