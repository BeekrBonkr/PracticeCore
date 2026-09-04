package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushShopData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mirrored MBedwars item shop, laid out the way its HypixelV2 layout is:
 * a Quick Buy tab first and the shop pages after it across the top row, a
 * separator row whose highlighted pane marks the open tab, and the page's
 * items in the 7-wide grid below. Purchases are settled against the player's
 * inventory — the price items removed, the products added — with no MBedwars
 * game in sight.
 *
 * <p>Tab and item names come from MBedwars' own data and are shown as it
 * ships them (style guide R50); everything PracticeCore adds — the Quick Buy
 * tab, price lines, hints, the close button — follows the guide.
 *
 * <p>Quick Buy is the player's real MBedwars quick-buy list, read from and
 * written back to their MBedwars profile: shift-click any item to pin it into
 * the first free slot, shift-click a pinned item to clear it. Pins made here
 * appear in real games and vice versa.
 */
public final class RushShopMenu extends Menu {

    /** The 7-wide, 3-row item grid (columns 1-7 of rows 2-4). */
    private static final int[] GRID = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final RushShopData shop;
    /** 0 = Quick Buy; 1.. = shop.pages() index + 1. */
    private int page;
    /** One warning per open, not one per refresh — buying re-renders. */
    private boolean warnedTabCap;
    private boolean warnedEntryCap;

    public RushShopMenu(PracticeCorePlugin plugin, Player viewer, RushShopData shop) {
        super(plugin, viewer, null);
        this.shop = shop;
    }

    @Override
    protected Component title() {
        return text("gui.rushshop.title");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        List<RushShopData.Page> pages = shop.pages();
        page = Math.clamp(page, 0, pages.size());

        // The full frame first (R46); the tab row and separator overlay it.
        border();
        renderTabs(pages);
        renderSeparator();
        if (page == 0) {
            renderQuickBuy();
        } else {
            renderPage(pages.get(page - 1));
        }
        nav("rushshop");
    }

    // ----------------------------------------------------------------- tabs

    private void renderTabs(List<RushShopData.Page> pages) {
        set(0, quickBuyTab(page == 0), event -> switchTo(0));
        if (pages.size() > 8 && !warnedTabCap) {
            warnedTabCap = true;
            plugin.getLogger().warning("The MBedwars shop has " + pages.size()
                    + " pages; only the first 8 fit beside Quick Buy — the rest are hidden.");
        }
        for (int i = 0; i < Math.min(8, pages.size()); i++) {
            int index = i + 1;
            set(index, tab(pages.get(i), page == index), event -> switchTo(index));
        }
    }

    private void switchTo(int index) {
        if (index == page) {
            deny();
            return;
        }
        click();
        page = index;
        refresh();
    }

    private ItemStack quickBuyTab(boolean selected) {
        Button tab = Button.of(plugin, plugin.guis()
                        .buttonMaterial("rushshop.quickbuy", Material.GOLD_BLOCK))
                .name("gui.rushshop.quickbuy.name")
                .glow(selected);
        if (selected) {
            tab.line(name("gui.rushshop.tab-selected"));
        } else {
            tab.hint("view");
        }
        return tab.build();
    }

    /** The page's own icon with our state or hint appended, glowing when selected. */
    private ItemStack tab(RushShopData.Page tab, boolean selected) {
        ItemStack icon = tab.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName() && tab.displayName() != null
                    && !tab.displayName().isBlank()) {
                // MBedwars display names carry legacy § color codes.
                meta.displayName(LegacyComponentSerializer.legacySection()
                        .deserialize(tab.displayName())
                        .decoration(TextDecoration.ITALIC, false));
            }
            List<Component> lines = new ArrayList<>(
                    meta.hasLore() && meta.lore() != null ? meta.lore() : List.of());
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.add(name(selected ? "gui.rushshop.tab-selected" : "gui.hint.click.view"));
            meta.lore(lines);
            if (selected) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** The MBedwars-style indicator row: green pane under the open tab. */
    private void renderSeparator() {
        ItemStack plain = Button.of(plugin, plugin.guis()
                        .material("rushshop.separator", Material.GRAY_STAINED_GLASS_PANE))
                .name(Component.empty())
                .build();
        ItemStack marker = Button.of(plugin, plugin.guis()
                        .material("rushshop.separator-selected", Material.LIME_STAINED_GLASS_PANE))
                .name(Component.empty())
                .build();
        for (int column = 0; column < 9; column++) {
            set(9 + column, column == page ? marker : plain);
        }
    }

