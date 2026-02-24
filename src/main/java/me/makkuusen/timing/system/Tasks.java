package me.makkuusen.timing.system;

import com.sk89q.worldedit.math.BlockVector2;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.drs.DrsManager;
import me.makkuusen.timing.system.drs.PushToPass;
import me.makkuusen.timing.system.heat.QualifyHeat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.participant.DriverState;
import me.makkuusen.timing.system.round.FinalRound;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.ActionBar;
import me.makkuusen.timing.system.timetrial.TimeTrial;
import me.makkuusen.timing.system.timetrial.TimeTrialAttempt;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.editor.TrackEditor;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.regions.TrackPolyRegion;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class Tasks {

    public Tasks() {
    }

    public void startParticleSpawner(TimingSystem plugin) {
        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : TrackEditor.playerTrackVisualisation) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                Track track = TrackEditor.getPlayerTrackSelection(uuid);

                track.getTrackRegions().getRegions().forEach(trackRegion -> setParticles(player, trackRegion));
                track.getTrackLocations().getLocations(TrackLocation.Type.GRID).forEach(location -> setParticles(player, location.getLocation(), Particle.WAX_OFF));
                track.getTrackLocations().getLocations(TrackLocation.Type.QUALYGRID).forEach(location -> setParticles(player, location.getLocation(), Particle.WAX_ON));
                track.getTrackLocations().getLocations(TrackLocation.Type.FINISH_TP).forEach(location -> setParticles(player, location.getLocation(), Particle.HEART));
                track.getTrackLocations().getLocations(TrackLocation.Type.FINISH_TP_ALL).forEach(location -> setParticles(player, location.getLocation(), Particle.ANGRY_VILLAGER));
            }
        }, 0, 10);
    }

    public void startPlayerTimer(TimingSystem plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                elytraProtectionCountdown(p);
                if (TimeTrialController.timeTrials.containsKey(p.getUniqueId())) {
                    timeTrialTimer(p);
                } else {
                    var maybeDriver = EventDatabase.getDriverFromRunningHeat(p.getUniqueId());
                    if (maybeDriver.isPresent()) {
                        displayDriverTimer(p, maybeDriver.get());
                    } else {
                        displaySpectatorTimer(p);
                    }
                }
            }

        }, 5, 1);
    }

    public void generateTotalTime(TimingSystem plugin) {

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {

            for (Track track : TrackDatabase.tracks) {
                long time = 0L;
                long bestTime = 0L;
                var topTime = track.getTimeTrials().getTopList(1);
                if (!topTime.isEmpty()) {
                    bestTime = topTime.get(0).getTime();

                    for (List<TimeTrialFinish> l : track.getTimeTrials().getTimeTrialFinishes().values()) {
                        for (TimeTrialFinish ttf : l) {
                            if (ttf.getTime() < (bestTime * 4)) {
                                time += ttf.getTime();
                            }
                        }
                    }
                }

                for (List<TimeTrialAttempt> l : track.getTimeTrials().getTimeTrialAttempts().values()) {
                    for (TimeTrialAttempt ttf : l) {
                        if (bestTime != 0) {
                            if (ttf.getTime() < (bestTime * 4)) {
                                time += ttf.getTime();
                            }
                        } else {
                            time += ttf.getTime();
                        }
                    }
                }
                track.getTimeTrials().setTotalTimeSpent(time);
            }
        }, 10*20, 900*20);

    }

    private static void displaySpectatorTimer(Player player) {
        var mightBeDriver = EventDatabase.getClosestDriverForSpectator(player);
        if (mightBeDriver.isPresent()) {
            var driver = mightBeDriver.get();
            if (driver.getHeat().getRound() instanceof FinalRound) {
                if (!driver.isFinished()) {
                    player.sendActionBar(Text.get(player, ActionBar.RACE_SPECTATOR, "%name%", driver.getTPlayer().getName(), "%laps%", String.valueOf(driver.getLaps().size()), "%totalLaps%", String.valueOf(driver.getHeat().getTotalLaps()), "%pos%", String.valueOf(driver.getPosition()), "%pits%", String.valueOf(driver.getPits()), "%totalPits%", String.valueOf(driver.getHeat().getTotalPits())));

                }
            } else if (driver.getHeat().getRound() instanceof QualificationRound) {
                if (!driver.getLaps().isEmpty() && driver.getCurrentLap() != null && driver.getState() == DriverState.RUNNING) {
                    long lapTime = Duration.between(driver.getCurrentLap().getLapStart(), TimingSystem.currentTime).toMillis();
                    long timeLeft = driver.getHeat().getTimeLimit() - Duration.between(driver.getStartTime(), TimingSystem.currentTime).toMillis();
                    String delta = QualifyHeat.getBestLapCheckpointDelta(driver, driver.getCurrentLap().getLatestCheckpoint());
                    player.sendActionBar(Text.getActionBar(player, "&2" + driver.getTPlayer().getName() + " > " + (timeLeft < 0 ? ("&e-" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft * -1)): "&w" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft)) + "&r&1 |&2&l P" + driver.getPosition() + "&r&1 | &2" + ApiUtilities.formatAsTime(lapTime) + delta));
                } else if (driver.getState() == DriverState.LOADED || driver.getState() == DriverState.STARTING) {
                    long timeLeft = driver.getHeat().getTimeLimit();
                    if (driver.getStartTime() != null) {
                        timeLeft = driver.getHeat().getTimeLimit() - Duration.between(driver.getStartTime(), TimingSystem.currentTime).toMillis();
                    }
                    player.sendActionBar(Text.getActionBar(player, "&2" + driver.getTPlayer().getName() + " &1> " + "&w" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft) + "&r&1 |&2&l P" + driver.getPosition() + "&r&1 | &200.000"));
                }
            }
        }
    }

    private static void displayDriverTimer(Player player, Driver driver) {
        if (driver.getHeat().getRound() instanceof FinalRound) {
            if (!driver.isFinished()) {
                String posDisplay = getPositionOrDrsDisplay(driver);
                String pitsDisplay = getPitsOrLapTimeDisplay(driver);

                if (pitsDisplay.contains("/")) {
                    player.sendActionBar(Text.get(player, ActionBar.RACE,
                            "%laps%", String.valueOf(driver.getLaps().size()),
                            "%totalLaps%", String.valueOf(driver.getHeat().getTotalLaps()),
                            "%pos%", posDisplay,
                            "%pits%", pitsDisplay));
                } else {
                    player.sendActionBar(Text.get(player, ActionBar.RACE_PITS_COMPLETED,
                            "%laps%", String.valueOf(driver.getLaps().size()),
                            "%totalLaps%", String.valueOf(driver.getHeat().getTotalLaps()),
                            "%pos%", posDisplay,
                            "%timer%", pitsDisplay));
                }
            }
        } else if (driver.getHeat().getRound() instanceof QualificationRound) {
            sendQualificationDriverActionBar(player, driver);
        }
    }
    
    private static String getPositionOrDrsDisplay(Driver driver) {
        UUID playerId = driver.getTPlayer().getUniqueId();
        
        if (driver.getHeat().getDrs() != null && driver.getHeat().getDrs()) {
            if (DrsManager.hasDrsActive(playerId)) {
                return "&s&lDRS";
            }
            else if (DrsManager.hasDrsEnabled(playerId)) {
                return "&w&lDRS";
            }
        }
        
        return "P" + driver.getPosition();
    }
    
    private static String getPitsOrLapTimeDisplay(Driver driver) {
        Integer totalPits = driver.getHeat().getTotalPits();
        int pits = driver.getPits();
        
        if (totalPits != null && pits >= totalPits) {
            if (driver.getCurrentLap() != null && driver.getCurrentLap().getLapStart() != null) {
                long lapTime = Duration.between(
                    driver.getCurrentLap().getLapStart(), 
                    TimingSystem.currentTime
                ).toMillis();
                
                String delta = QualifyHeat.getBestLapCheckpointDelta(driver, driver.getCurrentLap().getLatestCheckpoint());
                
                return "&2" + ApiUtilities.formatAsTime(lapTime) + delta;
            }
        }
        
        return "&2&l" + pits + "&1/&2&l" + totalPits;
    }

    private static void sendQualificationDriverActionBar(Player player, Driver driver) {
        if (!driver.getLaps().isEmpty() && driver.getCurrentLap() != null && (driver.getState() == DriverState.RUNNING || driver.getState() == DriverState.RESET || driver.getState() == DriverState.LAPRESET)) {
            long lapTime = Duration.between(driver.getCurrentLap().getLapStart(), TimingSystem.currentTime).toMillis();
            long timeLeft = driver.getHeat().getTimeLimit() - Duration.between(driver.getStartTime(), TimingSystem.currentTime).toMillis();
            String delta = QualifyHeat.getBestLapCheckpointDelta(driver, driver.getCurrentLap().getLatestCheckpoint());
            player.sendActionBar(Text.getActionBar(player, (timeLeft < 0 ? ("&e-" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft * -1)) : "&w" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft)) + "&r&1 |&2&l P" + driver.getPosition() + "&r&1 | &2" + ApiUtilities.formatAsTime(lapTime) + delta));
        } else if (driver.getState() == DriverState.LOADED || driver.getState() == DriverState.STARTING) {
            long timeLeft = driver.getHeat().getTimeLimit();
            if (driver.getStartTime() != null) {
                timeLeft = driver.getHeat().getTimeLimit() - Duration.between(driver.getStartTime(), TimingSystem.currentTime).toMillis();
            }
            player.sendActionBar(Text.getActionBar(player, "&w" + ApiUtilities.formatAsHeatTimeCountDown(timeLeft) + "&r&1 |&2&l P" + driver.getPosition() + "&r&1 | &200.000"));
        }
    }

    private static void timeTrialTimer(Player player) {
        TimeTrial timeTrial = TimeTrialController.timeTrials.get(player.getUniqueId());
        long mapTime = timeTrial.getCurrentTime();
        String timerTime = ApiUtilities.formatAsTime(mapTime);
        int decimalIndex = timerTime.indexOf('.');
        String timerTime1;
        if (decimalIndex != -1 && decimalIndex + 3 <= timerTime.length()) {
            timerTime1 = timerTime.substring(0, decimalIndex + 3);
        } else {
            timerTime1 = timerTime;
        }
        Component timer = Component.text(timerTime1);
        Theme theme = TSDatabase.getPlayer(player).getTheme();

        int latestCheckpoint = timeTrial.getLatestCheckpoint();
        Component delta = timeTrial.getBestLapDelta(theme, latestCheckpoint);

        if (timeTrial.getBestTime() == -1) {
            player.sendActionBar(timer.color(theme.getSuccess()));
        } else if (mapTime < timeTrial.getBestTime()) {
            player.sendActionBar(timer.color(theme.getWarning()).append(delta));
        } else {
            player.sendActionBar(timer.color(theme.getError()).append(delta));
        }
    }

    private static void elytraProtectionCountdown(Player player) {
        if (TimeTrialController.elytraProtection.get(player.getUniqueId()) != null && TimeTrialController.elytraProtection.get(player.getUniqueId()) >= TimingSystem.currentTime.getEpochSecond()) {
            String elytraCountdown = String.valueOf(TimeTrialController.elytraProtection.get(player.getUniqueId()) - TimingSystem.currentTime.getEpochSecond());
            player.sendActionBar(Component.text(elytraCountdown).color(TSDatabase.getPlayer(player).getTheme().getWarning()));
        }
    }


    private void setParticles(Player player, Location location, Particle particle) {
        player.spawnParticle(particle, location, 5);
    }

    private void setParticles(Player player, TrackRegion region) {

        if (!region.isDefined()) {
            return;
        }
        Particle particle;

        if (!region.getSpawnLocation().isWorldLoaded()) {
            return;
        }

        if (region.getSpawnLocation().getWorld() != player.getWorld()) {
            return;
        }

        if (region.getSpawnLocation().distance(player.getLocation()) > 200) {
            return;
        }


        if (region.getRegionType().equals(TrackRegion.RegionType.CHECKPOINT)) {
            particle = Particle.GLOW;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.RESET)) {
            particle = Particle.WAX_ON;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.START)) {
            particle = Particle.HAPPY_VILLAGER;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.END)) {
            particle = Particle.HAPPY_VILLAGER;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.PIT)) {
            particle = Particle.HEART;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.INPIT)) {
            particle = Particle.WITCH;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.DRSDETECT)) {
            particle = Particle.CHERRY_LEAVES;
        } else if (region.getRegionType().equals(TrackRegion.RegionType.DRSACTIVATE)) {
            particle = Particle.RAID_OMEN;
        } else {
            particle = Particle.WAX_OFF;
        }


        Location min = region.getMinP();
        Location max = region.getMaxP();

        int maxY = max.getBlockY() + 1;
        int maxX = max.getBlockX() + 1;
        int maxZ = max.getBlockZ() + 1;


        if (region instanceof TrackPolyRegion polyRegion) {
            drawPolyRegion(polyRegion, player, particle);
        } else {

            drawLineX(player, particle, min.getBlockX(), maxX, min.getBlockY(), min.getBlockZ());
            drawLineX(player, particle, min.getBlockX(), maxX, maxY, min.getBlockZ());
            drawLineX(player, particle, min.getBlockX(), maxX, min.getBlockY(), maxZ);
            drawLineX(player, particle, min.getBlockX(), maxX, maxY, maxZ);

            drawLineY(player, particle, min.getBlockX(), min.getBlockY(), maxY, min.getBlockZ());
            drawLineY(player, particle, min.getBlockX(), min.getBlockY(), maxY, maxZ);
            drawLineY(player, particle, maxX, min.getBlockY(), maxY, min.getBlockZ());
            drawLineY(player, particle, maxX, min.getBlockY(), maxY, maxZ);

            drawLineZ(player, particle, min.getBlockX(), min.getBlockY(), min.getBlockZ(), maxZ);
            drawLineZ(player, particle, min.getBlockX(), maxY, min.getBlockZ(), maxZ);
            drawLineZ(player, particle, maxX, min.getBlockY(), min.getBlockZ(), maxZ);
            drawLineZ(player, particle, maxX, maxY, min.getBlockZ(), maxZ);
        }

    }

    private void drawLineX(Player player, Particle particle, int x1, int x2, int y, int z) {
        for (int x = x1; x <= x2; x++) {
            player.spawnParticle(particle, x, y, z, 1);
        }
    }

    private void drawLineY(Player player, Particle particle, int x, int y1, int y2, int z) {
        for (int y = y1; y <= y2; y++) {
            player.spawnParticle(particle, x, y, z, 1);
        }
    }

    private void drawLineZ(Player player, Particle particle, int x, int y, int z1, int z2) {
        for (int z = z1; z <= z2; z++) {
            player.spawnParticle(particle, x, y, z, 1);
        }
    }

    private void drawLine(Player player, Particle particle, Location minP, Location maxP) {
        var newP = maxP.clone();
        newP.subtract(minP);
        var distance = minP.distance(maxP);
        double x = newP.getX() / distance;
        double z = newP.getZ() / distance;
        double y = newP.getY() / distance;

        var p = maxP.clone();
        for (int i = 0; i < distance - 1; i++) {
            p.subtract(x, y, z);
            player.spawnParticle(particle, p, 1);
        }
    }

    private void drawPolyRegion(TrackPolyRegion polyRegion, Player player, Particle particle) {

        int maxY = polyRegion.getMaxP().getBlockY() + 1;
        Location firstLocation = null;
        Location lastLocation = null;
        for (BlockVector2 point : polyRegion.getPolygonal2DRegion().getPoints()) {
            var loc = new Location(polyRegion.getSpawnLocation().getWorld(), point.getX() + 0.5, maxY, point.getZ() + 0.5);
            // Draw top
            if (lastLocation != null) {
                drawLine(player, particle, lastLocation, loc);
            }

            var bottomLocation = loc.clone();
            bottomLocation.setY(polyRegion.getMinP().getY());
            // Draw bottom
            if (lastLocation != null) {
                var lastBottomLocation = lastLocation.clone();
                lastBottomLocation.setY(polyRegion.getMinP().getY());
                drawLine(player, particle, lastBottomLocation, bottomLocation);
            }

            //Draw edge
            drawLine(player, particle, bottomLocation, loc);

            if (lastLocation == null) {
                firstLocation = loc.clone();
            }
            lastLocation = loc.clone();
        }
        drawLine(player, particle, lastLocation, firstLocation);
    }

    public void startDrsCleanup(TimingSystem plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, DrsManager::cleanupOldDetections, 100, 100);
    }
    
    public void startPushToPassUpdater(TimingSystem plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            PushToPass::updateAllCharges, 2, 2);
    }
}


