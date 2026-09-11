package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.command.AdminCommands;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything that can be done to a saved arena without opening the wizard —
 * the GUI twin of {@code /practice arena …}. Each change writes arena.yml at
 * once. While the arena is open in the setup wizard the buttons refuse, as
 * the command does, so the wizard's pending state can never be overwritten
 * from underneath it. Text is fixed — see {@link SetupGui}.
 */
final class ArenaOptionsMenu extends Menu {

    private final String arena;

    ArenaOptionsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, String arena) {
        super(plugin, viewer, parent);
        this.arena = arena;
    }

    @Override
    protected Component title() {
        return SetupGui.title("Arena — " + arena);
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void render() {
        ArenaTemplate template = plugin.templates().get(arena);
        if (template == null) {
            // Deleted or reloaded away since the list was drawn (R55).
            later(() -> {
                plugin.messages().note(viewer, "Arena " + arena + " no longer exists.");
                Menu back = parent();
                if (back != null) {
                    back.open();
                } else {
                    viewer.closeInventory();
                }
            });
            return;
        }
        border();

        set(10, SetupGui.control(plugin, Material.WRITABLE_BOOK, "Open in Wizard",
                        NamedTextColor.WHITE,
                        "Pastes the arena into the practice",
                        "world and opens the wizard panel,",
                        "for spawn, triggers, kit and build.")
                .hint("edit")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                plugin.setup().edit(viewer, arena);
                ArenaListMenu.openActionsWhenReady(plugin, viewer);
            });
        });

        set(11, SetupGui.control(plugin, Material.NAME_TAG, "Display Name", NamedTextColor.WHITE,
                        "The name players see in menus.",
                        "")
                .line(SetupGui.state("Current", template.displayName()))
                .hint("rename")
                .build(), event -> {
            if (busy()) {
                return;
            }
            click();
            promptThenReopen("Display Name", template.displayName(),
                    List.of("The name players see in menus."), answer -> {
                ArenaTemplate current = plugin.templates().get(arena);
                if (current == null || busy()) {
                    return;
                }
                current.setDisplayName(answer);
                persist(current, "Display name set to " + current.displayName() + ".");
            });
        });

        set(12, SetupGui.control(plugin, template.effectiveIcon(), "Menu Icon", NamedTextColor.WHITE,
                        "The item shown for this arena in",
                        "the practice menus. Click to use",
                        "the item you are holding. Answer",
                        "auto to derive it from the kit.",
                        "")
                .line(SetupGui.state("Current", template.icon() != null
                        ? template.icon().name() : "auto"))
                .hint("select")
                .rightHint("prompt")
                .build(), event -> {
            if (busy()) {
                return;
            }
            if (event.isRightClick()) {
                click();
                promptThenReopen("Menu Icon", template.icon() != null ? template.icon().name() : "auto",
                        List.of("A material name, or auto to", "derive it from the kit."), answer -> {
                    ArenaTemplate current = plugin.templates().get(arena);
                    if (current == null || busy()) {
                        return;
                    }
                    if (answer.equalsIgnoreCase("auto")) {
                        current.setIcon(null);
                        persist(current, "Icon for " + arena + " back to automatic.");
                        return;
                    }
                    Material material = Material.matchMaterial(answer);
                    if (material == null || !material.isItem()) {
                        plugin.messages().problem(viewer, answer + " is not an item material.");
                        return;
                    }
                    current.setIcon(material);
                    persist(current, "Icon for " + arena + " set to " + material + ".");
                });
                return;
            }
            ItemStack held = viewer.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                deny();
                plugin.messages().note(viewer, "Hold the item you want as the icon, then click again.");
                return;
            }
            click();
            template.setIcon(held.getType());
            persist(template, "Icon for " + arena + " set to " + held.getType() + ".");
            refresh();
        });

        set(13, SetupGui.control(plugin, Material.TRIPWIRE_HOOK, "Permission", NamedTextColor.WHITE,
                        "The node that gates this arena.",
                        "Answer default to use the standard",
                        "per-arena node.",
                        "")
                .line(SetupGui.state("Current", plugin.templates().permissionFor(template)
                        + (template.permission() == null ? " (default)" : "")))
                .hint("edit")
                .build(), event -> {
            if (busy()) {
                return;
            }
            click();
            promptThenReopen("Permission", template.permission() != null ? template.permission() : "default",
                    List.of("The node that gates this arena,", "or default for the standard",
                            "per-arena node."), answer -> {
                ArenaTemplate current = plugin.templates().get(arena);
                if (current == null || busy()) {
                    return;
                }
                boolean reset = answer.equalsIgnoreCase("default") || answer.equalsIgnoreCase("none");
                current.setPermission(reset ? null : answer.trim());
                persist(current, arena + " is now governed by "
                        + plugin.templates().permissionFor(current)
                        + (reset ? " (this arena's default node)." : "."));
            });
        });

        boolean requireBlocks = template.requireBlocksForPb();
        set(14, SetupGui.control(plugin, Material.BRICKS, "PB Requires Blocks",
                        NamedTextColor.WHITE,
                        "Whether a run must place at least",
                        "one block to count as a PB.",
                        "")
                .line(SetupGui.state("Currently",
                        plugin.messages().name(requireBlocks ? "label.state.on" : "label.state.off")))
                .glow(requireBlocks)
                .hint("toggle")
                .build(), event -> {
            if (busy()) {
                return;
            }
            boolean require = !requireBlocks;
            sound(require ? "menu.toggle-on" : "menu.toggle-off");
            template.setRequireBlocksForPb(require);
            persist(template, require
                    ? "Personal bests on " + arena + " now require a placed block."
                    : "Personal bests on " + arena + " no longer require placed blocks.");
            refresh();
        });

        set(15, SetupGui.control(plugin, Material.BOOKSHELF, "Category", NamedTextColor.WHITE,
                        "The menu group this arena is listed",
                        "under. Its folder moves to",
                        "templates/<category>/ at once.",
                        "Answer default to group by mode.",
                        "")
                .line(SetupGui.state("Current", template.category() != null
                        ? template.category() : template.mode() + " (mode)"))
                .hint("edit")
                .build(), event -> {
            if (busy()) {
                return;
            }
            click();
            promptThenReopen("Category", template.category() != null ? template.category() : "default",
                    List.of("The menu group this arena is", "listed under, or default to",
                            "group by mode."), answer -> {
                ArenaTemplate current = plugin.templates().get(arena);
                if (current == null || busy()) {
                    return;
                }
                AdminCommands.setCategory(plugin, viewer, current, answer);
            });
        });

        Button mode = SetupGui.control(plugin, Material.LEVER, "Mode", NamedTextColor.WHITE,
                        "Which practice mode this arena",
                        "runs. Steps through every one",
                        "the plugin knows. Rush and bed",
                        "defense also need a team base.",
                        "")
                .line(SetupGui.state("Current", template.mode()));
        if (AdminCommands.usesBaseLayout(template.mode())) {
            boolean playable = RushMapData.parse(template).playable();
            mode.line(SetupGui.state("Base", Component.text(playable ? "set" : "not set",
                    playable ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
        set(16, mode.hint("cycle").build(), event -> {
            if (busy()) {
                return;
            }
            click();
            AdminCommands.setMode(plugin, viewer, template, nextMode(template.mode()));
            refresh();
        });

        boolean isDefault = template.name().equals(plugin.pcConfig().defaultArenaName());
        Button defaultArena = SetupGui.control(plugin, Material.LODESTONE, "Default Arena",
                        NamedTextColor.WHITE,
                        "Where a bare /practice join lands.",
                        "Written to default-arena.name in",
                        "config.yml.",
                        "")
                .line(SetupGui.state("Currently",
                        plugin.messages().name(isDefault ? "label.state.on" : "label.state.off")))
                .glow(isDefault);
        if (template.isComplete()) {
            defaultArena.hint("toggle");
        } else {
            defaultArena.disabled("gui.reason.incomplete");
        }
        set(19, defaultArena.build(), event -> {
            if (!template.isComplete()) {
                deny();
                return;
            }
            sound(isDefault ? "menu.toggle-off" : "menu.toggle-on");
            plugin.setConfigValue("default-arena.name", isDefault ? "" : template.name());
            plugin.messages().done(viewer, isDefault
                    ? "Default arena cleared."
                    : "Default arena set to " + template.name() + ".");
            refresh();
        });

        set(20, SetupGui.control(plugin, Material.PAPER, "Info", NamedTextColor.WHITE,
                        "Prints every setting of this",
                        "arena in chat.")
                .hint("view")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                AdminCommands.arenaInfo(plugin, viewer, template);
            });
        });

        set(25, SetupGui.control(plugin, Material.LAVA_BUCKET, "Delete", NamedTextColor.RED,
                        "Removes the arena folder, its",
                        "schematic, its leaderboard and",
                        "every recorded time on it. You",
                        "are asked to confirm first.")
                .hint("delete")
                .build(), event -> {
            if (busy()) {
                return;
            }
            click();
            later(() -> new ConfirmDeleteMenu(plugin, viewer, this, arena).open());
        });

        backButton(navSlot("admin.back", 0));
        closeButton(navSlot("admin.close", 8));
    }

    // -------------------------------------------------------------- helpers

    /**
     * The wizard owns an arena while it is open on it: its pending state
     * would silently overwrite anything changed here on save.
     */
    private boolean busy() {
        if (!arena.equals(plugin.setup().activeName())) {
            return false;
        }
        deny();
        plugin.messages().note(viewer, "Arena " + arena + " is open in the setup wizard. "
                + "Finish or cancel that first.");
        return true;
    }

    private void persist(ArenaTemplate template, String done) {
        try {
            template.save();
        } catch (IOException e) {
            plugin.messages().problem(viewer, "Could not write arena.yml: " + e.getMessage());
            return;
        }
        plugin.messages().done(viewer, done);
    }

    private String nextMode(String current) {
        List<String> ids = new ArrayList<>(plugin.modes().ids());
        int index = ids.indexOf(current);
        return ids.get((index + 1) % ids.size());
    }

    /** Opens an anvil prompt in place of this menu and reopens it once answered. */
    private void promptThenReopen(String title, String initial, List<String> hint,
                                  Consumer<String> action) {
        later(() -> {
            plugin.prompts().prompt(viewer, title, initial, hint, answer -> {
                action.accept(answer);
                if (viewer.isOnline()) {
                    open();
                }
            });
        });
    }
}
