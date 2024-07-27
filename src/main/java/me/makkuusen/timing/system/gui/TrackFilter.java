package me.makkuusen.timing.system.gui;

import lombok.Getter;
import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.TrackTagManager;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.tags.TrackTag;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class TrackFilter {

    private Set<TrackTag> tags;
    private boolean anyMatch = false;
    public TrackFilter() {
        tags = new HashSet<>();
    }

    public void setAnyMatch(boolean anyMatch) {
        this.anyMatch = anyMatch;
    }

    public void addTag(TrackTag tag) {
        tags.add(tag);
    }

    public void removeTag(TrackTag tag) {
        tags.remove(tag);
    }

    public void setTags(Set<TrackTag> tags) {
        this.tags = tags;
    }

    public boolean hasValidTags() {
        for (TrackTag tag : tags) {
            if (TrackTagManager.getTrackTag(tag.getValue()) == null) {
                return false;
            }
        }
        return true;
    }

    public ItemStack getItem(TPlayer tPlayer){
        var item = new ItemBuilder(Material.HOPPER).setName(Text.get(tPlayer, Gui.FILTER_BY)).build();
        ItemMeta im = getItemMeta(tPlayer, item);
        im.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        im.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        im.addItemFlags(ItemFlag.HIDE_DYE);
        item.setItemMeta(im);

        return item;
    }

    @NotNull
    private ItemMeta getItemMeta(TPlayer tPlayer, ItemStack item) {
        List<Component> loreToSet = new ArrayList<>();

        boolean notFirst = false;
        Component tags = Component.empty();
        for (TrackTag tag : getTags()) {
            if (notFirst) {
                tags = tags.append(Component.text(", ").color(tPlayer.getTheme().getSecondary()));
            }
            tags = tags.append(Component.text(tag.getValue()).color(tag.getColor()));
            notFirst = true;
        }
        loreToSet.add(tags);

        ItemMeta im = item.getItemMeta();
        im.lore(loreToSet);
        return im;
    }
}
