package me.makkuusen.timing.system.permissions;

import co.aikar.commands.CommandReplacements;

public enum PermissionHeat implements Permissions {
    LIST,
    INFO,
    RESULTS,
    START,
    FINISH,
    LOAD,
    RESET,
    REMOVE,
    CREATE,
    SET_LAPS,
    SET_PITS,
    SET_STARTDELAY,
    SET_TIMELIMIT,
    SET_MAXDRIVERS,
    SET_COLLISION,
    SET_LONELY,
    SET_RESET,
    SET_LAPRESET,
    SET_GHOSTINGDELTA,
    SET_BOATSWITCHING,
    SET_DRS,
    SET_DRSDOWNTIME,
    SET_DRIVERPOSITION,
    SET_REVERSEGRID,
    ADD_DRIVER,
    ADD_ALL,
    REMOVEDRIVER,
    QUIT,
    SORT_TT,
    SORT_RANDOM,
    ADD_STREAKER,
    REMOVESTREAKER,
    DRIVER_SWAP;

    @Override
    public String getNode() {
        return "timingsystem.heat." + this.toString().replace("_", ".").toLowerCase();
    }

    public static void init(CommandReplacements replacements) {
        for(PermissionHeat perm : values()) {
            Permissions.register(perm, replacements);
        }
    }
}
