package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    /** Slot of a destructive control awaiting its second click, or -1. */
    private int armed = -1;

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
        armed = -1;
        redraw();
        viewer.openInventory(inventory);
        // One consistent open cue across every menu; refresh() stays silent.
        sound("menu.open");
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
        int slot = event.getRawSlot();
        boolean disarm = armed >= 0 && slot != armed;
        if (disarm) {
            // Any other click stands the armed control down (R56).
            armed = -1;
        }
        Consumer<InventoryClickEvent> action = actions.get(slot);
        if (action != null) {
            action.accept(event);
        } else if (disarm) {
            refresh();
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

    // ----------------------------------------------------------- navigation

    /** First slot of the bottom row: nav lives there in every menu (R43). */
    protected int bottomRow() {
        return (rows() - 1) * 9;
    }

    /**
     * A nav slot: the admin's override under {@code path} if set, else the
     * style guide position on the bottom row. {@code column} is 0 for back,
     * 3/4/5 for previous/page/next, 8 for close, 1/2/6/7 for footer extras.
     */
    protected int navSlot(String path, int column) {
        return plugin.guis().slot(path, bottomRow() + column);
    }

    /**
     * Back at the bottom-left corner (only with a parent) and Close at the
     * bottom-right. {@code path} is the menu's guis.yml section, so an admin
     * can still move either through {@code <path>.back.slot} / {@code .close.slot}.
     */
    protected void nav(String path) {
        backButton(navSlot(path + ".back", 0));
        closeButton(navSlot(path + ".close", 8));
    }

    protected void backButton(int slot) {
        if (parent == null) {
            return;
        }
        set(slot, Button.of(plugin, plugin.guis().buttonMaterial("nav.back", Material.ARROW))
                .name("gui.back")
                .lore("gui.back-lore")
                .hint("open")
                .build(), event -> {
            click();
            later(parent::open);
        });
    }

    protected void closeButton(int slot) {
        set(slot, Button.of(plugin, plugin.guis().buttonMaterial("nav.close", Material.BARRIER))
                .name("gui.close")
                .lore("gui.close-lore")
                .hint("close")
                .build(), event -> {
            click();
            later(viewer::closeInventory);
        });
    }

    // ----------------------------------------------------- destructive arm

    /** Whether this slot's destructive control is waiting for its confirm click. */
    protected boolean isArmed(int slot) {
        return armed == slot;
    }

    /** First click of a destructive control: arm it and redraw (R56). */
    protected void arm(int slot) {
        armed = slot;
        click();
        refresh();
    }

    /** The confirm click landed; the control executes now. */
    protected void disarm() {
        armed = -1;
        sound("menu.select");
    }

    /** An empty-list icon in the style-guide shape (R42). */
    protected ItemStack emptyIcon(String key) {
        return Button.of(plugin, emptyMaterial())
                .name(key + ".name")
                .lore(key + ".lore")
                .build();
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
        sound("menu.click");
    }

    protected void deny() {
        sound("menu.deny");
    }

    /** Plays one of the cues named in sounds.yml to this menu's viewer. */
    protected void sound(String cue) {
        plugin.sounds().play(viewer, cue);
    }

    protected Menu parent() {
        return parent;
    }
}
