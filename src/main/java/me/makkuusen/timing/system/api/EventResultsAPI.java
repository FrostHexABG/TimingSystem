package me.makkuusen.timing.system.api;

import me.makkuusen.timing.system.api.event.*;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.Round;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
public class EventResultsAPI {


    public static EventResult getEventResult(String name) {
        var event = EventDatabase.getEvent(name);
        return event.map(EventResultsAPI::getEventResult).orElse(null);
    }

    public static List<EventResult> getEventResults() {

        List<EventResult> eventResults = new ArrayList<>();
        var events = EventDatabase.getEvents();

        for (Event event : events) {
            eventResults.add(getEventResultWithoutData(event));
        }
        return eventResults;
    }

    private static DriverResult getDriverResult(Driver driver) {
        List<LapResult> lapResults = new ArrayList<>();
        for (Lap lap : driver.getLaps()) {
            LapResult lapResult;
            Optional<Lap> bestLapOptional = driver.getBestLap();
            if (bestLapOptional.isPresent()) {
                lapResult = new LapResult(lap.getPreciseLapTime(), lap.isPitted(), bestLapOptional.get().getPreciseLapTime() == lap.getPreciseLapTime());
            } else {
                lapResult = new LapResult(lap.getPreciseLapTime(), lap.isPitted(), false);
            }
            lapResults.add(lapResult);
        }
        return new DriverResult(driver.getPosition(), driver.getStartPosition(), driver.getTPlayer().getName(), driver.getTPlayer().getUniqueId().toString(), driver.getFinishTime(), lapResults);
    }

    private static HeatResult getHeatResult(Heat heat) {
        List<DriverResult> driverResults = new ArrayList<>();
        for (Driver driver : heat.getDrivers().values().stream().sorted(Comparator.comparingInt(Driver::getPosition)).toList()) {
            var driverResult = getDriverResult(driver);
            driverResults.add(driverResult);
        }
        return new HeatResult(heat.getName(), heat.getTotalLaps(), heat.getStartTime(), heat.getEndTime(), driverResults);
    }

    private static RoundResult getRoundResult(Round round) {
        List<HeatResult> heatResults = new ArrayList<>();
        for (Heat heat : round.getHeats()) {
            var heatResult = getHeatResult(heat);
            heatResults.add(heatResult);
        }
        return new RoundResult(round.getName(), round.getType().name(), round.getRoundIndex(), heatResults);
    }

    private static EventResult getEventResult(Event event) {
        List<RoundResult> roundResults = new ArrayList<>();
        for (Round round : event.getEventSchedule().getRounds()) {
            List<HeatResult> heatResults = new ArrayList<>();
            var roundResult = getRoundResult(round);
            roundResults.add(roundResult);
        }
        String trackName = null;
        Integer trackId = null;
        if (event.getTrack() != null) {
            trackName = event.getTrack().getDisplayName();
            trackId = event.getTrack().getId();
        }

        return new EventResult(event.getDisplayName(), event.getDate(), trackName, event.getState().name(), trackId, event.getSubscribers().size(), roundResults);
    }

    private static EventResult getEventResultWithoutData(Event event) {
        String trackName = null;
        Integer trackId = null;
        if (event.getTrack() != null) {
            trackName = event.getTrack().getDisplayName();
            trackId = event.getTrack().getId();
        }
        return new EventResult(event.getDisplayName(), event.getDate(), trackName, event.getState().name(), trackId, event.getSubscribers().keySet().size(), null);
    }
}
