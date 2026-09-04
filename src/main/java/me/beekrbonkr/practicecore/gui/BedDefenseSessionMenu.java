package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Phase;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.util.ItemBuilder;
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
        return text("gui.beddefense.session.title");
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

        set(slot("preview", 10), ItemBuilder.of(icon("preview", Material.SPYGLASS))
                .name(name("gui.beddefense.session.preview.name"))
                .lore(lore("gui.beddefense.session.preview.lore"))
                .build(), event -> {
            if (defense == null || state.phase() != Phase.PLAY) {
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

        set(slot("guided", 11), ItemBuilder.of(icon("guided", Material.COMPASS))
                .name(name(guided ? "gui.beddefense.session.guided.name-exit"
                        : "gui.beddefense.session.guided.name"))
                .lore(lore(guided ? "gui.beddefense.session.guided.lore-exit"
                        : "gui.beddefense.session.guided.lore"))
                .glow(guided)
                .build(), event -> {
            if (defense == null) {
                deny();
                return;
            }
            click();
            later(() -> {
                viewer.closeInventory();
                if (guided) {
                    service.exitGuided(viewer, session, state);
                } else {
                    service.enterGuided(viewer, session, state, false);
                }
            });
        });

        set(slot("defense", 12), ItemBuilder.of(defense != null ? defense.icon()
                        : icon("defense", Material.RED_BED))
                .name(name("gui.beddefense.session.defense.name"))
                .lore(lore("gui.beddefense.session.defense.lore",
                        "name", defense != null ? defense.name() : raw("gui.none")))
                .build(), event -> {
            click();
            later(() -> new BedDefenseGalleryMenu(plugin, viewer, this,
                    BedDefenseGalleryMenu.Purpose.SELECT, picked -> {
                viewer.closeInventory();
                service.play(viewer, picked);
            }).open());
        });

        set(slot("settings", 13), ItemBuilder.of(icon("settings", Material.COMPARATOR))
                .name(name("gui.beddefense.session.settings.name"))
                .lore(lore("gui.beddefense.session.settings.lore"))
                .build(), event -> {
            click();
            later(() -> new BedDefenseConfigMenu(plugin, viewer, this, session.template()).open());
        });

        set(slot("restart", 14), ItemBuilder.of(icon("restart", Material.CLOCK))
                .name(name("gui.beddefense.session.restart.name"))
                .lore(lore("gui.beddefense.session.restart.lore"))
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
            set(slot("edit", 20), ItemBuilder.of(icon("edit", Material.WRITABLE_BOOK))
                    .name(name("gui.beddefense.session.edit.name"))
                    .lore(lore("gui.beddefense.session.edit.lore", "name", defense.name()))
                    .build(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    service.edit(viewer, defense);
                });
            });
        }
        set(slot("new", 21), ItemBuilder.of(icon("new", Material.CRAFTING_TABLE))
                .name(name("gui.beddefense.session.new.name"))
                .lore(lore("gui.beddefense.session.new.lore",
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius())))
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                service.edit(viewer, null);
            });
        });
        set(slot("maps", 22), ItemBuilder.of(icon("maps", Material.FILLED_MAP))
                .name(name("gui.beddefense.session.maps.name"))
                .lore(lore("gui.beddefense.session.maps.lore"))
                .build(), event -> {
            click();
            later(() -> new BedDefenseArenaMenu(plugin, viewer, this).open());
        });
        closeButton(plugin.guis().slot("beddefense-session.close", 31));
    }
}
