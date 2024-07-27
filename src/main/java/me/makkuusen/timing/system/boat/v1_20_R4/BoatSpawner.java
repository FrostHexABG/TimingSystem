package me.makkuusen.timing.system.boat.v1_20_R4;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R4.CraftServer;
import org.bukkit.craftbukkit.v1_20_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftBoat;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftChestBoat;
import org.bukkit.event.entity.CreatureSpawnEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.Boat;

public class BoatSpawner {

    public static org.bukkit.entity.Boat spawnBoat(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        CollisionlessBoat boat = new CollisionlessBoat(level, location.getX(), location.getY(), location.getZ());
        float yaw = Location.normalizeYaw(location.getYaw());
        boat.setYRot(yaw);
        boat.yRotO = yaw;
        boat.setYHeadRot(yaw);
        level.addFreshEntity(boat, CreatureSpawnEvent.SpawnReason.COMMAND);
        boat.setVariant(Boat.Type.OAK);
        var craftBoat = new CraftBoat((CraftServer) Bukkit.getServer(), boat);
        return craftBoat;
    }

    public static org.bukkit.entity.ChestBoat spawnChestBoat(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        CollisionlessChestBoat boat = new CollisionlessChestBoat(level, location.getX(), location.getY(), location.getZ());
        float yaw = Location.normalizeYaw(location.getYaw());
        boat.setYRot(yaw);
        boat.yRotO = yaw;
        boat.setYHeadRot(yaw);
        level.addFreshEntity(boat, CreatureSpawnEvent.SpawnReason.COMMAND);
        boat.setVariant(Boat.Type.OAK);
        var craftBoat = new CraftChestBoat((CraftServer) Bukkit.getServer(), boat);
        return craftBoat;
    }

    public boolean isCollisionless(org.bukkit.entity.Boat boat) {
        return ((CraftBoat) boat).getHandle() instanceof CollisionlessBoat;
    }
	
}
