package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Everything you can do with one bed defense besides building it: like,
 * favorite, see its boards, report it — and, for your own, edit, publish
 * or delete. A moderator ({@code practicecore.beddefense.moderate}) gets
 * the visibility and delete controls on anyone's, plus its reports.
 * Deleting arms on the first click and executes on the second (style
 * guide R56), so a slip never costs a design.
 */
public final class BedDefenseActionsMenu extends Menu {

    /** Re-resolved from the store on every render: a reshape or reload swaps instances. */
    private BedDefense defense;
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
        BedDefense live = service.store().get(defense.id());
        if (live != null) {
            defense = live;
        }
        boolean own = defense.isAuthor(viewer.getUniqueId());
        boolean moderator = service.isModerator(viewer);
        if (live == null) {
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
        if (moderator && defense.reportCount() > 0) {
            // Opening a reported defense's menu is looking at its reports.
            service.markReportsSeen(defense);
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

        String key = BedDefenseService.statsKey(defense.id());
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

        if (!own && defense.published()) {
            boolean reported = defense.reportBy(viewer.getUniqueId()) != null;
            set(slot("report", 15), Button.of(plugin, icon("report", Material.BELL))
                    .name("gui.beddefense.actions.report.name")
                    .lore("gui.beddefense.actions.report.lore", plugin.messages().ref("state",
                            reported ? "gui.beddefense.actions.reported-yes"
                                    : "gui.beddefense.actions.reported-no"))
                    .hint("report")
                    .build(), event -> {
                click();
                later(() -> service.promptReport(viewer, defense));
            });
        }

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
        }
        if (own || moderator) {
            boolean published = defense.published();
            TagResolver state = TagResolver.resolver(
                    plugin.messages().ref("state", published ? "label.state.public" : "label.state.private"),
                    plugin.messages().ref("cleared", defense.authorCleared()
                            ? "gui.beddefense.actions.cleared-yes" : "gui.beddefense.actions.cleared-no"));
            Button visibility = Button.of(plugin, published
                            ? icon("visibility", Material.LANTERN)
                            : plugin.guis().material("beddefense-actions.buttons.visibility.material-private",
                                    Material.SOUL_LANTERN))
                    .name("gui.beddefense.actions.visibility.name")
                    .glow(published);
            if (own) {
                visibility.lore("gui.beddefense.actions.visibility.lore", state);
            } else {
                visibility.lore("gui.beddefense.actions.visibility.lore-moderator", state,
                        "author", defense.authorName());
            }
            // Hiding is always allowed. Publishing needs the author's own
            // competitive run whoever asks, and an auto-hidden defense
            // needs a moderator — the author waits for the review.
            boolean gated = !published && !service.canPublish(viewer, defense);
            if (defense.autoHidden() && !published) {
                visibility.line(name("gui.beddefense.actions.auto-hidden-line"));
            }
            if (gated) {
                visibility.disabled(defense.autoHidden() && service.canPublish(defense)
                        ? "gui.reason.auto-hidden" : "gui.reason.needs-clear");
            } else {
                visibility.hint("toggle");
            }
            set(slot("visibility", 21), visibility.build(), event -> {
                if (gated) {
                    // The refusal explains itself (and plays the deny cue).
                    service.setPublished(viewer, defense, true);
                    return;
                }
                service.setPublished(viewer, defense, !published);
                refresh();
            });

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
        if (moderator) {
            int count = defense.reportCount();
            Button reports = Button.of(plugin, icon("reports", Material.WRITTEN_BOOK))
                    .name("gui.beddefense.actions.reports.name")
                    .lore("gui.beddefense.actions.reports.lore", "count", String.valueOf(count));
            if (count == 0) {
                reports.disabled("gui.reason.no-reports");
            } else {
                reports.hint("view");
            }
            set(slot("reports", 23), reports.build(), event -> {
                if (count == 0) {
                    deny();
                    return;
                }
                click();
                later(() -> new BedDefenseReportsMenu(plugin, viewer, this, defense).open());
            });
        }
        nav("beddefense-actions");
    }
}
