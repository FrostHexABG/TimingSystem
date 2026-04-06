package me.makkuusen.timing.system.boatutils;

import lombok.Getter;

import java.util.Arrays;

public enum BoatUtilsMode {
    VANILLA(-1, 0, true),
    BROKEN_SLIME_RALLY(0, 0, false),
    BROKEN_SLIME_RALLY_BLUE(1, 0, false),
    BROKEN_SLIME_BA_NOFD(2, 0, true),
    BROKEN_SLIME_PARKOUR(3, 0, false),
    BROKEN_SLIME_BA_BLUE_NOFD(4, 2, true),
    BROKEN_SLIME_PARKOUR_BLUE(5, 4, false),
    BROKEN_SLIME_BA(6, 4, true),
    BROKEN_SLIME_BA_BLUE(7, 4, true),
    RALLY(8, 5, false),
    RALLY_BLUE(9, 5, false),
    BA_NOFD(10, 5, true),
    PARKOUR(11, 5, false),
    BA_BLUE_NOFD(12, 5, true),
    PARKOUR_BLUE(13, 5, false),
    BA(14, 5, true),
    BA_BLUE(15, 5, true),
    JUMP_BLOCKS(16, 6, true),
    BOOSTER_BLOCKS(17, 6, true),
    DEFAULT_ICE(18, 6, false),
    DEFAULT_BLUE_ICE(19, 6, false),
    NOCOL_BOATS_AND_PLAYERS(20, 10, true),
    NOCOL_ALL_ENTITIES(21, 10, true),
    BA_JANKLESS(22, 11, true),
    BA_BLUE_JANKLESS(23, 11, true),
    // NOTE: if you add DEFAULT_NINE_EIGHT_FIVE mode, set vanillaSlipperiness to false
    // NOTE: if you add a new mode, vanillaSlipperiness = true if there is no OpenBoatUtils.setAllBlocksSlipperiness and no OpenBoatUtils.setBlockSlipperiness (except minecraft:air)
    ;

    private final short id;
    private final short version;
    @Getter
    private final boolean vanillaSlipperiness;

    BoatUtilsMode(int id, int version, boolean vanillaSlipperiness) {
        this.id = (short) id;
        this.version = (short) version;
        this.vanillaSlipperiness = vanillaSlipperiness;
    }

    public short getId(){
        return id;
    }

    public short getRequiredVersion() {
        return version;
    }

    public static BoatUtilsMode getMode(int id) {
        return Arrays.stream(BoatUtilsMode.values()).filter(boatUtilsMode -> boatUtilsMode.getId() == id).findFirst().orElse(VANILLA);
    }
}
