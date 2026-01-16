package me.makkuusen.timing.system;

import co.aikar.commands.CommandReplacements;
import co.aikar.commands.PaperCommandManager;
import co.aikar.idb.DB;
import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainFactory;
import lombok.Getter;
import me.makkuusen.timing.system.commands.*;
import me.makkuusen.timing.system.database.*;
import me.makkuusen.timing.system.gui.GUIListener;
import me.makkuusen.timing.system.gui.GuiCommon;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.listeners.DrsListener;
import me.makkuusen.timing.system.listeners.DriverSwapListener;
import me.makkuusen.timing.system.listeners.GSitListener;
import me.makkuusen.timing.system.listeners.ReadyCheckListener;
import me.makkuusen.timing.system.loneliness.LonelinessController;
import me.makkuusen.timing.system.papi.TimingSystemPlaceholder;
import me.makkuusen.timing.system.permissions.*;
import me.makkuusen.timing.system.theme.TSColor;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.timetrial.TimeTrialListener;
import me.makkuusen.timing.system.tplayer.TPlayer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
public class TimingSystem extends JavaPlugin {

    public Logger logger;
    @Getter
    private static TimingSystem plugin;

    @Getter
    private static TSDatabase database;
    @Getter
    private static EventDatabase eventDatabase;
    @Getter
    private static TrackDatabase trackDatabase;
    @Getter
    private static LogDatabase logDatabase;
    @Getter
    private static TeamDatabase teamDatabase;

    public static TimingSystemConfiguration configuration;
    public static boolean enableLeaderboards = true;
    public static Map<UUID, TPlayer> players = new HashMap<>();
    @Getter
    private static LanguageManager languageManager;
    public static Instant currentTime = Instant.now();
    public static ScoreboardLibrary scoreboardLibrary;

    public static Theme defaultTheme = new Theme();
    private static TaskChainFactory taskChainFactory;



