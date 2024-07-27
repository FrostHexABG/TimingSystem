package me.makkuusen.timing.system.boat.v1_20_R4;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.level.Level;

public class CollisionlessChestBoat extends ChestBoat {

	public CollisionlessChestBoat(Level world, double d0, double d1, double d2) {
		super(world, d0, d1, d2);
	}

	public CollisionlessChestBoat(EntityType<? extends ChestBoat> entitytypes, Level world) {
        super(entitytypes, world);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
