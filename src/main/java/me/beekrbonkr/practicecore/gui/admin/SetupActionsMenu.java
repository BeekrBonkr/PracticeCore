package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Control panel for the open setup wizard: every wizard command as a button.
 * Text answers (display name, category, permission) go through a one-shot
 * chat prompt and the panel reopens afterwards. Text is fixed — see
 * {@link SetupGui}.
 */
final class SetupActionsMenu extends Menu {

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

        // STYLE-GUIDE: needs logic change (R59) — clearing every trigger is
        // destructive and runs on a single click with no confirmation.
        set(12, SetupGui.control(plugin, Material.STONE_BUTTON, "Finish Triggers",
                        NamedTextColor.WHITE,
                        "Buttons and pressure plates you",
                        "place in the arena finish a run.",
                        "Clicking removes them all at once.",
                        "")
                .line(SetupGui.state("Placed", String.valueOf(wizard().activeTriggerCount())))
                .hint("run")
                .build(), event -> {
            click();
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

        // STYLE-GUIDE: needs logic change (R59) — replaces the schematic on a
        // single click with no confirmation.
        set(14, SetupGui.control(plugin, Material.CARTOGRAPHY_TABLE, "Replace Schematic",
                        NamedTextColor.WHITE,
                        "Swaps the arena for whatever is on",
                        "your WorldEdit clipboard, at once.")
                .hint("run")
                .build(), event -> {
            click();
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

        boolean ready = wizard().activeReady();
        Button save = SetupGui.control(plugin, Material.EMERALD, "Save Arena", NamedTextColor.GREEN,
                "Writes everything to disk and",
                "makes the arena playable.");
        if (ready) {
            save.hint("save");
        } else {
            save.disabled(hasSpawn ? "gui.reason.needs-trigger" : "gui.reason.needs-spawn");
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

    private Material iconMaterial() {
        Material icon = wizard().activeIcon();
        return icon != null ? icon : Material.ITEM_FRAME;
    }

    private String nextMode() {
        List<String> ids = new ArrayList<>(plugin.modes().ids());
        int index = ids.indexOf(wizard().activeMode());
        return ids.get((index + 1) % ids.size());
    }

    private void promptThenReopen(String question, java.util.function.Consumer<String> action) {
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
