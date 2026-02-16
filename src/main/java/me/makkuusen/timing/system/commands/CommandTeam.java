package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.permissions.PermissionTeam;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.team.TeamManager;
import me.makkuusen.timing.system.team.TeamTuning;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Info;
import me.makkuusen.timing.system.theme.messages.Success;
import me.makkuusen.timing.system.tplayer.TPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Command handler for team management operations
 * Follows ACF patterns established in CommandTrack
 */
@CommandAlias("team")
public class CommandTeam extends BaseCommand {

    @Subcommand("create")
    @CommandPermission("%permissionteam_create")
    @Syntax("<teamName>")
    @Description("Create a new team")
    public void onTeamCreate(Player player, String teamName) {
        try {
            player.sendMessage("§7[DEBUG] Creating team: " + teamName);
            
            // Validate team name
            if (!Team.isValidTeamName(teamName)) {
                player.sendMessage("§cInvalid team name. Use only letters, numbers, spaces, hyphens, and underscores (max 32 chars).");
                return;
            }
            player.sendMessage("§7[DEBUG] Team name is valid");

            // Check if team name is available
            if (!TeamManager.isTeamNameAvailable(teamName)) {
                player.sendMessage("§cTeam name '" + teamName + "' is already taken.");
                return;
            }
            player.sendMessage("§7[DEBUG] Team name is available");

            // Create the team
            Team team = TeamManager.createTeam(teamName, player.getUniqueId());
            if (team == null) {
                player.sendMessage("§cFailed to create team.");
                return;
            }
            player.sendMessage("§7[DEBUG] Team created with ID: " + team.getId());

            player.sendMessage("§aTeam '" + team.getDisplayName() + "' has been created successfully!");
        } catch (Exception e) {
            player.sendMessage("§cError creating team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("delete")
    @CommandCompletion("@teams")
    @CommandPermission("%permissionteam_delete")
    @Syntax("<team>")
    @Description("Delete a team")
    public void onTeamDelete(CommandSender sender, Team team) {
        try {
            // Confirm deletion
            String teamName = team.getDisplayName();
            
            if (TeamManager.deleteTeam(team)) {
                sender.sendMessage("§aTeam '" + teamName + "' has been deleted.");
            } else {
                sender.sendMessage("§cFailed to delete team '" + teamName + "'.");
            }
        } catch (Exception e) {
            sender.sendMessage("§cError deleting team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("add")
    @CommandCompletion("@teams @players")
    @CommandPermission("%permissionteam_manage")
    @Syntax("<team> <playerName>")
    @Description("Add a player to a team")
    public void onTeamAddPlayer(CommandSender sender, Team team, String playerName) {
        try {
            sender.sendMessage("§7[DEBUG] Adding player " + playerName + " to team " + team.getDisplayName());
            
            // Get the player
            TPlayer player = TSDatabase.getPlayer(playerName);
            if (player == null) {
                sender.sendMessage("§cPlayer not found.");
                return;
            }
            sender.sendMessage("§7[DEBUG] Player found: " + player.getName());

            // Check if player is already in the team
            boolean hasPlayer = team.hasPlayer(player);
            sender.sendMessage("§7[DEBUG] Player already in team: " + hasPlayer);
            sender.sendMessage("§7[DEBUG] Current team size: " + team.getPlayerCount());
            
            if (hasPlayer) {
                sender.sendMessage("§cPlayer " + player.getName() + " is already in team " + team.getDisplayName() + ".");
                return;
            }

            // Add player to team
            if (TeamManager.addPlayerToTeam(team, player)) {
                sender.sendMessage("§7[DEBUG] Player added successfully. New team size: " + team.getPlayerCount());
                sender.sendMessage("§aPlayer " + player.getName() + " has been added to team " + team.getDisplayName() + ".");
            } else {
                sender.sendMessage("§cFailed to add player " + player.getName() + " to team.");
            }
        } catch (Exception e) {
            sender.sendMessage("§cError adding player to team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("remove")
    @CommandCompletion("@teams @teamplayers")
    @CommandPermission("%permissionteam_manage")
    @Syntax("<team> <playerName>")
    @Description("Remove a player from a team")
    public void onTeamRemovePlayer(CommandSender sender, Team team, String playerName) {
        try {
            // Get the player
            TPlayer player = TSDatabase.getPlayer(playerName);
            if (player == null) {
                sender.sendMessage("§cPlayer not found.");
                return;
            }

            // Check if player is in the team
            if (!team.hasPlayer(player)) {
                sender.sendMessage("§cPlayer " + player.getName() + " is not in team " + team.getDisplayName() + ".");
                return;
            }

            // Remove player from team
            if (TeamManager.removePlayerFromTeam(team, player)) {
                sender.sendMessage("§aPlayer " + player.getName() + " has been removed from team " + team.getDisplayName() + ".");
            } else {
                sender.sendMessage("§cFailed to remove player " + player.getName() + " from team.");
            }
        } catch (Exception e) {
            sender.sendMessage("§cError removing player from team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("info")
    @CommandCompletion("@teams")
    @CommandPermission("%permissionteam_info")
    @Syntax("<team>")
    @Description("Show team information")
    public void onTeamInfo(CommandSender sender, Team team) {
        try {
            sender.sendMessage("§7[DEBUG] Getting info for team: " + team.getDisplayName());
            sender.sendMessage("§7[DEBUG] Players loaded: " + team.arePlayersLoaded());
            sender.sendMessage("§7[DEBUG] Player count: " + team.getPlayerCount());
            sender.sendMessage("§7[DEBUG] Players list size: " + team.getPlayers().size());
            
            // Send team information
            sender.sendMessage("§b--- Team: " + team.getDisplayName() + " (" + team.getId() + ") ---");
            sender.sendMessage("§7Creator: §f" + (team.getCreator() != null ? team.getCreator().getName() : "Unknown"));
            sender.sendMessage("§7Created: §f" + ApiUtilities.niceDate(team.getDateCreated()));
            sender.sendMessage("§7Players: §f" + team.getPlayerCount());
            
            if (team.isEmpty()) {
                sender.sendMessage("§7No players in this team.");
            } else {
                StringBuilder playerList = new StringBuilder();
                for (int i = 0; i < team.getPlayers().size(); i++) {
                    if (i > 0) {
                        playerList.append(", ");
                    }
                    playerList.append(team.getPlayers().get(i).getName());
                }
                sender.sendMessage("§7Members: §f" + playerList.toString());
            }
        } catch (Exception e) {
            sender.sendMessage("§cError getting team info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("list")
    @CommandPermission("%permissionteam_list")
    @Description("List all teams")
    public void onTeamList(CommandSender sender) {
        try {
            List<Team> teams = TeamManager.getAllTeams();
            
            sender.sendMessage("§b--- Teams ---");
            
            if (teams.isEmpty()) {
                sender.sendMessage("§7No teams found.");
                return;
            }
            
            for (Team team : teams) {
                String playerCount = String.valueOf(team.getPlayerCount());
                sender.sendMessage("§f• " + team.getDisplayName() + " (" + playerCount + " players)");
            }
        } catch (Exception e) {
            sender.sendMessage("§cError listing teams: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("debug")
    @CommandPermission("%permissionteam_delete")
    @Description("Debug team system")
    public void onTeamDebug(CommandSender sender) {
        try {
            sender.sendMessage("§e--- Team Debug ---");
            
            // Clear cache
            TeamManager.initializeTeams();
            sender.sendMessage("§aCache cleared");
            
            // Show database teams
            List<co.aikar.idb.DbRow> dbTeams = TimingSystem.getTeamDatabase().selectTeams();
            sender.sendMessage("§7Database teams: " + dbTeams.size());
            
            for (co.aikar.idb.DbRow row : dbTeams) {
                int teamId = row.getInt("id");
                String name = row.getString("name");
                boolean isRemoved = row.getInt("isRemoved") == 1;
                sender.sendMessage("§7- ID: " + teamId + ", Name: " + name + ", Removed: " + isRemoved);
            }
            
        } catch (Exception e) {
            sender.sendMessage("§cError in debug: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 16/02/26 so I'm just now realising I didn't comment any of this so good luck to whoever works here next
    @Subcommand("tuning")
    @CommandCompletion("@teams")
    @CommandPermission("%permissionteam_tuning")
    @Description("Configure team tuning")
    public void onTuning(CommandSender sender, Team team){
        Theme theme = Theme.getTheme(sender);

        sender.sendMessage(
                theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/team tuning " + team.getName()))
                        .append(Component.space())
                        .append(theme.getTitleLine(Component.text(team.getName())
                                .append(Component.text(" tuning"))
                        ))
        );

        TeamTuning tuning = team.getTuning();
        Map<String, Integer> attributes = tuning.getAttributes();


        for(String attribute : attributes.keySet()){
            Component toSend;
            int currentValue = attributes.get(attribute);

            toSend = Component.text(attribute + ": ")
                    .color(theme.getPrimary())
                    .append(theme.getBrackets(Component.text("-"), NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/team tuning decrease " + team.getName() + " " + attribute))
                            .hoverEvent(Component.text("Decrease " + attribute)))
                    .append(Component.space())
                    .append(Component.text(currentValue).color(theme.getSecondary()))  // Fixed: wrap in Component.text()
                    .append(Component.space())
                    .append(theme.getBrackets(Component.text("+"), NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/team tuning increase " + team.getName() + " " + attribute))
                            .hoverEvent(Component.text("Increase " + attribute)));

            sender.sendMessage(toSend);
        }

        int totalPoints = tuning.getTotalPoints();
        int remaining = tuning.MAX_TOTAL_POINTS - totalPoints;

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Total Points: " + totalPoints + " / 15")
                .color(remaining < 0 ? NamedTextColor.RED : theme.getPrimary()));
        sender.sendMessage(Component.text("Remaining: " + remaining)
                .color(remaining < 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
    }

    @Subcommand("tuning increase")
    @CommandCompletion("@teams topSpeed|acceleration|handling")
    @CommandPermission("%permissionteam_tuning")
    @Description("Increase a tuning attribute")
    public void onTuningIncrease(Player player, Team team, String attribute){
        TeamTuning tuning = team.getTuning();

        if (!tuning.getAttributes().containsKey(attribute)) {
            player.sendMessage("§cInvalid attribute: " + attribute);
            return;
        }

        int current = tuning.getAttributes().get(attribute);
        int total = tuning.getTotalPoints();

        if (current >= tuning.MAX_STAT_VALUE){
            player.sendMessage("§c" + attribute + " is already at maximum (15)");
            return;
        }

        if (total >= tuning.MAX_TOTAL_POINTS){
            player.sendMessage("§cNo points remaining! Total is already 15");
            return;
        }

        tuning.increaseAttribute(attribute);

        onTuning(player, team);
    }

    @Subcommand("tuning decrease")
    @CommandCompletion("@teams topSpeed|acceleration|handling")
    @CommandPermission("%permissionteam_tuning")
    @Description("decrease a tuning attribute")
    public void onTuningDecrease(Player player, Team team, String attribute){
        TeamTuning tuning = team.getTuning();

        if (!tuning.getAttributes().containsKey(attribute)) {
            player.sendMessage("§cInvalid attribute: " + attribute);
            return;
        }

        int current = tuning.getAttributes().get(attribute);
        int total = tuning.getTotalPoints();

        if (current <= tuning.MIN_STAT_VALUE){
            player.sendMessage("§c" + attribute + " is already at maximum (0)");
            return;
        }

        tuning.decreaseAttribute(attribute);

        onTuning(player, team);
    }
}