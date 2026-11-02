package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.gui.BoatSetupGui;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.team.TeamManager;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.tuning.Attribute;
import me.makkuusen.timing.system.tuning.Part;
import me.makkuusen.timing.system.tuning.PartCategory;
import me.makkuusen.timing.system.tuning.PartManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;

@CommandAlias("parts")
public class CommandParts extends BaseCommand {
    @Subcommand("create")
    @CommandCompletion("OARS|HULL|RUDDER <name>")
    @CommandPermission("%permissiontimingsystem_part_create")
    public void create(CommandSender sender,PartCategory category, String name){
        Theme theme = Theme.getTheme(sender);

        Part lePart = new Part();
        lePart.setName(name);
        lePart.setCategoryName(category);

        PartManager.addPart(lePart);
        PartManager.saveParts();
        sender.sendMessage(Component.text("part was added").color(theme.getSuccess()));
    }

    @Subcommand("list")
    @CommandPermission("%permissiontimingsystem_part_list")
    public void list(CommandSender sender){
        Theme theme = Theme.getTheme(sender);

        sender.sendMessage(theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/parts list"))
                .append(theme.getTitleLine(Component.text("parts").color(theme.getSecondary())))
        );

        for (String name : PartManager.getPartNames()){
            sender.sendMessage(Component.text(name).color(theme.getSecondary()));
        }
    }

    @Subcommand("delete")
    @CommandCompletion("@parts")
    public void delete(CommandSender sender, String name){
        Theme theme = Theme.getTheme(sender);

        if (!PartManager.removePart(name)) {
            sender.sendMessage(Component.text("Part not found").color(theme.getError()));
            return;
        }
        sender.sendMessage(Component.text("Part deleted").color(theme.getSuccess()));
        PartManager.saveParts();
        applyLiveTuningIfActive(sender);
    }

    @Subcommand("manage")
    @CommandCompletion("@parts")
    @CommandPermission("%permissiontimingsystem_part_manage")
    public void manage(CommandSender sender, String name){
        Theme theme = Theme.getTheme(sender);
        Part workingPart = PartManager.getPartByName(name);

        if (workingPart == null){
            sender.sendMessage(Component.text("Part not found").color(theme.getError()));
            return;
        }

        Map<Attribute, Integer> attributes = workingPart.getAttributes();

        // Title
        sender.sendMessage(theme.getRefreshButton().clickEvent(ClickEvent.runCommand("/parts manage " + name))
                .append(theme.getTitleLine(Component.text(workingPart.getName()).color(theme.getSecondary())))
        );

        // Description
        sender.sendMessage(Component.text(workingPart.getDescription() == null ? "No Description" : workingPart.getDescription()).clickEvent(ClickEvent.suggestCommand("/parts set description " + name + " ")));

        //rating
        sender.sendMessage(Component.text(workingPart.getRating()).clickEvent(ClickEvent.suggestCommand("/parts set rating " + name + " ")));

        // attributes
        for (Attribute attribute : attributes.keySet()){
            sendTuningAttribute(sender, workingPart, attribute);
        }
        PartManager.saveParts();
        applyLiveTuningIfActive(sender);
    }


    @Subcommand("set description")
    @CommandCompletion("@parts")
    @CommandPermission("%permissiontimingsystem_part_manage")
    public void setDescription(CommandSender sender, String part,String... description){
        Theme theme = Theme.getTheme(sender);
        Part thePart = PartManager.getPartByName(part);

        thePart.setDescription(String.join(" ", description));
        sender.sendMessage(Component.text(thePart.getName() + "'s description was changed").color(theme.getSuccess()));
        PartManager.saveParts();

        applyLiveTuningIfActive(sender);
    }

    @Subcommand("set attribute")
    @CommandCompletion("@parts @tuningAttributes")
    @CommandPermission("%permissiontimingsystem_part_manage")
    public void setAttribute(CommandSender sender, String part, Attribute attribute, Integer value) {
        Theme theme = Theme.getTheme(sender);
        Part thePart = PartManager.getPartByName(part);

        thePart.removeAttribute(attribute); // ik its lazy but icba right now
        thePart.addAttribute(attribute, value);
        PartManager.saveParts();
        sender.sendMessage(Component.text(thePart.getName() + "'s " + attribute.toString() + " has been set to " + value.toString()).color(theme.getSuccess()));

        for (Team team : TeamManager.getAllTeams()){
            BoatSetupGui.applyLiveTuningIfActive(team);
        }
        applyLiveTuningIfActive(sender);
    }

    @Subcommand("set rating")
    @CommandCompletion("@parts")
    @CommandPermission("%permissiontimingsystem_part_manage")
    public void setRating(CommandSender sender, String part, Integer value){
        Theme theme = Theme.getTheme(sender);
        Part thePart = PartManager.getPartByName(part);

        thePart.setRating(value);
        PartManager.saveParts();
        sender.sendMessage(Component.text(thePart.getName() + "'s rating was set to " + value.toString()).color(theme.getSuccess()));
        applyLiveTuningIfActive(sender);
    }

    @Subcommand("set item")
    @CommandCompletion("@parts @materials")
    @CommandPermission("%permissiontimingsystem_part_manage")
    public void setItem(CommandSender sender, String part, String item){
        Theme theme = Theme.getTheme(sender);
        Part thePart = PartManager.getPartByName(part);

        Material material = Material.matchMaterial(item);

        thePart.setItem(material);
        PartManager.saveParts();
        sender.sendMessage(Component.text(thePart.getName() + "'s item was set to " + item).color(theme.getSuccess()));
    }


    private void sendTuningAttribute(CommandSender sender, Part part, Attribute attribute){
        Theme theme = Theme.getTheme(sender);
        Component toSend;

        toSend = Component.text(attribute + ": ")
                        .color(theme.getPrimary())
                                .append(
                                        theme.getBrackets(Component.text(part.getValue(attribute).toString())
                                                .clickEvent(ClickEvent.suggestCommand("/parts set attribute " + part.getName() + " " + attribute.name() + " ") ))
                                        );

        sender.sendMessage(toSend);
    }

    public void applyLiveTuningIfActive(CommandSender sender) {
        if (!(sender instanceof Player player)) return;

        Driver driver = EventDatabase.playerInRunningHeat.get(player.getUniqueId());
        if (driver == null) return;

        Heat heat = driver.getHeat();
        if (!heat.getLiveTuningEnabled()) return; // Live tuning disabled

        // Apply the updated tuning immediately
        heat.applyTeamTuning();
    }

}
