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
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TrackMedals {
    private boolean full;
    private final int playersLimit;
    private final Track track;
    private final TrackMedalsData author;
    private final TrackMedalsData netherite;
    private final TrackMedalsData emerald;
    private final TrackMedalsData diamond;
    private final TrackMedalsData gold;
    private final TrackMedalsData silver;
    private final TrackMedalsData copper;

    public TrackMedals(Track track) {
        this.track = track;
        full = false;
        playersLimit = TimingSystem.configuration.getMedalsPlayersLimit();
        author = new TrackMedalsData(0, "(author)");
        updateAuthorTime();
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

    /**
     * The author time is set per track and is independent of the leaderboard, so it is refreshed
     * separately from the position based medals.
     */
    public void updateAuthorTime() {
        author.setTime(track.hasAuthorTime() ? track.getAuthorTime() : 0);
    }

    public boolean hasAuthor() {
        return author.getTime() > 0;
    }

    /**
     * The medals of this track from hardest to easiest. Medals that have no time yet are left in
     * their nominal spot, and the medals that are only handed out on a full track are left out
     * entirely until it is full.
     */
    private List<MedalEntry> getOrderedMedals() {
        List<MedalEntry> entries = new ArrayList<>();
        entries.add(new MedalEntry(Medals.NETHERITE_CUP, netherite));
        entries.add(new MedalEntry(Medals.EMERALD_CUP, emerald));
        if (full) {
            entries.add(new MedalEntry(Medals.DIAMOND_MEDAL, diamond));
            entries.add(new MedalEntry(Medals.GOLD_MEDAL, gold));
            entries.add(new MedalEntry(Medals.SILVER_MEDAL, silver));
            entries.add(new MedalEntry(Medals.COPPER_MEDAL, copper));
        }
        if (hasAuthor()) {
            entries.add(getAuthorIndex(entries), new MedalEntry(Medals.AUTHOR_MEDAL, author));
        }
        return entries;
    }

    /**
     * The author medal is worth whatever its time makes it worth, so it slots in ahead of the
     * first medal that is no harder to reach than the author time. Medals without a time yet are
     * skipped, which keeps the author medal in front while a track is still filling up.
     */
    private int getAuthorIndex(List<MedalEntry> entries) {
        int index = 0;
        for (int i = 0; i < entries.size(); i++) {
            long medalTime = entries.get(i).data().getTime();
            if (medalTime <= 0) {
                continue;
            }
            if (medalTime >= author.getTime()) {
                return i;
            }
            index = i + 1;
        }
        return index;
    }

    /**
     * How hard a medal is to reach on this track, counting up from the easiest one. Medals that
     * are not handed out on this track rank 0.
     */
    private int getRank(Medals medal) {
        List<MedalEntry> entries = getOrderedMedals();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).medal() == medal) {
                return entries.size() - i;
            }
        }
        return 0;
    }

    /**
     * @return the next harder medal on this track, or null if the medal is already the hardest.
     */
    private MedalEntry getNextEntry(Medals medal) {
        List<MedalEntry> entries = getOrderedMedals();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).medal() == medal) {
                return i > 0 ? entries.get(i - 1) : null;
            }
        }
        return null;
    }

    /**
     * Only the hardest medals of a track are announced to the whole server. The position based
     * ones need a full leaderboard to mean anything, while the author time stands on its own.
     */
    public boolean isBroadcastWorthy(Medals medal) {
        if (medal == Medals.AUTHOR_MEDAL) {
            return getRank(medal) >= getRank(Medals.EMERALD_CUP);
        }
        return full && (medal == Medals.EMERALD_CUP || medal == Medals.NETHERITE_CUP);
    }

    private record MedalEntry(Medals medal, TrackMedalsData data) {
    }

    public void updateMedalsTimes(TimeTrials timeTrials) {
        updateAuthorTime();
        if (TimingSystem.configuration.isMedalsAddOnEnabled()) {
            timeTrials.getTopList(1);
            int totalPositions = timeTrials.getCachedPositions().size();
            if (totalPositions < netherite.getPos() + 1 || totalPositions < emerald.getPos() + 1) return;
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
        if (medal == Medals.AUTHOR_MEDAL && im instanceof SkullMeta skullMeta) {
            // No custom model data, otherwise a resource pack would render over the owner's skin.
            applyOwnerSkull(skullMeta);
        } else {
            im.setCustomModelData(medal.getCustomModelData());
        }
        item.setItemMeta(im);
        return item;
    }

    public Component getMedalMessage(TimeTrials timeTrials, boolean hasResourcePack, Medals prevMedal, long time, String trackName) {
        updateMedalsTimes(timeTrials);
        Medals medal = getMedal(time);
        int rank = getRank(medal);
        if (rank > getRank(prevMedal) && rank > 0) {
            String nextTime = "\n";
            if (TimingSystem.configuration.isMedalsShowNextMedal()) {
                MedalEntry nextEntry = getNextEntry(medal);
                if (nextEntry != null) { nextTime = "\nImprove by §l" + ApiUtilities.formatAsPersonalGap(time - nextEntry.data().getTime()) + "§r§f to unlock " + nextEntry.medal().getColor() + "§l" + nextEntry.medal().getName() + "\n"; }
            }
            Component hoverText = Component.join(JoinConfiguration.builder().separator(Component.text("\n")).build(), getMedalLore(time, hasResourcePack));
            return Component.text("\n§f=== §e§lNew Time Trial Trophy§r§f ===\n\nYou unlocked " + medal.getColor() + "§l" + medal.getName() + "§r§f on " + trackName + "!" + nextTime).hoverEvent(HoverEvent.showText(hoverText));
        }
        return null;
    }

    public List<Component> getMedalLore(long time, boolean hasResourcePack) {
        List<Component> lore = new ArrayList<>();
        List<MedalEntry> entries = getOrderedMedals();
        if (time != 0L) {
            String yourTime = ApiUtilities.formatAsMedalTime(time);
            long previous = 0;
            for (MedalEntry entry : entries) {
                long medalTime = entry.data().getTime();
                if (time > previous && time <= medalTime) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
                String color = time <= medalTime ? "§a" : "§c";
                lore.add(getMedalLoreLine(entry, color, hasResourcePack));
                previous = medalTime;
            }
            if (time > previous) lore.add(Component.text("§f§l   " + yourTime + " (YOU)"));
        } else {
            for (MedalEntry entry : entries) {
                lore.add(getMedalLoreLine(entry, "§c", hasResourcePack));
            }
        }
        return lore;
    }

    private Component getMedalLoreLine(MedalEntry entry, String color, boolean hasResourcePack) {
        return Component.text("§f" + entry.medal().getFont(hasResourcePack) + " : " + color + ApiUtilities.formatAsMedalTime(entry.data().getTime()) + " §f" + entry.data().getText());
    }

    public @NotNull Medals getMedal(long time) {
        if (time == 0) {
            return Medals.NO_MEDAL;
        }
        for (MedalEntry entry : getOrderedMedals()) {
            long medalTime = entry.data().getTime();
            if (medalTime > 0 && time <= medalTime) {
                return entry.medal();
            }
        }
        return full ? Medals.NO_MEDAL : Medals.NO_CUP;
    }

    /**
     * The author medal is represented by the head of the player who owns the track.
     */
    private void applyOwnerSkull(SkullMeta skullMeta) {
        TPlayer owner = track.getOwner();
        if (owner == null) {
            return;
        }
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner.getUniqueId()));
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
}
