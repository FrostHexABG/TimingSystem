package me.makkuusen.timing.system.team;

import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;

@Getter
public class TuningAttribute {
    private final String name;
    private final short boatUtilsPacketId;
    private final float vanillaDefault;
    @Getter private final String category;
    @Setter private float multiplier; // Balance knob: >1 amplifies effect, <1 dampens it

    public TuningAttribute(String name, short boatUtilsPacketId, float vanillaDefault, String category, float multiplier) {
        this.name = name;
        this.boatUtilsPacketId = boatUtilsPacketId;
        this.vanillaDefault = vanillaDefault;
        this.category = category;
        this.multiplier = multiplier;
    }

    /**
     * For simple float attributes - get base from track mode or fall back to vanilla
     */
    public float getBaseValue(CustomBoatUtilsMode mode) {
        if (mode == null) return vanillaDefault;

        return switch (name) {
            case "forwardAcceleration"         -> mode.getForwardAcceleration();
            case "turningForwardAcceleration"  -> mode.getTurningForwardAcceleration();
            case "backwardAcceleration"        -> mode.getBackwardAcceleration();
            case "defaultSlipperiness"         -> mode.getDefaultSlipperiness();
            case "yawAcceleration"             -> mode.getYawAcceleration();
            // Per-block slipperiness: fall back to vanilla, track mode doesn't expose these easily
            case "packedIceSlipperiness"       -> mode.getBlocksSlipperiness().getOrDefault("minecraft:packed_ice", vanillaDefault);
            case "blueIceSlipperiness"         -> mode.getBlocksSlipperiness().getOrDefault("minecraft:blue_ice", vanillaDefault);
            default                            -> vanillaDefault;
        };
    }

    /**
     * Whether this attribute needs a per-block packet (packet 3) instead of a simple float packet
     */
    public boolean isPerBlock() {
        return name.equals("packedIceSlipperiness") || name.equals("blueIceSlipperiness");
    }

    /**
     * Get the block ID string for per-block attributes
     */
    public String getBlockId() {
        return switch (name) {
            case "packedIceSlipperiness" -> "minecraft:packed_ice";
            case "blueIceSlipperiness"   -> "minecraft:blue_ice";
            default                      -> null;
        };
    }
}
