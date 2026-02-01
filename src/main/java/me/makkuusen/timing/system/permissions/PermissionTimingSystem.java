package me.makkuusen.timing.system.permissions;

import co.aikar.commands.CommandReplacements;

public enum PermissionTimingSystem implements Permissions {
    RESET,
    BOAT,
    BOAT_MODE,
    SETTINGS,
    SETTINGS_OVERRIDE,
    TAG_CREATE,
    TAG_DELETE,
    TAG_SET_COLOR,
    TAG_SET_ITEM,
    TAG_SET_WEIGHT,
    SCOREBOARD_SET_MAXROWS,
    SCOREBOARD_SET_INTERVAL,
    DRS_SET_MINDELTA,
    DRS_SET_MAXDELTA,
    DRS_SET_DURATION,
    DRS_SET_FORWARDACCEL,
    PUSHTOPASS_SET_MAXUSETIME,
    PUSHTOPASS_SET_FULLCHARGETIME,
    PUSHTOPASS_SET_FORWARDACCEL,
    PUSHTOPASS_SET_STARTINGCHARGE,
    COLOR_SET_NAMED,
    COLOR_SET_HEX,
    GHOST;

    @Override
    public String getNode() {
        return "timingsystem." + this.toString().replace("_", ".").toLowerCase();
    }

    public static void init(CommandReplacements replacements) {
        for (PermissionTimingSystem perm : values()) {
            Permissions.register(perm, replacements);
        }
    }
}