    public void onEnable() {

        plugin = this;
        logger = getLogger();
        configuration = new TimingSystemConfiguration(this);
        TSListener.plugin = this;
        Text.plugin = this;
        languageManager = new LanguageManager(this, "en_us");

        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch(NoPacketAdapterAvailableException e) {
            scoreboardLibrary = new NoopScoreboardLibrary();
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new GUIListener(), plugin);
        pm.registerEvents(new TSListener(), plugin);
        pm.registerEvents(new TimeTrialListener(), plugin);
        pm.registerEvents(new LonelinessController(plugin), plugin);
        pm.registerEvents(new ReadyCheckListener(), plugin);
        pm.registerEvents(new DriverSwapListener(), plugin);
        pm.registerEvents(new DrsListener(), plugin);

        if (pm.isPluginEnabled("GSit")) {
            pm.registerEvents(new GSitListener(), plugin);
        }

        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "openboatutils:settings", new PluginMessageReceiver());
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "openboatutils:settings");

        GuiCommon.init();

        PaperCommandManager manager = new PaperCommandManager(this);
        // enable brigadier integration for paper servers
        manager.enableUnstableAPI("brigadier");

        CommandReplacements cr = manager.getCommandReplacements();
        PermissionTimingSystem.init(cr);
        PermissionTrack.init(cr);
        PermissionTrackEdit.init(cr);
        PermissionTimeTrial.init(cr);
        PermissionRace.init(cr);
        PermissionEvent.init(cr);
        PermissionRound.init(cr);
        PermissionHeat.init(cr);
        PermissionBoatUtilsMode.init(cr);
        PermissionTeam.init(cr);

        ContextResolvers.loadCommandContextsAndCompletions(manager);

        manager.registerCommand(new CommandEvent());
        manager.registerCommand(new CommandRound());
        manager.registerCommand(new CommandTrack());
        manager.registerCommand(new CommandTrackEdit());
        manager.registerCommand(new CommandHeat());
        manager.registerCommand(new CommandTimeTrial());
        manager.registerCommand(new CommandSettings());
        manager.registerCommand(new CommandTimingSystem());
        manager.registerCommand(new CommandBoat());
        manager.registerCommand(new CommandRace());
        manager.registerCommand(new CommandReset());
        manager.registerCommand(new CommandTimeTrialRandom());
        manager.registerCommand(new CommandTimeTrialCancel());
        manager.registerCommand(new CommandGhost());
        manager.registerCommand(new CommandUnghost());
        manager.registerCommand(new CommandBoatUtilsModeEdit());
        manager.registerCommand(new CommandTeam());
        taskChainFactory = BukkitTaskChainFactory.create(this);

        database = configuration.getDatabaseType();
        eventDatabase = configuration.getDatabaseType();
        trackDatabase = configuration.getDatabaseType();
        logDatabase = configuration.getDatabaseType();
        teamDatabase = configuration.getDatabaseType();

        if (!database.initialize()) return;
        database.update();
        TSDatabase.synchronize();
        TrackDatabase.loadTrackFinishesAsync();
        EventDatabase.initSynchronize();
        //LogDatabase.synchronize();
        
        // Initialize team system with lightweight cache structures (no bulk loading)
        me.makkuusen.timing.system.team.TeamManager.initializeTeams();

        var tasks = new Tasks();
        tasks.startPlayerTimer(plugin);
        tasks.startParticleSpawner(plugin);
        tasks.generateTotalTime(plugin);
        tasks.startDrsCleanup(plugin);
        tasks.startPushToPassUpdater(plugin);

        // Small check to make sure that PlaceholderAPI is installed
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TimingSystemPlaceholder(this).register();
            ApiUtilities.msgConsole("PlaceholderAPI registered.");
        }

        if (Bukkit.getPluginManager().getPlugin("HolographicDisplays") == null && Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) {
            ApiUtilities.msgConsole("No Hologram Plugin installed. Leaderboards are disabled.");
            enableLeaderboards = false;
        } else {
            LeaderboardManager.startUpdateTask();
        }
        setConfigDefaultColors();
        logger.info("Version " + getPluginMeta().getVersion() + " enabled.");

        int pluginId = 16012;
        new Metrics(this, pluginId);
    }

    private void setConfigDefaultColors() {
        for (TSColor tsColor : TSColor.values()) {
            String configColor = plugin.getConfig().getString(tsColor.getKey());
            if (configColor != null && parseColor(configColor) != null) {
                var color = parseColor(configColor);
                switch (tsColor) {
                    case SECONDARY -> defaultTheme.setSecondary(color);
                    case PRIMARY -> defaultTheme.setPrimary(color);
                    case AWARD -> defaultTheme.setAward(color);
                    case AWARD_SECONDARY -> defaultTheme.setAwardSecondary(color);
                    case ERROR -> defaultTheme.setError(color);
                    case BROADCAST -> defaultTheme.setBroadcast(color);
                    case SUCCESS -> defaultTheme.setSuccess(color);
                    case WARNING -> defaultTheme.setWarning(color);
                    case TITLE -> defaultTheme.setTitle(color);
                    case BUTTON -> defaultTheme.setButton(color);
                    case BUTTON_ADD -> defaultTheme.setButtonAdd(color);
                    case BUTTON_REMOVE -> defaultTheme.setButtonRemove(color);
                    default -> {
                    }
                }
            }
        }
    }

    private TextColor parseColor(String color) {
        if (TextColor.fromHexString(color) != null) {
            return TextColor.fromHexString(color);
        }
        if (NamedTextColor.NAMES.value(color.toLowerCase()) != null) {
            return NamedTextColor.NAMES.value(color.toLowerCase());
        }
        return null;
    }

    @Override
    public void onDisable() {
        EventDatabase.getHeats().stream().filter(Heat::isActive).forEach(Heat::onShutdown);
        
        // Cleanup team system cache
        me.makkuusen.timing.system.team.TeamManager.unload();
        
        logger.info("Version " + getPluginMeta().getVersion() + " disabled.");
        scoreboardLibrary.close();
        TSListener.plugin = null;
        Text.plugin = null;
        logger = null;
        plugin = null;
        DB.close();
    }

    public static <T> TaskChain<T> newChain() {
        return taskChainFactory.newChain();
    }
}
