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
        return List.copyOf(grouped.keySet());
    }

    @Override
    protected ItemStack emptyIcon() {
        return ItemBuilder.of(emptyMaterial())
                .name(name("gui.categories.empty.name"))
                .lore(lore("gui.categories.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(String category) {
        List<ArenaTemplate> arenas = byCategory.getOrDefault(category, List.of());
        return ItemBuilder.of(categoryIcon(category, arenas))
                .name(name("gui.categories.entry-name",
                        "category", plugin.guis().categoryName(category)))
                .lore(lore("gui.categories.entry-lore",
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
        return arenas.isEmpty() ? Material.GRASS_BLOCK : arenas.get(0).effectiveIcon();
    }

    @Override
    protected void onEntryClick(String category, InventoryClickEvent event) {
        click();
        later(() -> new ArenaMenu(plugin, viewer, this, category).open());
    }
}
