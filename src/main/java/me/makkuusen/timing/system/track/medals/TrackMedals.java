package me.makkuusen.timing.system.track.medals;

import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.track.TimeTrials;
import me.makkuusen.timing.system.track.Track;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TrackMedals {
    private boolean full;
    private final int playersLimit;
    private final TrackMedalsData netherite;
    private final TrackMedalsData emerald;
    private final TrackMedalsData diamond;
    private final TrackMedalsData gold;
    private final TrackMedalsData silver;
    private final TrackMedalsData copper;

    public TrackMedals(Track track) {
        full = false;
        playersLimit = TimingSystem.configuration.getMedalsPlayersLimit();
        netherite = new TrackMedalsData(TimingSystem.configuration.getNetheritePos(), getPositionText(TimingSystem.configuration.getNetheritePos()));
        emerald = new TrackMedalsData(TimingSystem.configuration.getEmeraldPos(), getPositionText(TimingSystem.configuration.getEmeraldPos()));
        if (TimingSystem.configuration.isDynamicDiamondPosEnabled()) {
            double dynamicDiamondPos = getDynamicPos(track.getTimeTrials().getCachedPositions().size(), TimingSystem.configuration.getDiamondPos());
            diamond = new TrackMedalsData(dynamicDiamondPos, getPositionText(dynamicDiamondPos));
        } else {
            diamond = new TrackMedalsData(TimingSystem.configuration.getDiamondPos(), getPositionText(TimingSystem.configuration.getDiamondPos()));
        }
        gold = new TrackMedalsData(TimingSystem.configuration.getGoldPos(), getPositionText(TimingSystem.configuration.getGoldPos()));
        silver = new TrackMedalsData(TimingSystem.configuration.getSilverPos(), getPositionText(TimingSystem.configuration.getSilverPos()));
        copper = new TrackMedalsData(TimingSystem.configuration.getCopperPos(), getPositionText(TimingSystem.configuration.getCopperPos()));
    }

    public void updateMedalsTimes(TimeTrials timeTrials) {
        if (TimingSystem.configuration.isMedalsAddOnEnabled()) {
            timeTrials.getTopList(1);
            int totalPositions = timeTrials.getCachedPositions().size();
            full = totalPositions >= playersLimit;
            if (full) {
                if (TimingSystem.configuration.isDynamicDiamondPosEnabled()) {
                    double dynamicDiamondPos = getDynamicPos(timeTrials.getCachedPositions().size(), TimingSystem.configuration.getDiamondPos());
                    diamond.setPos(dynamicDiamondPos);
                    diamond.setText(getPositionText(dynamicDiamondPos));
                }
                diamond.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(diamond.getPos(), totalPositions))).getTime());
                gold.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(gold.getPos(), totalPositions))).getTime());
                silver.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(silver.getPos(), totalPositions))).getTime());
                copper.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(copper.getPos(), totalPositions))).getTime());
            }
            netherite.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(netherite.getPos(), totalPositions))).getTime());
            emerald.setTime(timeTrials.getBestFinish(timeTrials.getCachedPositions().get(getPosition(emerald.getPos(), totalPositions))).getTime());
        }
    }

    public ItemStack getMedalItem(TPlayer tPlayer, String trackName, Long time) {
        Medals medal;
        if (time == 0) {
            medal = full ? Medals.NO_MEDAL : Medals.NO_CUP;
        } else {
            medal = getMedal(time);
        }
        ItemStack item = new ItemStack(medal.getMaterial(), 1);
        ItemMeta im = item.getItemMeta();
        im.displayName(Component.text(trackName).color(tPlayer.getTheme().getSecondary()));
        im.lore(getMedalLore(time, tPlayer.getPlayer().hasResourcePack()));
        im.setCustomModelData(medal.getCustomModelData());
        item.setItemMeta(im);
        return item;
    }

    public Component getMedalMessage(TimeTrials timeTrials, boolean hasResourcePack, Medals prevMedal, long time, String trackName) {
        updateMedalsTimes(timeTrials);
        Medals medal = getMedal(time);
        if (medal.getNumber() > prevMedal.getNumber() && medal.getNumber() > 0) {
            String nextTime = "\n";
            if (TimingSystem.configuration.isMedalsShowNextMedal()) {
                Medals nextMedal = Medals.fromNumber(medal.getNumber() + 1);
                if (nextMedal != Medals.NO_MEDAL) { nextTime = "\nImprove by §l" + ApiUtilities.formatAsPersonalGap(time - fromNumber(medal.getNumber() + 1).getTime()) + "§r§f to unlock " + nextMedal.getColor() + "§l" + nextMedal.getName() + "\n"; }
            }
            Component hoverText = Component.join(JoinConfiguration.builder().separator(Component.text("\n")).build(), getMedalLore(time, hasResourcePack));
            return Component.text("\n§f=== §e§lNew Time Trial Trophy§r§f ===\n\nYou unlocked " + medal.getColor() + "§l" + medal.getName() + "§r§f on " + trackName + "!" + nextTime).hoverEvent(HoverEvent.showText(hoverText));
        }
        return null;
    }

    public List<Component> getMedalLore(long time, boolean hasResourcePack) {
        List<Component> lore = new ArrayList<>();
        if (time != 0L) {
            String yourTime = ApiUtilities.formatAsMedalTime(time);
            if (time <= netherite.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            String color = time <= netherite.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.NETHERITE_CUP.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(netherite.getTime()) + " §f" + netherite.getText()));
            if (time > netherite.getTime() && time <= emerald.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            color = time <= emerald.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.EMERALD_CUP.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(emerald.getTime()) + " §f" + emerald.getText()));
            if (!full) {
                if (time > emerald.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
                return lore;
            }
            if (time > emerald.getTime() && time <= diamond.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            color = time <= diamond.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.DIAMOND_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(diamond.getTime()) + " §f" + diamond.getText()));
            if (time > diamond.getTime() && time <= gold.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            color = time <= gold.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.GOLD_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(gold.getTime()) + " §f" + gold.getText()));
            if (time > gold.getTime() && time <= silver.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            color = time <= silver.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.SILVER_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(silver.getTime()) + " §f" + silver.getText()));
            if (time > silver.getTime() && time <= copper.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
            color = time <= copper.getTime() ? "§a" : "§c";
            lore.add(Component.text("§f" + Medals.COPPER_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(copper.getTime()) + " §f" + copper.getText()));
            if (time > copper.getTime()) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
        } else {
            String color = "§c";
            lore.add(Component.text("§f" + Medals.NETHERITE_CUP.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(netherite.getTime()) + " §f" + netherite.getText()));
            lore.add(Component.text("§f" + Medals.EMERALD_CUP.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(emerald.getTime()) + " §f" + emerald.getText()));
            if (!full) return lore;
            lore.add(Component.text("§f" + Medals.DIAMOND_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(diamond.getTime()) + " §f" + diamond.getText()));
            lore.add(Component.text("§f" + Medals.GOLD_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(gold.getTime()) + " §f" + gold.getText()));
            lore.add(Component.text("§f" + Medals.SILVER_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(silver.getTime()) + " §f" + silver.getText()));
            lore.add(Component.text("§f" + Medals.COPPER_MEDAL.getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(copper.getTime()) + " §f" + copper.getText()));
        }
        return lore;
    }

    public @NotNull Medals getMedal(long time) {
        if (time == 0)                        return Medals.NO_MEDAL;
        else if (time <= netherite.getTime()) return Medals.NETHERITE_CUP;
        else if (time <= emerald.getTime())   return Medals.EMERALD_CUP;
        else if (time <= diamond.getTime())   return Medals.DIAMOND_MEDAL;
        else if (time <= gold.getTime())      return Medals.GOLD_MEDAL;
        else if (time <= silver.getTime())    return Medals.SILVER_MEDAL;
        else if (time <= copper.getTime())    return Medals.COPPER_MEDAL;
        else                                  return full ? Medals.NO_MEDAL : Medals.NO_CUP;
    }

    private double getDynamicPos(int totalPositions, double defaultValue) {
        if (!TimingSystem.configuration.getDynamicDiamondPoses().isEmpty()) {
            for (DynamicPos dynamicPos : TimingSystem.configuration.getDynamicDiamondPoses()) {
                if (totalPositions >= dynamicPos.getMin() && totalPositions <= dynamicPos.getMax()) {
                    return dynamicPos.getPos(totalPositions, defaultValue);
                }
            }
            if (totalPositions < TimingSystem.configuration.getDynamicDiamondPoses().getFirst().getMin()) {
                return TimingSystem.configuration.getDynamicDiamondPoses().getFirst().getPos(totalPositions, defaultValue);
            } else {
                return TimingSystem.configuration.getDynamicDiamondPoses().getLast().getPos(totalPositions, defaultValue);
            }
        }
        return TimingSystem.configuration.getDiamondPos();
    }

    private int getPosition(double num, int totalPositions) {
        if (num <= 0) {
            return 0;
        } else if (num < 1) {
            return (int) (totalPositions * num);
        } else {
            return (int) (num - 1);
        }
    }

    private String getPositionText(double num) {
        if (num <= 0) {
            return "(top 1)";
        } else if (num < 0.1) {
            return "(top " + ((int)(num * 1000) / 10.0) + "%)";
        } else if (num < 1) {
            return "(top " + (int) (num * 100) + "%)";
        } else {
            return "(top " + (int) num + ")";
        }
    }

    private TrackMedalsData fromNumber(int number) {
        return switch (number) {
            case 2 -> silver;
            case 3 -> gold;
            case 4 -> diamond;
            case 5 -> emerald;
            case 6 -> netherite;
            default -> copper;
        };
    }
}
