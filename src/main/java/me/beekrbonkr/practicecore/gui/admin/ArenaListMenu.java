package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.setup.SetupManager;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
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
 * Every arena, complete or not, for the admin to manage: left-click opens the
 * editor on it, right-click deletes it (behind a confirmation), and the anvil
 * starts a brand-new arena from the admin's WorldEdit clipboard. Layout and
 * text are fixed — see {@link SetupGui}.
 */
final class ArenaListMenu extends Menu {

    private static final int SLOT_CREATE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

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
        frame();
        List<ArenaTemplate> arenas = new ArrayList<>(plugin.templates().all());
        int perPage = CONTENT_SLOTS.length;
        int pages = Math.max(1, (arenas.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);

        if (arenas.isEmpty()) {
            set(CONTENT_SLOTS[10], SetupGui.button(Material.COBWEB,
                    "No arenas yet", NamedTextColor.RED,
                    "Copy a build with //copy, then",
                    "use the anvil below to create one."));
        }
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= arenas.size()) {
                break;
            }
            ArenaTemplate arena = arenas.get(index);
            set(CONTENT_SLOTS[i], arenaIcon(arena), event -> {
                if (event.isRightClick()) {
                    later(() -> new ConfirmDeleteMenu(plugin, viewer, this, arena.name()).open());
                } else {
                    click();
                    later(() -> edit(arena.name()));
                }
            });
        }

        set(SLOT_CREATE, SetupGui.button(Material.ANVIL,
                "Create New Arena", NamedTextColor.GREEN,
                "Select your build and run //copy first;",
                "you will be asked for a name in chat."), event -> {
            click();
            later(this::promptCreate);
        });
        if (page > 0) {
            set(SLOT_PREV, SetupGui.button(Material.SPECTRAL_ARROW,
                    "Previous Page", NamedTextColor.YELLOW), event -> {
                click();
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, SetupGui.button(Material.SPECTRAL_ARROW,
                    "Next Page", NamedTextColor.YELLOW), event -> {
                click();
                page++;
                refresh();
            });
        }
        if (pages > 1) {
            set(SLOT_PAGE, SetupGui.button(Material.MAP,
                    "Page " + (page + 1) + " of " + pages, NamedTextColor.WHITE));
        }
        set(SLOT_CLOSE, SetupGui.button(Material.BARRIER, "Close", NamedTextColor.RED),
                event -> {
                    click();
                    later(viewer::closeInventory);
                });
    }

    private ItemStack arenaIcon(ArenaTemplate arena) {
        return ItemBuilder.of(arena.effectiveIcon())
                .name(Component.text(arena.name(),
                        arena.isComplete() ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                        TextDecoration.BOLD))
                .lore(line("Display", arena.displayName()))
                .lore(line("Mode", arena.mode()))
                .lore(line("Category", arena.effectiveCategory()))
                .lore(line("Triggers", String.valueOf(arena.triggers().size())))
                .lore(line("Status", arena.isComplete() ? "complete" : "incomplete"))
                .lore(Component.empty())
                .lore(Component.text("Left-click to edit", NamedTextColor.YELLOW))
                .lore(Component.text("Right-click to delete", NamedTextColor.RED))
                .hideAttributes()
                .build();
    }

    private static Component line(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
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

    /** Hard-coded gray border — this menu ignores guis.yml on purpose. */
    private void frame() {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
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
