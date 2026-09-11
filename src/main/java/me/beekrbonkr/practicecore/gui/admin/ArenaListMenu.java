package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.config.ReloadResult;
import me.beekrbonkr.practicecore.gui.BedDefenseModerationMenu;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.rush.MBedwarsHook;
import me.beekrbonkr.practicecore.setup.SetupManager;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Every arena, complete or not, for the admin to manage: click opens the
 * arena's options, right-click deletes it (behind a confirmation), and the
 * crafting table starts a brand-new arena from the admin's WorldEdit
 * clipboard. The footer reaches the rest of the admin's day: bed defense
 * moderation, MBedwars imports and a reload. Text is fixed — see
 * {@link SetupGui}.
 */
final class ArenaListMenu extends Menu {

    private int page;

    ArenaListMenu(PracticeCorePlugin plugin, Player viewer) {
        super(plugin, viewer, null);
    }

    @Override
    protected Component title() {
        return SetupGui.title("Arena Setup");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        border();
        List<ArenaTemplate> arenas = new ArrayList<>(plugin.templates().all());
        int perPage = CONTENT_SLOTS.length;
        int pages = Math.max(1, (arenas.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);

        if (arenas.isEmpty()) {
            set(CONTENT_SLOTS[10], Button.of(plugin, emptyMaterial())
                    .name(Component.text("No arenas yet", NamedTextColor.GRAY, TextDecoration.BOLD))
                    .line(SetupGui.gray("Nothing has been set up on this server."))
                    .line(SetupGui.gray("Copy a build with //copy, then Create New Arena."))
                    .build());
        }
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= arenas.size()) {
                break;
            }
            ArenaTemplate arena = arenas.get(index);
            set(CONTENT_SLOTS[i], arenaIcon(arena), event -> {
                if (event.isRightClick()) {
                    click();
                    later(() -> new ConfirmDeleteMenu(plugin, viewer, this, arena.name()).open());
                } else {
                    click();
                    later(() -> new ArenaOptionsMenu(plugin, viewer, this, arena.name()).open());
                }
            });
        }

