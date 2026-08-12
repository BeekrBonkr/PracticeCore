package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base for every PracticeCore menu.
 *
 * The menu instance is the inventory's {@link InventoryHolder}, so click
 * routing is an {@code instanceof} check — no per-player bookkeeping to leak
 * and no chance of a stale mapping firing on the wrong inventory.
 */
public abstract class Menu implements InventoryHolder {

    /** Slots inside a 6-row menu's one-block border: 28 usable cells. */
    protected static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    protected final PracticeCorePlugin plugin;
    protected final Player viewer;
    private final Menu parent;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.parent = parent;
    }

    protected abstract Component title();

    protected abstract int rows();

    /** Populates the inventory. Called on every open and refresh. */
    protected abstract void render();

    public void open() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows() * 9, title());
        }
        redraw();
        viewer.openInventory(inventory);
    }

    /** Rebuilds contents in place — used after a menu action changes state. */
    public void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        redraw();
    }

    private void redraw() {
        inventory.clear();
        actions.clear();
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    // ------------------------------------------------------------- painting

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            // Almost always a slot typo in guis.yml — say so instead of
            // silently vanishing the item.
            plugin.getLogger().warning("Ignoring slot " + slot + " in "
                    + getClass().getSimpleName() + " — this menu only has slots 0-"
                    + (inventory.getSize() - 1) + ". Check guis.yml.");
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void border() {
        ItemStack filler = ItemBuilder.of(
                        plugin.guis().material("filler.material", Material.GRAY_STAINED_GLASS_PANE))
                .name(Component.empty())
                .build();
        int size = rows() * 9;
        for (int slot = 0; slot < size; slot++) {
            int column = slot % 9;
            boolean edge = slot < 9 || slot >= size - 9 || column == 0 || column == 8;
            if (edge) {
                inventory.setItem(slot, filler);
            }
        }
    }

    protected void backButton(int slot) {
        if (parent == null) {
            return;
        }
        set(slot, ItemBuilder.of(plugin.guis().buttonMaterial("nav.back", Material.ARROW))
                .name(name("gui.back"))
                .lore(lore("gui.back-lore"))
                .build(), event -> {
            click();
            later(parent::open);
        });
    }

    protected void closeButton(int slot) {
        set(slot, ItemBuilder.of(plugin.guis().buttonMaterial("nav.close", Material.BARRIER))
                .name(name("gui.close"))
                .build(), event -> {
            click();
            later(viewer::closeInventory);
        });
    }

    /** Icon shown in the middle of an empty list menu. */
    protected Material emptyMaterial() {
        return plugin.guis().material("nav.empty-material", Material.COBWEB);
    }

    // -------------------------------------------------------------- messages

    /** Message text as a plain component — menu titles and the like. */
    protected Component text(String key, String... placeholders) {
        return plugin.messages().component(key, placeholders);
    }

    protected Component name(String key, String... placeholders) {
        return plugin.messages().name(key, placeholders);
    }

    protected List<Component> lore(String key, String... placeholders) {
        return plugin.messages().lore(key, placeholders);
    }

    protected List<Component> lore(String key, TagResolver extra, String... placeholders) {
        return plugin.messages().lore(key, extra, placeholders);
    }

    protected String raw(String key) {
        return plugin.messages().raw(key);
    }

    // ------------------------------------------------------------- helpers

    /**
     * Opening an inventory from inside a click handler has to wait a tick —
     * Bukkit is still unwinding the current view when the event fires.
     */
    protected void later(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    protected void click() {
        sound(Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    protected void deny() {
        sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    protected void sound(Sound sound, float volume, float pitch) {
        if (plugin.pcConfig().sounds()) {
            viewer.playSound(viewer.getLocation(), sound, volume, pitch);
        }
    }

    protected Menu parent() {
        return parent;
    }
}
