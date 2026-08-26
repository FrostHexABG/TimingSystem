package me.makkuusen.timing.system.tuning;

import org.bukkit.Material;

public enum PartCategory {
    OARS(Material.WOODEN_SHOVEL),
    HULL(Material.OAK_PLANKS),
    RUDDER(Material.OAK_FENCE);

    private final Material material;

    PartCategory(Material material){
        this.material = material;
    }

    public Material getMaterial(){
        return material;
    }
}
