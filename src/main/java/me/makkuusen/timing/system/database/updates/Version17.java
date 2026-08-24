package me.makkuusen.timing.system.database.updates;

import co.aikar.idb.DB;

import java.sql.SQLException;

public class Version17 {

    public static void updateMySQL() throws SQLException {
        try {
            DB.executeUpdate("ALTER TABLE `ts_tracks` ADD COLUMN `authorTime` bigint(30) DEFAULT NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                throw e;
            }
        }
    }

    public static void updateSQLite() throws SQLException {
        try {
            DB.executeUpdate("ALTER TABLE `ts_tracks` ADD COLUMN `authorTime` INTEGER DEFAULT NULL");
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                throw e;
            }
        }
    }
}
