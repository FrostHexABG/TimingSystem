package me.makkuusen.timing.system;

import co.aikar.commands.BukkitCommandExecutionContext;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.MessageKeys;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.contexts.ContextResolver;
import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.boatutils.BoatUtilsManager;
import me.makkuusen.timing.system.boatutils.BoatUtilsMode;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.theme.TSColor;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.editor.TrackEditor;
import me.makkuusen.timing.system.track.locations.TrackLocation;
import me.makkuusen.timing.system.track.options.TrackOption;
import me.makkuusen.timing.system.track.regions.TrackRegion;
import me.makkuusen.timing.system.track.tags.TrackTag;
import me.makkuusen.timing.system.tuning.Attribute;
import me.makkuusen.timing.system.tuning.PartManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Boat;

import java.util.*;

public class ContextResolvers {

    static void loadCommandContextsAndCompletions(PaperCommandManager manager) {

        manager.getCommandContexts().registerContext(
                Event.class, ContextResolvers.getEventContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("event", context ->
                EventDatabase.getEventsAsStrings()
        );
        manager.getCommandContexts().registerContext(
                Round.class, ContextResolvers.getRoundContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("round", context ->
                EventDatabase.getRoundsAsStrings(context.getPlayer().getUniqueId())
        );
        manager.getCommandContexts().registerContext(
                Heat.class, ContextResolvers.getHeatContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("heat", context ->
                EventDatabase.getHeatsAsStrings(context.getPlayer().getUniqueId())
        );
        manager.getCommandContexts().registerContext(
                Track.class, ContextResolvers.getTrackContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("track", (context -> TrackDatabase.getTracksAsStrings(context.getPlayer())));

        manager.getCommandContexts().registerContext(
                Team.class, ContextResolvers.getTeamContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("team", context -> {
            try {
                return TimingSystem.getTeamDatabase().selectTeams().stream()
                        .map(row -> row.getString("name"))
                        .sorted()
                        .collect(java.util.stream.Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        });

        manager.getCommandContexts().registerContext(
                TrackRegion.class, ContextResolvers.getRegionContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("region", TrackDatabase::getRegionsAsStrings
        );
        manager.getCommandContexts().registerContext(
                Track.TrackType.class, ContextResolvers.getTrackTypeContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("trackType", context -> {
            List<String> res = new ArrayList<>();
            for (Track.TrackType type : Track.TrackType.values()) {
                res.add(type.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                TrackLocation.Type.class, ContextResolvers.getTrackLocationTypeContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("locationType", context -> {
            List<String> res = new ArrayList<>();
            for (TrackLocation.Type type : TrackLocation.Type.values()) {
                res.add(type.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                TrackRegion.RegionType.class, ContextResolvers.getTrackRegionTypeContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("regionType", context -> {
            List<String> res = new ArrayList<>();
            for (TrackRegion.RegionType type : TrackRegion.RegionType.values()) {
                res.add(type.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                TrackTag.class, ContextResolvers.getTrackTagContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("trackTag", context -> {
            List<String> res = new ArrayList<>();
            for (String tag : TrackTagManager.getTrackTags().keySet()) {
                res.add(tag.toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                TrackOption.class, ContextResolvers.getTrackOptionContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("trackOption", context -> {
            List<String> res = new ArrayList<>();
            for (TrackOption option : TrackOption.values()) {
                res.add(option.toString().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                RoundType.class, ContextResolvers.getRoundTypeContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("roundType", context -> {
            List<String> res = new ArrayList<>();
            for (RoundType type : RoundType.values()) {
                res.add(type.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                Boat.Type.class, ContextResolvers.getBoatContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("boat", context -> {
            List<String> res = new ArrayList<>();
            for (Boat.Type tree : Boat.Type.values()) {
                res.add(tree.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                TSColor.class, ContextResolvers.getTSColorContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("tsColor", context -> {
            List<String> res = new ArrayList<>();
            for (TSColor color : TSColor.values()) {
                res.add(color.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandContexts().registerContext(
                NamedTextColor.class, ContextResolvers.getNamedColorContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("namedColor", context -> {
            List<String> res = new ArrayList<>();
            for (NamedTextColor color : NamedTextColor.NAMES.values()) {
                res.add(color.toString());
            }
            return res;
        });

        manager.getCommandContexts().registerContext(BoatUtilsMode.class, ContextResolvers.getBoatUtilsModeContextResolver());
        manager.getCommandCompletions().registerAsyncCompletion("allBoatUtilsMode", context -> {
            List<String> res = new ArrayList<>();
            Arrays.stream(BoatUtilsMode.values()).forEach(mode -> res.add(mode.name().toLowerCase()));
            return res;
        });
        manager.getCommandCompletions().registerAsyncCompletion("boatUtilsMode", context -> {
            TPlayer tPlayer = TSDatabase.getPlayer(context.getPlayer().getUniqueId());
            List<String> res = new ArrayList<>();

            if(tPlayer.hasBoatUtils()) {
                List<BoatUtilsMode> availableModes = BoatUtilsManager.getAvailableModes(tPlayer.getBoatUtilsVersion());
                availableModes.forEach(mode -> res.add(mode.name().toLowerCase()));
            } else {
                res.add(BoatUtilsMode.BROKEN_SLIME_BA_NOFD.name().toLowerCase());
                res.add(BoatUtilsMode.BROKEN_SLIME_RALLY.name().toLowerCase());
            }
            return res;
        });
        manager.getCommandCompletions().registerAsyncCompletion("bume", context -> {
            List<String> res = new ArrayList<>();
            try {
                var rows = co.aikar.idb.DB.getResults("SELECT name FROM ts_custom_boatutils_modes");
                if (rows != null) {
                    for (var row : rows) {
                        res.add(row.getString("name"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return res;
        });
        manager.getCommandCompletions().registerAsyncCompletion("tracks", context -> TrackDatabase.getTracksAsStrings(context.getPlayer()));

        // Team completions
        manager.getCommandCompletions().registerAsyncCompletion("teams", context -> {
            try {
                return TimingSystem.getTeamDatabase().selectTeams().stream()
                        .map(row -> row.getString("name"))
                        .sorted()
                        .collect(java.util.stream.Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        });

        manager.getCommandCompletions().registerAsyncCompletion("teamplayers", context -> {
            // Get the team from the current command context if available
            String[] args = context.getInput().split(" ");
            if (args.length >= 2) {
                String teamName = args[1];
                try {
                    Optional<me.makkuusen.timing.system.team.Team> maybeTeam = me.makkuusen.timing.system.team.TeamManager.getTeam(teamName);
                    if (maybeTeam.isPresent()) {
                        return maybeTeam.get().getPlayers().stream()
                                .map(TPlayer::getName)
                                .collect(java.util.stream.Collectors.toList());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return new ArrayList<>();
        });

        // parts stuff i guess (am tired)
        manager.getCommandCompletions().registerCompletion("parts", context -> {
            return PartManager.getPartNames();
        });

        manager.getCommandCompletions().registerCompletion(
                "materials",
                c -> Arrays.stream(Material.values())
                        .map(Material::name)
                        .toList()
        );

        manager.getCommandCompletions().registerCompletion("tuningAttributes", context -> {
            return Arrays.stream(Attribute.values())
                    .map(Enum::name)
                    .toList();
        });
    }
    public static ContextResolver<Track.TrackType, BukkitCommandExecutionContext> getTrackTypeContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return Track.TrackType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<TrackLocation.Type, BukkitCommandExecutionContext> getTrackLocationTypeContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return TrackLocation.Type.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<TrackRegion.RegionType, BukkitCommandExecutionContext> getTrackRegionTypeContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return TrackRegion.RegionType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<Track, BukkitCommandExecutionContext> getTrackContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            Optional<Track> maybeTrack;
            if (name.equalsIgnoreCase("random") || name.equalsIgnoreCase("r")) {
                maybeTrack = TimingSystemAPI.getRandomTrack();
            } else {
                maybeTrack = TrackDatabase.getTrack(name);
            }
            if (maybeTrack.isPresent()) {
                return maybeTrack.get();
            } else {
                // User didn't type an Event, show error!
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<TrackRegion, BukkitCommandExecutionContext> getRegionContextResolver() {
        return (c) -> {
            String region = c.popFirstArg();
            var maybeTrack = TrackEditor.getPlayerTrackSelection(c.getPlayer().getUniqueId());
            if (maybeTrack != null) {
                try {
                    String[] regionName = region.split("-");
                    int index = Integer.parseInt(regionName[1]);
                    String regionType = regionName[0];
                    var maybeRegion = maybeTrack.getTrackRegions().getRegion(TrackRegion.RegionType.valueOf(regionType.toUpperCase()), index);
                    if (maybeRegion.isPresent()) {
                        return maybeRegion.get();
                    }
                } catch (Exception ignored) {

                }
            }
            throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
        };
    }

    public static ContextResolver<Event, BukkitCommandExecutionContext> getEventContextResolver() {
        return (c) -> {
            String first = c.popFirstArg();
            var maybeEvent = EventDatabase.getEvent(first);
            if (maybeEvent.isPresent()) {
                return maybeEvent.get();
            } else {
                // User didn't type an Event, show error!
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<Round, BukkitCommandExecutionContext> getRoundContextResolver() {
        return (c) -> {
            String roundName = c.popFirstArg();
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(c.getPlayer().getUniqueId());
            if (maybeEvent.isPresent()) {
                Event event = maybeEvent.get();
                var maybeRound = event.getEventSchedule().getRound(roundName);
                if (maybeRound.isPresent()) {
                    return maybeRound.get();
                }
            }
            // User didn't type a heat, show error!
            throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
        };
    }

    public static ContextResolver<Heat, BukkitCommandExecutionContext> getHeatContextResolver() {
        return (c) -> {
            String heatName = c.popFirstArg();
            var maybeEvent = EventDatabase.getPlayerSelectedEvent(c.getPlayer().getUniqueId());
            if (maybeEvent.isPresent()) {
                Event event = maybeEvent.get();
                var maybeHeat = event.getEventSchedule().getHeat(heatName);
                if (maybeHeat.isPresent()) {
                    return maybeHeat.get();
                }
            }
            // User didn't type a heat, show error!
            throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
        };
    }

    public static ContextResolver<TrackTag, BukkitCommandExecutionContext> getTrackTagContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return TrackTagManager.getTrackTag(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<TrackOption, BukkitCommandExecutionContext> getTrackOptionContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return TrackOption.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<RoundType, BukkitCommandExecutionContext> getRoundTypeContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return RoundType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<Boat.Type, BukkitCommandExecutionContext> getBoatContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return Boat.Type.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                //no matching boat types
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<TSColor, BukkitCommandExecutionContext> getTSColorContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return TSColor.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                //no matching boat types
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<NamedTextColor, BukkitCommandExecutionContext> getNamedColorContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return NamedTextColor.NAMES.value(name);
            } catch (IllegalArgumentException e) {
                //no matching boat types
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<BoatUtilsMode, BukkitCommandExecutionContext> getBoatUtilsModeContextResolver() {
        return (c) -> {
            String name = c.popFirstArg();
            try {
                return BoatUtilsMode.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                //no matching boat types
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }

    public static ContextResolver<Team, BukkitCommandExecutionContext> getTeamContextResolver() {
        return (c) -> {
            String teamName = c.popFirstArg();
            try {
                Optional<Team> maybeTeam = me.makkuusen.timing.system.team.TeamManager.getTeam(teamName);
                if (maybeTeam.isPresent()) {
                    return maybeTeam.get();
                } else {
                    // Team not found, show error
                    throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
                }
            } catch (Exception e) {
                // Database error or team not found
                throw new InvalidCommandArgument(MessageKeys.INVALID_SYNTAX);
            }
        };
    }
}
