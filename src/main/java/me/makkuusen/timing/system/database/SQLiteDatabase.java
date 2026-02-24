package me.makkuusen.timing.system.database;

import co.aikar.idb.BukkitDB;
import co.aikar.idb.DB;
import co.aikar.idb.DatabaseOptions;
import co.aikar.idb.PooledDatabaseOptions;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;
import me.makkuusen.timing.system.database.updates.*;
import org.bukkit.Material;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;

import static me.makkuusen.timing.system.TimingSystem.getPlugin;

public class SQLiteDatabase extends MySQLDatabase {
    @Override
    public boolean initialize() {
        DatabaseOptions options = DatabaseOptions.builder().poolName(getPlugin().getDescription().getName() + " DB").logger(getPlugin().getLogger()).sqlite(new File(getPlugin().getDataFolder(), "ts.db").getPath()).build();
        PooledDatabaseOptions poolOptions = PooledDatabaseOptions.builder().options(options).build();
        BukkitDB.createHikariDatabase(TimingSystem.getPlugin(), poolOptions);
        return createTables();
    }

    @Override
    public boolean update() {
        try {
            var row = DB.getFirstRow("SELECT * FROM `ts_version` ORDER BY `date` DESC;");

            int databaseVersion = 14;
            if (row == null) { // First startup
                DB.executeInsert("INSERT INTO `ts_version` (`version`, `date`) VALUES(?, ?);",
                        databaseVersion,
                        ApiUtilities.getTimestamp()
                );
                return true;
            }

            var previousVersion = row.getInt("version");

            // Return if no update.
            if (previousVersion == databaseVersion) {
                return true;
            }

            getPlugin().getLogger().warning("UPDATING DATABASE FROM " + previousVersion + " to " + databaseVersion);
            updateDatabase(previousVersion);
            DB.executeInsert("INSERT INTO `ts_version` (`version`, `date`) VALUES(?, ?);",
                    databaseVersion,
                    ApiUtilities.getTimestamp()
            );
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            getPlugin().getLogger().warning("Failed to update database, disabling plugin.");
            getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
            return false;
        }
    }
    private static void updateDatabase(int previousVersion) throws SQLException {
        // Update logic here.
        if (previousVersion < 2) {
            Version2.update();
        }

        if (previousVersion < 3) {
            Version3.updateSQLite();
        }

        if (previousVersion < 4) {
            Version4.updateSQLite();
        }

        if (previousVersion < 5) {
            Version5.updateSQLite();
        }

        if (previousVersion < 6) {
            Version6.updateSQLite();
        }

        if (previousVersion < 7) {
            Version7.updateSQLite();
        }

        if (previousVersion < 8) {
            Version8.updateSQLite();
        }

        if (previousVersion < 9) {
            Version9.updateSQLite();
        }

        if (previousVersion < 10) {
            Version10.updateSQLite();
        }

        if (previousVersion < 11) {
            Version11.updateSQLite();
        }

        if (previousVersion < 12) {
            Version12.updateSQLite();
        }

        if (previousVersion < 13) {
            Version13.updateSQLite();
        }

        if (previousVersion < 14) {
            Version14.updateSQLite();
        }
    }


