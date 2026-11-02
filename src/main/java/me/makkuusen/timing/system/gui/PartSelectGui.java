package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ItemBuilder;
import me.makkuusen.timing.system.team.Team;
import me.makkuusen.timing.system.team.TeamTuning;
import me.makkuusen.timing.system.tplayer.TPlayer;
import me.makkuusen.timing.system.tuning.Part;
import me.makkuusen.timing.system.tuning.PartCategory;
import me.makkuusen.timing.system.tuning.PartManager;
import me.makkuusen.timing.system.sounds.PlaySound;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;

public class PartSelectGui extends BaseGui {

    private static final int PARTS_PER_PAGE = 45;

    private final Team team;
    private final TPlayer tPlayer;
    private final PartCategory category;

    private int page;
    private int maxPage;

    public PartSelectGui(TPlayer tPlayer, Team team, PartCategory category) {
        this(tPlayer, team, category, 0);
    }

    public PartSelectGui(TPlayer tPlayer, Team team, PartCategory category, int page) {
        super(Component.text(category.toString()), 6);

        this.tPlayer = tPlayer;
        this.team = team;
        this.category = category;
        this.page = page;

        update();
    }

    private void update() {
        setButtons();
        setNavigation();
    }

    public GuiButton getCategoryButton(TPlayer tPlayer, PartCategory category) {
        return new GuiButton(
                new ItemBuilder(category.getMaterial())
                        .setName(category.toString())
                        .build()
        );
    }

    public GuiButton getPartButton(TPlayer tPlayer, Part part) {
        var button = new GuiButton(part.getItem(tPlayer));

        button.setAction(() -> {
            TeamTuning tuning = team.getTuning();

            tuning.equipPart(part);

            new BoatSetupGui(tPlayer, team).show(tPlayer.getPlayer());
        });

        return button;
    }

    private List<Part> getParts() {
        return PartManager.getParts().stream()
                .filter(part -> part.getCategoryName() == category)
                .sorted(Comparator.comparingInt(Part::getRating))
                .toList();
    }

    private void setButtons() {
        setItem(getCategoryButton(tPlayer, category), 0);

        List<Part> parts = getParts();

        maxPage = Math.max(0, (parts.size() - 1) / PARTS_PER_PAGE);

        // Prevent invalid pages
        if (page > maxPage) {
            page = maxPage;
        }

        int start = page * PARTS_PER_PAGE;
        int end = Math.min(start + PARTS_PER_PAGE, parts.size());

        for (int i = start; i < end; i++) {
            Part part = parts.get(i);

            // Slots 0-44 are reserved for parts
            int slot = i - start;

            setItem(getPartButton(tPlayer, part), slot);
        }
    }

    private void setNavigation() {
        // Clear bottom row
        for (int slot = 45; slot <= 53; slot++) {
            removeItem(slot);
        }

        // Previous page
        if (page > 0) {
            ItemStack previous = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .setName(Component.text("Previous Page"))
                    .build();

            GuiButton button = new GuiButton(previous);

            button.setAction(() -> {
                PlaySound.pageTurn(tPlayer);
                openPage(page - 1);
            });

            setItem(button, 48);
        }

        // Page indicator
        ItemStack pageItem = new ItemBuilder(Material.PAPER)
                .setName(Component.text("Page " + (page + 1) + "/" + (maxPage + 1)))
                .build();

        setItem(new GuiButton(pageItem), 49);

        // Next page
        if (page < maxPage) {
            ItemStack next = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName(Component.text("Next Page"))
                    .build();

            GuiButton button = new GuiButton(next);

            button.setAction(() -> {
                PlaySound.pageTurn(tPlayer);
                openPage(page + 1);
            });

            setItem(button, 50);
        }
    }

    private void openPage(int newPage) {
        new PartSelectGui(tPlayer, team, category, newPage)
                .show(tPlayer.getPlayer());
    }
}