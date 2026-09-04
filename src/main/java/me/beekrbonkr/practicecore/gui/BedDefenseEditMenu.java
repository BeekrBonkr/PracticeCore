package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The editor's menu: name the build, save it (and go play it), load one of
 * your own, wipe the build, choose whether it publishes, or leave the
 * editor. Clearing and deleting both arm on the first click (R56).
 */
public final class BedDefenseEditMenu extends Menu {

    private final PracticeSession session;
    private final BedDefenseState state;

    public BedDefenseEditMenu(PracticeCorePlugin plugin, Player viewer, PracticeSession session,
                              BedDefenseState state) {
        super(plugin, viewer, null);
        this.session = session;
        this.state = state;
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.editor.title");
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("beddefense-editor", 4);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("beddefense-editor.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("beddefense-editor.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        BedDefenseService service = plugin.bedDefenses();
        String name = state.editName() == null ? raw("gui.beddefense.editor.unnamed") : state.editName();
        int blocks = state.editSequence().size();
        BedDefense source = state.editSourceId() == null ? null
                : service.store().get(state.editSourceId());
        boolean published = state.editPublished();
        var visibility = plugin.messages().ref("visibility",
                published ? "label.state.public" : "label.state.private");

        set(slot("name", 10), Button.of(plugin, icon("name", Material.NAME_TAG))
                .name("gui.beddefense.editor.name.name")
                .lore("gui.beddefense.editor.name.lore", "name", name)
                .hint("rename")
                .build(), event -> {
            click();
            later(() -> service.promptName(viewer, session, state, null));
        });

        Button save = Button.of(plugin, icon("save", Material.EMERALD))
                .name("gui.beddefense.editor.save.name")
                .lore(source != null ? "gui.beddefense.editor.save.lore-update"
                                : "gui.beddefense.editor.save.lore",
                        visibility, "name", name, "blocks", String.valueOf(blocks));
        if (blocks == 0) {
            save.disabled("gui.reason.no-blocks");
        } else {
            save.hint("save");
        }
        set(slot("save", 11), save.build(), event -> {
            if (blocks == 0) {
                deny();
                plugin.messages().send(viewer, "beddefense.edit.empty");
                return;
            }
            click();
            later(() -> {
                viewer.closeInventory();
                service.editSave(viewer, session, state);
            });
        });

        var mine = service.store().ownedBy(viewer.getUniqueId());
        Button load = Button.of(plugin, icon("load", Material.BOOKSHELF))
                .name("gui.beddefense.editor.load.name")
                .lore(mine.isEmpty() ? "gui.beddefense.editor.load.lore-none"
                        : "gui.beddefense.editor.load.lore", "count", String.valueOf(mine.size()));
        if (mine.isEmpty()) {
            load.disabled("gui.reason.nothing-to-load");
        } else {
            load.hint("open");
        }
        set(slot("load", 12), load.build(), event -> {
            if (mine.isEmpty()) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseGalleryMenu(plugin, viewer, this,
                    BedDefenseGalleryMenu.Purpose.EDIT, picked -> {
                viewer.closeInventory();
                service.loadIntoEditor(viewer, state, picked);
            }).open());
        });

        int resetSlot = slot("reset", 13);
        Button reset = Button.of(plugin, icon("reset", Material.RED_DYE));
        if (isArmed(resetSlot)) {
            reset.name("gui.beddefense.editor.reset.name-armed")
                    .lore("gui.beddefense.editor.reset.lore-armed")
                    .glow(true)
                    .hint("confirm")
                    .line(name("gui.beddefense.actions.delete.cancel-line"));
        } else {
            reset.name("gui.beddefense.editor.reset.name")
                    .lore("gui.beddefense.editor.reset.lore", "blocks", String.valueOf(blocks))
                    .hint("run");
        }
        set(resetSlot, reset.build(), event -> {
            if (!isArmed(resetSlot)) {
                arm(resetSlot);
                return;
            }
            disarm();
            later(() -> {
                viewer.closeInventory();
                service.editReset(viewer, state);
            });
        });

        set(slot("visibility", 14), Button.of(plugin, published
                        ? icon("visibility", Material.LANTERN)
                        : plugin.guis().material("beddefense-editor.buttons.visibility.material-private",
                                Material.SOUL_LANTERN))
                .name("gui.beddefense.editor.visibility.name")
                .lore("gui.beddefense.editor.visibility.lore", plugin.messages().ref("state",
                        published ? "label.state.public" : "label.state.private"))
                .glow(published)
                .hint("toggle")
                .build(), event -> {
            sound(published ? "menu.toggle-off" : "menu.toggle-on");
            state.setEditPublished(!published);
            if (source != null && source.isAuthor(viewer.getUniqueId())) {
                service.setPublished(viewer, source, !published);
            }
            refresh();
        });

        if (source != null && source.isAuthor(viewer.getUniqueId())) {
            int deleteSlot = slot("delete", 15);
            Button delete = Button.of(plugin, icon("delete", Material.LAVA_BUCKET));
            if (isArmed(deleteSlot)) {
                delete.name("gui.beddefense.editor.delete.name-armed")
                        .lore("gui.beddefense.editor.delete.lore-armed")
                        .glow(true)
                        .hint("confirm")
                        .line(name("gui.beddefense.actions.delete.cancel-line"));
            } else {
                delete.name("gui.beddefense.editor.delete.name")
                        .lore("gui.beddefense.editor.delete.lore")
                        .hint("delete");
            }
            set(deleteSlot, delete.build(), event -> {
                if (!isArmed(deleteSlot)) {
                    arm(deleteSlot);
                    return;
                }
                disarm();
                service.delete(viewer, source);
                state.setEditSourceId(null);
                later(() -> {
                    viewer.closeInventory();
                    service.editReset(viewer, state);
                });
            });
        }

        set(slot("exit", 16), Button.of(plugin, icon("exit", Material.OAK_DOOR))
                .name("gui.beddefense.editor.exit.name")
                .lore("gui.beddefense.editor.exit.lore")
                .hint("leave")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                service.leaveEditor(viewer, session, state);
            });
        });
        nav("beddefense-editor");
    }
}
