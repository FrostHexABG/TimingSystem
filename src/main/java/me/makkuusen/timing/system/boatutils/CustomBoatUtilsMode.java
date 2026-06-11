package me.makkuusen.timing.system.boatutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Hover;
import me.makkuusen.timing.system.theme.messages.Warning;
import me.makkuusen.timing.system.tplayer.TPlayer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Getter
@Setter
public class CustomBoatUtilsMode {

    private static final short PACKET_ID_RESET = 0;
    private static final short PACKET_ID_SET_STEP_HEIGHT = 1;
    private static final short PACKET_ID_SET_DEFAULT_SLIPPERINESS = 2;
    private static final short PACKET_ID_SET_BLOCKS_SLIPPERINESS = 3;
    private static final short PACKET_ID_SET_BOAT_FALL_DAMAGE = 4;
    private static final short PACKET_ID_SET_BOAT_WATER_ELEVATION = 5;
    private static final short PACKET_ID_SET_BOAT_AIR_CONTROL = 6;
    private static final short PACKET_ID_SET_BOAT_JUMP_FORCE = 7;
    private static final short PACKET_ID_SET_GRAVITY = 9;
    private static final short PACKET_ID_SET_YAW_ACCELERATION = 10;
    private static final short PACKET_ID_SET_FORWARD_ACCELERATION = 11;
    private static final short PACKET_ID_SET_BACKWARD_ACCELERATION = 12;
    private static final short PACKET_ID_SET_TURNING_FORWARD_ACCELERATION = 13;
    private static final short PACKET_ID_ALLOW_ACCELERATION_STACKING = 14;
    private static final short PACKET_ID_SET_UNDERWATER_CONTROL = 16;
    private static final short PACKET_ID_SET_SURFACE_WATER_CONTROL = 17;
    private static final short PACKET_ID_SET_COYOTE_TIME = 19;
    private static final short PACKET_ID_SET_WATER_JUMPING = 20;
    private static final short PACKET_ID_SET_SWIM_FORCE = 21;
    private static final short PACKET_ID_SET_PER_BLOCK_SETTING = 26;
    private static final short PACKET_ID_SET_AIR_STEPPING = 28;
    private static final short PACKET_ID_SET_WALLTAP_MULTIPLIER = 34;
    private static final short PACKET_ID_SET_JUMPS = 35;
    private static final short PACKET_ID_SET_SCALE = 36;
    private static final short PACKET_ID_SET_STEP_UP_SLIPPERINESS = 37;
    private static final short PACKET_ID_SET_FIX_DOUBLE_WATER_ELEVATION = 39;
    private static final short PACKET_ID_SET_LATERAL_SLIPPERINESS = 40;
    private static final short PACKET_ID_SET_BRAKE_SLIPPERINESS = 41;
    private static final short PACKET_ID_SET_MULTI_STEPPING = 44;
    private static final short PACKET_ID_SET_MAX_SPEED = 45;
    private static final short PACKET_ID_SET_MAX_SPEED_RESISTANCE = 46;
    private static final short PACKET_ID_SET_HONEY_COMPATIBILITY = 47;

    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    @Expose
    private String name;
    @Expose
    private float stepHeight;
    @Expose
    private float defaultSlipperiness;
    @Expose
    private Map<String, Float> blocksSlipperiness = new HashMap<>();
    @Expose
    private boolean boatFallDamage;
    @Expose
    private boolean boatWaterElevation;
    @Expose
    private boolean boatAirControl;
    @Expose
    private float boatJumpForce;
    @Expose
    private boolean airStepping;
    @Expose
    private double gravity;
    @Expose
    private float yawAcceleration;
    @Expose
    private float forwardAcceleration;
    @Expose
    private float backwardAcceleration;
    @Expose
    private float turningForwardAcceleration;
    @Expose
    private boolean allowAccelerationStacking;
    @Expose
    private boolean underwaterControl;
    @Expose
    private boolean surfaceWaterControl;
    @Expose
    private int coyoteTime;
    @Expose
    private boolean waterJumping;
    @Expose
    private float swimForce;
    @Expose
    private float walltapMultiplier;
    @Expose
    private int jumps;
    @Expose
    private float scale;
    @Expose
    private float stepUpSlipperiness;
    @Expose
    private boolean fixDoubleWaterElevation;
    @Expose
    private float lateralSlipperiness;
    @Expose
    private float brakeSlipperiness;
    @Expose
    private boolean multiStepping;
    @Expose
    private float maxSpeed;
    @Expose
    private float maxSpeedResistance;
    @Expose
    private boolean honeyCompatibility;

