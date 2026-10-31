package me.makkuusen.timing.system.tuning;

import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.tplayer.TPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.w3c.dom.Attr;

import java.util.*;

public class Part {
    @Getter
    @Setter
    private String name;
    @Getter
    private String id;
    @Getter
    @Setter
    public PartCategory CategoryName;
    @Getter
    @Setter
    public Integer rating;
    @Getter
    @Setter
    private String description = "replace me";
    @Setter
    private Material item;
    private Map<Attribute, Integer> attributes = new HashMap<>();

    public Part(){
        this.id = UUID.randomUUID().toString();
        this.rating = 0;

        addAttribute(Attribute.FORWARD_ACCEL, 0);
        addAttribute(Attribute.YAW_ACCEL, 0);
        addAttribute(Attribute.DEFAULT_SLIPPERINESS, 0);
        addAttribute(Attribute.PACKED_ICE_SLIPPERINESS, 0);
        addAttribute(Attribute.BLUE_ICE_SLIPPERINESS, 0);
        addAttribute(Attribute.TURNING_FORWARD_ACCEL, 0);
        addAttribute(Attribute.BACKWARD_ACCEL, 0);
        addAttribute(Attribute.SCALE, 0);
        addAttribute(Attribute.MAX_SPEED, 0);
        addAttribute(Attribute.MAX_SPEED_RESISTANCE, 0);
        addAttribute(Attribute.BRAKE_SLIPPERINESS, 0);
        addAttribute(Attribute.WALLTAP_MULTIPLIER, 0);
        addAttribute(Attribute.LATERAL_SLIPPERINESS, 0);
    }

    public void addAttribute(Attribute name, Integer value){
        attributes.put(name, value);
    }

    public void removeAttribute(Attribute name){
        attributes.remove(name);
    }

    public Map<Attribute, Integer> getAttributes(){
        return attributes;
    }

    public Integer getValue(Attribute attribute){
        return attributes.get(attribute);
    }

    public ItemStack getItem(TPlayer tPlayer){
        if (this.item == null){
            return new ItemBuilder(Material.PUFFERFISH).setName(this.getName()).build();
        }

        NamedTextColor nameColor;

        if (rating > 800){
            nameColor = NamedTextColor.DARK_BLUE;
        }
        else if (rating > 500){
            nameColor = NamedTextColor.RED;
        }
        else if (rating > 300){
            nameColor = NamedTextColor.GOLD;
        }
        else if (rating > 100){
            nameColor = NamedTextColor.YELLOW;
        }
        else if (rating > 1){
            nameColor = NamedTextColor.GREEN;
        }
        else if (rating < 0){
            nameColor = NamedTextColor.DARK_GRAY;
        }
        else{
            nameColor = NamedTextColor.WHITE;
        }


        ItemStack Item = new ItemBuilder(this.item).setName(Component.text(this.getName()).color(nameColor) ).build();

        List<Component> loreToSet = new ArrayList<>();

        loreToSet.add(Component.text(this.getDescription()));
        loreToSet.add(Component.text("rating: " + getRating()).color(NamedTextColor.YELLOW));

        for (Attribute thing : this.attributes.keySet()){
            if (attributes.get(thing) != 0){
                loreToSet.add(Component.text(thing.toString() +": [" + attributes.get(thing) + "]").color(NamedTextColor.GRAY));
            }
        }

        ItemMeta im = Item.getItemMeta();

        if (im != null) {
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            im.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
            im.addItemFlags(ItemFlag.HIDE_DYE);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            im.lore(loreToSet);
            Item.setItemMeta(im);
        }

        return Item;
    }
}
