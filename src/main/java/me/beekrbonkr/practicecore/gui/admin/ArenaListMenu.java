package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
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
 * editor on it, right-click deletes it (behind a confirmation), and the
 * crafting table starts a brand-new arena from the admin's WorldEdit
 * clipboard. Text is fixed — see {@link SetupGui}.
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
                    later(() -> edit(arena.name()));
                }
            });
        }

        // The primary action sits at the right end of the nav row (R43).
        set(bottomRow() + 7, SetupGui.control(plugin, Material.CRAFTING_TABLE,
                        "Create New Arena", NamedTextColor.GREEN,
                        "Starts a new arena from your",
                        "WorldEdit clipboard. You will be",
                        "asked for a name in chat.")
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
        return Button.of(plugin, arena.effectiveIcon())
                .name(Component.text(arena.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .line(SetupGui.state("Display", arena.displayName()))
                .line(SetupGui.state("Mode", arena.mode()))
                .line(SetupGui.state("Category", arena.effectiveCategory()))
                .line(SetupGui.state("Triggers", String.valueOf(arena.triggers().size())))
                .line(SetupGui.state("Status", Component.text(
                        complete ? "complete" : "incomplete",
                        complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW)))
                .hint("edit")
                .rightHint("delete")
                .hideAttributes()
                .build();
    }

    /** Opens the wizard on an arena, then its control panel once it is up. */
    private void edit(String name) {
        viewer.closeInventory();
        plugin.setup().edit(viewer, name);
        openActionsWhenReady();
    }

    private void promptCreate() {
        viewer.closeInventory();
        plugin.prompts().prompt(viewer, "What should the new arena be called? (a-z, 0-9, - and _)",
                name -> {
                    plugin.setup().start(viewer, SetupManager.normalize(name));
                    openActionsWhenReady();
                });
    }

    /**
     * start/edit report their own failures in chat and only hold a session on
     * success — so the panel opens exactly when the wizard actually opened.
     * The delay outlasts the wizard's async teleport, which would close any
     * inventory opened before it lands.
     */
    private void openActionsWhenReady() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline() && plugin.setup().isAdmin(viewer.getUniqueId())) {
                new SetupActionsMenu(plugin, viewer).open();
            }
        }, 10L);
    }
}
