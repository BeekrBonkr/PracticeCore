package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.Mode;
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
        return ItemBuilder.of(emptyMaterial())
                .name(name("gui.arenas.empty.name"))
                .lore(lore("gui.arenas.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(ArenaTemplate template) {
        boolean allowed = plugin.templates().canUse(viewer, template);
        // Modes without time boards get a tile without the dead time fields —
        // "Best: none, Rank: none" forever reads as broken, not as unranked.
        if (!plugin.modes().of(template).hasLeaderboards()) {
            return ItemBuilder.of(allowed ? template.effectiveIcon() : Material.IRON_BARS)
                    .name(name(allowed ? "gui.arenas.entry-name" : "gui.arenas.entry-name-locked",
                            "arena", template.displayName()))
                    .lore(lore("gui.arenas.entry-lore-unranked",
                            plugin.messages().ref("status", statusLine(template, allowed)),
                            "arena", template.displayName(),
                            "mode", modeName(template)))
                    .build();
        }
        long best = plugin.stats().bestMs(viewer.getUniqueId(), template.name());
        int rank = plugin.leaderboards().rank(template.name(), viewer.getUniqueId());
        LeaderboardService.Entry record = plugin.leaderboards().record(template.name());
        return ItemBuilder.of(allowed ? template.effectiveIcon() : Material.IRON_BARS)
                .name(name(allowed ? "gui.arenas.entry-name" : "gui.arenas.entry-name-locked",
                        "arena", template.displayName()))
                .lore(lore("gui.arenas.entry-lore",
                        plugin.messages().ref("status", statusLine(template, allowed)),
                        "arena", template.displayName(),
                        "mode", modeName(template),
                        "best", best >= 0 ? TimeFormat.precise(best) : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none"),
                        "players", String.valueOf(plugin.leaderboards().size(template.name())),
                        "finishes", String.valueOf(
                                plugin.stats().finishes(viewer.getUniqueId(), template.name())),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : ""))
                .glow(rank == 1)
                .build();
    }

    /**
     * "Click to play", or why not. Admins additionally see the node that
     * governs the arena, which is the thing they would go and change.
     */
    private Component statusLine(ArenaTemplate template, boolean allowed) {
        if (allowed) {
            return text("gui.arenas.status-open");
        }
        Component locked = text("gui.arenas.status-locked");
        if (viewer.hasPermission("practicecore.arena")) {
            locked = locked.append(Component.space()).append(text("gui.arenas.status-locked-node",
                    "node", plugin.templates().permissionFor(template)));
        }
        return locked;
    }

    private String modeName(ArenaTemplate template) {
        return plugin.modes().get(template.mode()).map(Mode::displayName).orElse(template.mode());
    }

    @Override
    protected void onEntryClick(ArenaTemplate template, InventoryClickEvent event) {
        if (!plugin.templates().canUse(viewer, template)) {
            deny();
            return;
        }
        click();
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