        // Footer extras sit on the cells paging never touches (R43):
        // secondary from the left, the primary action at the right end.
        bedDefensesButton(bottomRow() + 1);
        if (MBedwarsHook.available()) {
            importButton(bottomRow() + 2);
        }
        reloadButton(bottomRow() + 6);
        set(bottomRow() + 7, SetupGui.control(plugin, Material.CRAFTING_TABLE,
                        "Create New Arena", NamedTextColor.GREEN,
                        "Starts a new arena from your",
                        "WorldEdit clipboard. You will be",
                        "asked for a name.")
                .hint("run")
                .build(), event -> {
            click();
            later(this::promptCreate);
        });
        if (page > 0) {
            set(navSlot("nav.previous", 3), SetupGui.control(plugin, Material.SPECTRAL_ARROW,
                    "Previous Page", NamedTextColor.YELLOW).hint("open").build(), event -> {
                click();
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(navSlot("nav.next", 5), SetupGui.control(plugin, Material.SPECTRAL_ARROW,
                    "Next Page", NamedTextColor.YELLOW).hint("open").build(), event -> {
                click();
                page++;
                refresh();
            });
        }
        if (pages > 1) {
            set(navSlot("nav.page", 4), Button.of(plugin, Material.MAP, page + 1)
                    .name(Component.text("Page " + (page + 1), NamedTextColor.WHITE, TextDecoration.BOLD)
                            .append(Component.text(" of " + pages, NamedTextColor.GRAY)
                                    .decoration(TextDecoration.BOLD, false)))
                    .build());
        }
        closeButton(navSlot("admin.close", 8));
    }

    private ItemStack arenaIcon(ArenaTemplate arena) {
        boolean complete = arena.isComplete();
        Button tile = Button.of(plugin, arena.effectiveIcon())
                .name(Component.text(arena.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .line(SetupGui.state("Display", arena.displayName()))
                .line(SetupGui.state("Mode", arena.mode()))
                .line(SetupGui.state("Category", arena.effectiveCategory()))
                .line(SetupGui.state("Triggers", String.valueOf(arena.triggers().size())))
                .line(SetupGui.state("Status", Component.text(
                        complete ? "complete" : "incomplete",
                        complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        if (arena.name().equals(plugin.pcConfig().defaultArenaName())) {
            tile.line(SetupGui.state("Default", Component.text("yes", NamedTextColor.GREEN)));
        }
        return tile.hint("open")
                .rightHint("delete")
                .hideAttributes()
                .build();
    }

    // --------------------------------------------------------------- footer

    /** The bed defense moderation list — locked, not hidden, without the node (R52, R53). */
    private void bedDefensesButton(int slot) {
        boolean moderator = viewer.hasPermission(BedDefenseService.MODERATE_PERMISSION);
        Button button = SetupGui.control(plugin, Material.LECTERN, "Bed Defenses",
                NamedTextColor.WHITE,
                "Every published bed defense and",
                "its reports, for moderation.");
        if (moderator) {
            button.hint("open");
        } else {
            button.locked("gui.reason.needs-node", "node", BedDefenseService.MODERATE_PERMISSION);
        }
        set(slot, button.build(), event -> {
            if (!moderator) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseModerationMenu(plugin, viewer, this).open());
        });
    }

    /** Only drawn while MBedwars is present — there is nothing to import otherwise. */
    private void importButton(int slot) {
        set(slot, SetupGui.control(plugin, Material.HOPPER, "Import Maps", NamedTextColor.WHITE,
                        "Pulls maps out of MBedwars as rush",
                        "arenas or bed defense maps.")
                .hint("open")
                .build(), event -> {
            click();
            later(() -> new ImportMenu(plugin, viewer, this).open());
        });
    }

    /**
     * The GUI twin of /practice reload. It takes two clicks (R59): a reload
     * that finds arena changes ends every running session. It never forces —
     * a reload that needs confirming is pointed at the command instead.
     */
    private void reloadButton(int slot) {
        boolean allowed = viewer.hasPermission("practicecore.reload");
        Button button;
        if (isArmed(slot)) {
            button = SetupGui.control(plugin, Material.COMMAND_BLOCK, "Confirm Reload",
                            NamedTextColor.RED,
                            "Config, messages and every arena",
                            "are re-read from disk.")
                    .line(Component.text("Running sessions may be ended.", NamedTextColor.RED))
                    .line(Component.text("Any other click cancels", NamedTextColor.YELLOW))
                    .glow(true)
                    .hint("confirm");
        } else {
            button = SetupGui.control(plugin, Material.COMMAND_BLOCK, "Reload",
                    NamedTextColor.YELLOW,
                    "Re-reads config.yml, messages.yml,",
                    "guis.yml and every arena from disk.");
            if (allowed) {
                button.hint("run");
            } else {
                button.locked("gui.reason.needs-node", "node", "practicecore.reload");
            }
        }
        set(slot, button.build(), event -> {
            if (!allowed) {
                deny();
                return;
            }
            if (!isArmed(slot)) {
                arm(slot);
                return;
            }
            disarm();
            ReloadResult result = plugin.reload(false);
            result.notes().forEach(note -> plugin.messages().note(viewer, note));
            if (result.ok()) {
                plugin.messages().done(viewer, "PracticeCore reloaded.");
            } else if (result.needsConfirm()) {
                plugin.messages().confirmPrompt(viewer,
                        "Nothing was changed yet — this reload needs confirming.",
                        "/practice reload confirm");
            } else {
                plugin.messages().problem(viewer,
                        "Reload failed. The previous settings are still running.");
            }
            later(this::refresh);
        });
    }

    // -------------------------------------------------------------- wizard

    private void promptCreate() {
        plugin.prompts().prompt(viewer, "New Arena", "",
                List.of("The arena's id, used in commands", "and folder names.", "",
                        "Letters, digits, - and _ only."),
                name -> {
                    plugin.setup().start(viewer, SetupManager.normalize(name));
                    openActionsWhenReady(plugin, viewer);
                });
    }

    /**
     * start/edit report their own failures in chat and only hold a session on
     * success — so the panel opens exactly when the wizard actually opened.
     * The delay outlasts the wizard's async teleport, which would close any
     * inventory opened before it lands. Shared with the arena options menu.
     */
    static void openActionsWhenReady(PracticeCorePlugin plugin, Player viewer) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline() && plugin.setup().isAdmin(viewer.getUniqueId())) {
                new SetupActionsMenu(plugin, viewer).open();
            }
        }, 10L);
    }
}