    @Expose
    private Map<String, PerBlockSetting> perBlockSettings = new HashMap<>();

    public CustomBoatUtilsMode() {
        resetToVanilla();
    }

    public void resetToVanilla() {
        stepHeight = 0f;
        defaultSlipperiness = 0.6f;
        blocksSlipperiness.clear();
        boatFallDamage = true;
        boatWaterElevation = false;
        boatAirControl = false;
        boatJumpForce = 0f;
        airStepping = false;
        gravity = -0.03999999910593033;
        yawAcceleration = 1.0f;
        forwardAcceleration = 0.04f;
        backwardAcceleration = 0.005f;
        turningForwardAcceleration = 0.005f;
        allowAccelerationStacking = false;
        underwaterControl = false;
        surfaceWaterControl = false;
        coyoteTime = 0;
        waterJumping = false;
        swimForce = 0f;
        perBlockSettings.clear();
        walltapMultiplier = 0f;
        jumps = 0;
        scale = 1;
        stepUpSlipperiness = 1f;
        fixDoubleWaterElevation = false;
        lateralSlipperiness = 1f;
        brakeSlipperiness = 1f;
        multiStepping = false;
        maxSpeed = -1;
        maxSpeedResistance = 0;
        honeyCompatibility = false;
    }

    public boolean applyToPlayer(Player player) {
        if (playerHasCorrectVersion(player)) {
            finallyApplyToPlayer(player);
            return true;
        } else {
            TPlayer tPlayer = TimingSystemAPI.getTPlayer(player.getUniqueId());
            var boatUtilsWarning = tPlayer.getTheme().warning(">> ").append(Text.get(player, Warning.TRACK_REQUIRES_NEWER_BOAT_UTILS)).append(tPlayer.getTheme().warning(" <<"))
                .hoverEvent(HoverEvent.showText(Text.get(player, Hover.CLICK_TO_OPEN)))
                .clickEvent(ClickEvent.openUrl("https://modrinth.com/mod/openboatutils"));
            player.sendMessage(boatUtilsWarning);
            return false;
        }

    }

