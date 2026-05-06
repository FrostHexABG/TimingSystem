package me.makkuusen.timing.system.database.updates;

import co.aikar.idb.DB;

import java.sql.SQLException;

public class Version14 {

    public static void updateMySQL() throws SQLException {
        DB.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `ts_team_tuning` (
                  `teamId` int(11) NOT NULL,
                  `attributesJson` TEXT NOT NULL,
                  PRIMARY KEY (`teamId`),
                  FOREIGN KEY (`teamId`) REFERENCES `ts_teams`(`id`) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """);

        try {
            DB.executeUpdate("ALTER TABLE `ts_events` ADD COLUMN `tuningEnabled` tinyint(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) throw e; // 1060 = duplicate column
        }

        try {
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `liveTuningEnabled` tinyint(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) throw e;
        }

        try {
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `pushToPass` tinyint(1) NOT NULL DEFAULT 0");
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `joinMidHeat` tinyint(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                throw e;
            }
        }
    }

    public static void updateSQLite() throws SQLException {
        DB.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `ts_team_tuning` (
                  `teamId` INTEGER NOT NULL,
                  `attributesJson` TEXT NOT NULL,
                  PRIMARY KEY (`teamId`),
                  FOREIGN KEY (`teamId`) REFERENCES `ts_teams`(`id`) ON DELETE CASCADE
                );
                """);

        try {
            DB.executeUpdate("ALTER TABLE `ts_events` ADD COLUMN `tuningEnabled` INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) throw e;
        }

        try {
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `liveTuningEnabled` INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) throw e;
        }

        try {
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `pushToPass` INTEGER NOT NULL DEFAULT 0");
            DB.executeUpdate("ALTER TABLE `ts_heats` ADD COLUMN `joinMidHeat` INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                throw e;
            }
        }
    }
}
