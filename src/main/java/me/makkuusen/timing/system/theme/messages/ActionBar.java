package me.makkuusen.timing.system.theme.messages;

public enum ActionBar implements Message {
    RACE,
    RACE_PITS_COMPLETED,
    RACE_SPECTATOR,
    RACE_TIME,
    RACE_TIME_PITS_COMPLETED,
    RACE_SPECTATOR_TIME;

    @Override
    public String getKey() {
        return "actionbar." + this.name().toLowerCase();
    }
}