    @Override
    public boolean createTables() {
        try {
            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_players` (
                        `uuid` TEXT PRIMARY KEY NOT NULL DEFAULT '',
                        `name` TEXT NOT NULL,
                        `shortName` TEXT DEFAULT NULL,
                        `boat` TEXT DEFAULT NULL,
                        `color` TEXT NOT NULL DEFAULT '#9D9D97',
                        `chestBoat` INTEGER NOT NULL DEFAULT 0,
                        `compactScoreboard` INTEGER NOT NULL DEFAULT 0,
                        `override` INTEGER NOT NULL DEFAULT 0,
                        `verbose` INTEGER NOT NULL DEFAULT 0,
                        `timetrial` INTEGER NOT NULL DEFAULT 1,
                        `toggleSound` INTEGER DEFAULT 1 NOT NULL,
                        `sendFinalLaps` INTEGER DEFAULT 0 NOT NULL
                        )
                        """);

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_custom_boatutils_modes` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL UNIQUE,
                          `data` TEXT NOT NULL
                        );
                        """);

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_tracks` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `uuid` TEXT DEFAULT NULL,
                          `name` TEXT NOT NULL,
                          `contributors` TEXT DEFAULT NULL,
                          `dateCreated` INTEGER DEFAULT NULL,
                          `dateChanged` INTEGER DEFAULT NULL,
                          `weight` INTEGER NOT NULL DEFAULT 100,
                          `guiItem` TEXT NOT NULL,
                          `spawn` TEXT NOT NULL,
                          `type` TEXT NOT NULL,
                          `timeTrial` INTEGER NOT NULL DEFAULT 1,
                          `toggleOpen` INTEGER NOT NULL,
                          `boatUtilsMode` INTEGER NOT NULL DEFAULT -1,
                          `customBoatUtilsModeId` INTEGER DEFAULT NULL,
                          `isRemoved` INTEGER NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_finishes` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INT NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `date` INTEGER NOT NULL,
                          `time` INTEGER NOT NULL,
                          `isRemoved` INT NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_finishes_checkpoints` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `finishId` INTEGER NOT NULL,
                          `checkpointIndex` INTEGER DEFAULT NULL,
                          `time` INTEGER NOT NULL,
                          `isRemoved` INTEGER NOT NULL DEFAULT 0
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_attempts` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `date` INTEGER NOT NULL,
                          `time` INTEGER NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_regions` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `regionIndex` INTEGER DEFAULT NULL,
                          `regionType` TEXT DEFAULT NULL,
                          `regionShape` TEXT NOT NULL,
                          `minP` TEXT DEFAULT NULL,
                          `maxP` TEXT DEFAULT NULL,
                          `spawn` TEXT NOT NULL,
                          `isRemoved` INTEGER NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_events` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `date` INTEGER DEFAULT NULL,
                          `track` INTEGER DEFAULT NULL,
                          `state` TEXT NOT NULL,
                          `open` INTEGER NOT NULL DEFAULT 1,
                          `isRemoved` INTEGER NOT NULL DEFAULT '0'
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_heats` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `roundId` INTEGER NOT NULL,
                          `heatNumber` INTEGER NOT NULL,
                          `state` TEXT NOT NULL,
                          `startTime` INTEGER DEFAULT NULL,
                          `endTime` INTEGER DEFAULT NULL,
                          `fastestLapUUID` TEXT NULL,
                          `totalLaps` INTEGER DEFAULT NULL,
                          `totalPitstops` INT DEFAULT NULL,
                          `timeLimit` INTEGER DEFAULT NULL,
                          `startDelay` INTEGER DEFAULT NULL,
                          `maxDrivers` INTEGER DEFAULT NULL,
                          `lonely` INTEGER DEFAULT NULL,
                          `canReset` INTEGER DEFAULT NULL,
                          `lapReset` INTEGER DEFAULT NULL,
                          `ghostingDelta` INTEGER DEFAULT NULL,
                          `boatSwitching` INTEGER DEFAULT NULL,
                          `collisionMode` TEXT DEFAULT NULL,
                          `drs` INTEGER NOT NULL DEFAULT 0,
                          `drsDowntime` INTEGER DEFAULT NULL,
                          `pushToPass` INTEGER NOT NULL DEFAULT 0,
                          `isRemoved` INTEGER NOT NULL DEFAULT '0'
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_drivers` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `heatId` INTEGER NOT NULL,
                          `position` INTEGER NOT NULL,
                          `startPosition` INTEGER NOT NULL,
                          `startTime` INTEGER DEFAULT NULL,
                          `endTime` INTEGER DEFAULT NULL,
                          `pitstops` INTEGER DEFAULT NULL,
                          `isRemoved` INTEGER NOT NULL DEFAULT '0'
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_events_signs`(
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `eventId` INTEGER NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `type` TEXT NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_laps` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `uuid` TEXT NOT NULL,
                          `heatId` INTEGER NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `lapStart` INTEGER DEFAULT NULL,
                          `lapEnd` INTEGER DEFAULT NULL,
                          `pitted` INTEGER NOT NULL,
                          `isRemoved` INTEGER NOT NULL DEFAULT '0'
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_locations` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `type` TEXT NOT NULL,
                          `index` INTEGER DEFAULT NULL,
                          `location` TEXT NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_points` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `regionId` INTEGER NOT NULL,
                          `x` INTEGER DEFAULT NULL,
                          `z` INTEGER DEFAULT NULL
                        );""");


            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_rounds` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `eventId` INTEGER NOT NULL,
                          `roundIndex` INTEGER NOT NULL DEFAULT 1,
                          `type` TEXT DEFAULT NULL,
                          `state` TEXT NOT NULL,
                          `isRemoved` INTEGER NOT NULL DEFAULT 0
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_version` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `version` INTEGER NOT NULL,
                          `date` INTEGER NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_tags` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `tag` TEXT NOT NULL,
                          `color` TEXT NOT NULL DEFAULT '#FFFFFF',
                          `item` TEXT NOT NULL DEFAULT '%ITEM%;',
                          `weight` INT NOT NULL DEFAULT 100
                        );""".replace("%ITEM%", ApiUtilities.itemToString(new ItemBuilder(Material.ANVIL).build()))
            );

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_tracks_tags` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `tag` TEXT NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_tracks_options` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `trackId` INTEGER NOT NULL,
                          `option` INTEGER NOT NULL
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_teams` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL UNIQUE,
                          `creator` TEXT NOT NULL,
                          `dateCreated` INTEGER NOT NULL,
                          `isRemoved` INTEGER NOT NULL DEFAULT 0
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_team_players` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `teamId` INTEGER NOT NULL,
                          `playerUuid` TEXT NOT NULL,
                          `position` INTEGER NOT NULL,
                          `dateAdded` INTEGER NOT NULL,
                          FOREIGN KEY (teamId) REFERENCES ts_teams(id),
                          UNIQUE(teamId, playerUuid),
                          UNIQUE(teamId, position)
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_team_heat_entries` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `heatId` INTEGER NOT NULL,
                          `teamId` INTEGER NOT NULL,
                          `activeDriverUUID` TEXT DEFAULT NULL,
                          `currentLap` INTEGER NOT NULL DEFAULT 0,
                          `currentCheckpoint` INTEGER NOT NULL DEFAULT 0,
                          `startTime` INTEGER DEFAULT NULL,
                          `endTime` INTEGER DEFAULT NULL,
                          `position` INTEGER DEFAULT NULL,
                          `startPosition` INTEGER NOT NULL,
                          `pits` INTEGER NOT NULL DEFAULT 0,
                          `finished` INTEGER NOT NULL DEFAULT 0,
                          FOREIGN KEY (heatId) REFERENCES ts_heats(id) ON DELETE CASCADE,
                          FOREIGN KEY (teamId) REFERENCES ts_teams(id) ON DELETE CASCADE
                        );""");

            DB.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS `ts_team_laps` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `teamHeatEntryId` INTEGER NOT NULL,
                          `lapNumber` INTEGER NOT NULL,
                          `lapStart` INTEGER NOT NULL,
                          `lapEnd` INTEGER DEFAULT NULL,
                          `driverUUID` TEXT NOT NULL,
                          `pitted` INTEGER NOT NULL DEFAULT 0,
                          FOREIGN KEY (teamHeatEntryId) REFERENCES ts_team_heat_entries(id) ON DELETE CASCADE
                        );""");

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void trackSet(int trackId, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_tracks` SET `" + column + "` = ? WHERE `id` = ?;",
                (value ? 1 : 0),
                trackId
        );
    }

    @Override
    public void playerUpdateValue(UUID uuid, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_players` SET `" + column + "` = ? WHERE `uuid` = ?;",
                (value ? 1 : 0),
                uuid.toString()
        );
    }

    @Override
    public CustomBoatUtilsMode getCustomBoatUtilsModeFromName(String name) {
        try {
            var rows = DB.getResults("SELECT data FROM ts_custom_boatutils_modes WHERE name = ?", name);
            if (rows == null || rows.isEmpty()) return null;
            String json = rows.get(0).getString("data");
            return CustomBoatUtilsMode.fromJson(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int getCustomBoatUtilsModeIdFromName(String name) {
        try {
            var rows = DB.getResults("SELECT id FROM ts_custom_boatutils_modes WHERE name = ?", name);
            if (rows == null || rows.isEmpty()) return -1;
            return rows.get(0).getInt("id");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public CustomBoatUtilsMode getCustomBoatUtilsModeFromId(int id) {
        try {
            var rows = DB.getResults("SELECT data FROM ts_custom_boatutils_modes WHERE id = ?", id);
            if (rows == null || rows.isEmpty()) return null;
            String json = rows.get(0).getString("data");
            return CustomBoatUtilsMode.fromJson(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean saveOrUpdateCustomBoatUtilsMode(CustomBoatUtilsMode mode) {
        try {
            String json = mode.toJson();
            // SQLite uses INSERT OR REPLACE instead of ON CONFLICT
            DB.executeUpdate("INSERT OR REPLACE INTO ts_custom_boatutils_modes (name, data) VALUES (?, ?)", mode.getName(), json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
