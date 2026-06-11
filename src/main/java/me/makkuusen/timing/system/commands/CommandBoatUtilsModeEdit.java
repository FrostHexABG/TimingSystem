package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.boatutils.NonDefaultSetting;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.theme.messages.Success;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandAlias("boatutilsmodeedit|bume|bumode")
public class CommandBoatUtilsModeEdit extends BaseCommand {

    private static final Map<Player, CustomBoatUtilsMode> modeEditSessions = new HashMap<>();

    @Subcommand("create")
    @CommandCompletion("name")
    @CommandPermission("%permissionboatutilsmode_create")
    public static void onCreate(Player player, String name) {
        if (TimingSystem.getTrackDatabase().getCustomBoatUtilsModeFromName(name) != null) {
            Text.send(player, Error.CUSTOM_BOATUTILS_MODE_EXISTS, "%mode%", name);
            return;
        }

        if (!name.matches("[a-zA-Z0-9_]+")) {
            Text.send(player, Error.CUSTOM_BOATUTILS_MODE_INVALID_NAME);
            return;
        }

        CustomBoatUtilsMode mode = new CustomBoatUtilsMode();
        mode.setName(name);
        modeEditSessions.put(player, mode);
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_CREATED, "%mode%", name);
    }

    @Subcommand("edit")
    @CommandCompletion("@bume")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onEdit(Player player, String name) {
        CustomBoatUtilsMode mode = TimingSystem.getTrackDatabase().getCustomBoatUtilsModeFromName(name);
        if (mode == null) {
            Text.send(player, Error.CUSTOM_BOATUTILS_MODE_NOT_FOUND, "%mode%", name);
            return;
        }
        modeEditSessions.put(player, mode);
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_SELECTED, "%mode%", name);
    }

    @Subcommand("save")
    @CommandPermission("%permissionboatutilsmode_save")
    public static void onSave(Player player) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        boolean success = TimingSystem.getTrackDatabase().saveOrUpdateCustomBoatUtilsMode(mode);
        if (success) {
            Text.send(player, Success.CUSTOM_BOATUTILS_MODE_SAVED, "%mode%", mode.getName());
        } else {
            Text.send(player, Error.FAILED_TO_SAVE_CUSTOM_BOATUTILS_MODE);
        }
    }

    @Subcommand("set")
    @CommandCompletion("name|stepHeight|defaultSlipperiness|boatJumpForce|yawAcceleration|forwardAcceleration|backwardAcceleration|turningForwardAcceleration|swimForce|gravity|boatFallDamage|boatWaterElevation|boatAirControl|airStepping|allowAccelerationStacking|underwaterControl|surfaceWaterControl|waterJumping|coyoteTime|walltapMultiplier|jumps|scale|stepUpSlipperiness|fixDoubleWaterElevation|lateralSlipperiness|brakeSlipperiness|multiStepping|maxSpeed|maxSpeedResistance|honeyCompatibility <value>")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onSet(Player player, String property, String value) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        try {
            switch (property.toLowerCase()) {
                case "name" -> mode.setName(value);
                case "stepheight" -> mode.setStepHeight(Float.parseFloat(value));
                case "defaultslipperiness" -> mode.setDefaultSlipperiness(Float.parseFloat(value));
                case "boatjumpforce" -> mode.setBoatJumpForce(Float.parseFloat(value));
                case "yawacceleration" -> mode.setYawAcceleration(Float.parseFloat(value));
                case "forwardacceleration" -> mode.setForwardAcceleration(Float.parseFloat(value));
                case "backwardacceleration" -> mode.setBackwardAcceleration(Float.parseFloat(value));
                case "turningforwardacceleration" -> mode.setTurningForwardAcceleration(Float.parseFloat(value));
                case "swimforce" -> mode.setSwimForce(Float.parseFloat(value));
                case "gravity" -> mode.setGravity(Double.parseDouble(value));
                case "boatfalldamage" -> mode.setBoatFallDamage(Boolean.parseBoolean(value));
                case "boatwaterelevation" -> mode.setBoatWaterElevation(Boolean.parseBoolean(value));
                case "boataircontrol" -> mode.setBoatAirControl(Boolean.parseBoolean(value));
                case "airstepping" -> mode.setAirStepping(Boolean.parseBoolean(value));
                case "allowaccelerationstacking" -> mode.setAllowAccelerationStacking(Boolean.parseBoolean(value));
                case "underwatercontrol" -> mode.setUnderwaterControl(Boolean.parseBoolean(value));
                case "surfacewatercontrol" -> mode.setSurfaceWaterControl(Boolean.parseBoolean(value));
                case "waterjumping" -> mode.setWaterJumping(Boolean.parseBoolean(value));
                case "coyotetime" -> mode.setCoyoteTime(Integer.parseInt(value));
                case "walltapmultiplier" -> mode.setWalltapMultiplier(Float.parseFloat(value));
                case "jumps" -> mode.setJumps(Integer.parseInt(value));
                case "scale" -> mode.setScale(Float.parseFloat(value));
                case "stepupslipperiness" -> mode.setStepUpSlipperiness(Float.parseFloat(value));
                case "fixdoublewaterelevation" -> mode.setFixDoubleWaterElevation(Boolean.parseBoolean(value));
                case "lateralslipperiness" -> mode.setLateralSlipperiness(Float.parseFloat(value));
                case "brakeslipperiness" -> mode.setBrakeSlipperiness(Float.parseFloat(value));
                case "multistepping" -> mode.setMultiStepping(Boolean.parseBoolean(value));
                case "maxspeed" -> mode.setMaxSpeed(Float.parseFloat(value));
                case "maxspeedresistance" -> mode.setMaxSpeedResistance(Float.parseFloat(value));
                case "honeycompatibility" -> mode.setHoneyCompatibility(Boolean.parseBoolean(value));
            }
            Text.send(player, Success.CUSTOM_BOATUTILS_MODE_PROPERTY_SET, "%property%", property, "%value%", value);
        } catch (NumberFormatException e) {
            Text.send(player, Error.NUMBER_FORMAT);
        } catch (Exception e) {
            Text.send(player, Error.SYNTAX);
        }
    }

    @Subcommand("addblockslip")
    @CommandCompletion("<slipperiness> <blockIds>")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onAddBlockSlip(Player player, float slipperiness, String blockIds) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        String normalizedBlockIds = normalizeBlockIds(blockIds);
        mode.setBlocksSlipperiness(slipperiness, normalizedBlockIds);
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_BLOCK_SLIP_ADDED, "%blocks%", normalizedBlockIds);
    }

    @Subcommand("clearblockslip")
    @CommandCompletion("<blockIds>")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onClearBlockSlip(Player player, String blockIds) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        String normalizedBlockIds = normalizeBlockIds(blockIds);
        mode.clearBlocksSlipperiness(normalizedBlockIds);
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_BLOCK_SLIP_CLEARED, "%blocks%", normalizedBlockIds);
    }

    @Subcommand("clearallblockslip")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onClearAllBlockSlip(Player player) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        mode.clearAllSlipperiness();
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_BLOCK_SLIP_CLEARED, "%blocks%", "all blocks");
    }

    @Subcommand("addperblock")
    @CommandCompletion("boatJumpForce|forwardAcceleration|backwardAcceleration|yawAcceleration|turningForwardAcceleration|walltapMultiplier|jumps|coyoteTime|stepUpSlipperiness|lateralSlipperiness|brakeSlipperiness|maxSpeed|maxSpeedResistance <value> <blockIds>")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onAddPerBlock(Player player, String settingName, String value, String blockIds) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }

        try {
            short settingType = getSettingTypeFromName(settingName);
            Object parsedValue = parseValueForSetting(settingName, value);
            String normalizedBlockIds = normalizeBlockIds(blockIds);

            mode.setPerBlockSetting(settingType, parsedValue, normalizedBlockIds);
            Text.send(player, Success.CUSTOM_BOATUTILS_MODE_PER_BLOCK_ADDED, "%blocks%", normalizedBlockIds);
        } catch (IllegalArgumentException e) {
            Text.send(player, Error.SYNTAX);
        }
    }

    /**
     * Normalizes block IDs by adding the minecraft: prefix if not present
     * @param blockIds Comma-separated block IDs
     * @return Normalized block IDs with minecraft: prefix
     */
    private static String normalizeBlockIds(String blockIds) {
        if (blockIds == null || blockIds.trim().isEmpty()) {
            return blockIds;
        }
        
        String[] blocks = blockIds.split(",");
        StringBuilder normalized = new StringBuilder();
        
        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (!block.contains(":")) {
                block = "minecraft:" + block;
            }
            normalized.append(block);
            if (i < blocks.length - 1) {
                normalized.append(",");
            }
        }
        
        return normalized.toString();
    }

    /**
     * Maps setting names to their corresponding per-block setting IDs
     * These are different from global setting packet IDs and follow the BoatUtils documentation:
     * jumpForce=0, forwardsAccel=1, backwardsAccel=2, yawAccel=3, turnForwardsAccel=4
     */
    private static short getSettingTypeFromName(String settingName) throws IllegalArgumentException {
        return switch (settingName.toLowerCase()) {
            case "boatjumpforce" -> 0;  // jumpForce
            case "forwardacceleration" -> 1;  // forwardsAccel
            case "backwardacceleration" -> 2;  // backwardsAccel
            case "yawacceleration" -> 3;  // yawAccel
            case "turningforwardacceleration" -> 4;  // turnForwardsAccel
            case "walltapmultiplier" -> 5;
            case "jumps" -> 6;
            case "coyotetime" -> 7;
            case "stepupslipperiness" -> 8;
            case "lateralslipperiness" -> 9;
            case "brakeslipperiness" -> 10;
            case "maxspeed" -> 11;
            case "maxspeedresistance" -> 12;
            default -> throw new IllegalArgumentException("Per-block setting not supported: " + settingName + 
                ". Only jumpForce, forwardsAccel, backwardsAccel, yawAccel, and turnForwardsAccel are supported for per-block settings.");
        };
    }

    /**
     * Parses the value string for per-block settings
     * All per-block settings are float values according to BoatUtils documentation
     */
    private static Object parseValueForSetting(String settingName, String value) throws NumberFormatException {
        return switch (settingName.toLowerCase()) {
            case "boatjumpforce", "forwardacceleration", "backwardacceleration", 
                    "yawacceleration", "turningforwardacceleration" ->
                Float.parseFloat(value);
            default -> throw new IllegalArgumentException("Unsupported per-block setting: " + settingName);
        };
    }

    @Subcommand("clearperblock")
    @CommandCompletion("<blockIds>")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onClearPerBlock(Player player, String blockIds) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        String normalizedBlockIds = normalizeBlockIds(blockIds);
        mode.clearPerBlockSettings(normalizedBlockIds);
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_PER_BLOCK_CLEARED, "%blocks%", normalizedBlockIds);
    }

    @Subcommand("clearallperblock")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onClearAllPerBlock(Player player) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        mode.clearAllPerBlockSettings();
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_PER_BLOCK_CLEARED, "%blocks%", "all blocks");
    }

    @Subcommand("reset")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onReset(Player player) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }
        mode.resetToVanilla();
        Text.send(player, Success.CUSTOM_BOATUTILS_MODE_RESET);
    }

    @Subcommand("info")
    @CommandPermission("%permissionboatutilsmode_edit")
    public static void onInfo(Player player) {
        CustomBoatUtilsMode mode = modeEditSessions.get(player);
        if (mode == null) {
            Text.send(player, Error.NO_CUSTOM_BOATUTILS_MODE_SELECTED);
            return;
        }

        Text.send(player, Info.BUME_INFO_DIVIDER);
        Text.send(player, Info.BUME_INFO_TITLE, "%mode%", mode.getName());
        Text.send(player, Info.BUME_INFO_DIVIDER);

        Map<String, List<NonDefaultSetting>> nonDefaultSettings = mode.getNonDefaultSettings();

        if (nonDefaultSettings.isEmpty()) {
            Text.send(player, Info.BUME_ALL_SETTINGS_DEFAULT);
        } else {
            processSimpleSettings(player, "Numeric Settings", Info.BUME_NUMERIC_SETTINGS_TITLE, nonDefaultSettings);
            processSimpleSettings(player, "Boolean Toggles", Info.BUME_BOOLEAN_SETTINGS_TITLE, nonDefaultSettings);

            nonDefaultSettings.forEach((category, settings) -> {
                if (category.equals("Numeric Settings") || category.equals("Boolean Toggles")) {
                    return;
                }

                Text.send(player, Info.BUME_PER_BLOCK_SETTING_TITLE, "%setting%", category);
                settings.forEach(setting -> {
                    Text.send(player, Info.BUME_PER_BLOCK_SETTING, "%block%", setting.name().split(":")[1], "%value%", setting.currentValue().toString());
                });
            });
        }

        Text.send(player, Info.BUME_INFO_DIVIDER);
    }

     static void processSimpleSettings(Player player, String categoryKey, Info titleKey, Map<String, List<NonDefaultSetting>> allSettings) {
        List<NonDefaultSetting> settings = allSettings.get(categoryKey);
        if (settings != null && !settings.isEmpty()) {
            Text.send(player, titleKey);
            settings.forEach(setting -> {
                Text.send(player, Info.BUME_SETTING, "%setting%", setting.name(), "%value%", setting.currentValue().toString());
            });
        }
    }
}
