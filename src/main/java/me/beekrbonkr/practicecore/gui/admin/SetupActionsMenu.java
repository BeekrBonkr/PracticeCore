package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Control panel for the open setup wizard: every wizard command as a button.
 * Text answers (display name, category, permission) go through a one-shot
 * chat prompt and the panel reopens afterwards. Layout and text are fixed —
 * see {@link SetupGui}.
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
        return SetupGui.title((wizard().activeEditing() ? "Editing: " : "Creating: ")
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
        frame();

        set(10, SetupGui.button(Material.ENDER_PEARL, "Set Spawn Here",
                        NamedTextColor.GREEN,
                        "Uses the exact spot and direction",
                        "you are standing in right now.",
                        "", wizard().activeHasSpawn() ? "Spawn: set" : "Spawn: NOT SET"),
                event -> {
                    click();
                    wizard().setSpawn(viewer);
                    refresh();
                });

        set(11, SetupGui.button(Material.CHEST, "Kit", NamedTextColor.GREEN,
                        "Left-click: save your current",
                        "inventory as this arena's kit.",
                        "Right-click: load the saved kit",
                        "into your inventory to tweak it.",
                        "", "Saved stacks: " + wizard().activeKitSize()),
                event -> {
                    click();
                    if (event.isRightClick()) {
                        wizard().loadKit(viewer);
                    } else {
                        wizard().saveKit(viewer);
                    }
                    refresh();
                });

        set(12, SetupGui.button(Material.STONE_BUTTON, "Finish Triggers",
                        NamedTextColor.GREEN,
                        "Place buttons or pressure plates",
                        "in the arena to add finishes —",
                        "as many as you like.",
                        "Click here to remove them all.",
                        "", "Placed: " + wizard().activeTriggerCount()),
                event -> {
                    click();
                    wizard().clearTriggers(viewer);
                    refresh();
                });

        set(13, SetupGui.button(Material.STRUCTURE_BLOCK, "Capture Blocks",
                        NamedTextColor.GREEN,
                        "Bakes the arena as it stands in",
                        "the world into the schematic."),
                event -> {
                    click();
                    wizard().capture(viewer);
                    refresh();
                });

        set(14, SetupGui.button(Material.PAPER, "Replace Schematic",
                        NamedTextColor.GREEN,
                        "Swaps the arena for whatever is",
                        "on your WorldEdit clipboard."),
                event -> {
                    click();
                    wizard().replaceSchematic(viewer);
                    refresh();
                });

        set(15, SetupGui.button(iconMaterial(), "Menu Icon", NamedTextColor.GREEN,
                        "Left-click: use the item you are",
                        "holding as this arena's icon.",
                        "Right-click: type a material name."),
                event -> {
                    click();
                    if (event.isRightClick()) {
                        promptThenReopen("Which material should the icon be?", answer -> {
                            Material material = Material.matchMaterial(answer);
                            if (material != null && material.isItem()) {
                                wizard().setIcon(viewer, material);
                            } else {
                                viewer.sendMessage(Component.text(
                                        "'" + answer + "' is not an item material.",
                                        NamedTextColor.RED));
                            }
                        });
                    } else {
                        wizard().setIcon(viewer, null); // falls back to the held item
                        refresh();
                    }
                });

        set(16, SetupGui.button(Material.COMPARATOR, "Mode", NamedTextColor.GREEN,
                        "Click to cycle through the",
                        "registered practice modes.",
                        "", "Current: " + wizard().activeMode()),
                event -> {
                    click();
                    wizard().setMode(viewer, nextMode());
                    refresh();
                });

        set(19, SetupGui.button(Material.NAME_TAG, "Display Name", NamedTextColor.AQUA,
                        "The pretty name players see.",
                        "", "Current: " + wizard().activeDisplayName()),
                event -> {
                    click();
                    promptThenReopen("What should the display name be?",
                            answer -> wizard().setDisplayName(viewer, answer));
                });

        set(20, SetupGui.button(Material.BOOKSHELF, "Category", NamedTextColor.AQUA,
                        "Which menu group this arena",
                        "is listed under.",
                        "Answer 'default' to group by mode.",
                        "", "Current: " + (wizard().activeCategory() != null
                                ? wizard().activeCategory() : wizard().activeMode() + " (mode)")),
                event -> {
                    click();
                    promptThenReopen("Which category should this arena be in?",
                            answer -> wizard().setCategory(viewer, answer));
                });

        set(21, SetupGui.button(Material.IRON_BARS, "Permission", NamedTextColor.AQUA,
                        "The node that gates this arena.",
                        "Answer 'default' to use the",
                        "standard per-arena node.",
                        "", "Current: " + (wizard().activePermission() != null
                                ? wizard().activePermission() : "arena default")),
                event -> {
                    click();
                    promptThenReopen("Which permission node should gate this arena?",
                            answer -> wizard().setPermission(viewer, answer));
                });

        set(22, SetupGui.button(Material.BRICKS, "PB Requires Blocks",
                        NamedTextColor.AQUA,
                        "Whether a run must place at least",
                        "one block to count as a PB.",
                        "", "Current: " + (wizard().activeRequireBlocks() ? "yes" : "no")),
                event -> {
                    click();
                    wizard().setRequireBlocks(viewer, !wizard().activeRequireBlocks());
                    refresh();
                });

        set(23, SetupGui.button(Material.BOOK, "Info", NamedTextColor.AQUA,
                        "Prints the full setup state",
                        "in chat."),
                event -> {
                    click();
                    later(() -> {
                        viewer.closeInventory();
                        wizard().info(viewer);
                    });
                });

        boolean ready = wizard().activeReady();
        set(39, SetupGui.button(ready ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE,
                        "Save Arena", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                        ready ? "Writes everything to disk and"
                              : "Needs a spawn (and a finish trigger",
                        ready ? "makes the arena playable."
                              : "for trigger modes) before saving."),
                event -> {
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

        set(41, SetupGui.button(Material.TNT, "Cancel Setup", NamedTextColor.RED,
                        "Closes the wizard without saving.",
                        wizard().activeEditing()
                                ? "The saved arena stays unchanged."
                                : "The new arena is discarded."),
                event -> {
                    click();
                    later(() -> {
                        viewer.closeInventory();
                        wizard().cancel(viewer);
                    });
                });

        set(44, SetupGui.button(Material.BARRIER, "Close", NamedTextColor.RED,
                        "Keeps the wizard open —",
                        "reopen with /practice setup gui."),
                event -> {
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

    /** Hard-coded gray border — this menu ignores guis.yml on purpose. */
    private void frame() {
        ItemStack filler = me.beekrbonkr.practicecore.util.ItemBuilder
                .of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        int size = rows() * 9;
        for (int slot = 0; slot < size; slot++) {
            int column = slot % 9;
            if (slot < 9 || slot >= size - 9 || column == 0 || column == 8) {
                set(slot, filler);
            }
        }
    }
}
