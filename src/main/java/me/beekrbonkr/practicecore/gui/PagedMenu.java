package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A six-row menu whose entries flow across pages. Subclasses supply the list
 * and how one entry looks; everything else — paging, nav row, empty state —
 * is handled here.
 */
public abstract class PagedMenu<T> extends Menu {

    private int page;

    protected PagedMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onEntryClick(T entry, InventoryClickEvent event);

    /** Shown in the middle of the grid when there is nothing to list. */
    protected abstract ItemStack emptyIcon();

    /**
     * Hook for extra items on the nav row. Use {@link #footerSlot} so they
     * land on the four cells paging never touches (R43).
     */
    protected void renderFooter() {
    }

    /**
     * The nav-row cells free for a menu's own buttons: 0 and 1 from the left
     * (secondary), 2 and 3 from the right (primary).
     */
    protected int footerSlot(int index) {
        int[] columns = {1, 2, 7, 6};
        return bottomRow() + columns[Math.clamp(index, 0, 3)];
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        border();
        List<T> all = entries();
        int perPage = CONTENT_SLOTS.length;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);

        if (all.isEmpty()) {
            set(CONTENT_SLOTS[10], emptyIcon());
        } else {
            for (int i = 0; i < perPage; i++) {
                int index = page * perPage + i;
                if (index >= all.size()) {
                    break;
                }
                T entry = all.get(index);
                set(CONTENT_SLOTS[i], icon(entry), event -> onEntryClick(entry, event));
            }
        }

        if (page > 0) {
            set(navSlot("nav.previous", 3),
                    Button.of(plugin, plugin.guis().buttonMaterial("nav.previous", Material.SPECTRAL_ARROW))
                    .name("gui.previous-page")
                    .hint("open")
                    .build(), event -> {
                click();
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(navSlot("nav.next", 5),
                    Button.of(plugin, plugin.guis().buttonMaterial("nav.next", Material.SPECTRAL_ARROW))
                    .name("gui.next-page")
                    .hint("open")
                    .build(), event -> {
                click();
                page++;
                refresh();
            });
        }
        if (pages > 1) {
            set(navSlot("nav.page", 4),
                    Button.of(plugin, plugin.guis().buttonMaterial("nav.page", Material.MAP), page + 1)
                    .name("gui.page",
                            "page", String.valueOf(page + 1),
                            "pages", String.valueOf(pages))
                    .build());
        }
        backButton(navSlot("nav.back", 0));
        closeButton(navSlot("nav.close", 8));
        renderFooter();
    }

    protected void setFooter(int slot, ItemStack item) {
        set(slot, item);
    }
}
