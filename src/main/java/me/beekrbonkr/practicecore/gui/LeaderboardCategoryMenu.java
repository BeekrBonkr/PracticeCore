package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Category picker for leaderboards, mirroring the Play button's
 * {@link CategoryMenu}: one tile per category, each opening that category's
 * own {@link LeaderboardMenu}. Only categories with at least one
 * board-bearing arena appear — leaderboards are public regardless of who may
 * play, so this groups the same arenas the flat leaderboard list would show.
 */
public final class LeaderboardCategoryMenu extends PagedMenu<String> {

    /** category → its board-bearing arenas, from one scan per render. */
    private Map<String, List<ArenaTemplate>> byCategory = Map.of();

    public LeaderboardCategoryMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.leaderboards.categories.title");
    }

    @Override
    protected List<String> entries() {
        Map<String, List<ArenaTemplate>> grouped = new LinkedHashMap<>();
        for (ArenaTemplate template : plugin.templates().completeTemplates()) {
            if (!plugin.modes().of(template).hasLeaderboards()) {
                continue;
            }
            grouped.computeIfAbsent(template.effectiveCategory(), k -> new ArrayList<>())
                    .add(template);
        }
        byCategory = grouped;
        return List.copyOf(grouped.keySet());
    }

    @Override
    protected ItemStack emptyIcon() {
        return ItemBuilder.of(emptyMaterial())
                .name(name("gui.leaderboards.empty.name"))
                .lore(lore("gui.leaderboards.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(String category) {
        List<ArenaTemplate> arenas = byCategory.getOrDefault(category, List.of());
        return ItemBuilder.of(categoryIcon(category, arenas))
                .name(name("gui.leaderboards.categories.entry-name",
                        "category", plugin.guis().categoryName(category)))
                .lore(lore("gui.leaderboards.categories.entry-lore",
                        "category", plugin.guis().categoryName(category),
                        "count", String.valueOf(arenas.size())))
                .build();
    }

    /** Configured icon, else the first arena's icon, else a fallback. */
    private Material categoryIcon(String category, List<ArenaTemplate> arenas) {
        Material configured = plugin.guis().categoryIcon(category);
        if (configured != null) {
            return configured;
        }
        return arenas.isEmpty() ? Material.GRASS_BLOCK
                : plugin.modes().of(arenas.get(0)).menuIcon(plugin, arenas.get(0));
    }

    @Override
    protected void onEntryClick(String category, InventoryClickEvent event) {
        click();
        later(() -> new LeaderboardMenu(plugin, viewer, this, category).open());
    }
}