    // ------------------------------------------------------------ quick buy

    private void renderQuickBuy() {
        List<String> pins = plugin.rush().quickBuyIds(viewer.getUniqueId());
        ItemStack empty = Button.of(plugin, plugin.guis()
                        .material("rushshop.quickbuy-empty", Material.RED_STAINED_GLASS_PANE))
                .name("gui.rushshop.quickbuy.empty-name")
                .lore("gui.rushshop.quickbuy.empty-lore")
                .build();
        for (int cell = 0; cell < GRID.length; cell++) {
            RushShopData.Entry entry = cell < pins.size()
                    ? shop.entryById(pins.get(cell)) : null;
            if (entry == null) {
                set(GRID[cell], empty);
            } else {
                set(GRID[cell], entryIcon(entry, true), event -> clickEntry(entry, event));
            }
        }
    }

    private void renderPage(RushShopData.Page current) {
        List<RushShopData.Entry> entries = current.entries();
        if (entries.size() > GRID.length && !warnedEntryCap) {
            warnedEntryCap = true;
            plugin.getLogger().warning("Shop page '" + current.name() + "' has "
                    + entries.size() + " items; only the first " + GRID.length
                    + " fit the grid — the rest are hidden.");
        }
        // Entries pinned to a slot in the MBedwars GUI land on the same slot
        // here when it falls inside the grid; the rest flow in order.
        boolean[] taken = new boolean[GRID.length];
        List<RushShopData.Entry> flowing = new ArrayList<>();
        for (RushShopData.Entry entry : entries) {
            int cell = cellOf(entry.forceSlot());
            if (cell >= 0 && !taken[cell]) {
                taken[cell] = true;
                set(GRID[cell], entryIcon(entry, false), event -> clickEntry(entry, event));
            } else {
                flowing.add(entry);
            }
        }
        int cell = 0;
        for (RushShopData.Entry entry : flowing) {
            while (cell < GRID.length && taken[cell]) {
                cell++;
            }
            if (cell >= GRID.length) {
                break;
            }
            taken[cell] = true;
            set(GRID[cell], entryIcon(entry, false), event -> clickEntry(entry, event));
        }
    }

    /** The grid cell a forced MBedwars slot maps to, or -1 for none. */
    private static int cellOf(Integer forceSlot) {
        if (forceSlot == null) {
            return -1;
        }
        for (int cell = 0; cell < GRID.length; cell++) {
            if (GRID[cell] == forceSlot) {
                return cell;
            }
        }
        return -1;
    }

