package me.makkuusen.timing.system.boat;

import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.boat.*;

public class DefaultBoatSpawner implements BoatSpawner {

	@Override
	public Boat spawnBoat(Location location, String woodType, Boolean chestBoat) {

		Boat boat;

		switch (woodType) {
			case "ACACIA" -> {
				if (chestBoat) {
					boat = (AcaciaChestBoat) location.getWorld().spawnEntity(location, EntityType.ACACIA_CHEST_BOAT);
				} else {
					boat = (AcaciaBoat) location.getWorld().spawnEntity(location, EntityType.ACACIA_BOAT);
				}
			}
			case "BIRCH" -> {
				if (chestBoat) {
					boat = (BirchChestBoat) location.getWorld().spawnEntity(location, EntityType.BIRCH_CHEST_BOAT);
				} else {
					boat = (BirchBoat) location.getWorld().spawnEntity(location, EntityType.BIRCH_BOAT);
				}
			}
			case "DARK_OAK" -> {
				if (chestBoat) {
					boat = (DarkOakChestBoat) location.getWorld().spawnEntity(location, EntityType.DARK_OAK_CHEST_BOAT);
				} else {
					boat = (DarkOakBoat) location.getWorld().spawnEntity(location, EntityType.DARK_OAK_BOAT);
				}
			}
			case "SPRUCE" -> {
				if (chestBoat) {
					boat = (SpruceChestBoat) location.getWorld().spawnEntity(location, EntityType.SPRUCE_CHEST_BOAT);
				} else {
					boat = (SpruceBoat) location.getWorld().spawnEntity(location, EntityType.SPRUCE_BOAT);
				}
			}
			case "JUNGLE" -> {
				if (chestBoat) {
					boat = (JungleChestBoat) location.getWorld().spawnEntity(location, EntityType.JUNGLE_CHEST_BOAT);
				} else {
					boat = (JungleBoat) location.getWorld().spawnEntity(location, EntityType.JUNGLE_BOAT);
				}
			}
			case "MANGROVE" -> {
				if (chestBoat) {
					boat = (MangroveChestBoat) location.getWorld().spawnEntity(location, EntityType.MANGROVE_CHEST_BOAT);
				} else {
					boat = (MangroveBoat) location.getWorld().spawnEntity(location, EntityType.MANGROVE_BOAT);
				}
			}
			case "CHERRY" -> {
				if (chestBoat) {
					boat = (CherryChestBoat) location.getWorld().spawnEntity(location, EntityType.CHERRY_CHEST_BOAT);
				} else {
					boat = (CherryBoat) location.getWorld().spawnEntity(location, EntityType.CHERRY_BOAT);
				}
			}
			case "BAMBOO" -> {
				if (chestBoat) {
					boat = (BambooChestRaft) location.getWorld().spawnEntity(location, EntityType.BAMBOO_CHEST_RAFT);
				} else {
					boat = (BambooRaft) location.getWorld().spawnEntity(location, EntityType.BAMBOO_RAFT);
				}
			}
			default -> {
				if (chestBoat) {
					boat = (OakChestBoat) location.getWorld().spawnEntity(location, EntityType.OAK_CHEST_BOAT);
				} else {
					boat = (OakBoat) location.getWorld().spawnEntity(location, EntityType.OAK_BOAT);
				}
			}
		}

		return boat;
	}
}
