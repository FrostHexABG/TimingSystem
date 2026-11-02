package me.makkuusen.timing.system.database;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.event.EventSchedule;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.tplayer.TPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class EventLoader {

    private static final int CHUNK = 50_000;
    private static final String LOADED_EVENT = "e.isRemoved = 0 AND e.name NOT IN ('QuickRace', 'LobbyRace')";
    private static final String LOADED_HEAT = " JOIN ts_heats h ON h.id = x.heatId JOIN ts_rounds r ON r.id = h.roundId JOIN ts_events e ON e.id = r.eventId"
            + " WHERE x.isRemoved = 0 AND h.isRemoved = 0 AND r.isRemoved = 0 AND " + LOADED_EVENT;

    private record DriverRow(int id, String uuid, TPlayer tPlayer, Integer position, Integer startPosition, Long startTime, Long endTime, Integer pitstops) {

        static DriverRow of(DbRow row) {
            return new DriverRow(row.get("id"), row.getString("uuid"), TSDatabase.getPlayer(UUID.fromString(row.getString("uuid"))),
                    row.getInt("position"), row.getInt("startPosition"), row.getLong("startTime"), row.getLong("endTime"), row.getInt("pitstops"));
        }
    }

    private EventLoader() {
    }

    static void load() throws SQLException {
        List<DbRow> eventRows = DB.getResults("SELECT * FROM ts_events e WHERE " + LOADED_EVENT + " ORDER BY e.id");
        List<DbRow> signRows = DB.getResults("SELECT x.* FROM ts_events_signs x JOIN ts_events e ON e.id = x.eventId WHERE " + LOADED_EVENT + " ORDER BY x.id");
        List<DbRow> roundRows = DB.getResults("SELECT x.* FROM ts_rounds x JOIN ts_events e ON e.id = x.eventId WHERE x.isRemoved = 0 AND " + LOADED_EVENT + " ORDER BY x.id");
        List<DbRow> heatRows = DB.getResults("SELECT x.* FROM ts_heats x JOIN ts_rounds r ON r.id = x.roundId JOIN ts_events e ON e.id = r.eventId"
                + " WHERE x.isRemoved = 0 AND r.isRemoved = 0 AND " + LOADED_EVENT + " ORDER BY x.id");
        Map<Integer, List<DriverRow>> driversByHeat = readDrivers();
        Map<Integer, Map<String, List<Lap>>> lapsByHeat = readLaps();

        Map<Integer, List<DbRow>> signsByEvent = signRows.stream().collect(Collectors.groupingBy(r -> r.getInt("eventId")));
        Map<Integer, List<DbRow>> roundsByEvent = roundRows.stream().collect(Collectors.groupingBy(r -> r.getInt("eventId")));
        Map<Integer, List<DbRow>> heatsByRound = heatRows.stream().collect(Collectors.groupingBy(r -> r.getInt("roundId")));

        signRows.clear(); roundRows.clear(); heatRows.clear();
        for (DbRow eventData : eventRows) {
            Event event = new Event(eventData);
            EventDatabase.events.add(event);
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
    }

    private static void initHeat(Round round, DbRow heatData, List<DriverRow> drivers, Map<String, List<Lap>> laps) {
        Heat heat = new Heat(heatData, round);
        EventDatabase.heats.add(heat);
        round.addHeat(heat);

        for (DriverRow row : drivers) {
            Driver driver = new Driver(row.id(), row.tPlayer(), heat, row.position(), row.startPosition(), row.startTime(), row.endTime(), row.pitstops());
            heat.addDriver(driver);
            driver.setLaps(new ArrayList<>(laps.getOrDefault(row.uuid(), List.of())));
        }
    }

    private static Map<Integer, List<DriverRow>> readDrivers() throws SQLException {
        Map<Integer, List<DriverRow>> driversByHeat = new HashMap<>();
        int lastId = 0;
        List<DbRow> rows;
        do {
            rows = DB.getResults("SELECT x.* FROM ts_drivers x" + LOADED_HEAT + " AND x.id > ? ORDER BY x.id LIMIT ?", lastId, CHUNK);
            for (DbRow row : rows) {
                driversByHeat.computeIfAbsent(row.getInt("heatId"), k -> new ArrayList<>()).add(DriverRow.of(row));
                lastId = row.getInt("id");
            }
        } while (rows.size() == CHUNK);
        return driversByHeat;
    }

    private static Map<Integer, Map<String, List<Lap>>> readLaps() throws SQLException {
        Map<Integer, Map<String, List<Lap>>> lapsByHeat = new HashMap<>();
        int lastId = 0;
        List<DbRow> rows;
        do {
            rows = DB.getResults("SELECT x.* FROM ts_laps x" + LOADED_HEAT + " AND x.id > ? ORDER BY x.id LIMIT ?", lastId, CHUNK);
            for (DbRow row : rows) {
                lapsByHeat.computeIfAbsent(row.getInt("heatId"), k -> new HashMap<>()).computeIfAbsent(row.getString("uuid"), k -> new ArrayList<>()).add(new Lap(row));
                lastId = row.getInt("id");
            }
        } while (rows.size() == CHUNK);
        return lapsByHeat;
    }
}
