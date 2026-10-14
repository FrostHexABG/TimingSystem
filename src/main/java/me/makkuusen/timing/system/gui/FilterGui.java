package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.TrackTagManager;
import me.makkuusen.timing.system.sounds.PlaySound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.tags.TrackTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class FilterGui extends BaseGui{

    TrackPageGui oldTrackPage;
    TrackFilter filter;
    public FilterGui(TrackPageGui trackPage) {
        super(Text.getGuiComponent(trackPage.tPlayer.getPlayer(), Gui.FILTER_TITLE), 4);
        oldTrackPage = trackPage;
        this.filter = trackPage.filter;
        setButtons();
    }

    public void setButtons() {
        setFilterOptions();
        GuiButton save = new GuiButton(new ItemBuilder(Material.ARROW).setName(Text.get(oldTrackPage.tPlayer, Gui.RETURN)).build());
        save.setAction(() -> {
            PlaySound.buttonClick(oldTrackPage.tPlayer);
            oldTrackPage.tPlayer.setFilter(filter);
            TrackPageGui.openNewTrackPage(oldTrackPage, oldTrackPage.tPlayer, oldTrackPage.title);
        });
        setItem(save, 35);
    }

    public void setFilterOptions() {

        int count = 0;
        boolean getZeroWeight = oldTrackPage.tPlayer.getPlayer().hasPermission("timingsystem.packs.trackadmin");
        for (TrackTag tag : TrackTagManager.getSortedTrackTags(getZeroWeight)) {
            if (count > 25) {
                break;
            }

            if (count == 9) {
                count = count + 9;
            }

            setItem(getStatusButton(tag), count);
            var button = new GuiButton(tag.getItem(oldTrackPage.tPlayer.getPlayer()));
            int finalCount = count;
            button.setAction(() -> {
                PlaySound.buttonClick(oldTrackPage.tPlayer);
                if (filter.getTags().contains(tag)) {
                    filter.removeTag(tag);
                    filter.excludeTag(tag);
                } else if (filter.getExcludedTags().contains(tag)) {
                    filter.removeExcludedTag(tag);
                } else {
                    filter.addTag(tag);
                }
                setItem(getStatusButton(tag), finalCount);
            });
            setItem(button, count + 9);
            count++;
        }
    }

    private GuiButton getStatusButton(TrackTag tag) {
        var tPlayer = oldTrackPage.tPlayer;
        if (filter.getTags().contains(tag)) {
            return GuiCommon.getStatusOnButton(tPlayer);
        }
        if (filter.getExcludedTags().contains(tag)) {
            var button = new GuiButton(new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).setName("§r").build());
            button.setAction(() -> {});
            return button;
        }
        return GuiCommon.getStatusOffButton(tPlayer);
    }
}
