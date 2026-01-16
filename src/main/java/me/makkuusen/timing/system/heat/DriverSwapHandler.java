package me.makkuusen.timing.system.heat;

import co.aikar.idb.DbRow;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.events.driver.DriverSwapEvent;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.drs.PushToPass;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Message;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.database.EventDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DriverSwapHandler {

    public static boolean handleRightClickSwap(Player clicker, Player target) {
        SwapValidation validation = validateSwap(clicker, target, true);
        
        if (!validation.isValid()) {
            if (validation.getErrorMessage() != null) {
                Text.send(clicker, validation.getErrorMessage());
            }
            return validation.isHandled();
        }
        
        Optional<TeamHeatEntry> clickerExistingEntry = findActiveTeamEntry(clicker.getUniqueId());
        if (clickerExistingEntry.isPresent() && clickerExistingEntry.get().getId() != validation.getTeamEntry().getId()) {
            Text.send(clicker, Error.DRIVER_SWAP_ALREADY_IN_HEAT);
            return true;
        }
        
        TeamHeatEntry entry = validation.getTeamEntry();
        UUID oldDriverUUID = entry.getActiveDriverUUID();
        UUID newDriverUUID = clicker.getUniqueId();
        
        transferDriverState(entry, oldDriverUUID, newDriverUUID, false, DriverSwapEvent.SwapType.RIGHT_CLICK);
        
        Text.send(clicker, Success.DRIVER_SWAP_SUCCESS_NEW);
        Text.send(target, Success.DRIVER_SWAP_SUCCESS_OLD, "%player%", clicker.getName());
        
        return true;
    }

    public static boolean handleOfflineReplacement(Player requester) {
        TPlayer tRequester = TSDatabase.getPlayer(requester.getUniqueId());
        if (tRequester == null) {
            Text.send(requester, Error.PLAYER_NOT_FOUND);
            return false;
        }
        
        Optional<TeamHeatEntry> entryOpt = findActiveTeamEntry(requester.getUniqueId());
        if (entryOpt.isEmpty()) {
            Text.send(requester, Error.DRIVER_SWAP_NOT_IN_ACTIVE_HEAT);
            return false;
        }
        
        TeamHeatEntry entry = entryOpt.get();
        Heat heat = entry.getHeat();
        
        if (!heat.isBoatSwitchingEnabled()) {
            Text.send(requester, Error.DRIVER_SWAP_BOAT_SWITCHING_DISABLED);
            return false;
        }
        
        if (!heat.isActive()) {
            Text.send(requester, Error.DRIVER_SWAP_NOT_IN_ACTIVE_HEAT);
            return false;
        }
        
        if (heat.getHeatState() == HeatState.FINISHED) {
            Text.send(requester, Error.DRIVER_SWAP_HEAT_FINISHED);
            return false;
        }
        
        UUID currentDriverUUID = entry.getActiveDriverUUID();
        if (currentDriverUUID == null) {
            Text.send(requester, Error.DRIVER_SWAP_NOT_IN_ACTIVE_HEAT);
            return false;
        }
        
        Player currentDriver = Bukkit.getPlayer(currentDriverUUID);
        if (currentDriver != null && currentDriver.isOnline()) {
            Text.send(requester, Error.DRIVER_SWAP_DRIVER_ONLINE);
            return false;
        }

        if (!hasOnlineTeamMembers(entry)) {
            Text.send(requester, Error.DRIVER_SWAP_NO_ONLINE_MEMBERS);
            return false;
        }
        
        if (!requester.hasPermission("timingsystem.heat.driver.swap")) {
            Text.send(requester, Error.PERMISSION_DENIED);
            return false;
        }
        
        UUID newDriverUUID = requester.getUniqueId();
        transferDriverState(entry, currentDriverUUID, newDriverUUID, true, DriverSwapEvent.SwapType.OFFLINE_REPLACEMENT);
        
        Location checkpointLoc = entry.getLastCheckpointLocation();
        if (checkpointLoc != null) {
            Text.send(requester, Success.DRIVER_SWAP_OFFLINE_SUCCESS,
                "%checkpoint%", String.valueOf(entry.getCurrentCheckpoint()));
        } else {
            Text.send(requester, Success.DRIVER_SWAP_OFFLINE_SUCCESS_START);
        }
        
        return true;
    }

    private static boolean isInPitRegion(Location location, Track track) {
        if (track == null || location == null) {
            return false;
        }
        
        List<TrackRegion> pitRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.PIT);
        
        if (pitRegions.isEmpty()) {
            return false;
        }
        
        return pitRegions.stream().anyMatch(region -> region.contains(location));
    }

    private static SwapValidation validateSwap(Player requester, Player target, boolean requirePitRegion) {
        TPlayer tRequester = TSDatabase.getPlayer(requester.getUniqueId());
        TPlayer tTarget = TSDatabase.getPlayer(target.getUniqueId());
        
        if (tRequester == null || tTarget == null) {
            return SwapValidation.invalid(Error.PLAYER_NOT_FOUND, false);
        }
        
        Optional<TeamHeatEntry> entryOpt = findActiveTeamEntry(requester.getUniqueId());
        if (entryOpt.isEmpty()) {
            return SwapValidation.invalid(null, false);
        }
        
        TeamHeatEntry entry = entryOpt.get();
        Heat heat = entry.getHeat();
        
        if (!heat.isBoatSwitchingEnabled()) {
            return SwapValidation.invalid(Error.DRIVER_SWAP_BOAT_SWITCHING_DISABLED, true);
        }
        
        if (!entry.isPlayerInTeam(target.getUniqueId())) {
            return SwapValidation.invalid(Error.DRIVER_SWAP_NOT_SAME_TEAM, true);
        }

        if (requester.getUniqueId().equals(entry.getActiveDriverUUID())) {
            return SwapValidation.invalid(null, true);
        }
        
        if (!target.getUniqueId().equals(entry.getActiveDriverUUID())) {
            return SwapValidation.invalid(Error.DRIVER_SWAP_NOT_SAME_TEAM, true);
        }
        
        if (!heat.isActive()) {
            return SwapValidation.invalid(Error.DRIVER_SWAP_NOT_IN_ACTIVE_HEAT, true);
        }
        
        if (heat.getHeatState() == HeatState.FINISHED) {
            return SwapValidation.invalid(Error.DRIVER_SWAP_HEAT_FINISHED, true);
        }
        
        if (requirePitRegion) {
            Track track = heat.getEvent().getTrack();
            
            List<TrackRegion> pitRegions = track.getTrackRegions().getRegions(TrackRegion.RegionType.PIT);
            if (pitRegions.isEmpty()) {
                return SwapValidation.invalid(Error.REGIONS_NOT_FOUND, true);
            }
            
            Entity vehicle = target.getVehicle();
            if (vehicle == null || !(vehicle instanceof Boat)) {
                return SwapValidation.invalid(Error.DRIVER_SWAP_NOT_IN_PIT, true);
            }
            
            if (!isInPitRegion(vehicle.getLocation(), track)) {
                return SwapValidation.invalid(Error.DRIVER_SWAP_NOT_IN_PIT, true);
            }
        }
        
        if (!requester.hasPermission("timingsystem.heat.driver.swap")) {
            return SwapValidation.invalid(Error.PERMISSION_DENIED, true);
        }
        
        return SwapValidation.valid(entry);
    }

    private static void transferDriverState(TeamHeatEntry entry, UUID oldDriverUUID, UUID newDriverUUID, boolean resetToCheckpoint, DriverSwapEvent.SwapType swapType) {
        Heat heat = entry.getHeat();
        
        Player oldDriver = Bukkit.getPlayer(oldDriverUUID);
        Player newDriver = Bukkit.getPlayer(newDriverUUID);
        
        if (newDriver == null) {
            return;
        }
        
        Boat boat = null;
        Location boatLocation = null;
        Boat.Type boatType = Boat.Type.OAK;
        
        Driver oldDriverObj = heat.getDrivers().get(oldDriverUUID);
        if (oldDriverObj == null) {
            return;
        }
        
        TPlayer tNewDriver = TSDatabase.getPlayer(newDriverUUID);
        if (tNewDriver == null) {
            return;
        }
        
        int oldPosition = oldDriverObj.getPosition();
        int oldStartPosition = oldDriverObj.getStartPosition();
        Instant oldStartTime = oldDriverObj.getStartTime();
        Instant oldEndTime = oldDriverObj.getEndTime();
        int oldPits = oldDriverObj.getPits();
        java.util.List<Lap> oldLaps = new java.util.ArrayList<>(oldDriverObj.getLaps());
        DriverState oldState = oldDriverObj.getState();
        
        if (oldDriver != null && oldDriver.isOnline()) {
            Entity vehicle = oldDriver.getVehicle();
            if (vehicle instanceof Boat) {
                boat = (Boat) vehicle;
                boatLocation = boat.getLocation().clone();
                boatType = boat.getBoatType();
                oldDriver.leaveVehicle();
            }
        }
        
        heat.getDrivers().remove(oldDriverUUID);
        heat.getStartPositions().remove(oldDriverObj);
        heat.getLivePositions().remove(oldDriverObj);
        
        TimingSystem.getEventDatabase().driverSet(oldDriverObj.getId(), "isRemoved", 1);
        
        EventDatabase.removePlayerFromRunningHeat(oldDriverUUID);
        
        PushToPass.cleanupPlayer(oldDriverUUID);
        
        TPlayer tOldDriver = TSDatabase.getPlayer(oldDriverUUID);
        if (tOldDriver != null) {
            tOldDriver.clearScoreboard();
        }
        
        DbRow newDriverRow = TimingSystem.getEventDatabase().createDriver(
            newDriverUUID,
            heat,
            oldStartPosition
        );
        
        if (newDriverRow == null) {
            return;
        }
        
        Driver newDriverObj =
            new Driver(newDriverRow);
        
        newDriverObj.setStartTime(oldStartTime);
        newDriverObj.setEndTime(oldEndTime);
        newDriverObj.setPosition(oldPosition);
        newDriverObj.setPits(oldPits);
        newDriverObj.setState(oldState);
        
        newDriverObj.getLaps().clear();
        newDriverObj.getLaps().addAll(oldLaps);
        
        heat.getDrivers().put(newDriverUUID, newDriverObj);
        heat.getStartPositions().add(newDriverObj);
        heat.getStartPositions().sort(java.util.Comparator.comparingInt(
            Driver::getStartPosition));
        heat.getLivePositions().add(newDriverObj);
        heat.getLivePositions().sort(java.util.Comparator.comparingInt(
            Driver::getPosition));
        
        EventDatabase.addPlayerToRunningHeat(newDriverObj);
        
        // Initialize push to pass for the new driver if this is a push to pass heat
        if (heat.getPushToPass() != null && heat.getPushToPass()) {
            PushToPass.initializePushToPass(newDriverUUID);
        }
        
        // Update TeamHeatEntry with new active driver
        entry.swapDriver(newDriverUUID);
        
        // If old driver held the fastest lap, transfer it to new driver
        if (heat.getFastestLapUUID() != null && heat.getFastestLapUUID().equals(oldDriverUUID)) {
            heat.setFastestLapUUID(newDriverUUID);
        }
        
        // Remove old boat if it exists
        if (boat != null) {
            boat.remove();
        }
        
        // Handle new driver placement using ApiUtilities for proper boat spawning
        if (resetToCheckpoint) {
            // Offline replacement: Reset to checkpoint position
            Location checkpointLoc = entry.getLastCheckpointLocation();
            if (checkpointLoc != null) {
                ApiUtilities.teleportPlayerAndSpawnBoat(
                        Objects.requireNonNull(newDriver.getPlayer()),
                        heat.getEvent().getTrack(),
                        checkpointLoc
                );
            }
        } else {
            // Right-click swap: Transfer boat control at current location
            if (boatLocation != null) {
                ApiUtilities.teleportPlayerAndSpawnBoat(
                        Objects.requireNonNull(newDriver.getPlayer()),
                        heat.getEvent().getTrack(),
                        boatLocation
                );
            }
        }
        
        heat.updateScoreboard();
        
        DriverSwapEvent event = new DriverSwapEvent(entry, oldDriverUUID, newDriverUUID, swapType);
        Bukkit.getPluginManager().callEvent(event);
    }

    private static Optional<TeamHeatEntry> findActiveTeamEntry(UUID playerUUID) {
        TPlayer tPlayer = TSDatabase.getPlayer(playerUUID);
        if (tPlayer == null) {
            return Optional.empty();
        }
        
        for (Heat heat : EventDatabase.getHeats()) {
            if (!heat.isActive() || !heat.isBoatSwitchingEnabled()) {
                continue;
            }
            
            Optional<TeamHeatEntry> entryOpt = heat.getTeamEntryByPlayer(playerUUID);
            if (entryOpt.isPresent()) {
                return entryOpt;
            }
        }
        
        return Optional.empty();
    }

    private static boolean hasOnlineTeamMembers(TeamHeatEntry entry) {
        Team team = entry.getTeam();
        if (team == null) {
            return false;
        }
        
        for (TPlayer teamMember : team.getPlayers()) {
            Player player = Bukkit.getPlayer(teamMember.getUniqueId());
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        
        return false;
    }
    
    @Getter
    private static class SwapValidation {
        private final boolean valid;
        private final boolean handled;
        private final Message errorMessage;
        private final TeamHeatEntry teamEntry;
        
        private SwapValidation(boolean valid, boolean handled, Message errorMessage, TeamHeatEntry teamEntry) {
            this.valid = valid;
            this.handled = handled;
            this.errorMessage = errorMessage;
            this.teamEntry = teamEntry;
        }
        
        public static SwapValidation valid(TeamHeatEntry entry) {
            return new SwapValidation(true, true, null, entry);
        }
        
        public static SwapValidation invalid(Message errorMessage, boolean handled) {
            return new SwapValidation(false, handled, errorMessage, null);
        }
    }
}
