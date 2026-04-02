package me.makkuusen.timing.system.api.events;

import lombok.Getter;
import me.makkuusen.timing.system.track.Track;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TimeTrialEarlyAttemptEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    @Getter
    private final Player player;
    @Getter
    private final Track track;

    public TimeTrialEarlyAttemptEvent(Player player, Track track) {
        this.player = player;
        this.track = track;

    }
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
}

