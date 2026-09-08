package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.mode.Mode;
import me.beekrbonkr.practicecore.mode.PvpBotMode;
import me.beekrbonkr.practicecore.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Control panel for the open setup wizard: every wizard command as a button.
 * Text answers (display name, category, permission, team color) go through
 * a one-shot chat prompt and the panel reopens afterwards. The third row is
 * mode-aware: rush and bed defense arenas get the team-base layout buttons,
 * PvP bot arenas the bot spawn marker. Text is fixed — see {@link SetupGui}.
 */
final class SetupActionsMenu extends Menu {

    /** The team colors a base layout knows, as /practice setup rush team accepts them. */
    private static final List<String> TEAM_COLORS = List.of(
            "red", "blue", "green", "yellow", "aqua", "white", "pink", "gray");
    private static final String TEAM_COLOR_LIST = "red, blue, green, yellow, aqua, white, pink or gray";

    private static final Component CANNOT_UNDO =
            Component.text("This cannot be undone.", NamedTextColor.RED);
    private static final Component ANY_OTHER_CLICK =
            Component.text("Any other click cancels", NamedTextColor.YELLOW);

    SetupActionsMenu(PracticeCorePlugin plugin, Player viewer) {
        super(plugin, viewer, null);
    }

    private SetupManager wizard() {
        return plugin.setup();
    }

