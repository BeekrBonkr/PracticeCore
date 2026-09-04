package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
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
 * Category picker: one tile per arena category, each opening that category's
 * own arena menu. Which categories exist is derived from the arenas the
 * viewer can see, so an empty category can never be offered.
 */
public final class CategoryMenu extends PagedMenu<String> {

    /** category → its visible arenas, from one scan per render (see entries()). */
    private Map<String, List<ArenaTemplate>> byCategory = Map.of();

    public CategoryMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.categories.title");
    }

    @Override
    protected List<String> entries() {
        // One permission-filtered pass; icon() reuses the grouping rather than
        // re-scanning every template per tile.
        Map<String, List<ArenaTemplate>> grouped = new LinkedHashMap<>();
        for (ArenaTemplate template : plugin.templates().visibleTo(viewer)) {
            grouped.computeIfAbsent(template.effectiveCategory(), k -> new ArrayList<>())
                    .add(template);
        }
        byCategory = grouped;
        List<String> categories = new ArrayList<>(grouped.keySet());
        // Bed defense practice has no arenas of its own — it borrows every
        // rush map — so it appears as a category of its own here.
        if (!plugin.bedDefenses().maps(viewer).isEmpty()) {
            categories.add(me.beekrbonkr.practicecore.mode.BedDefenseMode.ID);
        }
        return List.copyOf(categories);
    }

    private boolean bedDefense(String category) {
        return category.equals(me.beekrbonkr.practicecore.mode.BedDefenseMode.ID)
                && !byCategory.containsKey(category);
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.categories.empty");
    }

    @Override
    protected ItemStack icon(String category) {
        int count = bedDefense(category)
                ? plugin.bedDefenses().maps(viewer).size()
                : byCategory.getOrDefault(category, List.of()).size();
        return Button.of(plugin, categoryIcon(category))
                .name("gui.categories.entry-name",
                        "category", plugin.guis().categoryName(category))
                .lore("gui.categories.entry-lore",
                        "category", plugin.guis().categoryName(category),
                        "count", String.valueOf(count))
                .hint("open")
                .build();
    }

    /** Configured icon, else the first arena's icon, else a fallback. */
    private Material categoryIcon(String category) {
        Material configured = plugin.guis().categoryIcon(category);
        if (configured != null) {
            return configured;
        }
        if (bedDefense(category)) {
            return Material.RED_BED;
        }
        List<ArenaTemplate> arenas = byCategory.getOrDefault(category, List.of());
        return arenas.isEmpty() ? Material.GRASS_BLOCK
                : plugin.modes().of(arenas.get(0)).menuIcon(plugin, arenas.get(0));
    }

    @Override
    protected void onEntryClick(String category, InventoryClickEvent event) {
        sound("menu.select");
        if (bedDefense(category)) {
            later(() -> new BedDefenseArenaMenu(plugin, viewer, this).open());
            return;
        }
        later(() -> new ArenaMenu(plugin, viewer, this, category).open());
    }
}
