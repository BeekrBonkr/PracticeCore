package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BlockKinds;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The bed defense gallery, three tabs across the footer: <b>Public</b>
 * (everything published, most liked then most played first), <b>Mine</b>
 * (the viewer's own, newest first) and <b>Favorites</b>. Left-click picks a
 * defense for whatever the menu was opened for (playing it, or editing
 * it); right-click opens its actions (like, favorite, board, publish,
 * delete).
 */
public final class BedDefenseGalleryMenu extends PagedMenu<BedDefense> {

    /** What a left-click does. */
    public enum Purpose {
        /** Choose the defense to build. */
        SELECT,
        /** Load one of your own into the editor (Mine tab only). */
        EDIT
    }

    public enum Tab {
        PUBLIC, MINE, FAVORITES;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Purpose purpose;
    private final Consumer<BedDefense> onPick;
    private Tab tab;

    public BedDefenseGalleryMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                                 Purpose purpose, Consumer<BedDefense> onPick) {
        super(plugin, viewer, parent);
        this.purpose = purpose;
        this.onPick = onPick;
        this.tab = purpose == Purpose.EDIT ? Tab.MINE : rememberedTab();
    }

    private Tab rememberedTab() {
        String stored = plugin.stats().pref(viewer.getUniqueId(), "beddefense.gallery-tab", "PUBLIC");
        try {
            return Tab.valueOf(stored.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Tab.PUBLIC;
        }
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.gallery.title", "tab", raw("gui.beddefense.gallery.tab." + tab.key()));
    }

    @Override
    protected List<BedDefense> entries() {
        BedDefenseService service = plugin.bedDefenses();
        return switch (tab) {
            case PUBLIC -> service.store().published();
            case MINE -> service.store().ownedBy(viewer.getUniqueId());
            case FAVORITES -> service.favorites(viewer.getUniqueId());
        };
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.gallery.empty." + tab.key());
    }

    @Override
    protected ItemStack icon(BedDefense defense) {
        BedDefenseService service = plugin.bedDefenses();
        boolean own = defense.isAuthor(viewer.getUniqueId());
        boolean selected = defense.id().equals(service.rawSelection(viewer.getUniqueId()).defense());
        long best = plugin.stats().bestMs(viewer.getUniqueId(),
                BedDefenseService.statsKey(defense.id(), false));
        var record = plugin.leaderboards().record(BedDefenseService.statsKey(defense.id(), false));
        List<Component> lines = new ArrayList<>(lore("gui.beddefense.gallery.tile-lore",
                TagResolver.resolver(
                        plugin.messages().ref("visibility", defense.published()
                                ? "label.state.public" : "label.state.private"),
                        plugin.messages().ref("liked", defense.likedBy(viewer.getUniqueId())
                                ? "gui.beddefense.gallery.liked-yes"
                                : "gui.beddefense.gallery.liked-no"),
                        plugin.messages().ref("favorite", service.isFavorite(viewer.getUniqueId(), defense)
                                ? "gui.beddefense.gallery.favorite-yes"
                                : "gui.beddefense.gallery.favorite-no")),
                "name", defense.name(),
                "author", defense.authorName(),
                "blocks", String.valueOf(defense.blocks().size()),
                "reach", String.valueOf(defense.reach()),
                "likes", String.valueOf(defense.likeCount()),
                "players", String.valueOf(defense.uniquePlayers()),
                "completions", String.valueOf(defense.completions()),
                "best", best >= 0 ? TimeFormat.precise(best) : raw("gui.none"),
                "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                "record-holder", record != null ? record.displayName() : raw("gui.none")));
        // What it is made of, most of first.
        List<Map.Entry<Material, Integer>> kinds = new ArrayList<>(defense.kindCounts().entrySet());
        kinds.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<Material, Integer> entry : kinds) {
            lines.add(plugin.messages().name("gui.beddefense.gallery.kind-line",
                    "count", String.valueOf(entry.getValue()),
                    "material", BlockKinds.pretty(entry.getKey())));
        }
        boolean chosen = selected && purpose == Purpose.SELECT;
        if (chosen) {
            lines.add(Component.empty());
            lines.add(name("gui.beddefense.gallery.selected-line"));
        }
        Button tile = Button.of(plugin, defense.icon())
                .name(own ? "gui.beddefense.gallery.tile-name-own"
                        : "gui.beddefense.gallery.tile-name", "name", defense.name())
                .lore(lines)
                .glow(chosen);
        if (purpose == Purpose.EDIT) {
            tile.hint("edit");
        } else if (!chosen) {
            tile.hint("select");
        }
        return tile.rightHint("options").build();
    }

    @Override
    protected void onEntryClick(BedDefense defense, InventoryClickEvent event) {
        if (event.isRightClick()) {
            click();
            later(() -> new BedDefenseActionsMenu(plugin, viewer, this, defense, onPick).open());
            return;
        }
        if (purpose == Purpose.EDIT && !defense.isAuthor(viewer.getUniqueId())) {
            deny();
            return;
        }
        sound("menu.select");
        onPick.accept(defense);
        later(() -> {
            if (viewer.getOpenInventory().getTopInventory().getHolder() != this) {
                return; // the pick took the player somewhere else
            }
            if (parent() != null) {
                parent().open();
            } else {
                viewer.closeInventory();
            }
        });
    }

    @Override
    protected void renderFooter() {
        if (purpose == Purpose.EDIT) {
            return; // only your own can be edited — no other tab applies
        }
        tabButton(Tab.PUBLIC, plugin.guis().slot("beddefense-gallery.tabs.public", footerSlot(0)),
                plugin.guis().buttonMaterial("beddefense-gallery.tabs.public", Material.BOOKSHELF));
        tabButton(Tab.MINE, plugin.guis().slot("beddefense-gallery.tabs.mine", footerSlot(1)),
                Material.PLAYER_HEAD);
        tabButton(Tab.FAVORITES, plugin.guis().slot("beddefense-gallery.tabs.favorites", footerSlot(2)),
                plugin.guis().buttonMaterial("beddefense-gallery.tabs.favorites", Material.AMETHYST_SHARD));
    }

    private void tabButton(Tab which, int slot, Material material) {
        boolean current = tab == which;
        int count = switch (which) {
            case PUBLIC -> plugin.bedDefenses().store().published().size();
            case MINE -> plugin.bedDefenses().store().ownedBy(viewer.getUniqueId()).size();
            case FAVORITES -> plugin.bedDefenses().favorites(viewer.getUniqueId()).size();
        };
        Button button = Button.of(plugin, material)
                .name("gui.beddefense.gallery.tab-name",
                        "tab", raw("gui.beddefense.gallery.tab." + which.key()))
                .lore(current ? "gui.beddefense.gallery.tab-lore-current"
                        : "gui.beddefense.gallery.tab-lore", "count", String.valueOf(count))
                .glow(current);
        if (!current) {
            button.hint("view");
        }
        ItemStack item = button.build();
        if (which == Tab.MINE) {
            // The Mine tab wears the viewer's own head.
            if (item.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(viewer);
                item.setItemMeta(skull);
            }
        }
        set(slot, item, event -> {
            if (current) {
                deny();
                return;
            }
            click();
            tab = which;
            plugin.stats().setPref(viewer.getUniqueId(), "beddefense.gallery-tab", which.name());
            refresh();
        });
    }
}