    /**
     * The product's own MBedwars icon and lore, then our block: price lines,
     * the pin state, and either the buy hints or — unaffordable — the
     * {@code Unavailable: cannot afford} line in place of them (R54). The
     * click still goes to {@link #buy}, which re-checks the purse.
     */
    private ItemStack entryIcon(RushShopData.Entry entry, boolean onQuickBuy) {
        ItemStack icon = entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            boolean affordable = entry.affordableWith(viewer.getInventory());
            List<Component> lines = new ArrayList<>(
                    meta.hasLore() && meta.lore() != null ? meta.lore() : List.of());
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            for (RushShopData.Price price : entry.prices()) {
                lines.add(name("gui.rushshop.price-line",
                        "amount", String.valueOf(price.amount()),
                        "currency", prettyMaterial(price.material())));
            }
            boolean pinned = onQuickBuy
                    || plugin.rush().isQuickBuyPinned(viewer.getUniqueId(), entry.id());
            if (pinned) {
                lines.add(name("gui.rushshop.quickbuy.pinned"));
            }
            lines.add(Component.empty());
            if (affordable) {
                lines.add(name("gui.hint.click.buy"));
            } else {
                lines.add(plugin.messages().name("gui.unavailable",
                        plugin.messages().ref("reason", "gui.reason.cannot-afford")));
            }
            lines.add(name(pinned ? "gui.hint.shift.unpin" : "gui.hint.shift.pin"));
            meta.lore(lines);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    // STYLE-GUIDE: duplicated in RushDefenseMenu.pretty; a shared util is
    // outside this pass (F7 also wants these strings translatable).
    private static String prettyMaterial(Material material) {
        String lower = material.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    // ------------------------------------------------------------- purchase

    /** Shift-click pins and unpins; a plain click buys — like MBedwars. */
    private void clickEntry(RushShopData.Entry entry, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            togglePin(entry);
            return;
        }
        buy(entry);
    }

    private void togglePin(RushShopData.Entry entry) {
        if (plugin.rush().isQuickBuyPinned(viewer.getUniqueId(), entry.id())) {
            if (plugin.rush().unpinQuickBuy(viewer, entry.id())) {
                sound("rush.quickbuy-unpin");
                plugin.messages().actionBar(viewer, "rush.quickbuy-unpinned");
                refresh();
            }
            return;
        }
        if (plugin.rush().pinQuickBuy(viewer, entry.id())) {
            sound("rush.quickbuy-pin");
            plugin.messages().actionBar(viewer, "rush.quickbuy-pinned");
            refresh();
        } else {
            deny();
            plugin.messages().actionBar(viewer, "rush.quickbuy-full");
        }
    }

    private void buy(RushShopData.Entry entry) {
        if (plugin.sessions().get(viewer.getUniqueId()) == null) {
            deny();
            return; // the session ended while the shop was open
        }
        PlayerInventory inventory = viewer.getInventory();
        if (!entry.affordableWith(inventory)) {
            deny();
            plugin.messages().actionBar(viewer, "rush.shop-cannot-afford");
            return;
        }
        for (RushShopData.Price price : entry.prices()) {
            removeItems(inventory, price.material(), price.amount());
        }
        boolean overflow = false;
        for (RushShopData.Product product : entry.products()) {
            ItemStack give = plugin.settings().recolor(viewer.getUniqueId(),
                    product.stack().clone());
            if (product.specialType() != null) {
                plugin.rush().tagSpecial(give, product.specialType());
            }
            // Auto-wear armor equips itself, exactly like the MBedwars shop.
            if (product.autoWear() && wear(inventory, give)) {
                continue;
            }
            Map<Integer, ItemStack> left = inventory.addItem(give);
            if (!left.isEmpty()) {
                overflow = true;
                left.values().forEach(stack -> plugin.rush()
                        .dropTracked(viewer.getLocation(), stack, "shop", false));
            }
        }
        if (overflow) {
            plugin.messages().actionBar(viewer, "rush.shop-inventory-full");
        }
        sound("rush.purchase");
        refresh(); // afford states may have flipped
    }

    /** Equips armor into its slot; true when it went on the body. */
    private static boolean wear(PlayerInventory inventory, ItemStack armor) {
        String name = armor.getType().name();
        if (name.endsWith("_HELMET")) {
            inventory.setHelmet(armor);
        } else if (name.endsWith("_CHESTPLATE")) {
            inventory.setChestplate(armor);
        } else if (name.endsWith("_LEGGINGS")) {
            inventory.setLeggings(armor);
        } else if (name.endsWith("_BOOTS")) {
            inventory.setBoots(armor);
        } else {
            return false;
        }
        return true;
    }

    private static void removeItems(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && amount > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) {
                continue;
            }
            int taken = Math.min(amount, item.getAmount());
            amount -= taken;
            if (taken >= item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - taken);
            }
        }
    }
}