    private void finallyApplyToPlayer(Player player) {
        resetPlayer(player);

        if (this.stepHeight != 0f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_STEP_HEIGHT, this.stepHeight);
        if (this.defaultSlipperiness != 0.6f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_DEFAULT_SLIPPERINESS, this.defaultSlipperiness);
        if (!this.boatFallDamage)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_BOAT_FALL_DAMAGE, this.boatFallDamage);
        if (this.boatWaterElevation)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_BOAT_WATER_ELEVATION, this.boatWaterElevation);
        if (this.boatAirControl)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_BOAT_AIR_CONTROL, this.boatAirControl);
        if (this.boatJumpForce != 0f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_BOAT_JUMP_FORCE, this.boatJumpForce);
        if (this.airStepping)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_AIR_STEPPING, this.airStepping);
        if (this.gravity != -0.03999999910593033)
            sendShortAndDoublePacket(player, PACKET_ID_SET_GRAVITY, this.gravity);
        if (this.yawAcceleration != 1.0f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_YAW_ACCELERATION, this.yawAcceleration);
        if (this.forwardAcceleration != 0.04f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_FORWARD_ACCELERATION, this.forwardAcceleration);
        if (this.backwardAcceleration != 0.005f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_BACKWARD_ACCELERATION, this.backwardAcceleration);
        if (this.turningForwardAcceleration != 0.005f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_TURNING_FORWARD_ACCELERATION,
                    this.turningForwardAcceleration);
        if (this.allowAccelerationStacking)
            sendShortAndBooleanPacket(player, PACKET_ID_ALLOW_ACCELERATION_STACKING, this.allowAccelerationStacking);
        if (this.underwaterControl)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_UNDERWATER_CONTROL, this.underwaterControl);
        if (this.surfaceWaterControl)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_SURFACE_WATER_CONTROL, this.surfaceWaterControl);
        if (this.coyoteTime != 0)
            sendShortAndIntPacket(player, PACKET_ID_SET_COYOTE_TIME, this.coyoteTime);
        if (this.waterJumping)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_WATER_JUMPING, this.waterJumping);
        if (this.swimForce != 0f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_SWIM_FORCE, this.swimForce);
        if (this.walltapMultiplier != 0f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_WALLTAP_MULTIPLIER, this.walltapMultiplier);
        if (this.jumps != 0)
            sendShortAndIntPacket(player, PACKET_ID_SET_JUMPS, this.jumps);
        if (this.scale != 1)
            sendShortAndFloatPacket(player, PACKET_ID_SET_SCALE, this.scale);
        if (this.stepUpSlipperiness != 1f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_STEP_UP_SLIPPERINESS, this.stepUpSlipperiness);
        if (this.fixDoubleWaterElevation)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_FIX_DOUBLE_WATER_ELEVATION, true);
        if (this.lateralSlipperiness != 1f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_LATERAL_SLIPPERINESS, this.lateralSlipperiness);
        if (this.brakeSlipperiness != 1f)
            sendShortAndFloatPacket(player, PACKET_ID_SET_BRAKE_SLIPPERINESS, this.brakeSlipperiness);
        if (this.multiStepping)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_MULTI_STEPPING, true);
        if (this.maxSpeed >= 0)
            sendShortAndFloatPacket(player, PACKET_ID_SET_MAX_SPEED, this.maxSpeed);
        if (this.maxSpeedResistance > 0)
            sendShortAndFloatPacket(player, PACKET_ID_SET_MAX_SPEED_RESISTANCE, this.maxSpeedResistance);
        if (this.honeyCompatibility)
            sendShortAndBooleanPacket(player, PACKET_ID_SET_HONEY_COMPATIBILITY, true);

        // Block slipperiness settings
        for (Map.Entry<String, Float> entry : this.blocksSlipperiness.entrySet()) {
            sendShortAndFloatAndStringPacket(player, PACKET_ID_SET_BLOCKS_SLIPPERINESS, entry.getValue(),
                    entry.getKey());
        }

        // Per-block settings
        for (PerBlockSetting setting : this.perBlockSettings.values()) {
            sendShortAndShortAndFloatAndStringPacket(player, PACKET_ID_SET_PER_BLOCK_SETTING, setting.getType(),
                    setting.getAsFloat(), setting.getBlockId());
        }
    }

    public static void resetPlayer(Player player) {
        sendPacket(player, PACKET_ID_RESET);
    }

    // <editor-fold desc="Packet Sending Helpers">
    private static void sendPacket(Player player, short packetId) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndBooleanPacket(Player player, short packetId, boolean value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeBoolean(value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndFloatPacket(Player player, short packetId, float value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeFloat(value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndDoublePacket(Player player, short packetId, double value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeDouble(value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndIntPacket(Player player, short packetId, int value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeInt(value);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndFloatAndStringPacket(Player player, short packetId, float value,
            String stringValue) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeFloat(value);
            writeString(out, stringValue);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void sendShortAndShortAndFloatAndStringPacket(Player player, short packetId, short settingType,
            float value, String stringValue) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(byteStream)) {
            out.writeShort(packetId);
            out.writeShort(settingType);
            out.writeFloat(value);
            writeString(out, stringValue);
            player.sendPluginMessage(TimingSystem.getPlugin(), "openboatutils:settings", byteStream.toByteArray());
        } catch (IOException e) {
            logPacketError(player, packetId, e);
        }
    }

    private static void logPacketError(Player player, short packetId, IOException e) {
        TimingSystem.getPlugin().getLogger().log(Level.SEVERE,
                "Failed to serialize and send packet " + packetId + " for player " + player.getName(), e);
    }
    // </editor-fold>

    // Writes a String to a DataOutputStream in a format compatible with Minecraft's PacketByteBuf
    public static void writeString(DataOutputStream out, String stringValue) throws IOException {
        byte[] stringBytes = stringValue.getBytes(StandardCharsets.UTF_8);
        int length = stringBytes.length;

        while (true) {
            if ((length & ~SEGMENT_BITS) == 0) {
                out.writeByte(length);
                break;
            }
            out.writeByte((length & SEGMENT_BITS) | CONTINUE_BIT);
            length >>>= 7;
        }

        out.write(stringBytes);
    }

    // Applies settings from another mode to this mode
    public void applySettingsFrom(CustomBoatUtilsMode other) {
        if (other == null)
            return;

        if (other.stepHeight != 0f)
            this.stepHeight = other.stepHeight;
        if (other.defaultSlipperiness != 0.6f)
            this.defaultSlipperiness = other.defaultSlipperiness;
        this.blocksSlipperiness.putAll(other.blocksSlipperiness);
        this.boatFallDamage = other.boatFallDamage;
        this.boatWaterElevation = other.boatWaterElevation;
        this.boatAirControl = other.boatAirControl;
        if (other.boatJumpForce != 0f)
            this.boatJumpForce = other.boatJumpForce;
        if (other.gravity != -0.03999999910593033)
            this.gravity = other.gravity;
        if (other.yawAcceleration != 1.0f)
            this.yawAcceleration = other.yawAcceleration;
        if (other.forwardAcceleration != 0.04f)
            this.forwardAcceleration = other.forwardAcceleration;
        if (other.backwardAcceleration != 0.005f)
            this.backwardAcceleration = other.backwardAcceleration;
        if (other.turningForwardAcceleration != 0.005f)
            this.turningForwardAcceleration = other.turningForwardAcceleration;
        this.allowAccelerationStacking = other.allowAccelerationStacking;
        this.underwaterControl = other.underwaterControl;
        this.surfaceWaterControl = other.surfaceWaterControl;
        if (other.coyoteTime != 0)
            this.coyoteTime = other.coyoteTime;
        this.waterJumping = other.waterJumping;
        if (other.swimForce != 0f)
            this.swimForce = other.swimForce;
        this.perBlockSettings.putAll(other.perBlockSettings);
        this.airStepping = other.airStepping;

        this.walltapMultiplier = other.walltapMultiplier;
        this.jumps = other.jumps;
        this.scale = other.scale;
        this.stepUpSlipperiness = other.stepUpSlipperiness;
        this.fixDoubleWaterElevation = other.fixDoubleWaterElevation;
        this.lateralSlipperiness = other.lateralSlipperiness;
        this.brakeSlipperiness = other.brakeSlipperiness;
        this.multiStepping = other.multiStepping;
        this.maxSpeed = other.maxSpeed;
        this.maxSpeedResistance = other.maxSpeedResistance;
        this.honeyCompatibility = other.honeyCompatibility;
    }

    public void setBlocksSlipperiness(float slipperiness, String blockIds) {
        if (blockIds == null || blockIds.trim().isEmpty()) {
            return;
        }

        String[] blocks = blockIds.split(",");
        for (String blockId : blocks) {
            blockId = blockId.trim();
            if (!blockId.isEmpty()) {
                blocksSlipperiness.put(blockId, slipperiness);
            }
        }
    }

    public void clearBlocksSlipperiness(String blockIds) {
        if (blockIds == null || blockIds.trim().isEmpty()) {
            return;
        }

        String[] blocks = blockIds.split(",");
        for (String blockId : blocks) {
            blockId = blockId.trim();
            if (!blockId.isEmpty()) {
                blocksSlipperiness.remove(blockId);
            }
        }
    }

    public void clearAllSlipperiness() {
        blocksSlipperiness.clear();
    }

    public void setPerBlockSetting(short settingType, Object value, String blockIds) {
        if (blockIds == null || blockIds.trim().isEmpty()) {
            return;
        }

        String[] blocks = blockIds.split(",");
        for (String blockId : blocks) {
            blockId = blockId.trim();
            if (!blockId.isEmpty()) {
                String key = blockId + ":" + settingType;
                perBlockSettings.put(key, new PerBlockSetting(settingType, value, blockId));
            }
        }
    }

    public void clearPerBlockSettings(String blockIds) {
        if (blockIds == null || blockIds.trim().isEmpty()) {
            return;
        }

        String[] blocks = blockIds.split(",");
        for (String blockId : blocks) {
            String trimmedBlockId = blockId.trim();
            if (!trimmedBlockId.isEmpty()) {
                perBlockSettings.entrySet().removeIf(entry -> entry.getValue().getBlockId().equals(trimmedBlockId));
            }
        }
    }

    public void clearAllPerBlockSettings() {
        perBlockSettings.clear();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CustomBoatUtilsMode fromJson(String json) {
        return GSON.fromJson(json, CustomBoatUtilsMode.class);
    }

    public Map<String, List<NonDefaultSetting>> getNonDefaultSettings() {
        Map<String, List<NonDefaultSetting>> nonDefaultSettings = new HashMap<>();

        // --- Numeric Settings ---
        List<NonDefaultSetting> numericSettings = new ArrayList<>();
        if (this.stepHeight != 0f)
            numericSettings.add(new NonDefaultSetting("stepHeight", this.stepHeight, 0f));
        if (this.defaultSlipperiness != 0.6f)
            numericSettings.add(new NonDefaultSetting("defaultSlipperiness", this.defaultSlipperiness, 0.6f));
        if (this.boatJumpForce != 0f)
            numericSettings.add(new NonDefaultSetting("boatJumpForce", this.boatJumpForce, 0f));
        if (this.yawAcceleration != 1.0f)
            numericSettings.add(new NonDefaultSetting("yawAcceleration", this.yawAcceleration, 1.0f));
        if (this.forwardAcceleration != 0.04f)
            numericSettings.add(new NonDefaultSetting("forwardAcceleration", this.forwardAcceleration, 0.04f));
        if (this.backwardAcceleration != 0.005f)
            numericSettings.add(new NonDefaultSetting("backwardAcceleration", this.backwardAcceleration, 0.005f));
        if (this.turningForwardAcceleration != 0.005f)
            numericSettings.add(new NonDefaultSetting("turningForwardAcceleration", this.turningForwardAcceleration, 0.005f));
        if (this.swimForce != 0f)
            numericSettings.add(new NonDefaultSetting("swimForce", this.swimForce, 0f));
        if (this.gravity != -0.03999999910593033)
            numericSettings.add(new NonDefaultSetting("gravity", this.gravity, -0.03999999910593033));
        if (this.coyoteTime != 0)
            numericSettings.add(new NonDefaultSetting("coyoteTime", this.coyoteTime, 0));
        if (this.walltapMultiplier != 0f)
            numericSettings.add(new NonDefaultSetting("walltapMultiplier", this.walltapMultiplier, 0));
        if (this.jumps != 0)
            numericSettings.add(new NonDefaultSetting("jumps", this.jumps, 0));
        if (this.scale != 1f)
            numericSettings.add(new NonDefaultSetting("scale", this.scale, 1f));
        if (this.stepUpSlipperiness != 1f)
            numericSettings.add(new NonDefaultSetting("stepUpSlipperiness", this.stepUpSlipperiness, 1f));
        if (this.lateralSlipperiness != 1f)
            numericSettings.add(new NonDefaultSetting("lateralSlipperiness", this.lateralSlipperiness, 1f));
        if (this.brakeSlipperiness != 0)
            numericSettings.add(new NonDefaultSetting("brakeSlipperiness", this.brakeSlipperiness, 0));
        if (this.maxSpeed >= 0)
            numericSettings.add(new NonDefaultSetting("maxSpeed", this.maxSpeed, -1));
        if (this.maxSpeedResistance > 0)
            numericSettings.add(new NonDefaultSetting("maxSpeedResistance", this.maxSpeedResistance, 0));

        if (!numericSettings.isEmpty()) {
            nonDefaultSettings.put("Numeric Settings", numericSettings);
        }

        // --- Boolean Toggles ---
        List<NonDefaultSetting> booleanSettings = new ArrayList<>();
        if (!this.boatFallDamage)
            booleanSettings.add(new NonDefaultSetting("boatFallDamage", this.boatFallDamage, true));
        if (this.boatWaterElevation)
            booleanSettings.add(new NonDefaultSetting("boatWaterElevation", this.boatWaterElevation, false));
        if (this.boatAirControl)
            booleanSettings.add(new NonDefaultSetting("boatAirControl", this.boatAirControl, false));
        if (this.airStepping)
            booleanSettings.add(new NonDefaultSetting("airStepping", this.airStepping, false));
        if (this.allowAccelerationStacking)
            booleanSettings.add(new NonDefaultSetting("allowAccelerationStacking", this.allowAccelerationStacking, false));
        if (this.underwaterControl)
            booleanSettings.add(new NonDefaultSetting("underwaterControl", this.underwaterControl, false));
        if (this.surfaceWaterControl)
            booleanSettings.add(new NonDefaultSetting("surfaceWaterControl", this.surfaceWaterControl, false));
        if (this.waterJumping)
            booleanSettings.add(new NonDefaultSetting("waterJumping", this.waterJumping, false));
        if (this.fixDoubleWaterElevation)
            booleanSettings.add(new NonDefaultSetting("fixDoubleWaterElevation", this.fixDoubleWaterElevation, false));
        if (this.multiStepping)
            booleanSettings.add(new NonDefaultSetting("multiStepping", this.multiStepping, false));
        if (this.honeyCompatibility)
            booleanSettings.add(new NonDefaultSetting("honeyCompatibility", this.honeyCompatibility, false));

        if (!booleanSettings.isEmpty()) {
            nonDefaultSettings.put("Boolean Toggles", booleanSettings);
        }

        // --- Block Slipperiness ---
        if (this.blocksSlipperiness != null && !this.blocksSlipperiness.isEmpty()) {
            List<NonDefaultSetting> slipperinessList = new ArrayList<>();
            this.blocksSlipperiness.forEach(
                    (blockId, slipperiness) -> slipperinessList.add(
                            new NonDefaultSetting(blockId, slipperiness, null) // No simple default for a map entry
                    )
            );
            nonDefaultSettings.put("Block Slipperiness", slipperinessList);
        }

        // --- Per-Block Settings ---
        if (this.perBlockSettings != null && !this.perBlockSettings.isEmpty()) {
            Map<Short, List<NonDefaultSetting>> settingsByType = new HashMap<>();
            this.perBlockSettings.forEach((blockId, setting) -> {
                settingsByType
                        .computeIfAbsent(setting.getType(), k -> new ArrayList<>())
                        .add(new NonDefaultSetting(blockId, setting.getValue(), null)); // No simple default
            });

            settingsByType.forEach((type, settings) -> {
                nonDefaultSettings.put("Per-Block Settings (Type " + type + ")", settings);
            });
        }

        return nonDefaultSettings;
    }

    private int getVersionRequirementFromSettingName(String settingName) {
        return switch (settingName) {
            case "walltapMultiplier",
                 "jumps",
                 "scale",
                 "stepUpSlipperiness" -> 19;
            case "fixDoubleWaterElevation",
                 "lateralSlipperiness",
                 "brakeSlipperiness",
                 "multiStepping",
                 "maxSpeed",
                 "maxSpeedResistance",
                 "honeyCompatibility" -> 20;
            default -> 11;
        };
    }

    public int getRequiredVersion() {
        Map<String, List<NonDefaultSetting>> nonDefaultSettings = this.getNonDefaultSettings();

        return nonDefaultSettings.values().stream()
                .flatMap(List::stream)
                .map(NonDefaultSetting::name)
                .mapToInt(this::getVersionRequirementFromSettingName)
                .max()
                .orElse(0);
    }

    public boolean playerHasCorrectVersion(Player player) {
        if (player == null) return false;
        TPlayer tplayer = TimingSystemAPI.getTPlayer(player.getUniqueId());
        if (!tplayer.hasBoatUtils()) return false;
        return (tplayer.getBoatUtilsVersion() >= getRequiredVersion());
    }
}