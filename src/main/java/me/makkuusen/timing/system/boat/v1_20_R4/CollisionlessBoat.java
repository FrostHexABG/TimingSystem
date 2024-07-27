package me.makkuusen.timing.system.boat.v1_20_R4;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

public class CollisionlessBoat extends Boat {

	public CollisionlessBoat(Level world, double d0, double d1, double d2) {
		super(world, d0, d1, d2);
	}
	
	@Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

}
