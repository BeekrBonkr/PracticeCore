package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Everything you can do with one bed defense besides building it: like,
 * favorite, see its boards, and — for your own — edit, publish or delete.
 * Deleting arms on the first click and executes on the second (style
 * guide R56), so a slip never costs a design.
 */
public final class BedDefenseActionsMenu extends Menu {

    private final BedDefense defense;
    private final Consumer<BedDefense> onPick;

    public BedDefenseActionsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                                 BedDefense defense, Consumer<BedDefense> onPick) {
        super(plugin, viewer, parent);
        this.defense = defense;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.actions.title", "name", defense.name());
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("beddefense-actions", 4);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("beddefense-actions.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("beddefense-actions.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        BedDefenseService service = plugin.bedDefenses();
        boolean own = defense.isAuthor(viewer.getUniqueId());
        boolean admin = viewer.hasPermission("practicecore.arena");
        if (plugin.bedDefenses().store().get(defense.id()) == null) {
            // Deleted while this menu was open.
            later(() -> {
                if (parent() != null) {
                    parent().open();
                } else {
                    viewer.closeInventory();
                }
            });
            return;
        }

        set(slot("play", 11), Button.of(plugin, defense.icon())
                .name("gui.beddefense.actions.play.name")
                .lore("gui.beddefense.actions.play.lore", "name", defense.name())
                .hint("play")
                .build(), event -> {
            sound("menu.select");
            if (onPick != null) {
                onPick.accept(defense);
                later(() -> {
                    if (viewer.getOpenInventory().getTopInventory().getHolder() == this) {
                        // Two menus up: past the gallery, back to whoever opened it.
                        Menu gallery = parent();
                        Menu above = gallery != null ? gallery.parent() : null;
                        if (above != null) {
                            above.open();
                        } else {
                            viewer.closeInventory();
                        }
                    }
                });
            } else {
                later(() -> {
                    viewer.closeInventory();
                    service.play(viewer, defense);
                });
            }
        });

        boolean liked = defense.likedBy(viewer.getUniqueId());
        Button like = Button.of(plugin, icon("like", Material.GOLD_NUGGET))
                .name("gui.beddefense.actions.like.name")
                .lore("gui.beddefense.actions.like.lore",
                        plugin.messages().ref("state", liked
                                ? "gui.beddefense.gallery.liked-yes" : "gui.beddefense.gallery.liked-no"),
                        "likes", String.valueOf(defense.likeCount()))
                .glow(liked);
        if (own) {
            like.disabled("gui.reason.own-defense");
        } else {
            like.hint("toggle");
        }
        set(slot("like", 12), like.build(), event -> {
            if (own || service.toggleLike(viewer, defense) == null) {
                deny();
                return;
            }
            sound(liked ? "menu.toggle-off" : "menu.toggle-on");
            refresh();
        });

        boolean favorite = service.isFavorite(viewer.getUniqueId(), defense);
        set(slot("favorite", 13), Button.of(plugin, icon("favorite", Material.AMETHYST_SHARD))
                .name("gui.beddefense.actions.favorite.name")
                .lore("gui.beddefense.actions.favorite.lore", plugin.messages().ref("state",
                        favorite ? "gui.beddefense.gallery.favorite-yes"
                                : "gui.beddefense.gallery.favorite-no"))
                .glow(favorite)
                .hint("toggle")
                .build(), event -> {
            service.toggleFavorite(viewer, defense);
            sound(favorite ? "menu.toggle-off" : "menu.toggle-on");
            refresh();
        });

        String key = BedDefenseService.statsKey(defense.id(), false);
        var record = plugin.leaderboards().record(key);
        int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
        boolean canView = viewer.hasPermission("practicecore.leaderboard");
        Button board = Button.of(plugin, icon("board", Material.GOLD_INGOT))
                .name("gui.beddefense.actions.board.name")
                .lore("gui.beddefense.actions.board.lore",
                        "players", String.valueOf(plugin.leaderboards().size(key)),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none"));
        if (canView) {
            board.hint("view");
        } else {
            if (viewer.hasPermission("practicecore.arena")) {
                board.locked("gui.reason.needs-node", "node", "practicecore.leaderboard");
            } else {
                board.locked("gui.reason.no-permission");
            }
        }
        set(slot("board", 14), board.build(), event -> {
            if (!canView) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseBoardsMenu(plugin, viewer, this, defense).open());
        });

        if (own) {
            set(slot("edit", 20), Button.of(plugin, icon("edit", Material.WRITABLE_BOOK))
                    .name("gui.beddefense.actions.edit.name")
                    .lore("gui.beddefense.actions.edit.lore")
                    .hint("edit")
                    .build(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    service.edit(viewer, defense);
                });
            });
            boolean published = defense.published();
            set(slot("visibility", 21), Button.of(plugin, published
                            ? icon("visibility", Material.LANTERN)
                            : plugin.guis().material("beddefense-actions.buttons.visibility.material-private",
                                    Material.SOUL_LANTERN))
                    .name("gui.beddefense.actions.visibility.name")
                    .lore("gui.beddefense.actions.visibility.lore", plugin.messages().ref("state",
                            published ? "label.state.public" : "label.state.private"))
                    .glow(published)
                    .hint("toggle")
                    .build(), event -> {
                sound(published ? "menu.toggle-off" : "menu.toggle-on");
                service.setPublished(viewer, defense, !published);
                refresh();
            });
        }
        if (own || admin) {
            int slot = slot("delete", 22);
            boolean armed = isArmed(slot);
            Button delete = Button.of(plugin, icon("delete", Material.LAVA_BUCKET));
            if (armed) {
                delete.name("gui.beddefense.actions.delete.name-armed")
                        .lore("gui.beddefense.actions.delete.lore-armed")
                        .glow(true)
                        .hint("confirm")
                        .line(name("gui.beddefense.actions.delete.cancel-line"));
            } else {
                delete.name("gui.beddefense.actions.delete.name")
                        .lore("gui.beddefense.actions.delete.lore")
                        .hint("delete");
            }
            set(slot, delete.build(), event -> {
                if (!isArmed(slot)) {
                    arm(slot);
                    return;
                }
                disarm();
                service.delete(viewer, defense);
                later(() -> {
                    Menu gallery = parent();
                    if (gallery != null) {
                        gallery.open();
                    } else {
                        viewer.closeInventory();
                    }
                });
            });
        }
        nav("beddefense-actions");
    }
}
