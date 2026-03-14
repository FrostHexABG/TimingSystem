package me.makkuusen.timing.system.api.events.driver;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DriverActionbarUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Component actionBar;

    public DriverActionbarUpdateEvent(Player player, Component actionBar, Boolean isFinalHeat) {
        this.player = player;
        this.actionBar = actionBar;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
}
