package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Everything you can do with one bed defense besides building it: like,
 * favorite, see its boards, and — for your own — edit, publish or delete.
 * Deleting takes two clicks on the same button, so a slip never costs a
 * design.
 */
public final class BedDefenseActionsMenu extends Menu {

    private final BedDefense defense;
    private final Consumer<BedDefense> onPick;
    private boolean deleteArmed;

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

        set(slot("play", 11), ItemBuilder.of(defense.icon())
                .name(name("gui.beddefense.actions.play.name"))
                .lore(lore("gui.beddefense.actions.play.lore", "name", defense.name()))
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
        set(slot("like", 12), ItemBuilder.of(icon("like", Material.GOLD_NUGGET))
                .name(name("gui.beddefense.actions.like.name"))
                .lore(lore("gui.beddefense.actions.like.lore",
                        plugin.messages().ref("state", liked
                                ? "gui.beddefense.gallery.liked-yes" : "gui.beddefense.gallery.liked-no"),
                        "likes", String.valueOf(defense.likeCount())))
                .glow(liked)
                .build(), event -> {
            if (service.toggleLike(viewer, defense) == null) {
                deny();
            }
            refresh();
        });

        boolean favorite = service.isFavorite(viewer.getUniqueId(), defense);
        set(slot("favorite", 13), ItemBuilder.of(icon("favorite", Material.NETHER_STAR))
                .name(name("gui.beddefense.actions.favorite.name"))
                .lore(lore("gui.beddefense.actions.favorite.lore", plugin.messages().ref("state",
                        favorite ? "gui.beddefense.gallery.favorite-yes"
                                : "gui.beddefense.gallery.favorite-no")))
                .glow(favorite)
                .build(), event -> {
            service.toggleFavorite(viewer, defense);
            refresh();
        });

        String key = BedDefenseService.statsKey(defense.id(), false);
        var record = plugin.leaderboards().record(key);
        int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
        set(slot("board", 14), ItemBuilder.of(icon("board", Material.GOLD_INGOT))
                .name(name("gui.beddefense.actions.board.name"))
                .lore(lore("gui.beddefense.actions.board.lore",
                        "players", String.valueOf(plugin.leaderboards().size(key)),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none")))
                .glow(rank == 1)
                .build(), event -> {
            if (!viewer.hasPermission("practicecore.leaderboard")) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseBoardsMenu(plugin, viewer, this, defense).open());
        });

        if (own) {
            set(slot("edit", 20), ItemBuilder.of(icon("edit", Material.WRITABLE_BOOK))
                    .name(name("gui.beddefense.actions.edit.name"))
                    .lore(lore("gui.beddefense.actions.edit.lore"))
                    .build(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    service.edit(viewer, defense);
                });
            });
            set(slot("visibility", 21), ItemBuilder.of(icon("visibility",
                            defense.published() ? Material.ENDER_EYE : Material.ENDER_PEARL))
                    .name(name("gui.beddefense.actions.visibility.name"))
                    .lore(lore("gui.beddefense.actions.visibility.lore", plugin.messages().ref("state",
                            defense.published() ? "gui.beddefense.gallery.visibility-public"
                                    : "gui.beddefense.gallery.visibility-private")))
                    .glow(defense.published())
                    .build(), event -> {
                service.setPublished(viewer, defense, !defense.published());
                refresh();
            });
        }
        if (own || admin) {
            set(slot("delete", 22), ItemBuilder.of(icon("delete",
                            deleteArmed ? Material.TNT : Material.LAVA_BUCKET))
                    .name(name(deleteArmed ? "gui.beddefense.actions.delete.name-armed"
                            : "gui.beddefense.actions.delete.name"))
                    .lore(lore(deleteArmed ? "gui.beddefense.actions.delete.lore-armed"
                            : "gui.beddefense.actions.delete.lore"))
                    .glow(deleteArmed)
                    .build(), event -> {
                if (!deleteArmed) {
                    click();
                    deleteArmed = true;
                    refresh();
                    return;
                }
                sound("menu.select");
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
        backButton(plugin.guis().slot("beddefense-actions.back", 27));
        closeButton(plugin.guis().slot("beddefense-actions.close", 35));
    }
}
