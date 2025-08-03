package me.makkuusen.timing.system.api.events.driver;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverScoreboardTitleUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    @Getter
    private final Player player;
    @Getter
    private final String title;

    public DriverScoreboardTitleUpdateEvent(Player player, String title) {
        this.player = player;
        this.title = title;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
}
