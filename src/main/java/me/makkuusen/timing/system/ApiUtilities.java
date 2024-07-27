package me.makkuusen.timing.system;

import co.aikar.taskchain.TaskChain;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.api.events.BoatSpawnEvent;
import me.makkuusen.timing.system.boat.v1_20_R4.BoatSpawner;
import me.makkuusen.timing.system.boatutils.BoatUtilsManager;
import me.makkuusen.timing.system.boatutils.BoatUtilsMode;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.*;
import me.makkuusen.timing.system.track.regions.TrackCuboidRegion;
import me.makkuusen.timing.system.track.regions.TrackPolyRegion;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApiUtilities {

    private static final String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};


    public static long getTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    public static String locationToString(Location location) {
        if (location == null) {
            return null;
        }

        return location.getWorld().getName() + " " + location.getX() + " " + location.getY() + " " + location.getZ() + " " + location.getYaw() + " " + location.getPitch();
    }

    public static Location stringToLocation(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }

        String[] split = string.split(" ");
        return new Location(Bukkit.getWorld(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]), Float.parseFloat(split[4]), Float.parseFloat(split[5]));
    }

    public static String niceDate(long timestamp) {
        if (timestamp == 0) {
            return "unknown";
        }

        long rightNow = getTimestamp();

        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timestamp * 1000);

        Calendar dateNow = Calendar.getInstance();
        dateNow.setTimeInMillis(rightNow * 1000);

        String day;

        if (date.get(Calendar.DAY_OF_MONTH) == dateNow.get(Calendar.DAY_OF_MONTH) && date.get(Calendar.MONTH) == dateNow.get(Calendar.MONTH) && date.get(Calendar.YEAR) == dateNow.get(Calendar.YEAR)) {
            day = "today";
        } else {
            Calendar dateYesterday = Calendar.getInstance();
            dateYesterday.setTimeInMillis((rightNow - 86400) * 1000);

            day = date.get(Calendar.DAY_OF_MONTH) == dateYesterday.get(Calendar.DAY_OF_MONTH) && date.get(Calendar.MONTH) == dateYesterday.get(Calendar.MONTH) && date.get(Calendar.YEAR) == dateYesterday.get(Calendar.YEAR) ? "yesterday" : date.get(Calendar.DAY_OF_MONTH) + " " + months[date.get(Calendar.MONTH)] + (dateNow.get(Calendar.YEAR) != date.get(Calendar.YEAR) ? " " + date.get(Calendar.YEAR) : "");
        }

        return day + ", " + String.format("%02d", date.get(Calendar.HOUR_OF_DAY)) + ":" + String.format("%02d", date.get(Calendar.MINUTE));
    }

    public static String niceLocation(Location location) {
        return location == null || !location.isWorldLoaded() ? "unknown" : "([" + location.getWorld().getName() + "]" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    public static String parseFlagChange(char[] flagsOriginal, String change) {
        String flagsRaw = new String(flagsOriginal);
        change = change.replace("*", "bcgeptsu");

        boolean isAdding = true;

        for (int i = 0; i < change.length(); i++) {
            char currentChar = change.charAt(i);

            // the first character must be either a + or a -
            if (i == 0 && currentChar != '+' && currentChar != '-') {
                return null;
            }

            if (currentChar == '+') {
                isAdding = true;
                continue;
            } else if (currentChar == '-') {
                isAdding = false;
                continue;
            }

            if (!isValidFlag(currentChar)) {
                return null;
            }

            flagsRaw = isAdding ? flagsRaw + currentChar : flagsRaw.replace(String.valueOf(currentChar), "");
        }

        StringBuilder flagsNew = new StringBuilder();

        for (char flag : flagsRaw.toCharArray()) {
            boolean exists = false;

            for (int j = 0; j < flagsNew.length(); j++) {
                if (flagsNew.charAt(j) == flag) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                flagsNew.append(flag);
            }
        }

        char[] flagsNewArray = flagsNew.toString().toCharArray();

        Arrays.sort(flagsOriginal);
        Arrays.sort(flagsNewArray);

        flagsNew = new StringBuilder(new String(flagsNewArray));

        // don't change if there's nothing to change
        if (flagsNew.toString().equals(new String(flagsOriginal))) {
            return null;
        }

        return flagsNew.toString();
    }

    private static boolean isValidFlag(char currentChar) {
        return currentChar == 'b' || currentChar == 'c' || currentChar == 'g' || currentChar == 'e' || currentChar == 'p' || currentChar == 't' || currentChar == 's' || currentChar == 'u';
    }

    public static Integer parseDurationToMillis(String input) {
        long duration = 0;
        String tmp = "";

        for (Character character : input.toCharArray()) {
            if (character.toString().matches("[0-9]")) {
                tmp += character;
            } else {
                if (tmp.isEmpty()) {
                    return null;
                }

                if (character == 's') {
                    duration += Integer.parseInt(tmp) * 1000L;
                } else if (character == 'm') {
                    duration += (long) Integer.parseInt(tmp) * 60 * 1000;
                } else if (character == 'h') {
                    duration += (long) Integer.parseInt(tmp) * 3600 * 1000;
                } else if (character == 'd') {
                    duration += (long) Integer.parseInt(tmp) * 86400 * 1000;
                } else {
                    return null;
                }

                tmp = "";
            }
        }

        // default to milliseconds
        if (!tmp.isEmpty()) {
            try {
                duration += Integer.parseInt(tmp);
            } catch (Exception exception) {
                return null;
            }
        }

        return Integer.valueOf(String.valueOf(duration));
    }

    public static String formatPermissions(char[] permissions) {
        if (permissions.length == 0) {
            return "(none)";
        }

        StringBuilder output = new StringBuilder();

        for (char permission : permissions) {
            if (permission == 0) {
                continue;
            }
            output.append(permission);
        }

        return output.toString();
    }

    public static ItemStack stringToItem(String string) {
        try {
            YamlConfiguration yamlConfig = new YamlConfiguration();
            yamlConfig.loadFromString(string);

            return yamlConfig.getItemStack("item");
        } catch (Exception exception) {
            TimingSystem.getPlugin().getLogger().warning("Failed to translate from String to ItemStack: " + string);
            return null;
        }
    }

    public static String itemToString(ItemStack item) {
        YamlConfiguration yamlConfig = new YamlConfiguration();
        yamlConfig.set("item", item);

        return yamlConfig.saveToString();
    }

    public static void msgConsole(String msg) {
        TimingSystem.getPlugin().logger.info(msg);
    }

    public static void warnConsole(String msg) {
        TimingSystem.getPlugin().logger.warning(msg);
    }

    public static String formatAsTime(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%03d", (timeInMillis % 1000));

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%02d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%02d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static String formatAsTimeWithoutRounding(long timeInMillis) {
        String toReturn;
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%03d", (timeInMillis % 1000));

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%02d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static String formatAsSeconds(long time) {
        String toReturn;
        long hours = TimeUnit.SECONDS.toHours(time);
        long minutes = TimeUnit.SECONDS.toMinutes(time) % TimeUnit.HOURS.toMinutes(1);
        String seconds = String.format("%02d", time % 60);

        if (hours == 0 && minutes == 0) {
            toReturn = seconds;
        } else if (hours == 0){
            toReturn = String.format("%02d:", minutes) + seconds;
        } else {
            toReturn = String.format("%d:%02d:", hours, minutes) + seconds;
        }
        return toReturn;
    }

    public static String formatAsTimeNoRounding(long time) {
        String toReturn;
        long hours = TimeUnit.MILLISECONDS.toHours(time);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(time) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(time) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%03d", (time % 1000));

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%02d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%02d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static String formatAsTimeSpent(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);

        if (hours == 0) {
            toReturn = minutes + "m";
        } else {
            toReturn = hours + "h " + minutes + "m";
        }
        return toReturn;
    }

    public static String formatAsHeatTimeCountDown(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%02d", seconds);
        } else if (hours == 0) {
            toReturn = String.format("%d:%02d", minutes, seconds);
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return toReturn;
    }

    public static String formatAsRacingGap(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%02d", (timeInMillis % 1000) / 10);

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%02d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static String formatAsQualificationGap(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%02d", (timeInMillis % 1000) / 10);

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%02d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static String formatAsPersonalGap(long time) {
        String toReturn;
        long timeInMillis = getRoundedToTick(time);
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % TimeUnit.MINUTES.toSeconds(1);
        String millis = String.format("%02d", (timeInMillis % 1000) / 10);

        if (hours == 0 && minutes == 0) {
            toReturn = String.format("%d", seconds) + "." + millis;
        } else if (hours == 0) {
            toReturn = String.format("%d:%02d", minutes, seconds) + "." + millis;
        } else {
            toReturn = String.format("%d:%02d:%02d", hours, minutes, seconds) + "." + millis;
        }
        return toReturn;
    }

    public static Boat spawnBoat(Location location, Boat.Type type, boolean isChestBoat) {
        if (!location.isWorldLoaded()) {
            return null;
        }
        
        Boat boat;
        
        if (isChestBoat) {
        	if (isServerVersion1_20_6()) { // TODO add option to turn off boat lag prevention
        		boat = BoatSpawner.spawnChestBoat(location);        		
        	} else {
        		 boat = (Boat) location.getWorld().spawnEntity(location, EntityType.CHEST_BOAT);
        		 
        	}           
        } else {
        	if (isServerVersion1_20_6()) { // TODO add option to turn off boat lag prevention
        		boat = BoatSpawner.spawnBoat(location);        		
        	} else {
        		boat = (Boat) location.getWorld().spawnEntity(location, EntityType.BOAT);        		
        	}           
        }
        
        Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), ()-> {
            boat.setBoatType(type);
            boat.getPersistentDataContainer().set(Objects.requireNonNull(NamespacedKey.fromString("spawned", TimingSystem.getPlugin())), PersistentDataType.INTEGER, 1);
        }, 3);

        return boat;
    }
    
    private static boolean isServerVersion1_20_6() {
    	return Bukkit.getVersion().contains("1.20.6");
    }

    public static Optional<Region> getSelection(Player player) {
        BukkitPlayer bPlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(bPlayer);
        try {
            return Optional.of(session.getSelection(bPlayer.getWorld()));
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
    }

    public static Location getLocationFromBlockVector3(World world, BlockVector3 v) {
        return new Location(world, v.getBlockX(), v.getBlockY(), v.getBlockZ());
    }

    public static Optional<Track> findClosestTrack(Player player) {

        var playerLocation = player.getLocation();
        double minDistance = -1;
        Optional<Track> minDistanceTrack = Optional.empty();

        for (Track track : TrackDatabase.tracks) {

            if (!playerLocation.getWorld().equals(track.getSpawnLocation().getWorld())) {
                continue;
            }

            var distance = playerLocation.distance(track.getSpawnLocation());
            if (minDistanceTrack.isEmpty()) {
                minDistanceTrack = Optional.of(track);
                minDistance = distance;
            } else if (distance < minDistance) {
                minDistance = distance;
                minDistanceTrack = Optional.of(track);
            }
        }
        return minDistanceTrack;
    }

    public static long getRoundedToTick(long mapTime) {
        if (mapTime % 50 == 0) {
            return mapTime;
        } else {
            long mapTime2 = mapTime + 1;
            if (mapTime2 % 50 == 0) {
                return mapTime2;
            } else {
                long rest = mapTime % 50;
                if (rest < 25) {
                    return mapTime - rest;
                } else {
                    mapTime = mapTime + (50 - rest);
                }
            }
        }
        return mapTime;
    }

    public static boolean isRegionMatching(TrackRegion trackRegion, Region selection) {
        if (trackRegion instanceof TrackCuboidRegion && selection instanceof CuboidRegion) {
            return true;
        } else return trackRegion instanceof TrackPolyRegion && selection instanceof Polygonal2DRegion;
    }

    private static final List<String> rejectedWords = Arrays.asList("random", "randomunfinished", "r", "cancel", "c", "help");

    public static boolean checkTrackName(String name) {
        for (String rejected : rejectedWords) {
            if (name.equalsIgnoreCase(rejected)) {
                return true;
            }
        }
        return false;
    }

    public static List<Material> getBoatMaterials() {
        return List.of(Material.BIRCH_BOAT, Material.BIRCH_CHEST_BOAT, Material.ACACIA_BOAT, Material.ACACIA_CHEST_BOAT, Material.DARK_OAK_BOAT, Material.DARK_OAK_CHEST_BOAT, Material.JUNGLE_BOAT, Material.JUNGLE_CHEST_BOAT, Material.MANGROVE_BOAT, Material.MANGROVE_CHEST_BOAT, Material.OAK_BOAT, Material.OAK_CHEST_BOAT, Material.SPRUCE_BOAT, Material.SPRUCE_CHEST_BOAT, Material.CHERRY_BOAT, Material.CHERRY_CHEST_BOAT, Material.BAMBOO_RAFT, Material.BAMBOO_CHEST_RAFT);
    }

    public static Boat.Type getBoatType(Material material) {
        switch (material) {
            case ACACIA_BOAT, ACACIA_CHEST_BOAT -> {
                return Boat.Type.ACACIA;
            }
            case BIRCH_BOAT, BIRCH_CHEST_BOAT -> {
                return Boat.Type.BIRCH;
            }
            case DARK_OAK_BOAT, DARK_OAK_CHEST_BOAT -> {
                return Boat.Type.DARK_OAK;
            }
            case SPRUCE_BOAT, SPRUCE_CHEST_BOAT -> {
                return Boat.Type.SPRUCE;
            }
            case JUNGLE_BOAT, JUNGLE_CHEST_BOAT -> {
                return Boat.Type.JUNGLE;
            }
            case MANGROVE_BOAT, MANGROVE_CHEST_BOAT -> {
                return Boat.Type.MANGROVE;
            }
            case CHERRY_BOAT, CHERRY_CHEST_BOAT -> {
                return Boat.Type.CHERRY;
            }
            case BAMBOO_RAFT, BAMBOO_CHEST_RAFT -> {
                return Boat.Type.BAMBOO;
            }
            default -> {
                return Boat.Type.OAK;
            }
        }
    }

    public static boolean isChestBoat(Material material) {
        return material.name().contains("CHEST_BOAT") || material.name().contains("CHEST_RAFT") ;
    }

    public static String getHexFromDyeColor(Material dye) {
        String dyeColorName = dye.name().replace("_DYE", "");
        try {
            return getHexFromColor(DyeColor.valueOf(dyeColorName).getColor());
        } catch (IllegalArgumentException exception) {
            return "#ffffff";
        }
    }

    public static String getHexFromColor(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static void spawnBoatAndAddPlayer(Player player, Location location) {

        BoatSpawnEvent boatSpawnEvent = new BoatSpawnEvent(player, location);
        Bukkit.getServer().getPluginManager().callEvent(boatSpawnEvent);

        if (boatSpawnEvent.isCancelled()) {
            return;
        }

        if (boatSpawnEvent.getBoat() != null) {
            return;
        }

        BoatUtilsManager.sendBoatUtilsModePluginMessage(player, BoatUtilsMode.VANILLA, null, true);

        var tPlayer = TSDatabase.getPlayer(player.getUniqueId());
        Boat boat = ApiUtilities.spawnBoat(location, tPlayer.getSettings().getBoat(), tPlayer.getSettings().isChestBoat());
        if (boat != null) {
            boat.addPassenger(player);
        }
    }

    public static Boat spawnBoatAndAddPlayerWithBoatUtils(Player player, Location location, Track track, boolean sameAsLastTrack) {

        BoatSpawnEvent boatSpawnEvent = new BoatSpawnEvent(player, location);
        Bukkit.getServer().getPluginManager().callEvent(boatSpawnEvent);

        if (boatSpawnEvent.isCancelled()) {
            return null;
        }

        var mode = track.getBoatUtilsMode();
        BoatUtilsManager.sendBoatUtilsModePluginMessage(player, mode, track, sameAsLastTrack);

        var tPlayer = TSDatabase.getPlayer(player.getUniqueId());

        if (boatSpawnEvent.getBoat() != null) {
            return boatSpawnEvent.getBoat();
        }

        Boat boat = ApiUtilities.spawnBoat(location, tPlayer.getSettings().getBoat(), tPlayer.getSettings().isChestBoat());
        if (boat != null) {
            boat.addPassenger(player);
        }
        return boat;
    }

    public static void teleportPlayerAndSpawnBoat(Player player, Track track, Location location) {

        TaskChain<?> chain = TimingSystem.newChain();
        location.setPitch(player.getLocation().getPitch());
        boolean sameAsLastTrack = TimeTrialController.lastTimeTrialTrack.containsKey(player.getUniqueId()) && TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId()).getId() == track.getId();
        TimeTrialController.lastTimeTrialTrack.put(player.getUniqueId(), track);
        if (player.isInsideVehicle()) {
            if (player.getVehicle().getPassengers().size() < 2) {
                player.getVehicle().remove();
            } else if (player.getVehicle().getPassengers().get(1) instanceof Villager) {
                player.getVehicle().remove();
            }
        }
        chain.async(() -> player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)).delay(4);
        if (track.isBoatTrack()) {
            chain.sync(() -> ApiUtilities.spawnBoatAndAddPlayerWithBoatUtils(player, location, track, sameAsLastTrack)).execute();
        } else if (track.isElytraTrack()) {
            chain.sync(() -> {
                ItemStack chest = player.getInventory().getChestplate();
                if (chest == null) {
                    giveElytra(player);
                } else if (chest.getItemMeta().hasCustomModelData() && chest.getType() == Material.ELYTRA && chest.getItemMeta().getCustomModelData() == 747) {
                    giveElytra(player);
                }
            }).execute();
        } else {
            chain.execute();
        }
    }

    private static void giveElytra(Player player) {
        player.getInventory().setChestplate(new ItemBuilder(Material.ELYTRA).setCustomModelData(747).setName(Component.text("Disposable wings").color(NamedTextColor.RED)).build());
        TimeTrialController.elytraProtection.put(player.getUniqueId(), Instant.now().getEpochSecond() + 10);
    }

    public static void teleportPlayerAndSpawnBoat(Player player, Track track, Location location, PlayerTeleportEvent.TeleportCause teleportCause) {
        TaskChain<?> chain = TimingSystem.newChain();
        location.setPitch(player.getLocation().getPitch());
        TimeTrialController.lastTimeTrialTrack.put(player.getUniqueId(), track);
        chain.async(() -> player.teleportAsync(location, teleportCause)).delay(3);
        if (track.isBoatTrack()) {
            chain.sync(() -> ApiUtilities.spawnBoatAndAddPlayerWithBoatUtils(player, location, track, true)).execute();
        } else if (track.isElytraTrack()) {
            chain.sync(() -> {
                ItemStack chest = player.getInventory().getChestplate();
                if (chest == null) {
                    giveElytra(player);
                } else if (chest.getItemMeta().hasCustomModelData() && chest.getType() == Material.ELYTRA && chest.getItemMeta().getCustomModelData() == 747) {
                    giveElytra(player);
                }
            }).execute();
        } else {
            chain.execute();
        }
    }

    public static boolean hasBoatUtilsEffects(Player player) {
        if (BoatUtilsManager.playerBoatUtilsMode.get(player.getUniqueId()) != null) {
            return !(BoatUtilsManager.playerBoatUtilsMode.get(player.getUniqueId()) == BoatUtilsMode.VANILLA);
        }
        return false;
    }

    public static void removeBoatUtilsEffects(Player player) {
        BoatUtilsManager.sendBoatUtilsModePluginMessage(player, BoatUtilsMode.VANILLA, null, false);
    }

    public static void resetPlayerTimeTrial(Player player) {
        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                Text.send(player, Error.NOT_NOW);
                return;
            }
        }

        if (TimeTrialController.timeTrials.containsKey(player.getUniqueId())) {
            var tt = TimeTrialController.timeTrials.get(player.getUniqueId());
            Track track = tt.getTrack();
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }
            TimingSystemAPI.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
            return;
        }

        if (TimeTrialController.lastTimeTrialTrack.containsKey(player.getUniqueId())) {
            Track track = TimeTrialController.lastTimeTrialTrack.get(player.getUniqueId());
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }
            TimingSystemAPI.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
            return;
        }
        Text.send(player, Error.NOT_NOW);
    }

    public static String darkenHexColor(String hexColor, double darkenAmount) {
        // Remove the '#' symbol and convert to RGB values
        int r = Integer.parseInt(hexColor.substring(1, 3), 16);
        int g = Integer.parseInt(hexColor.substring(3, 5), 16);
        int b = Integer.parseInt(hexColor.substring(5, 7), 16);

        // Darken each color component
        r = (int) (r * (1 - darkenAmount));
        g = (int) (g * (1 - darkenAmount));
        b = (int) (b * (1 - darkenAmount));

        // Ensure the color components are within the valid range (0-255)
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        // Convert the darkened RGB values back to hex
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static List<UUID> extractUUIDsFromString(String s) {
        List<UUID> result = new ArrayList<>();

        for(String split : s.split(",")) {
            result.add(UUID.fromString(split));
        }

        return result;
    }

    public static String uuidListToString(List<UUID> uuids) {
        if(uuids.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(uuids.get(0).toString());
        uuids = uuids.subList(1, uuids.size());

        uuids.forEach(uuid -> sb.append(",").append(uuid));

        return sb.toString();
    }

    public static List<TPlayer> tPlayersFromUUIDList(List<UUID> uuids) {
        if(uuids.isEmpty()) return new ArrayList<>();
        List<TPlayer> result = new ArrayList<>();
        uuids.forEach(uuid -> result.add(TSDatabase.getPlayer(uuid)));
        return result;
    }

    public static List<UUID> uuidListFromTPlayersList(List<TPlayer> tPlayers) {
        List<UUID> uuids = new ArrayList<>();
        tPlayers.forEach(tPlayer -> uuids.add(tPlayer.getUniqueId()));
        return uuids;
    }
}
