package me.makkuusen.timing.system.event;

import co.aikar.idb.DbRow;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.SQLiteDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Spectator;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.track.Track;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Getter
public class Event {

    public static TimingSystem plugin;
    public EventSchedule eventSchedule;
    public EventCountdown eventCountdown;
    @Getter(AccessLevel.PUBLIC)
    HashMap<UUID, Subscriber> subscribers = new HashMap<>(); // Signed drivers
    @Getter(AccessLevel.PUBLIC)
    HashMap<UUID, Subscriber> reserves = new HashMap<>();
    @Getter(AccessLevel.PUBLIC)
    HashMap<UUID, Spectator> spectators = new HashMap<>();
    Track track;
    private int id;
    private UUID uuid;
    private String displayName;
    private long date;
    private boolean openSign;
    private EventState state;
    private Boolean tuningEnabled = false;

    public Event(DbRow data) {
        id = data.getInt("id");
        displayName = data.getString("name");
        uuid = UUID.fromString(data.getString("uuid")); // Be aware this can be filled with zeros if admin created event.
        date = data.getInt("date");
        Optional<Track> maybeTrack = data.get("track") == null ? Optional.empty() : TrackDatabase.getTrackById(data.getInt("track"));
        track = maybeTrack.orElse(null);
        state = EventState.valueOf(data.getString("state"));
        openSign = data.get("open") instanceof Boolean ? data.get("open") : data.get("open").equals(1);
        eventSchedule = new EventSchedule();
        eventCountdown = new EventCountdown(this);
    }

    public boolean start() {
        if (state != EventState.SETUP) {
            return false;
        }
        if (track == null) {
            return false;
        }
        return eventSchedule.start(this);
    }

    public boolean finish() {
        if (state == EventState.FINISHED || state == EventState.SETUP) {
            return false;
        }
        if (eventSchedule.isLastRound() && eventSchedule.getRound().isPresent() && eventSchedule.getRound().get().getState() == Round.RoundState.FINISHED) {
            setState(EventState.FINISHED);
            return true;
        }

        return false;
    }

    public boolean hasRunningHeat() {
        if (getState() == Event.EventState.RUNNING) {
            var maybeRound = getEventSchedule().getRound();
            if (maybeRound.isPresent()) {
                return maybeRound.get().getHeats().stream().anyMatch(Heat::isActive);
            }
        }
        return false;
    }

    public Optional<Heat> getRunningHeat() {
        if (getState() != Event.EventState.RUNNING) {
            return Optional.empty();
        }

        var maybeRound = getEventSchedule().getRound();

        return maybeRound.flatMap(round -> round.getHeats().stream().filter(Heat::isActive).findFirst());
    }

    public void addSpectator(UUID uuid) {
        spectators.put(uuid, new Spectator(TSDatabase.getPlayer(uuid)));
        var maybeHeat = getRunningHeat();
        maybeHeat.ifPresent(Heat::updateScoreboard);
        eventCountdown.addSpectator(TSDatabase.getPlayer(uuid));
    }

    public boolean isSpectating(UUID uuid) {
        return spectators.containsKey(uuid);
    }

    public void removeSpectator(UUID uuid) {
        if (spectators.containsKey(uuid)) {
            spectators.remove(uuid);
            if (TSDatabase.getPlayer(uuid).getPlayer() != null) {
                TPlayer tPlayer = TSDatabase.getPlayer(uuid);
                eventCountdown.removeSpectator(tPlayer);
                var maybeHeat = getRunningHeat();
                if (maybeHeat.isPresent()) {
                    tPlayer.clearScoreboard();
                }
            }
        }
    }

    public void addSubscriber(TPlayer tPlayer) {
        subscribers.put(tPlayer.getUniqueId(), EventDatabase.subscriberNew(tPlayer, this, Subscriber.Type.SUBSCRIBER));
        if (!getSpectators().containsKey(tPlayer.getUniqueId())) {
            addSpectator(tPlayer.getUniqueId());
        }
    }

    public boolean isSubscribing(UUID uuid) {
        return subscribers.containsKey(uuid);
    }

    public void removeSubscriber(UUID uuid) {
        if (subscribers.containsKey(uuid)) {
            TimingSystem.getEventDatabase().removeSign(uuid, id, Subscriber.Type.SUBSCRIBER);
            subscribers.remove(uuid);
        }
    }

    public void addReserve(TPlayer tPlayer) {
        reserves.put(tPlayer.getUniqueId(), EventDatabase.subscriberNew(tPlayer, this, Subscriber.Type.RESERVE));
    }

    public boolean isReserving(UUID uuid) {
        return reserves.containsKey(uuid);
    }

    public void removeReserve(UUID uuid) {
        if (reserves.containsKey(uuid)) {
            TimingSystem.getEventDatabase().removeSign(uuid, id, Subscriber.Type.RESERVE);
            reserves.remove(uuid);
        }
    }

    public void setTrack(Track track) {
        this.track = track;
        TimingSystem.getEventDatabase().eventSet(id, "track", track.getId());
    }

    public void setState(EventState state) {
        this.state = state;
        TimingSystem.getEventDatabase().eventSet(id, "state", state.name());
    }

    public void setOpenSign(boolean open) {
        this.openSign = open;
        TimingSystem.getEventDatabase().eventSet(id, "open", open);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return id == event.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean isActive() {
        return state != EventState.FINISHED;
    }

    public void setEventSchedule(EventSchedule es) {
        this.eventSchedule = es;
    }

    public enum EventState {
        SETUP, RUNNING, FINISHED
    }

    public boolean isTuningEnabled(){return tuningEnabled;}

    public void setTuningEnabled(boolean enabled){this.tuningEnabled = enabled;}
}
