package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.sounds.PlaySound;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.team.TeamTuning;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.tuning.Attribute;
import me.makkuusen.timing.system.tuning.Part;
import me.makkuusen.timing.system.tuning.PartCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BoatSetupGui extends BaseGui{

    private final Team team;

    public BoatSetupGui(TPlayer tPlayer, Team team){
        super(Text.getGuiComponent(tPlayer.getPlayer(), Gui.SETTINGS_TITLE), 3);
        this.team = team;
        setButtons(tPlayer);
    }

    public GuiButton boatDisplay(TPlayer tPlayer){
        TeamTuning tuning = team.getTuning();

        List<Component> loreToSet = new ArrayList<>();
        int rating = 0;

        for (Part part : tuning.getEquippedParts().values()){
            rating += part.getRating();
        }

        loreToSet.add(Component.text("rating: " + rating).color(NamedTextColor.YELLOW));

        applyLiveTuningIfActive(team);

        for (Attribute thing : tuning.getAttributes().keySet()){
            loreToSet.add(Component.text(
                    thing.toString() + ": [" +
                            (tuning.getAttributes().get(thing) - 5) +
                            "]"
            ).color(NamedTextColor.WHITE));
        }

        ItemStack item;

        Component itemName = Component.text(team.getName() + " tuning")
                .color(getRatingColor(rating));

        // Check if all three categories contain the same item
        List<Part> equippedParts = new ArrayList<>(tuning.getEquippedParts().values());

        boolean sameItem = equippedParts.size() == 3 &&
                equippedParts.get(0).getItem(tPlayer).getType() == equippedParts.get(1).getItem(tPlayer).getType() &&
                equippedParts.get(0).getItem(tPlayer).getType() == equippedParts.get(2).getItem(tPlayer).getType();

        if (sameItem) {
            item = equippedParts.get(0).getItem(tPlayer).clone();
        } else {
            item = new ItemBuilder(Material.OAK_BOAT).build();
        }

        
        ItemMeta im = item.getItemMeta();

        if (im != null) {
            im.displayName(itemName);

            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            im.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
            im.addItemFlags(ItemFlag.HIDE_DYE);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            im.lore(loreToSet);
            item.setItemMeta(im);
        }

        return new GuiButton(item);
    }

    public GuiButton getCategoryButton(TPlayer tPlayer, PartCategory category){
        TeamTuning tuning = team.getTuning();
        Part currentlyEquipped = tuning.getEquippedParts().get(category);
        GuiButton button;
        if (currentlyEquipped == null){
            button = new GuiButton(
                    new ItemBuilder(category.getMaterial())
                            .setName("Empty " + category)
                            .build()
            );
        } else{
            button = new GuiButton(currentlyEquipped.getItem(tPlayer));
        }

        button.setAction(() -> new PartSelectGui(tPlayer, team, category).show(tPlayer.getPlayer()));
        return button;
    }

    private void setButtons(TPlayer tPlayer){
        setItem(boatDisplay(tPlayer), 13);

        int x = 18;

        for (PartCategory category : PartCategory.values()){
            setItem(getCategoryButton(tPlayer, category), x);
            x++;
        }
    }

    private NamedTextColor getRatingColor(int rating) {
        if (rating > 0 && rating < 300) {
            return NamedTextColor.GREEN;
        } else if (rating < 0){
            return  NamedTextColor.DARK_GRAY;
        }else if (rating == 0){
            return  NamedTextColor.WHITE;
        }else if (rating < 900) {
            return NamedTextColor.YELLOW;
        } else if (rating < 1500) {
            return NamedTextColor.GOLD;
        } else if (rating < 2400) {
            return NamedTextColor.RED;
        } else {
            return NamedTextColor.DARK_BLUE;
        }
    }

    public static void applyLiveTuningIfActive(Team team) {
        // For each online player on the team
        for (TPlayer tPlayer : team.getPlayers()) {
            Player player = tPlayer.getPlayer();
            if (player == null) continue; // Offline

            // Check if they're in an active heat (O(1) lookup)
            Driver driver = EventDatabase.playerInRunningHeat.get(player.getUniqueId());
            if (driver == null) continue; // Not racing

            Heat heat = driver.getHeat();
            if (!heat.getLiveTuningEnabled()) continue; // Live tuning disabled

            // Apply the updated tuning immediately
            heat.applyTuningToPlayer(player, team.getTuning());
        }
    }
}
