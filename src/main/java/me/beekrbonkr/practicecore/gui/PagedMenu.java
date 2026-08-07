package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
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

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private int page;

    protected PagedMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onEntryClick(T entry, InventoryClickEvent event);

    /** Shown in the middle of the grid when there is nothing to list. */
    protected abstract ItemStack emptyIcon();

    /** Hook for extra items in the nav row (slots 46, 47, 51, 52). */
    protected void renderFooter() {
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
            set(SLOT_PREV, ItemBuilder.of(Material.SPECTRAL_ARROW)
                    .name(name("gui.previous-page"))
                    .build(), event -> {
                click();
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, ItemBuilder.of(Material.SPECTRAL_ARROW)
                    .name(name("gui.next-page"))
                    .build(), event -> {
                click();
                page++;
                refresh();
            });
        }
        if (pages > 1) {
            set(SLOT_PAGE, ItemBuilder.of(Material.MAP, page + 1)
                    .name(name("gui.page",
                            "page", String.valueOf(page + 1),
                            "pages", String.valueOf(pages)))
                    .build());
        }
        backButton(SLOT_BACK);
        closeButton(SLOT_CLOSE);
        renderFooter();
    }

    protected void setFooter(int slot, ItemStack item) {
        set(slot, item);
    }
}
