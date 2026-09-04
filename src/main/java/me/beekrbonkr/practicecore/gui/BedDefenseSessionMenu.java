package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Phase;
import me.beekrbonkr.practicecore.session.PracticeSession;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The in-arena menu behind the bed defense hotbar item while playing:
 * preview, guided building, a different defense, the round settings, and
 * the way into the editor.
 */
public final class BedDefenseSessionMenu extends Menu {

    private final PracticeSession session;
    private final BedDefenseState state;

    public BedDefenseSessionMenu(PracticeCorePlugin plugin, Player viewer, PracticeSession session,
                                 BedDefenseState state) {
        super(plugin, viewer, null);
        this.session = session;
        this.state = state;
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.session.title", "arena", session.template().displayName());
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("beddefense-session", 4);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("beddefense-session.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("beddefense-session.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        BedDefenseService service = plugin.bedDefenses();
        BedDefense defense = state.defense();
        boolean guided = state.phase() == Phase.GUIDED;

        boolean canPreview = defense != null && state.phase() == Phase.PLAY;
        Button preview = Button.of(plugin, icon("preview", Material.PAINTING))
                .name("gui.beddefense.session.preview.name")
                .lore("gui.beddefense.session.preview.lore");
        if (defense == null) {
            preview.disabled("gui.reason.no-defense");
        } else if (!canPreview) {
            preview.disabled("gui.reason.wrong-phase");
        } else {
            preview.hint("view");
        }
        set(slot("preview", 10), preview.build(), event -> {
            if (!canPreview) {
                deny();
                return;
            }
            click();
            later(() -> {
                viewer.closeInventory();
                if (state.attemptInProgress(session.timerRunning())) {
                    // Blocks are down: a preview would wreck them. Guided
                    // keeps the attempt — the same thing the drop gesture does.
                    service.enterGuided(viewer, session, state, true);
                } else {
                    service.enterPreview(viewer, session, state);
                }
            });
        });

        Button guide = Button.of(plugin, icon("guided", Material.LEAD))
                .name("gui.beddefense.session.guided.name")
                .lore("gui.beddefense.session.guided.lore", plugin.messages().ref("state",
                        guided ? "label.state.on" : "label.state.off"));
        if (defense == null) {
            guide.disabled("gui.reason.no-defense");
        } else {
            guide.glow(guided).hint("toggle");
        }
        set(slot("guided", 11), guide.build(), event -> {
            if (defense == null) {
                deny();
                return;
            }
            sound(guided ? "menu.toggle-off" : "menu.toggle-on");
            later(() -> {
                viewer.closeInventory();
                if (guided) {
                    service.exitGuided(viewer, session, state);
                } else {
                    service.enterGuided(viewer, session, state, false);
                }
            });
        });

        set(slot("defense", 12), Button.of(plugin, defense != null ? defense.icon()
                        : icon("defense", Material.RED_BED))
                .name("gui.beddefense.session.defense.name")
                .lore("gui.beddefense.session.defense.lore",
                        "name", defense != null ? defense.name() : raw("gui.none"))
                .hint("open")
                .build(), event -> {
            click();
            later(() -> new BedDefenseGalleryMenu(plugin, viewer, this,
                    BedDefenseGalleryMenu.Purpose.SELECT, picked -> {
                viewer.closeInventory();
                service.play(viewer, picked);
            }).open());
        });

        set(slot("settings", 13), Button.of(plugin, icon("settings", Material.COMPARATOR))
                .name("gui.beddefense.session.settings.name")
                .lore("gui.beddefense.session.settings.lore")
                .hint("open")
                .build(), event -> {
            click();
            later(() -> new BedDefenseConfigMenu(plugin, viewer, this, session.template()).open());
        });

        set(slot("restart", 14), Button.of(plugin, icon("restart", Material.CLOCK))
                .name("gui.beddefense.session.restart.name")
                .lore("gui.beddefense.session.restart.lore")
                .hint("restart")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                if (state.phase() == Phase.GUIDED) {
                    service.exitGuided(viewer, session, state);
                } else {
                    plugin.sessions().restart(viewer);
                }
            });
        });

        if (defense != null && defense.isAuthor(viewer.getUniqueId())) {
            set(slot("edit", 20), Button.of(plugin, icon("edit", Material.WRITABLE_BOOK))
                    .name("gui.beddefense.session.edit.name")
                    .lore("gui.beddefense.session.edit.lore", "name", defense.name())
                    .hint("edit")
                    .build(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    service.edit(viewer, defense);
                });
            });
        }
        set(slot("new", 21), Button.of(plugin, icon("new", Material.CRAFTING_TABLE))
                .name("gui.beddefense.session.new.name")
                .lore("gui.beddefense.session.new.lore",
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius()))
                .hint("open")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                service.edit(viewer, null);
            });
        });
        set(slot("maps", 22), Button.of(plugin, icon("maps", Material.FILLED_MAP))
                .name("gui.beddefense.session.maps.name")
                .lore("gui.beddefense.session.maps.lore")
                .hint("open")
                .build(), event -> {
            click();
            later(() -> new BedDefenseArenaMenu(plugin, viewer, this).open());
        });
        nav("beddefense-session");
    }
}
