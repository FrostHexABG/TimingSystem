package me.makkuusen.timing.system.database;

import co.aikar.idb.*;
import com.sk89q.worldedit.math.BlockVector2;
import me.makkuusen.timing.system.*;
import me.makkuusen.timing.system.boatutils.BoatUtilsMode;
import me.makkuusen.timing.system.boatutils.CustomBoatUtilsMode;
import me.makkuusen.timing.system.database.updates.*;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.heat.Lap;
import me.makkuusen.timing.system.logger.LogEntry;
import me.makkuusen.timing.system.participant.Subscriber;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.team.TeamManager;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.*;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.options.TrackOption;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static me.makkuusen.timing.system.TimingSystem.getPlugin;

public class MySQLDatabase implements TSDatabase, EventDatabase, TrackDatabase, LogDatabase, TeamDatabase {


    @Override
    public boolean initialize() {
        TimingSystemConfiguration config = TimingSystem.configuration;
        String hostAndPort = config.getSqlHost() + ":" + config.getSqlPort();

        PooledDatabaseOptions options = BukkitDB.getRecommendedOptions(TimingSystem.getPlugin(), config.getSqlUsername(), config.getSqlPassword(), config.getSqlDatabase(), hostAndPort);
        options.setDataSourceProperties(new HashMap<>() {{
            put("useSSL", false);
        }});
        options.setMinIdleConnections(5);
        options.setMaxConnections(5);
        co.aikar.idb.Database db = new HikariPooledDatabase(options);
        DB.setGlobalDatabase(db);
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

            var previousVersion = row.getString("version");


            // Migrate from old to new database versioning.
            if (previousVersion.equals("1.9")) {
                getPlugin().getLogger().warning("UPDATING DATABASE FROM " + previousVersion + " to " + 1);
                previousVersion = "1";
            }

            // Return if no update.
            if (previousVersion.equalsIgnoreCase(Integer.toString(databaseVersion))) {
                return true;
            }

            try {
                int oldVersion = Integer.parseInt(previousVersion);
                // Update database on new version.
                getPlugin().getLogger().warning("UPDATING DATABASE FROM " + previousVersion + " to " + databaseVersion);
                updateDatabase(oldVersion);
                DB.executeInsert("INSERT INTO `ts_version` (`version`, `date`) VALUES(?, ?);",
                        databaseVersion,
                        ApiUtilities.getTimestamp()
                );
                return true;
            } catch (NumberFormatException e) {
                getPlugin().getLogger().warning("Please upgrade to version 1.9 before trying to upgrade to this version, disabling plugin.");
                getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
                return false;
            }


        } catch (Exception e) {
            e.printStackTrace();
            getPlugin().getLogger().warning("Failed to update database, disabling plugin.");
            getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
            return false;
        }
    }


    private static void updateDatabase(int previousVersion) throws SQLException {
        //Update logic here.
        if (previousVersion < 2) {
            Version2.update();
        }
        if (previousVersion < 3) {
            Version3.updateMySQL();
        }
        if (previousVersion < 4) {
            Version4.updateMySQL();
        }
        if (previousVersion <5) {
            Version5.updateMySQL();
        }
        if (previousVersion < 6) {
            Version6.updateMySQL();
        }
        if (previousVersion < 7) {
            Version7.updateMySQL();
        }
        if (previousVersion < 8) {
            Version8.updateMySQL();
        }
        if (previousVersion < 9) {
            Version9.updateMySQL();
        }
        if (previousVersion < 10) {
            Version10.updateMySQL();
        }
        if (previousVersion < 11) {
            Version11.updateMySQL();
        }
        if (previousVersion < 12) {
            Version12.updateMySQL();
        }
        if (previousVersion < 13) {
            Version13.updateMySQL();
        }
        if (previousVersion < 14) {
            Version14.updateMySQL();
        }
    }


    @Override
    public boolean createTables() {
        try {
            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_players` (
                      `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
                      `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `shortName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `boat` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `verbose` tinyint(1) NOT NULL DEFAULT '0',
                      `timetrial` tinyint(1) NOT NULL DEFAULT '1',
                      `override` tinyint(1) NOT NULL DEFAULT '0',
                      `chestBoat` tinyint(1) NOT NULL DEFAULT '0',
                      `compactScoreboard` tinyint(1) NOT NULL DEFAULT '0',
                      `color` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#9D9D97',
                      `toggleSound` tinyint(1) DEFAULT 1 NOT NULL,
                      `sendFinalLaps` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`uuid`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_custom_boatutils_modes` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `data` TEXT COLLATE utf8mb4_unicode_ci NOT NULL,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `name` (`name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_tracks` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `contributors` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `dateCreated` bigint(30) DEFAULT NULL,
                      `dateChanged` bigint(30) DEFAULT NULL,
                      `weight` int(11) NOT NULL DEFAULT '100',
                      `guiItem` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `spawn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `timeTrial` tinyint(1) NOT NULL DEFAULT 1,
                      `toggleOpen` tinyint(1) NOT NULL DEFAULT 0,
                      `boatUtilsMode` int(4) NOT NULL DEFAULT '-1',
                      `customBoatUtilsModeId` int(11) DEFAULT NULL,
                      `isRemoved` tinyint(1) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_finishes` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `date` bigint(30) NOT NULL,
                      `time` int(11) NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_finishes_checkpoints` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `finishId` int(11) NOT NULL,
                      `checkpointIndex` int(11) DEFAULT NULL,
                      `time` int(11) NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_attempts` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `date` bigint(30) NOT NULL,
                      `time` int(11) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_regions` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `regionIndex` int(11) DEFAULT NULL,
                      `regionType` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `regionShape` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `minP` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `maxP` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `spawn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_events` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `date` bigint(30) DEFAULT NULL,
                      `track` int(11) DEFAULT NULL,
                      `state` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `open` tinyint(1) NOT NULL DEFAULT '1',
                      `isRemoved` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_heats` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `roundId` int(11) NOT NULL,
                      `heatNumber` int(11) NOT NULL,
                      `state` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `startTime` bigint(30) DEFAULT NULL,
                      `endTime` bigint(30) DEFAULT NULL,
                      `fastestLapUUID` varchar(255) COLLATE utf8mb4_unicode_ci NULL,
                      `totalLaps` int(11) DEFAULT NULL,
                      `totalPitstops` int(11) DEFAULT NULL,
                      `timeLimit` int(11) DEFAULT NULL,
                      `startDelay` int(11) DEFAULT NULL,
                      `maxDrivers` int(11) DEFAULT NULL,
                      `lonely` tinyint(1) NOT NULL DEFAULT '0',
                      `collisionMode` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `canReset` tinyint(1) NOT NULL DEFAULT '0',
                      `lapReset` tinyint(1) NOT NULL DEFAULT '0',
                      `ghostingDelta` int(11) DEFAULT NULL,
                      `boatSwitching` tinyint(1) DEFAULT NULL,
                      `drs` tinyint(1) NOT NULL DEFAULT '0',
                      `drsDowntime` int(11) DEFAULT NULL,
                      `pushToPass` tinyint(1) NOT NULL DEFAULT '0',
                      `isRemoved` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_drivers` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `heatId` int(11) NOT NULL,
                      `position` int(11) NOT NULL,
                      `startPosition` int(11) NOT NULL,
                      `startTime` bigint(30) DEFAULT NULL,
                      `endTime` bigint(30) DEFAULT NULL,
                      `pitstops` int(11) DEFAULT NULL,
                      `isRemoved` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_events_signs`(
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `eventId` int(11) NOT NULL,
                      `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_laps` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `heatId` int(11) NOT NULL,
                      `trackId` int(11) NOT NULL,
                      `lapStart` bigint(30) DEFAULT NULL,
                      `lapEnd` bigint(30) DEFAULT NULL,
                      `pitted` tinyint(1) NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_locations` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `index` int(11) DEFAULT NULL,
                      `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_points` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `regionId` int(11) NOT NULL,
                      `x` int(11) DEFAULT NULL,
                      `z` int(11) DEFAULT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");


            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_rounds` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `eventId` int(11) NOT NULL,
                      `roundIndex` int(11) NOT NULL DEFAULT 1,
                      `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `state` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_version` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `version` varchar(255) NOT NULL,
                      `date` bigint(30) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_tags` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `tag` varchar(255) NOT NULL,
                      `color` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#ffffff',
                      `item` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `weight` int(11) NOT NULL DEFAULT '100',
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_tracks_tags` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `tag` varchar(255) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_tracks_options` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `trackId` int(11) NOT NULL,
                      `option` int(11) NOT NULL,
                      PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_teams` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `creator` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `dateCreated` bigint(30) NOT NULL,
                      `isRemoved` tinyint(1) NOT NULL DEFAULT '0',
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `name` (`name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_team_players` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `teamId` int(11) NOT NULL,
                      `playerUuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `position` int(11) NOT NULL,
                      `dateAdded` bigint(30) NOT NULL,
                      PRIMARY KEY (`id`),
                      FOREIGN KEY (`teamId`) REFERENCES `ts_teams`(`id`) ON DELETE CASCADE,
                      UNIQUE KEY `team_player` (`teamId`, `playerUuid`),
                      UNIQUE KEY `team_position` (`teamId`, `position`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_team_heat_entries` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `heatId` int(11) NOT NULL,
                      `teamId` int(11) NOT NULL,
                      `activeDriverUUID` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                      `currentLap` int(11) NOT NULL DEFAULT 0,
                      `currentCheckpoint` int(11) NOT NULL DEFAULT 0,
                      `startTime` bigint(30) DEFAULT NULL,
                      `endTime` bigint(30) DEFAULT NULL,
                      `position` int(11) DEFAULT NULL,
                      `startPosition` int(11) NOT NULL,
                      `pits` int(11) NOT NULL DEFAULT 0,
                      `finished` tinyint(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      FOREIGN KEY (`heatId`) REFERENCES `ts_heats`(`id`) ON DELETE CASCADE,
                      FOREIGN KEY (`teamId`) REFERENCES `ts_teams`(`id`) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            DB.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `ts_team_laps` (
                      `id` int(11) NOT NULL AUTO_INCREMENT,
                      `teamHeatEntryId` int(11) NOT NULL,
                      `lapNumber` int(11) NOT NULL,
                      `lapStart` bigint(30) NOT NULL,
                      `lapEnd` bigint(30) DEFAULT NULL,
                      `driverUUID` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                      `pitted` tinyint(1) NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      FOREIGN KEY (`teamHeatEntryId`) REFERENCES `ts_team_heat_entries`(`id`) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<DbRow> selectPlayers() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_players`;");
    }

    @Override
    public TPlayer createPlayer(UUID uuid, String name) {
        try {
            DB.executeUpdate("INSERT INTO `ts_players` (`uuid`, `name`, `boat`) VALUES(?, ?, ?);",
                    uuid.toString(),
                    name,
                    Boat.Type.BIRCH.name()
            );
            var dbRow = DB.getFirstRow("SELECT * FROM `ts_players` WHERE `uuid` = ?;",
                    uuid.toString()
            );
            return new TPlayer(getPlugin(), dbRow);
        } catch (SQLException e) {
            getPlugin().getLogger().warning("Failed to create new player: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void playerUpdateValue(UUID uuid, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_players` SET `" + column + "` = ? WHERE `uuid` = ?;",
                value,
                uuid.toString()
        );
    }

    @Override
    public void playerUpdateValue(UUID uuid, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_players` SET `" + column + "` = ? WHERE `uuid` = ?;",
                value,
                uuid.toString()
        );
    }

    // EVENT DATABASE
    private boolean eventDatabaseFinishedLoading = false;

    @Override
    public Event createEvent(UUID uuid, String name) {
        try {
            var eventId = DB.executeInsert("INSERT INTO `ts_events`(`name`,`uuid`,`date`,`track`,`state`,`isRemoved`) VALUES (?, ?, ?,NULL, ?, 0);",
                    name,
                    uuid.toString(),
                    ApiUtilities.getTimestamp(),
                    Event.EventState.SETUP.name()
            );
            var dbRow = DB.getFirstRow("SELECT * FROM `ts_events` WHERE `id` = ?;",
                    eventId
            );
            return new Event(dbRow);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Round createRound(Event event, RoundType roundType, int roundIndex) {
        try {
            var roundId = DB.executeInsert("INSERT INTO `ts_rounds`(`eventId`, `roundIndex`, `type`, `state`, `isRemoved`) VALUES (?, ?, ?, ?, 0);",
                    event.getId(),
                    roundIndex,
                    roundType.name(),
                    Round.RoundState.SETUP.name()
            );
            var dbRow = DB.getFirstRow("SELECT * FROM `ts_rounds` WHERE `id` = ?;",
                    roundId
            );
            if (roundType == RoundType.QUALIFICATION)
                return new QualificationRound(dbRow);
            return new FinalRound(dbRow);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Heat createHeat(Round round, int heatIndex) {
        try {
            var heatId = DB.executeInsert("INSERT INTO `ts_heats`(`roundId`, `heatNumber`, `state`, `startTime`, `endTime`, `fastestLapUUID`, `totalLaps`, `totalPitstops`, `timeLimit`, `startDelay`, `maxDrivers`, `lonely`, `canReset`, `lapReset`, `isRemoved`) VALUES (?, ?, ?,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL, 0, 0, 0, 0);",
                    round.getId(),
                    heatIndex,
                    HeatState.SETUP.name()
            );
            var dbRow = DB.getFirstRow("SELECT * FROM `ts_heats` WHERE `id` = ?;",
                    heatId
            );
            return new Heat(dbRow, round);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Override
    public void createSign(TPlayer tPlayer, Event event, Subscriber.Type type) {
        DB.executeUpdateAsync("INSERT INTO `ts_events_signs`(`eventId`, `uuid`, `type`) VALUES (?, ?, ?);",
                event.getId(),
                tPlayer.getUniqueId().toString(),
                type.name()
        );
    }

    @Override
    public void removeSign(UUID uuid, int eventId, Subscriber.Type type) {
        DB.executeUpdateAsync("DELETE FROM `ts_events_signs` WHERE `uuid` = ? AND `eventId` = ? AND `type` = ?;",
                uuid.toString(),
                eventId,
                type.name()
        );
    }

    @Override
    public DbRow createDriver(UUID uuid, Heat heat, int startPosition) {
        try {
            var driverId = DB.executeInsert("INSERT INTO `ts_drivers`(`uuid`, `heatId`, `position`, `startPosition`, `startTime`, `endTime`, `pitstops`) VALUES (?, ?, ?, ?,NULL,NULL, 0);",
                    uuid.toString(),
                    heat.getId(),
                    startPosition,
                    startPosition
            );
            return DB.getFirstRow("SELECT * FROM `ts_drivers` WHERE `id` = ?;",
                    driverId
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void createLap(Lap lap) {
        String lapEnd = lap.getLapEnd() == null ? "NULL" : String.valueOf(lap.getLapEnd().toEpochMilli());
        DB.executeUpdateAsync("INSERT INTO `ts_laps`(`uuid`, `heatId`, `trackId`, `lapStart`, `lapEnd`, `pitted`) VALUES (?, ?, ?, ?, ?, ?);",
                lap.getPlayer().getUniqueId().toString(),
                lap.getHeatId(),
                lap.getTrack().getId(),
                lap.getLapStart().toEpochMilli(),
                lapEnd,
                lap.isPitted()
        );
    }

    @Override
    public List<DbRow> selectEvents() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_events` WHERE `isRemoved` = 0;");
    }

    @Override
    public List<DbRow> selectRounds(int eventId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_rounds` WHERE `eventId` = ? AND `isRemoved` = 0;",
                eventId
        );
    }

    @Override
    public List<DbRow> selectHeats(int roundId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_heats` WHERE `roundId` = ? AND `isRemoved` = 0;",
                roundId
        );
    }

    @Override
    public List<DbRow> selectSigns(int eventId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_events_signs` WHERE `eventId` = ?;",
                eventId
        );
    }

    @Override
    public List<DbRow> selectDrivers(int heatId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_drivers` WHERE `heatId` = ? AND `isRemoved` = 0;",
                heatId
        );
    }

    @Override
    public List<DbRow> selectLaps(int heatId, String uuid) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_laps` WHERE `heatId` = ? AND `uuid` = ? AND `isRemoved` = 0;",
                heatId,
                uuid.toString()
        );
    }

    @Override
    public <T> void remove(T thing) {
        if (thing instanceof Event event) {
            DB.executeUpdateAsync("UPDATE `ts_events` SET `isRemoved` = 1 WHERE `id` = ?;",
                    event.getId()
            );
        } else if (thing instanceof Round round) {
            DB.executeUpdateAsync("UPDATE `ts_rounds` SET `isRemoved` = 1 WHERE `id` = ?;",
                    round.getId()
            );
        } else if (thing instanceof Heat heat) {
            DB.executeUpdateAsync("UPDATE `ts_heats` SET `isRemoved` = 1 WHERE `id` = ?;",
                    heat.getId()
            );
        }
    }

    @Override
    public void setHasFinishedLoading(boolean b) {
        eventDatabaseFinishedLoading = b;
    }

    @Override
    public boolean hasFinishedLoading() {
        return eventDatabaseFinishedLoading;
    }

    @Override
    public void createCheckpointFinish(long finishId, int checkpointIndex, long time) {
        DB.executeUpdateAsync("INSERT INTO `ts_finishes_checkpoints`(`finishId`, `checkpointIndex`, `time`) VALUES (?, ?, ?);",
                finishId,
                checkpointIndex,
                time
        );
    }

    @Override
    public void createAttempt(int id, UUID uuid, long date, long time) {
        DB.executeUpdateAsync("INSERT INTO `ts_attempts` (`trackId`, `uuid`, `date`, `time`) VALUES(?, ?, ?, ?);",
                id,
                uuid.toString(),
                date,
                time
        );
    }

    @Override
    public void eventSet(long heatId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_events` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void eventSet(long heatId, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_events` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void eventSet(long heatId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_events` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void roundSet(long heatId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_rounds` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void heatSet(long heatId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_heats` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void heatSet(long heatId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_heats` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void heatSet(long heatId, String column, Long value) {
        DB.executeUpdateAsync("UPDATE `ts_heats` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void heatSet(long heatId, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_heats` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                heatId
        );
    }

    @Override
    public void driverSet(long driverId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_drivers` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                driverId
        );
    }

    @Override
    public void driverSet(long driverId, String column, Long value) {
        DB.executeUpdateAsync("UPDATE `ts_drivers` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                driverId
        );
    }

    @Override
    public DbRow teamHeatEntryNew(int heatId, int teamId, int startPosition) {
        try {
            var entryId = DB.executeInsert("INSERT INTO `ts_team_heat_entries`(`heatId`, `teamId`, `activeDriverUUID`, `currentLap`, `currentCheckpoint`, `startTime`, `endTime`, `position`, `startPosition`, `pits`, `finished`) VALUES (?, ?, NULL, 0, 0, NULL, NULL, NULL, ?, 0, 0);",
                    heatId,
                    teamId,
                    startPosition
            );
            return DB.getFirstRow("SELECT * FROM `ts_team_heat_entries` WHERE `id` = ?;",
                    entryId
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<DbRow> selectTeamHeatEntries(int heatId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_team_heat_entries` WHERE `heatId` = ?;",
                heatId
        );
    }

    @Override
    public void teamHeatEntrySet(long teamHeatEntryId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_team_heat_entries` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                teamHeatEntryId
        );
    }

    @Override
    public void teamHeatEntrySet(long teamHeatEntryId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_team_heat_entries` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                teamHeatEntryId
        );
    }

    @Override
    public void teamHeatEntrySet(long teamHeatEntryId, String column, Long value) {
        DB.executeUpdateAsync("UPDATE `ts_team_heat_entries` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                teamHeatEntryId
        );
    }

    @Override
    public void teamHeatEntrySet(long teamHeatEntryId, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_team_heat_entries` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                teamHeatEntryId
        );
    }

    @Override
    public void teamHeatEntryRemove(long teamHeatEntryId) {
        DB.executeUpdateAsync("DELETE FROM `ts_team_heat_entries` WHERE `id` = ?;", teamHeatEntryId);
    }

    @Override
    public void createTeamLap(int teamHeatEntryId, int lapNumber, long lapStart, Long lapEnd, UUID driverUUID, boolean pitted) {
        DB.executeUpdateAsync("INSERT INTO `ts_team_laps`(`teamHeatEntryId`, `lapNumber`, `lapStart`, `lapEnd`, `driverUUID`, `pitted`) VALUES (?, ?, ?, ?, ?, ?);",
                teamHeatEntryId,
                lapNumber,
                lapStart,
                lapEnd,
                driverUUID.toString(),
                pitted
        );
    }

    @Override
    public List<DbRow> selectTeamLaps(int teamHeatEntryId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_team_laps` WHERE `teamHeatEntryId` = ?;",
                teamHeatEntryId
        );
    }

    // Track Database

    @Override
    public List<DbRow> selectTracks() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_tracks` WHERE `isRemoved` = 0;");
    }

    @Override
    public DbRow selectTrack(long trackId) throws SQLException {
        return DB.getFirstRow("SELECT * FROM `ts_tracks` WHERE `id` = ?;",
                trackId
        );
    }

    @Override
    public List<DbRow> selectFinishes() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_finishes` WHERE `isRemoved` = 0;");
    }

    @Override
    public List<DbRow> selectAttempts() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_attempts`;");
    }

    @Override
    public List<DbRow> selectTrackTags() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_tracks_tags`;");
    }

    @Override
    public List<DbRow> selectTags() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_tags`");
    }

    @Override
    public List<DbRow> selectCheckpointTimes() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_finishes_checkpoints` WHERE `isRemoved` = 0;");
    }

    @Override
    public List<DbRow> selectTrackRegions() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_regions` WHERE `isRemoved` = 0;");
    }

    @Override
    public DbRow selectTrackRegion(long regionId) throws SQLException {
        return DB.getFirstRow("SELECT * FROM `ts_regions` WHERE `id` = ?;",
                regionId
        );
    }

    @Override
    public List<DbRow> selectRegionPoints(int regionId) throws SQLException {
        return DB.getResults("SELECT * FROM `ts_points` WHERE `regionId` = ?;",
                regionId
        );
    }

    @Override
    public List<DbRow> selectLocations() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_locations`;");
    }

    @Override
    public List<DbRow> selectOptions() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_tracks_options`;");
    }

    @Override
    public long createTrack(String uuid, String name, long date, int weight, ItemStack gui, Location location, Track.TrackType type, BoatUtilsMode boatUtilsMode) throws SQLException {
        return DB.executeInsert("INSERT INTO ts_tracks (`uuid`, `name`, dateCreated, weight, guiItem, spawn, type, toggleOpen, boatUtilsMode, isRemoved) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 0);",
                uuid.toString(),
                name,
                date,
                weight,
                ApiUtilities.itemToString(gui),
                ApiUtilities.locationToString(location),
                type == null ? null : type.toString(),
                boatUtilsMode.getId()
                );
    }

    @Override
    public long createRegion(long trackId, int index, String minP, String maxP, TrackRegion.RegionType type, TrackRegion.RegionShape shape, Location location) throws SQLException {
        return DB.executeInsert("INSERT INTO `ts_regions` (`trackId`, `regionIndex`, `regionType`, `regionShape`, `minP`, `maxP`, `spawn`, `isRemoved`) VALUES(?, ?, ?, ?, ?, ?, ?, 0);",
                trackId,
                index,
                type.toString(),
                shape.toString(),
                minP,
                maxP,
                ApiUtilities.locationToString(location)
                );
    }

    @Override
    public long createPoint(long regionId, BlockVector2 v) throws SQLException {
        return DB.executeInsert("INSERT INTO `ts_points` (`regionId`, `x`, `z`) VALUES(?, ?, ?);",
                regionId,
                v.getBlockX(),
                v.getBlockZ()
                );
    }

    @Override
    public long createLocation(long trackId, int index, TrackLocation.Type type, Location location) throws SQLException {
        return DB.executeInsert("INSERT INTO `ts_locations` (`trackId`, `index`, `type`, `location`) VALUES(?, ?, ?, ?);",
                trackId,
                index,
                type.name(),
                ApiUtilities.locationToString(location)
        );
    }

    @Override
    public void removeTrack(long trackId) {
        DB.executeUpdateAsync("UPDATE `ts_tracks` SET `isRemoved` = 1 WHERE `id` = ?;",
                trackId
        );
    }

    @Override
    public void createTrackOptionAsync(int trackId, TrackOption trackOption) {
        DB.executeUpdateAsync("INSERT INTO `ts_tracks_options` (`trackId`, `option`) VALUES(?, ?);",
                trackId,
                trackOption.getId()
        );
    }

    public void deleteTrackOptionAsync(int trackId, TrackOption trackOption) {
        DB.executeUpdateAsync("DELETE FROM `ts_tracks_options` WHERE `option` = ? AND `trackId` = ?;",
                trackOption.getId(),
                trackId
        );
    }

    @Override
    public void createTagAsync(TrackTag tag, TextColor color, ItemStack item) {
        DB.executeUpdateAsync("INSERT INTO `ts_tags` (`tag`, `color`, `item`) VALUES(?, ?, ?);",
                tag.getValue(),
                color.asHexString(),
                ApiUtilities.itemToString(item)
        );
    }

    @Override
    public void deleteTagAsync(TrackTag tag) {
        DB.executeUpdateAsync("DELETE FROM `ts_tags` WHERE `tag` = ?;",
                tag.getValue()
        );
        DB.executeUpdateAsync("DELETE FROM `ts_tracks_tags` WHERE `tag` = ?;",
                tag.getValue()
        );
    }

    @Override
    public void deletePoint(long regionId) throws SQLException {
        DB.executeUpdate("DELETE FROM `ts_points` WHERE `regionId` = ?;",
                regionId
        );
    }

    @Override
    public void deleteLocation(int trackId, int index, TrackLocation.Type type) {
        DB.executeUpdateAsync("DELETE FROM `ts_locations` WHERE `trackId` = ? AND `index` = ? AND `type` = ?;",
                trackId,
                index,
                type.name()
        );
    }

    @Override
    public void updateLocation(int index, Location location, TrackLocation.Type type, long trackId) {
        DB.executeUpdateAsync("UPDATE `ts_locations` SET `location` = ? WHERE `trackId` = ? AND `index` = ? AND `type` = ?;",
                ApiUtilities.locationToString(location),
                trackId,
                index,
                type.name()
        );
    }

    @Override
    public void addTagToTrack(int trackId, TrackTag tag) {
        DB.executeUpdateAsync("INSERT INTO `ts_tracks_tags` (`trackId`, `tag`) VALUES(?, ?);",
                trackId,
                tag.getValue()
        );
    }

    @Override
    public void tagSet(String tag, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_tags` SET `" + column + "` = ? WHERE `tag` = ?;",
                value,
                tag
        );
    }

    @Override
    public void removeFinish(int finishId) {
        DB.executeUpdateAsync("UPDATE `ts_finishes` SET `isRemoved` = 1 WHERE `id` = ?;",
                finishId
        );
    }

    @Override
    public void removeAllFinishes(int trackId, UUID uuid) {
        DB.executeUpdateAsync("UPDATE `ts_finishes` SET `isRemoved` = 1 WHERE `trackId` = ? AND `uuid` = ?;",
                trackId,
                uuid.toString()
        );
    }

    @Override
    public void removeAllFinishes(int trackId) {
        DB.executeUpdateAsync("UPDATE `ts_finishes` SET `isRemoved` = 1 WHERE `trackId` = ?;",
                trackId
        );
    }

    @Override
    public void removeTagFromTrack(int trackId, TrackTag tag) {
        DB.executeUpdateAsync("DELETE FROM `ts_tracks_tags` WHERE `tag` = ? AND `trackId` = ?;",
                tag.getValue(),
                trackId
        );
    }

    @Override
    public void trackSet(int trackId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_tracks` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                trackId
        );
    }

    @Override
    public void trackSet(int trackId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_tracks` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                trackId
        );
    }

    @Override
    public void trackSet(int trackId, String column, Boolean value) {
        DB.executeUpdateAsync("UPDATE `ts_tracks` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                trackId
        );
    }

    @Override
    public void trackRegionSet(int trackId, String column, String value) {
        DB.executeUpdateAsync("UPDATE `ts_regions` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                trackId
        );
    }
    @Override
    public void trackRegionSet(int trackId, String column, Integer value) {
        DB.executeUpdateAsync("UPDATE `ts_regions` SET `" + column + "` = ? WHERE `id` = ?;",
                value,
                trackId
        );
    }

    // Log Database

    @Override
    public List<DbRow> selectTrackEntries() throws SQLException {
        return DB.getResults("SELECT * FROM `ts_track_events`;");
    }

    @Override
    public void insertEvent(LogEntry logEntry) throws SQLException {
        DB.executeInsert("INSERT INTO `ts_track_events` (`date`, `body`) VALUES(?, ?);",
                logEntry.getDate(),
                logEntry.getBody().toJSONString()
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
            DB.executeUpdate("INSERT INTO ts_custom_boatutils_modes (name, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)", mode.getName(), json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // TeamDatabase implementation
    @Override
    public long createTeam(String name, UUID creator, long dateCreated) throws SQLException {
        return DB.executeInsert("INSERT INTO ts_teams (name, creator, dateCreated, isRemoved) VALUES (?, ?, ?, ?)",
                name, creator.toString(), dateCreated, 0);
    }

    @Override
    public List<DbRow> selectTeams() throws SQLException {
        return DB.getResults("SELECT * FROM ts_teams WHERE isRemoved = 0 ORDER BY name");
    }

    @Override
    public DbRow selectTeam(int teamId) throws SQLException {
        return DB.getFirstRow("SELECT * FROM ts_teams WHERE id = ? AND isRemoved = 0", teamId);
    }

    @Override
    public void deleteTeam(int teamId) {
        try {
            // Delete team players first (foreign key constraint)
            DB.executeUpdate("DELETE FROM ts_team_players WHERE teamId = ?", teamId);
            // Actually delete the team instead of soft delete to allow name reuse
            DB.executeUpdate("DELETE FROM ts_teams WHERE id = ?", teamId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addPlayerToTeam(int teamId, UUID playerUuid, int position) throws SQLException {
        DB.executeUpdate("INSERT INTO ts_team_players (teamId, playerUuid, position, dateAdded) VALUES (?, ?, ?, ?)",
                teamId, playerUuid.toString(), position, System.currentTimeMillis() / 1000);
    }



    @Override
    public List<DbRow> selectTeamPlayers(int teamId) throws SQLException {
        return DB.getResults("SELECT * FROM ts_team_players WHERE teamId = ? ORDER BY position", teamId);
    }

    @Override
    public boolean teamNameExists(String name) throws SQLException {
        DbRow row = DB.getFirstRow("SELECT id FROM ts_teams WHERE name = ? AND isRemoved = 0", name);
        return row != null;
    }

    @Override
    public DbRow selectTeamByName(String name) throws SQLException {
        return DB.getFirstRow("SELECT * FROM ts_teams WHERE name = ? AND isRemoved = 0", name);
    }

    @Override
    public void removePlayerFromTeam(int teamId, UUID playerUuid) {
        try {
            DB.executeUpdate("DELETE FROM ts_team_players WHERE teamId = ? AND playerUuid = ?",
                    teamId, playerUuid.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void teamSet(int teamId, String column, String value) {
        DB.executeUpdateAsync("UPDATE ts_teams SET " + column + " = ? WHERE id = ?", value, teamId);
    }

    @Override
    public int getNextPlayerPosition(int teamId) throws SQLException {
        DbRow row = DB.getFirstRow("SELECT MAX(position) as maxPos FROM ts_team_players WHERE teamId = ?", teamId);
        if (row == null || row.get("maxPos") == null) {
            return 1;
        }
        return row.getInt("maxPos") + 1;
    }

    @Override
    public java.util.Optional<Team> getTeam(Integer teamId) {
        return TeamManager.getTeam(teamId);
    }
}
