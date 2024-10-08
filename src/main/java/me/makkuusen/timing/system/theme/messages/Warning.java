package me.makkuusen.timing.system.theme.messages;

public enum Warning implements Message {
    DRIVERS_LEFT_OUT,
    NO_LONGER_SPECTATING,
    NO_LONGER_SIGNED,
    NO_LONGER_RESERVE,
    PLAYER_NO_LONGER_SIGNED,
    PLAYER_NO_LONGER_RESERVE,
    TRACK_REQUIRES_BOAT_UTILS,
    TRACK_REQUIRES_NEWER_BOAT_UTILS,
    DANGEROUS_COMMAND,
    CONFIRM_COMMAND,
    GHOSTING_TARGET_ON,
    GHOSTING_TARGET_OFF;

    @Override
    public String getKey() {
        return "warning." + this.name().toLowerCase();
    }
}
