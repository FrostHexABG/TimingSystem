package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class TimeTrialGui extends TrackPageGui {

    public TimeTrialGui(TPlayer tPlayer) {
        super(tPlayer, Text.getGuiComponent(tPlayer.getPlayer(), Gui.TRACKS_TITLE));
    }

    public TimeTrialGui(TPlayer tPlayer, Component title) {
        super(tPlayer, title);
    }

    public List<Track> getTracks() {
        return TrackDatabase.getOpenTracks();
    }

    @Override
    public GuiButton getTrackButton(Player player, Track track, boolean isMedal) {
        var button = new GuiButton(track.getItem(player.getUniqueId(), isMedal));
        button.setAction(() -> {
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }
            ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation(), true);
            player.closeInventory();
        });
        return button;
    }
}
