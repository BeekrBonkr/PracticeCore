package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The editor's menu: name the build, save it (and go play it), load one of
 * your own, wipe the build, choose whether it publishes, or leave the
 * editor.
 */
public final class BedDefenseEditMenu extends Menu {

    private final PracticeSession session;
    private final BedDefenseState state;
    private boolean deleteArmed;

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

        set(slot("name", 10), ItemBuilder.of(icon("name", Material.NAME_TAG))
                .name(name("gui.beddefense.editor.name.name"))
                .lore(lore("gui.beddefense.editor.name.lore", "name", name))
                .build(), event -> {
            click();
            later(() -> service.promptName(viewer, session, state, null));
        });

        set(slot("save", 11), ItemBuilder.of(icon("save", Material.LIME_DYE))
                .name(name("gui.beddefense.editor.save.name"))
                .lore(lore(source != null ? "gui.beddefense.editor.save.lore-update"
                                : "gui.beddefense.editor.save.lore",
                        plugin.messages().ref("visibility", state.editPublished()
                                ? "gui.beddefense.gallery.visibility-public"
                                : "gui.beddefense.gallery.visibility-private"),
                        "name", name, "blocks", String.valueOf(blocks)))
                .glow(blocks > 0)
                .build(), event -> {
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
        set(slot("load", 12), ItemBuilder.of(icon("load", Material.BOOK))
                .name(name("gui.beddefense.editor.load.name"))
                .lore(lore(mine.isEmpty() ? "gui.beddefense.editor.load.lore-none"
                        : "gui.beddefense.editor.load.lore", "count", String.valueOf(mine.size())))
                .build(), event -> {
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

        set(slot("reset", 13), ItemBuilder.of(icon("reset", Material.BARRIER))
                .name(name("gui.beddefense.editor.reset.name"))
                .lore(lore("gui.beddefense.editor.reset.lore", "blocks", String.valueOf(blocks)))
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                service.editReset(viewer, state);
            });
        });

        boolean published = state.editPublished();
        set(slot("visibility", 14), ItemBuilder.of(icon("visibility",
                        published ? Material.ENDER_EYE : Material.ENDER_PEARL))
                .name(name("gui.beddefense.editor.visibility.name"))
                .lore(lore("gui.beddefense.editor.visibility.lore", plugin.messages().ref("state",
                        published ? "gui.beddefense.gallery.visibility-public"
                                : "gui.beddefense.gallery.visibility-private")))
                .glow(published)
                .build(), event -> {
            sound(published ? "menu.toggle-off" : "menu.toggle-on");
            state.setEditPublished(!published);
            if (source != null && source.isAuthor(viewer.getUniqueId())) {
                service.setPublished(viewer, source, !published);
            }
            refresh();
        });

        if (source != null && source.isAuthor(viewer.getUniqueId())) {
            set(slot("delete", 15), ItemBuilder.of(icon("delete",
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
                service.delete(viewer, source);
                state.setEditSourceId(null);
                later(() -> {
                    viewer.closeInventory();
                    service.editReset(viewer, state);
                });
            });
        }

        set(slot("exit", 16), ItemBuilder.of(icon("exit", Material.OAK_DOOR))
                .name(name("gui.beddefense.editor.exit.name"))
                .lore(lore("gui.beddefense.editor.exit.lore"))
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                service.leaveEditor(viewer, session, state);
            });
        });
        closeButton(plugin.guis().slot("beddefense-editor.close", 31));
    }
}