    @Override
    protected Component title() {
        return SetupGui.title((wizard().activeEditing() ? "Editing — " : "Creating — ")
                + String.valueOf(wizard().activeName()));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void render() {
        // The wizard may have been closed under us (save, cancel, /reload).
        if (!wizard().isAdmin(viewer.getUniqueId())) {
            later(viewer::closeInventory);
            return;
        }
        border();

        boolean hasSpawn = wizard().activeHasSpawn();
        set(10, SetupGui.control(plugin, Material.ENDER_PEARL, "Set Spawn Here",
                        NamedTextColor.WHITE,
                        "Uses the exact spot and direction",
                        "you are standing in right now.",
                        "")
                .line(SetupGui.state("Spawn", Component.text(
                        hasSpawn ? "set" : "not set",
                        hasSpawn ? NamedTextColor.GREEN : NamedTextColor.RED)))
                .hint("run")
                .build(), event -> {
            click();
            wizard().setSpawn(viewer);
            refresh();
        });

        set(11, SetupGui.control(plugin, Material.CHEST, "Kit", NamedTextColor.WHITE,
                        "Saves your current inventory as",
                        "this arena's kit, or loads the",
                        "saved kit back so you can tweak it.",
                        "")
                .line(SetupGui.state("Saved stacks", String.valueOf(wizard().activeKitSize())))
                .hint("save")
                .rightHint("load")
                .build(), event -> {
            click();
            if (event.isRightClick()) {
                wizard().loadKit(viewer);
            } else {
                wizard().saveKit(viewer);
            }
            refresh();
        });

        // Clearing every trigger is destructive, so it takes two clicks (R56, R59).
        int triggersSlot = 12;
        Button triggers = isArmed(triggersSlot)
                ? armed(Material.STONE_BUTTON, "Confirm Clear",
                        "Every finish trigger in this arena",
                        "is removed.")
                : SetupGui.control(plugin, Material.STONE_BUTTON, "Finish Triggers",
                        NamedTextColor.WHITE,
                        "Buttons and pressure plates you",
                        "place in the arena finish a run.",
                        "Clicking removes them all at once.",
                        "")
                .line(SetupGui.state("Placed", String.valueOf(wizard().activeTriggerCount())))
                .hint("run");
        set(triggersSlot, triggers.build(), event -> {
            if (!isArmed(triggersSlot)) {
                arm(triggersSlot);
                return;
            }
            disarm();
            wizard().clearTriggers(viewer);
            refresh();
        });

        set(13, SetupGui.control(plugin, Material.STRUCTURE_BLOCK, "Capture Blocks",
                        NamedTextColor.WHITE,
                        "Bakes the arena as it stands in",
                        "the world into the schematic.")
                .hint("run")
                .build(), event -> {
            click();
            wizard().capture(viewer);
            refresh();
        });

        // Replacing the schematic throws the current build away, so it takes
        // two clicks as well (R56, R59).
        int schematicSlot = 14;
        Button schematic = isArmed(schematicSlot)
                ? armed(Material.CARTOGRAPHY_TABLE, "Confirm Replace",
                        "The current build is replaced by",
                        "your clipboard and its triggers",
                        "are cleared.")
                : SetupGui.control(plugin, Material.CARTOGRAPHY_TABLE, "Replace Schematic",
                        NamedTextColor.WHITE,
                        "Swaps the arena for whatever is on",
                        "your WorldEdit clipboard, at once.")
                .hint("run");
        set(schematicSlot, schematic.build(), event -> {
            if (!isArmed(schematicSlot)) {
                arm(schematicSlot);
                return;
            }
            disarm();
            wizard().replaceSchematic(viewer);
            refresh();
        });

        set(15, SetupGui.control(plugin, iconMaterial(), "Menu Icon", NamedTextColor.WHITE,
                        "The item shown for this arena in",
                        "the practice menus. Click to use",
                        "the item you are holding.")
                .hint("select")
                .rightHint("prompt")
                .build(), event -> {
            click();
            if (event.isRightClick()) {
                promptThenReopen("Which material should the icon be?", answer -> {
                    Material material = Material.matchMaterial(answer);
                    if (material != null && material.isItem()) {
                        wizard().setIcon(viewer, material);
                    } else {
                        plugin.messages().problem(viewer, answer + " is not an item material.");
                    }
                });
            } else {
                wizard().setIcon(viewer, null); // falls back to the held item
                refresh();
            }
        });

        set(16, SetupGui.control(plugin, Material.LEVER, "Mode", NamedTextColor.WHITE,
                        "Which practice mode this arena",
                        "runs. Steps through every one",
                        "the plugin knows.",
                        "")
                .line(SetupGui.state("Current", wizard().activeMode()))
                .hint("cycle")
                .build(), event -> {
            click();
            wizard().setMode(viewer, nextMode());
            refresh();
        });

        set(19, SetupGui.control(plugin, Material.NAME_TAG, "Display Name", NamedTextColor.WHITE,
                        "The name players see in menus.",
                        "")
                .line(SetupGui.state("Current", wizard().activeDisplayName()))
                .hint("rename")
                .build(), event -> {
            click();
            promptThenReopen("What should the display name be?",
                    answer -> wizard().setDisplayName(viewer, answer));
        });

        set(20, SetupGui.control(plugin, Material.BOOKSHELF, "Category", NamedTextColor.WHITE,
                        "The menu group this arena is listed",
                        "under. Saving moves its folder to",
                        "templates/<category>/. Answer",
                        "default to group by mode.",
                        "")
                .line(SetupGui.state("Current", wizard().activeCategory() != null
                        ? wizard().activeCategory() : wizard().activeMode() + " (mode)"))
                .hint("edit")
                .build(), event -> {
            click();
            promptThenReopen("Which category should this arena be in?",
                    answer -> wizard().setCategory(viewer, answer));
        });

        set(21, SetupGui.control(plugin, Material.TRIPWIRE_HOOK, "Permission", NamedTextColor.WHITE,
                        "The node that gates this arena.",
                        "Answer default to use the standard",
                        "per-arena node.",
                        "")
                .line(SetupGui.state("Current", wizard().activePermission() != null
                        ? wizard().activePermission() : "arena default"))
                .hint("edit")
                .build(), event -> {
            click();
            promptThenReopen("Which permission node should gate this arena?",
                    answer -> wizard().setPermission(viewer, answer));
        });

        boolean requireBlocks = wizard().activeRequireBlocks();
        set(22, SetupGui.control(plugin, Material.BRICKS, "PB Requires Blocks",
                        NamedTextColor.WHITE,
                        "Whether a run must place at least",
                        "one block to count as a PB.",
                        "")
                .line(SetupGui.state("Currently",
                        plugin.messages().name(requireBlocks ? "label.state.on" : "label.state.off")))
                .glow(requireBlocks)
                .hint("toggle")
                .build(), event -> {
            click();
            wizard().setRequireBlocks(viewer, !requireBlocks);
            refresh();
        });

        set(23, SetupGui.control(plugin, Material.PAPER, "Info", NamedTextColor.WHITE,
                        "Prints the full setup state",
                        "in chat.")
                .hint("view")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                wizard().info(viewer);
            });
        });

        renderLayoutRow();

        boolean ready = wizard().activeReady();
        Button save = SetupGui.control(plugin, Material.EMERALD, "Save Arena", NamedTextColor.GREEN,
                "Writes everything to disk and",
                "makes the arena playable.");
        if (ready) {
            save.hint("save");
        } else {
            save.disabled(saveReason(hasSpawn));
        }
        set(39, save.build(), event -> {
            if (!ready) {
                deny(); // saving would only fail — keep the panel open
                return;
            }
            click();
            later(() -> {
                viewer.closeInventory();
                wizard().save(viewer);
            });
        });

        set(41, SetupGui.control(plugin, Material.RED_DYE, "Cancel Setup", NamedTextColor.RED,
                        "Closes the wizard without saving.",
                        wizard().activeEditing()
                                ? "The saved arena stays unchanged."
                                : "The new arena is discarded.")
                .hint("cancel")
                .build(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                wizard().cancel(viewer);
            });
        });

        set(navSlot("admin.close", 8), Button.of(plugin,
                        plugin.guis().buttonMaterial("nav.close", Material.BARRIER))
                .name("gui.close")
                .lore("gui.close-lore")
                .line(SetupGui.gray("The wizard stays open; reopen it"))
                .line(SetupGui.gray("with /practice setup gui."))
                .hint("close")
                .build(), event -> {
            click();
            later(viewer::closeInventory);
        });
    }

    // ------------------------------------------------------- mode-aware row

    /** Row 3 (slots 28-34): the layout steps only this arena's mode needs. */
    private void renderLayoutRow() {
        if (wizard().activeUsesBaseLayout()) {
            renderBaseLayout();
        } else if (PvpBotMode.ID.equals(wizard().activeMode())) {
            renderBotLayout();
        }
    }

    /** Rush and bed defense share one layout: team spawns, beds, generators, dealers. */
    private void renderBaseLayout() {
        int bases = wizard().activeBaseCount();
        int teams = wizard().activeTeamCount();
        Component base = Component.text(bases + " playable of " + teams + " set",
                bases > 0 ? NamedTextColor.GREEN : NamedTextColor.RED);

        set(28, SetupGui.control(plugin, Material.RESPAWN_ANCHOR, "Team Spawn Here",
                        NamedTextColor.WHITE,
                        "Marks a team's spawn where you",
                        "stand. You are asked for the team",
                        "color in chat.",
                        "")
                .line(SetupGui.state("Bases", base))
                .hint("run")
                .build(), event -> {
            click();
            promptTeam("Which team spawns here? (" + TEAM_COLOR_LIST + ")",
                    color -> wizard().rushTeamSpawn(viewer, color));
        });

        set(29, SetupGui.control(plugin, Material.RED_BED, "Bed Here", NamedTextColor.WHITE,
                        "Marks the bed you are looking at",
                        "as a team's target bed. Keep",
                        "looking at it while you answer.",
                        "")
                .line(SetupGui.state("Bases", base))
                .hint("run")
                .build(), event -> {
            click();
            promptTeam("Which team's bed is this? (" + TEAM_COLOR_LIST + ")",
                    color -> wizard().rushBed(viewer, color));
        });

        set(30, SetupGui.control(plugin, Material.IRON_INGOT, "Generator Here",
                        NamedTextColor.WHITE,
                        "Marks a resource generator on the",
                        "block you stand on. You are asked",
                        "for the type in chat.",
                        "")
                .line(SetupGui.state("Generators",
                        Component.text(wizard().activeGeneratorCount(), NamedTextColor.WHITE)))
                .hint("run")
                .build(), event -> {
            click();
            promptThenReopen("Which generator is this? (iron, gold, diamond or emerald)",
                    answer -> wizard().rushGenerator(viewer, answer));
        });

        set(31, SetupGui.control(plugin, Material.VILLAGER_SPAWN_EGG, "Dealer Here",
                        NamedTextColor.WHITE,
                        "Marks a shop dealer spot where you",
                        "stand, facing your way.",
                        "")
                .line(SetupGui.state("Dealers",
                        Component.text(wizard().activeDealerCount(), NamedTextColor.WHITE)))
                .hint("run")
                .build(), event -> {
            click();
            wizard().rushDealer(viewer);
            refresh();
        });

        int clearSlot = 32;
        Button clear = isArmed(clearSlot)
                ? armed(Material.RED_DYE, "Confirm Clear",
                        "Every team spawn, bed, generator",
                        "and dealer spot is removed.")
                : SetupGui.control(plugin, Material.RED_DYE, "Clear Layout", NamedTextColor.RED,
                        "Removes every team spawn, bed,",
                        "generator and dealer spot so you",
                        "can lay the map out again.")
                .hint("run");
        set(clearSlot, clear.build(), event -> {
            if (!isArmed(clearSlot)) {
                arm(clearSlot);
                return;
            }
            disarm();
            wizard().rushClear(viewer);
            refresh();
        });
    }

    /** The PvP bot layout: one optional marker for where the bot appears. */
    private void renderBotLayout() {
        set(28, SetupGui.control(plugin, Material.ZOMBIE_HEAD, "Bot Spawn Here",
                        NamedTextColor.WHITE,
                        "Marks where the PvP bot spawns,",
                        "at your feet facing your way.",
                        "Without one it spawns a few blocks",
                        "ahead of the player spawn.",
                        "")
                .line(SetupGui.state("Bot spawn", Component.text(
                        wizard().activeHasBotSpawn() ? "set" : "auto",
                        wizard().activeHasBotSpawn() ? NamedTextColor.GREEN : NamedTextColor.GRAY)))
                .hint("run")
                .build(), event -> {
            click();
            wizard().pvpBotSpawn(viewer);
            refresh();
        });

        set(29, SetupGui.control(plugin, Material.RED_DYE, "Clear Bot Spawn", NamedTextColor.RED,
                        "Removes the bot spawn marker so",
                        "the bot spawns ahead of the player",
                        "spawn again.")
                .hint("run")
                .build(), event -> {
            click();
            wizard().pvpBotClear(viewer);
            refresh();
        });
    }

    // -------------------------------------------------------------- helpers

    /**
     * The armed look of a two-click control (R56): same slot, glow, a red
     * "Confirm …" name, the consequence, and the confirm hint.
     */
    private Button armed(Material material, String name, String... consequence) {
        return SetupGui.control(plugin, material, name, NamedTextColor.RED, consequence)
                .line(CANNOT_UNDO)
                .line(ANY_OTHER_CLICK)
                .glow(true)
                .hint("confirm");
    }

    /**
     * Why Save is unavailable, in the order the wizard itself checks: spawn,
     * then the finish trigger (for modes that use one), then the team base.
     */
    private String saveReason(boolean hasSpawn) {
        if (!hasSpawn) {
            return "gui.reason.needs-spawn";
        }
        boolean needsTrigger = plugin.modes().get(wizard().activeMode())
                .map(Mode::requiresTrigger).orElse(true);
        if (needsTrigger && wizard().activeTriggerCount() == 0) {
            return "gui.reason.needs-trigger";
        }
        if (wizard().activeUsesBaseLayout() && !wizard().activeHasBase()) {
            return "gui.reason.needs-base";
        }
        return "gui.reason.needs-trigger";
    }

    private Material iconMaterial() {
        Material icon = wizard().activeIcon();
        return icon != null ? icon : Material.ITEM_FRAME;
    }

    private String nextMode() {
        List<String> ids = new ArrayList<>(plugin.modes().ids());
        int index = ids.indexOf(wizard().activeMode());
        return ids.get((index + 1) % ids.size());
    }

    /** Asks for a team color and only hands a known one on to the wizard. */
    private void promptTeam(String question, Consumer<String> action) {
        promptThenReopen(question, answer -> {
            String color = answer.trim().toLowerCase(Locale.ROOT);
            if (!TEAM_COLORS.contains(color)) {
                plugin.messages().problem(viewer, answer + " is not a team color. Use "
                        + TEAM_COLOR_LIST + ".");
                return;
            }
            action.accept(color);
        });
    }

    private void promptThenReopen(String question, Consumer<String> action) {
        later(() -> {
            viewer.closeInventory();
            plugin.prompts().prompt(viewer, question, answer -> {
                action.accept(answer);
                if (viewer.isOnline() && wizard().isAdmin(viewer.getUniqueId())) {
                    new SetupActionsMenu(plugin, viewer).open();
                }
            });
        });
    }
}
