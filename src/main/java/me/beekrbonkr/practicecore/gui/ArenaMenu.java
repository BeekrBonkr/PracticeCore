package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.Mode;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Arena picker. Which arenas appear here is decided by the same permission
 * check the join command uses, so the menu can never offer something the
 * command would refuse.
 */
public final class ArenaMenu extends PagedMenu<ArenaTemplate> {

    /** Null lists every visible arena; otherwise only that category's. */
    private final String category;

    public ArenaMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, String category) {
        super(plugin, viewer, parent);
        this.category = category;
    }

    @Override
    protected Component title() {
        if (category == null) {
            return text("gui.arenas.title");
        }
        return text("gui.arenas.title-category",
                "category", plugin.guis().categoryName(category));
    }

    @Override
    protected List<ArenaTemplate> entries() {
        return category == null
                ? plugin.templates().visibleTo(viewer)
                : plugin.templates().visibleTo(viewer, category);
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.arenas.empty");
    }

    @Override
    protected ItemStack icon(ArenaTemplate template) {
        boolean allowed = plugin.templates().canUse(viewer, template);
        Button tile = Button.of(plugin, plugin.modes().of(template).menuIcon(plugin, template))
                .name("gui.arenas.entry-name", "arena", template.displayName());
        // Modes without time boards get a tile without the dead time fields —
        // "Best: none, Rank: none" forever reads as broken, not as unranked.
        if (!plugin.modes().of(template).hasLeaderboards()) {
            tile.lore("gui.arenas.entry-lore-unranked",
                    "arena", template.displayName(),
                    "mode", modeName(template));
        } else {
            long best = plugin.stats().bestMs(viewer.getUniqueId(), template.name());
            int rank = plugin.leaderboards().rank(template.name(), viewer.getUniqueId());
            LeaderboardService.Entry record = plugin.leaderboards().record(template.name());
            tile.lore("gui.arenas.entry-lore",
                    "arena", template.displayName(),
                    "mode", modeName(template),
                    "best", best >= 0 ? TimeFormat.precise(best) : raw("gui.none"),
                    "rank", rank > 0 ? "#" + rank : raw("gui.none"),
                    "players", String.valueOf(plugin.leaderboards().size(template.name())),
                    "finishes", String.valueOf(
                            plugin.stats().finishes(viewer.getUniqueId(), template.name())),
                    "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                    "record-holder", record != null ? record.displayName() : raw("gui.none"));
            if (rank == 1) {
                tile.lore("gui.arenas.record-line");
            }
        }
        if (allowed) {
            tile.hint("play");
        } else if (viewer.hasPermission("practicecore.arena")) {
            // Admins see the node that governs the arena — the thing they
            // would go and change.
            tile.locked("gui.reason.needs-node",
                    "node", plugin.templates().permissionFor(template));
        } else {
            tile.locked("gui.reason.no-permission");
        }
        return tile.build();
    }

    private String modeName(ArenaTemplate template) {
        return plugin.modes().get(template.mode()).map(Mode::displayName).orElse(template.mode());
    }

    /** With categories off there is no category tile — the flat list carries the button. */
    @Override
    protected void renderFooter() {
        if (category != null || plugin.bedDefenses().maps(viewer).isEmpty()
                || !plugin.guis().buttonEnabled("beddefense.flat-button")) {
            return;
        }
        set(plugin.guis().slot("beddefense.flat-button", footerSlot(2)),
                Button.of(plugin, plugin.guis().buttonMaterial("beddefense.flat-button", Material.RED_BED))
                        .name("gui.beddefense.arenas.flat-button.name")
                        .lore("gui.beddefense.arenas.flat-button.lore")
                        .hint("open")
                        .build(), event -> {
            click();
            later(() -> new BedDefenseArenaMenu(plugin, viewer, this).open());
        });
    }

    @Override
    protected void onEntryClick(ArenaTemplate template, InventoryClickEvent event) {
        if (!plugin.templates().canUse(viewer, template)) {
            deny();
            return;
        }
        sound("menu.select");
        if (template.mode().equals(me.beekrbonkr.practicecore.mode.RushMode.ID)) {
            // Rush needs its objective/team/modifier choices before joining.
            later(() -> new RushConfigMenu(plugin, viewer, this, template).open());
            return;
        }
        later(() -> {
            viewer.closeInventory();
            // Switching from another arena is handled inside join().
            plugin.sessions().join(viewer, template);
        });
    }
}
