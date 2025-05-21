package me.makkuusen.timing.system.tplayer;

import co.aikar.idb.DbRow;
import lombok.Getter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.EventDatabase;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import java.awt.*;
import java.util.UUID;

@Getter
public class Settings {

    private final UUID uuid;
    private Boat.Type boat;
    private boolean chestBoat;
    private boolean toggleSound;
    private String color;
    private boolean verbose;
    private boolean timeTrial;
    private boolean override;
    private boolean compactScoreboard;
    private boolean sendFinalLaps;
    private String shortName;
    private boolean lonely;

    public Settings(TPlayer tPlayer, DbRow data) {
        this.uuid = tPlayer.getUniqueId();
        boat = stringToType(data.getString("boat"));
        chestBoat = getBoolean(data, "chestBoat");
        toggleSound = getBoolean(data, "toggleSound");
        verbose = getBoolean(data, "verbose");
        timeTrial = getBoolean(data, "timetrial");
        color = data.getString("color");
        compactScoreboard = getBoolean(data, "compactScoreboard");
        sendFinalLaps = getBoolean(data, "sendFinalLaps");
        shortName = data.getString("shortName") != null ? data.getString("shortName") : extractShortName(tPlayer.getName());
    }

    private String extractShortName(String name) {
        return name.length() < 5 ? name : name.substring(0, 4);
    }

    private boolean getBoolean(DbRow data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        return false;  // Default value if key is missing or not a boolean/number
    }

    public String getHexColor() {
        return color;
    }

    public void setHexColor(String hexColor) {
        color = hexColor;
        EventDatabase.getDriverFromRunningHeat(uuid).ifPresent(driver -> driver.getHeat().updateScoreboard());
        TimingSystem.getDatabase().playerUpdateValue(uuid, "color", hexColor);
    }

    public org.bukkit.Color getBukkitColor() {
        var c = Color.decode(color);
        return org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue());
    }

    public TextColor getTextColor() {
        return TextColor.fromHexString(color);
    }

    public void setShortName(String name) {
        this.shortName = name;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "shortName", name);
    }

    public void setBoat(Boat.Type boat) {
        this.boat = boat;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "boat", boat.name());
    }

    public void setChestBoat(boolean b) {
        if (chestBoat == b)
            return;
        chestBoat = b;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "chestBoat", chestBoat);
    }

    public void toggleOverride() {
        override = !override;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "override", override);
    }

    public void toggleVerbose() {
        verbose = !verbose;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "verbose", verbose);
    }

    public void toggleTimeTrial() {
        timeTrial = !timeTrial;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "timetrial", timeTrial);
    }

    public void toggleSound() {
        toggleSound = !toggleSound;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "toggleSound", toggleSound);
    }

    public void toggleCompactScoreboard() {
        this.compactScoreboard = !compactScoreboard;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "compactScoreboard", compactScoreboard);
    }

    public void toggleSendFinalLaps() {
        sendFinalLaps = !sendFinalLaps;
        TimingSystem.getDatabase().playerUpdateValue(uuid, "sendFinalLaps", sendFinalLaps);
    }

    public boolean isCompactScoreboard() {
        return compactScoreboard;
    }

    public boolean getCompactScoreboard() {
        return compactScoreboard;
    }

    public boolean isSound() {
        return toggleSound;
    }

    public Material getBoatMaterial() {
        String boat = getBoat().name();
        if (chestBoat) {
            boat += "_CHEST";
        }
        if (boat.contains("BAMBOO")) {
            boat += "_RAFT";
        } else {
            boat += "_BOAT";
        }
        return Material.valueOf(boat);
    }

    private Boat.Type stringToType(String boatType) {
        if (boatType == null) {
            return Boat.Type.BIRCH;
        }
        try {
            return Boat.Type.valueOf(boatType);
        } catch (IllegalArgumentException e) {
            //REDWOOD is the only old option possible.
            return Boat.Type.SPRUCE;
        }
    }
}
