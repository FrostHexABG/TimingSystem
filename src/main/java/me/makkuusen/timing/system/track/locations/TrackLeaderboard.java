package me.makkuusen.timing.system.track.locations;

import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.holograms.HologramDH;
import me.makkuusen.timing.system.track.holograms.HologramHD;
import me.makkuusen.timing.system.track.holograms.HologramManager;
import me.makkuusen.timing.system.TimingSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class TrackLeaderboard extends TrackLocation {

    HologramManager hologramManager;
    Track track;

    public TrackLeaderboard(int trackId, int index, Location location, TrackLocation.Type locationType) {
        super(trackId, index, location, locationType);
        this.init();
    }

    public TrackLeaderboard(DbRow data) {
        super(data);
        this.init();
    }

    private void init() {
        var maybeTrack = TrackDatabase.getTrackById(getTrackId());
        maybeTrack.ifPresent(value -> this.track = value);

        if (Bukkit.getServer().getPluginManager().getPlugin("HolographicDisplays") != null) {
            hologramManager = new HologramHD();
        } else if (Bukkit.getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
            hologramManager = new HologramDH();
        }
    }

    public void createOrUpdateHologram() {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }

        Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> {
            if (!getLocation().isWorldLoaded()) {
                // World not ready yet (e.g. lazy-loaded world or empty server after restart).
                // Retry shortly; will succeed once world is loaded.
                Bukkit.getScheduler().runTaskLater(TimingSystem.getPlugin(), this::createOrUpdateHologram, 100);
                return;
            }

            hologramManager.createOrUpdateHologram(getLocation(), getHologramLines());
        });
    }

    public void removeHologram() {
        if (!TimingSystem.enableLeaderboards) {
            return;
        }
        Bukkit.getScheduler().runTask(TimingSystem.getPlugin(), () -> hologramManager.removeHologram());
    }

    private List<String> getHologramLines() {
        var topTen = track.getTimeTrials().getTopList(10);
        List<String> textLines = new ArrayList<>();

        for (String line : TimingSystem.configuration.getLeaderboardsFastestTimeLines()) {

            line = line.replace("{mapname}", track.getDisplayName());

            // Replace stuff

            for (int i = 1; i <= 10; i++) {
                String playerName;
                String time;
                try {
                    playerName = topTen.get(i - 1).getPlayer().getName();
                    time = ApiUtilities.formatAsTime(topTen.get(i - 1).getTime());
                } catch (IndexOutOfBoundsException e) {
                    playerName = "Empty";
                    time = "None";
                }
                line = line.replace("{name" + i + "}", playerName);
                line = line.replace("{time" + i + "}", time);
            }
            textLines.add(line);
        }
        return textLines;
    }
}
