package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Arena picker for leaderboards. */
public final class LeaderboardMenu extends PagedMenu<ArenaTemplate> {

    /** Null lists every board-bearing arena; otherwise only that category's. */
    private final String category;

    public LeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        this(plugin, viewer, parent, null);
    }

    public LeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                           String category) {
        super(plugin, viewer, parent);
        this.category = category;
    }

    @Override
    protected Component title() {
        if (category == null) {
            return text("gui.leaderboards.title");
        }
        return text("gui.leaderboards.title-category",
                "category", plugin.guis().categoryName(category));
    }

    @Override
    protected List<ArenaTemplate> entries() {
        // Leaderboards are public even for arenas you cannot play — knowing
        // the times is half of why anyone wants access in the first place.
        // Modes without time boards (MLG streaks, PvP bot sparring) are left
        // out entirely rather than listed forever-empty.
        return plugin.templates().completeTemplates().stream()
                .filter(template -> plugin.modes().of(template).hasLeaderboards())
                .filter(template -> category == null
                        || template.effectiveCategory().equals(category))
                .toList();
    }

    @Override
    protected ItemStack emptyIcon() {
        return ItemBuilder.of(emptyMaterial())
                .name(name("gui.leaderboards.empty.name"))
                .lore(lore("gui.leaderboards.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(ArenaTemplate template) {
        // Rush arenas keep one board per objective; the tile sums them and
        // shows the fastest record of the three.
        List<String> keys = plugin.modes().of(template).statsKeys(template);
        LeaderboardService.Entry record = null;
        int players = 0;
        int bestRank = 0;
        for (String key : keys) {
            LeaderboardService.Entry keyRecord = plugin.leaderboards().record(key);
            if (keyRecord != null && (record == null || keyRecord.millis() < record.millis())) {
                record = keyRecord;
            }
            players += plugin.leaderboards().size(key);
            int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
            if (rank > 0 && (bestRank == 0 || rank < bestRank)) {
                bestRank = rank;
            }
        }
        return ItemBuilder.of(plugin.modes().of(template).menuIcon(plugin, template))
                .name(name("gui.leaderboards.entry-name", "arena", template.displayName()))
                .lore(lore("gui.leaderboards.entry-lore",
                        "arena", template.displayName(),
                        "players", String.valueOf(players),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", bestRank > 0 ? "#" + bestRank : raw("gui.none")))
                .glow(bestRank == 1)
                .build();
    }

    @Override
    protected void onEntryClick(ArenaTemplate template, InventoryClickEvent event) {
        click();
        if (template.mode().equals(me.beekrbonkr.practicecore.mode.RushMode.ID)) {
            later(() -> new RushBoardPickerMenu(plugin, viewer, this, template).open());
            return;
        }
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template).open());
    }
}
