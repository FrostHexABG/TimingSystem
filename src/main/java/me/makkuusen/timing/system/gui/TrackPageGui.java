package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.sounds.PlaySound;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.medals.Medals;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class TrackPageGui extends BaseGui {

    public static final Integer SORT_SLOT = 45;
    public static final Integer FILTER_SLOT = 46;
    public static final Integer MEDALS_SLOT = 47;
    public static final Integer RESET_SLOT = 53;

    public Integer page;
    public Integer maxPage = 1;
    public Integer TRACKS_PER_PAGE = 45;
    public TPlayer tPlayer;
    public Track.TrackType trackType;
    public TrackSort trackSort;
    public TrackFilter filter;
    public boolean isMedal;

    public Comparator<Track> compareTrackPosition = (k1, k2) -> {
        if (k1.getTimeTrials().getCachedPlayerPosition(tPlayer) == -1 && k2.getTimeTrials().getCachedPlayerPosition(tPlayer) > 0) {
            return 1;
        } else if (k2.getTimeTrials().getCachedPlayerPosition(tPlayer) == -1 && k1.getTimeTrials().getCachedPlayerPosition(tPlayer) > 0) {
            return -1;
        }
        return k1.getTimeTrials().getCachedPlayerPosition(tPlayer).compareTo(k2.getTimeTrials().getCachedPlayerPosition(tPlayer));
    };

    public TrackPageGui(TPlayer tPlayer, Component title) {
        super(title, 6);
        this.tPlayer = tPlayer;
        this.page = tPlayer.getTrackPage() == null ? 0 : tPlayer.getTrackPage();

        if (tPlayer.getFilter() != null && tPlayer.getFilter().hasValidTags()) {
            this.filter = tPlayer.getFilter();
        } else {
            this.filter = new TrackFilter();
        }
        this.trackSort = tPlayer.getTrackSort() == null ? TrackSort.WEIGHT : tPlayer.getTrackSort();
        this.trackType = tPlayer.getTrackType() == null ? Track.TrackType.BOAT : tPlayer.getTrackType();
        this.isMedal = tPlayer.isMedal();
        update();
    }

    public Component getTitle() {
        return Text.get(tPlayer.getPlayer(), Gui.TRACKS_TITLE);
    }

    public void update() {
        setTrackButtons();
        setBorder();
        setNavigationItems();
        setSortingItem();
        setFilterItems();
        if (TimingSystem.configuration.isMedalsAddOnEnabled()) {
            setMedalItem();
        }
        setResetItem();
    }

    public void clearNavRow() {
        List.of(45, 46, 47, 48, 49, 50, 51, 52, 53).forEach(this::removeItem);
        setBorder();
    }

    public void clearTracks() {
        Arrays.stream(getTrackSlots()).forEach(this::removeItem);
    }

    private void setBorder() {
        Integer[] borderSlots = {45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (Integer slot : borderSlots) {
            setItem(GuiCommon.getBorderGlassButton(), slot);
        }
    }

    private void setSortingItem() {
        ItemStack item;
        if (trackSort == TrackSort.RECENTLY_ADDED) {
            item = new ItemBuilder(Material.CLOCK).setName(Text.get(tPlayer, Gui.SORTED_BY_RECENTLY_ADDED)).build();
        } else if (trackSort == TrackSort.POPULARITY) {
            item = new ItemBuilder(Material.SUNFLOWER).setName(Text.get(tPlayer, Gui.SORTED_BY_POPULARITY)).build();
        } else if (trackSort == TrackSort.POSITION) {
            item = new ItemBuilder(Material.DRAGON_BREATH).setName(Text.get(tPlayer, Gui.SORTED_BY_POSITION)).build();
        } else if (trackSort == TrackSort.TIME_SPENT) {
            item = new ItemBuilder(Material.NETHER_STAR).setName(Text.get(tPlayer, Gui.SORTED_BY_TIME_SPENT)).build();
        } else {
            item = new ItemBuilder(Material.ANVIL).setName(Text.get(tPlayer, Gui.SORTED_BY_DEFAULT)).build();
        }

        var button = new GuiButton(item);
        button.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            clearNavRow();
            setSortingItems();
            tPlayer.setOpenGui(this);
            tPlayer.getPlayer().openInventory(getInventory());
        });
        setItem(button, SORT_SLOT);
    }

    private void setSortingItems() {
        setItem(getSortingButton(new ItemBuilder(Material.ANVIL).setName(Text.get(tPlayer, Gui.SORTED_BY_DEFAULT)).build(), TrackSort.WEIGHT), 45);
        setItem(getSortingButton(new ItemBuilder(Material.DRAGON_BREATH).setName(Text.get(tPlayer, Gui.SORTED_BY_POSITION)).build(), TrackSort.POSITION), 46);
        setItem(getSortingButton(new ItemBuilder(Material.SUNFLOWER).setName(Text.get(tPlayer, Gui.SORTED_BY_POPULARITY)).build(), TrackSort.POPULARITY), 47);
        setItem(getSortingButton(new ItemBuilder(Material.NETHER_STAR).setName(Text.get(tPlayer, Gui.SORTED_BY_TIME_SPENT)).build(), TrackSort.TIME_SPENT), 48);
        setItem(getSortingButton(new ItemBuilder(Material.CLOCK).setName(Text.get(tPlayer, Gui.SORTED_BY_RECENTLY_ADDED)).build(), TrackSort.RECENTLY_ADDED), 49);
    }

    private void setFilterItems() {
        setItem(getFilterButtons(filter), FILTER_SLOT);
    }

    private GuiButton getFilterButtons(TrackFilter filter) {
        if (filter.isEmpty()) {
            return getFilterButton(new ItemBuilder(Material.HOPPER).setName(Text.get(tPlayer, Gui.FILTER_BY_NONE)).build());
        }
        return getFilterButton(filter.getItem(tPlayer));
    }

    public GuiButton getFilterButton(ItemStack item) {
        var button = new GuiButton(item);
        button.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            new FilterGui(this).show(tPlayer.getPlayer());
        });
        return button;
    }

    private void setMedalItem() {
        setItem(getMedalButton(), MEDALS_SLOT);
    }

    public GuiButton getMedalButton() {
        ItemStack i = new ItemStack(Medals.GOLD_MEDAL.getMaterial(), 1);
        ItemMeta im = i.getItemMeta();
        im.displayName(Component.text("§fToggle Medals"));
        im.setCustomModelData(Medals.GOLD_MEDAL.getCustomModelData());
        i.setItemMeta(im);
        var button = new GuiButton(i);
        button.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setMedal(!tPlayer.isMedal());
            openNewTrackPage(this, tPlayer, title);
        });
        return button;
    }

    public GuiButton getSortingButton(ItemStack item, TrackSort trackSort) {
        var button = new GuiButton(item);
        button.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackSort(trackSort);
            openNewTrackPage(this, tPlayer, title);
        });
        return button;
    }

    private void setNavigationItems() {
        int slot = 49;

        ItemStack previous = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).setName(Text.get(tPlayer, Gui.PREVIOUS_PAGE)).build();
        ItemStack next = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).setName(Text.get(tPlayer, Gui.NEXT_PAGE)).build();

        Material trackMaterial = Material.OAK_BOAT;
        if (trackType == Track.TrackType.ELYTRA){
            trackMaterial = Material.ELYTRA;
        } else if (trackType == Track.TrackType.PARKOUR){
            trackMaterial = Material.BIG_DRIPLEAF;
        }
        ItemStack current = new ItemBuilder(trackMaterial).setName(Text.get(tPlayer, Gui.CHANGE_TRACK_TYPE)).build();

        if (page > 0) {
            setItem(getPageButton(previous, page - 1), slot - 1);
        }

        current.setAmount(page + 1);
        setItem(getTrackTypeButton(current), slot);
        setItem(getPageButton(current, page), slot);

        if (page < maxPage) {
            setItem(getPageButton(next, page + 1), slot + 1);
        }
    }

    private void setResetItem() {
        var resetButton = new GuiButton(new ItemBuilder(Material.BARRIER).setName(Text.get(tPlayer, Gui.RESET)).build());
        resetButton.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackPage(null);
            tPlayer.setFilter(null);
            tPlayer.setTrackSort(null);
            tPlayer.setTrackType(null);
            openNewTrackPage(this, tPlayer, title);
        });
        setItem(resetButton, RESET_SLOT);
    }

    public void setResetItem(Integer slot) {
        var resetButton = new GuiButton(new ItemBuilder(Material.BARRIER).setName(Text.get(tPlayer, Gui.RESET)).build());
        resetButton.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackPage(null);
            tPlayer.setFilter(null);
            tPlayer.setTrackSort(null);
            tPlayer.setTrackType(null);
            openNewTrackPage(this, tPlayer, title);
        });
        setItem(resetButton, slot);
    }

    private void setTrackButtons() {
        List<Track> tempTracks = getTracks();

        //Filter TrackType
        var trackStream = tempTracks.stream().filter(Track::isWeightAboveZero).filter(track -> track.isTrackType(trackType)).filter(track -> track.getTrackTags().hasNoExcludedTags(filter));

        //Filter Tags
        if (filter.isAnyMatch()) {
            tempTracks = trackStream.filter(track -> track.getTrackTags().hasAnyTag(filter)).collect(Collectors.toList());
        } else {
            tempTracks = trackStream.filter(track -> track.getTrackTags().hasAllTags(filter)).collect(Collectors.toList());
        }

        maxPage = tempTracks.size() / TRACKS_PER_PAGE;
        if (tempTracks.size() % TRACKS_PER_PAGE == 0 && !tempTracks.isEmpty()) {
            maxPage = maxPage - 1;
        }
        if (maxPage < page) {
            page = maxPage;
            tPlayer.setTrackPage(maxPage);
        }
        sortTracks(tempTracks);
        int start = TRACKS_PER_PAGE * page;

        List<Track> tracks = new ArrayList<>();
        for (int i = start; i < Math.min(start + TRACKS_PER_PAGE, tempTracks.size()); i++) {
            tracks.add(tempTracks.get(i));
        }
        setTracks(tracks, getTrackSlots());
    }

    public GuiButton getPageButton(ItemStack item, int newPage) {
        var button = new GuiButton(item);
        button.setAction(() -> {
            PlaySound.pageTurn(tPlayer);
            tPlayer.setTrackPage(newPage);
            openNewTrackPage(this, tPlayer, title);
        });
        return button;
    }

    protected Integer[] getTrackSlots() {
        Integer[] slots = new Integer[45];
        int count = 0;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = count++;
        }
        return slots;
    }

    public void setTracks(List<Track> tracks, Integer[] slots) {
        int count = 0;
        for (Track track : tracks) {
            if (count < slots.length) {
                setItem(getTrackButton(tPlayer.getPlayer(), track, isMedal), slots[count]);
                count++;
            }
        }
    }

    public abstract GuiButton getTrackButton(Player player, Track track, boolean isMedal);

    public abstract List<Track> getTracks();

    public void sortTracks(List<Track> tracks) {
        if (trackSort == TrackSort.POPULARITY) {
            tracks.sort(Comparator.comparingLong(Track::getTotalTimeSpent).reversed());
        } else if (trackSort == TrackSort.WEIGHT) {
            tracks.sort(Comparator.comparingInt(Track::getWeight).reversed());
        } else if (trackSort == TrackSort.POSITION) {
            tracks.sort(compareTrackPosition);
        } else if (trackSort == TrackSort.TIME_SPENT) {
            tracks.sort(Comparator.comparingLong(track -> track.getPlayerTotalTimeSpent(tPlayer)));
            Collections.reverse(tracks);
        } else {
            tracks.sort(Comparator.comparingLong(Track::getDateCreated).reversed());
        }
    }


    public void setTrackTypeButtons() {
        var boatButton = new GuiButton(new ItemBuilder(Material.OAK_BOAT).setName(Text.get(tPlayer, Gui.BOAT_TRACKS)).build());
        boatButton.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackType(Track.TrackType.BOAT);
            tPlayer.setTrackPage(0);
            openNewTrackPage(this, tPlayer, title);
        });
        setItem(boatButton,48);

        var elytraButton = new GuiButton(new ItemBuilder(Material.ELYTRA).setName(Text.get(tPlayer, Gui.ELYTRA_TRACKS)).build());
        elytraButton.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackType(Track.TrackType.ELYTRA);
            tPlayer.setTrackPage(0);
            openNewTrackPage(this, tPlayer, title);
        });
        setItem(elytraButton,49);

        var parkourButton = new GuiButton(new ItemBuilder(Material.BIG_DRIPLEAF).setName(Text.get(tPlayer, Gui.PARKOUR_TRACKS)).build());
        parkourButton.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            tPlayer.setTrackType(Track.TrackType.PARKOUR);
            tPlayer.setTrackPage(0);
            openNewTrackPage(this, tPlayer, title);
        });
        setItem(parkourButton,50);
    }

    public GuiButton getTrackTypeButton(ItemStack item) {
        var button = new GuiButton(item);
        button.setAction(() -> {
            PlaySound.buttonClick(tPlayer);
            clearNavRow();
            setTrackTypeButtons();
            tPlayer.getPlayer().openInventory(getInventory());
        });
        return button;
    }

    public static void openNewTrackPage(TrackPageGui gui, TPlayer tPlayer, Component title) {
        var constructors = gui.getClass().getDeclaredConstructors();
        for (var construct : constructors) {
            if (construct.getParameterCount() == 2) {
                try {
                    var instance = (TrackPageGui) construct.newInstance(tPlayer, title);
                    instance.show(tPlayer.getPlayer());
                } catch (Exception e) {
                    //sadge
                }
            }
        }
    }
}


