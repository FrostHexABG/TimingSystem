package me.makkuusen.timing.system;

import co.aikar.taskchain.TaskChain;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLeaderboard;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import org.bukkit.Bukkit;

import java.util.List;

public class LeaderboardManager {

	public static final int TICKS_BETWEEN_INDIVIDUAL_UPDATES = 20;

    public static void updateFastestTimeLeaderboard(Track track) {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }

        List<TrackLocation> trackLeaderboards = track.getTrackLocations().getLocations(TrackLocation.Type.LEADERBOARD);

        for (TrackLocation tl : trackLeaderboards) {
            if (tl instanceof TrackLeaderboard trackLeaderboard) {
                trackLeaderboard.createOrUpdateHologram();
            }
        }

    }

    public static void updateAllFastestTimeLeaderboard() {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }

        TaskChain<?> chain = TimingSystem.newChain();
        for (Track t : TrackDatabase.tracks) {
            chain.sync(() -> updateFastestTimeLeaderboard(t)).delay(TICKS_BETWEEN_INDIVIDUAL_UPDATES);
        }
        chain.execute();
    }

    public static void removeAllLeaderboards() {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }
        for (Track t : TrackDatabase.tracks) {
            removeLeaderboards(t);
        }
    }

    public static void removeLeaderboards(Track track) {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }
        Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> {

            List<TrackLocation> trackLeaderboards = track.getTrackLocations().getLocations(TrackLocation.Type.LEADERBOARD);

            for (TrackLocation tl : trackLeaderboards) {
                if (tl instanceof TrackLeaderboard trackLeaderboard) {
                    trackLeaderboard.removeHologram();
                }
            }
        });
    }

    public static void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(TimingSystem.getPlugin(), LeaderboardManager::updateAllFastestTimeLeaderboard, 30 * 20, TimingSystem.configuration.getLeaderboardsUpdateTick());
    }
}
