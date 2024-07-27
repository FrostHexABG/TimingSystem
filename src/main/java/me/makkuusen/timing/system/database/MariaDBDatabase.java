package me.makkuusen.timing.system.database;

import co.aikar.idb.BukkitDB;
import co.aikar.idb.DB;
import co.aikar.idb.HikariPooledDatabase;
import co.aikar.idb.PooledDatabaseOptions;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.TimingSystemConfiguration;

import java.util.HashMap;

public class MariaDBDatabase extends MySQLDatabase {
    @Override
    public boolean initialize() {
        TimingSystemConfiguration config = TimingSystem.configuration;
        String hostAndPort = config.getSqlHost() + ":" + config.getSqlPort();

        PooledDatabaseOptions options = BukkitDB.getRecommendedOptions(TimingSystem.getPlugin(), config.getSqlUsername(), config.getSqlPassword(), config.getSqlDatabase(), hostAndPort);

        //Fix invalid MariaDB Options
        options.getOptions().setDsn("mariadb://" + hostAndPort + "/" + config.getSqlDatabase());
        //End Fix

        options.setMinIdleConnections(5);
        options.setMaxConnections(5);
        co.aikar.idb.Database db = new HikariPooledDatabase(options);
        DB.setGlobalDatabase(db);
        return createTables();
    }
}
