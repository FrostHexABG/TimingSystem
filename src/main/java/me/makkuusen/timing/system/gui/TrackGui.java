package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TrackGui extends TrackPageGui {

    public TrackGui(TPlayer tPlayer) {
        super(tPlayer, Text.getGuiComponent(tPlayer.getPlayer(), Gui.TRACKS_TITLE));
    }

    public TrackGui(TPlayer tPlayer, Component title) {
        super(tPlayer, title);
    }

    public List<Track> getTracks() {
        return TrackDatabase.tracks;
    }

    @Override
    public GuiButton getTrackButton(Player player, Track track, boolean isMedal) {
        var item = setTrackLore(player, track, track.getItem(player.getUniqueId(), isMedal));
        var button = new GuiButton(item);
        button.setAction(() -> {
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }
            player.teleport(track.getSpawnLocation());
            player.closeInventory();
        });
        return button;
    }

    private ItemStack setTrackLore(Player player, Track track, ItemStack toReturn) {
        List<Component> loreToSet = new ArrayList<>();
        loreToSet.add(Text.get(player, Gui.TOTAL_FINISHES, "%total%", String.valueOf(track.getTimeTrials().getTotalFinishes())));
        loreToSet.add(Text.get(player, Gui.TOTAL_ATTEMPTS, "%total%", String.valueOf(track.getTimeTrials().getTotalAttempts())));
        loreToSet.add(Text.get(player, Gui.TIME_SPENT, "%time%", ApiUtilities.formatAsTimeSpent(track.getTotalTimeSpent())));
        loreToSet.add(Text.get(player, Gui.GRIDS, "%grids%", String.valueOf(track.getTrackLocations().getLocations(TrackLocation.Type.GRID).size())));
        loreToSet.add(Text.get(player, Gui.CREATED_BY, "%player%", track.getOwner().getName()));
        if(!track.getContributorsAsString().isBlank()) loreToSet.add(Text.get(player, Gui.CONTRIBUTORS, "%contributors%", track.getContributorsAsString()));
        loreToSet.add(Text.get(player, Gui.CREATED_AT, "%time%", ApiUtilities.niceDate(track.getDateCreated())));
        loreToSet.add(Text.get(player, Gui.WEIGHT, "%weight%", String.valueOf(track.getWeight())));

        Component tags = Component.empty();
        boolean notFirst = false;
        for (TrackTag tag : track.getTrackTags().get()) {
            if (notFirst) {
                tags = tags.append(Component.text(", ").color(tPlayer.getTheme().getSecondary()));
            }
            tags = tags.append(Component.text(tag.getValue()).color(tag.getColor()));
            notFirst = true;
        }
        loreToSet.add(Text.get(player, Gui.TAGS).append(tags));
        ItemMeta im = toReturn.getItemMeta();
        if (im != null) {
            im.lore(loreToSet);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            im.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
            im.addItemFlags(ItemFlag.HIDE_DYE);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            toReturn.setItemMeta(im);
        }
        return toReturn;
    }


}
