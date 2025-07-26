package me.makkuusen.timing.system;

import lombok.Getter;
import me.makkuusen.timing.system.database.*;

import java.util.List;

@Getter
public class TimingSystemConfiguration {
    private final int leaderboardsUpdateTick;
    private final List<String> leaderboardsFastestTimeLines;
    private final int timesPageSize;
    private final int laps;
    private final int pits;
    private final Integer timeLimit;
    private final Integer qualyStartDelayInMS;
    private final Integer finalStartDelayInMS;
    private final String databaseTypeRaw;
    private final String sqlHost;
    private final int sqlPort;
    private final String sqlDatabase;
    private final String sqlUsername;
    private final String sqlPassword;
    private int scoreboardMaxRows;
    private Integer scoreboardInterval;
    private final boolean frostHexAddOnEnabled;
    private final boolean medalsAddOnEnabled;
    private final boolean medalsShowNextMedal;
    private final boolean medalsShowEveryone;
    private final int medalsPlayersLimit;
    private final double netheritePos;
    private final double emeraldPos;
    private final double diamondPos;
    private final double goldPos;
    private final double silverPos;
    private final double copperPos;

    private final Object databaseType;

    TimingSystemConfiguration(TimingSystem plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        leaderboardsUpdateTick = plugin.getConfig().getInt("leaderboards.updateticks");
        leaderboardsFastestTimeLines = plugin.getConfig().getStringList("leaderboards.fastesttime.lines");
        timesPageSize = plugin.getConfig().getInt("tracks.timesPageSize");

        laps = plugin.getConfig().getInt("finals.laps");
        pits = plugin.getConfig().getInt("finals.pits");
        timeLimit = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("qualifying.timeLimit", "60s"));

        qualyStartDelayInMS = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("qualifying.startDelay", "1s"));
        finalStartDelayInMS = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("finals.startDelay", "0"));

        databaseTypeRaw = plugin.getConfig().getString("sql.databaseType", "MySQL");
        sqlHost = plugin.getConfig().getString("sql.host");
        sqlPort = plugin.getConfig().getInt("sql.port");
        sqlDatabase = plugin.getConfig().getString("sql.database");
        sqlUsername = plugin.getConfig().getString("sql.username");
        sqlPassword = plugin.getConfig().getString("sql.password");

        scoreboardMaxRows = plugin.getConfig().getInt("scoreboard.maxRows", 15);
        scoreboardInterval = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("scoreboard.interval","1000"));
        frostHexAddOnEnabled = plugin.getConfig().getBoolean("frosthexaddon.enabled");
        medalsAddOnEnabled = plugin.getConfig().getBoolean("medalsaddon.enabled");
        medalsShowNextMedal = plugin.getConfig().getBoolean("medalsaddon.showNextMedal");
        medalsShowEveryone = plugin.getConfig().getBoolean("medalsaddon.showEveryone");
        medalsPlayersLimit = plugin.getConfig().getInt("medalsaddon.playersLimit", 500);
        netheritePos = plugin.getConfig().getDouble("medalsaddon.netheritePos", 3);
        emeraldPos = plugin.getConfig().getDouble("medalsaddon.emeraldPos", 10);
        diamondPos = plugin.getConfig().getDouble("medalsaddon.diamondPos", 0.05);
        goldPos = plugin.getConfig().getDouble("medalsaddon.goldPos", 0.1);
        silverPos = plugin.getConfig().getDouble("medalsaddon.silverPos", 0.25);
        copperPos = plugin.getConfig().getDouble("medalsaddon.copperPos", 0.50);

        databaseType = switch (databaseTypeRaw.toLowerCase()) {
            case "sqlite" -> new SQLiteDatabase();
            case "mariadb" -> new MariaDBDatabase();
            default -> new MySQLDatabase();
        };
    }


    public void setScoreboardMaxRows(int rows) {
        scoreboardMaxRows = rows;
    }

    public void setScoreboardInterval(String value) {
        scoreboardInterval =  ApiUtilities.parseDurationToMillis(value);
    }

    public <T extends TSDatabase & EventDatabase> T getDatabaseType() {
        // This could maybe be improved but I have no idea :P
        return (T) databaseType;
    }
}
