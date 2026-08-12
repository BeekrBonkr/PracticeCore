package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushShopData;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mirrored MBedwars item shop: page tabs across the top row, that page's
 * items below. Purchases are settled against the player's inventory — the
 * price items are removed, the products added — with no MBedwars game in
 * sight. Built from a {@link RushShopData} snapshot taken at open time.
 */
public final class RushShopMenu extends Menu {

    /** First slot of the item grid (row 1); the grid runs to slot 44. */
    private static final int GRID_START = 9;
    private static final int GRID_SIZE = 36;

    private final RushShopData shop;
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
        page = Math.clamp(page, 0, pages.size() - 1);

        if (pages.size() > 9 && !warnedTabCap) {
            warnedTabCap = true;
            plugin.getLogger().warning("The MBedwars shop has " + pages.size()
                    + " pages; only the first 9 fit the tab row — the rest are hidden.");
        }
        for (int i = 0; i < Math.min(9, pages.size()); i++) {
            RushShopData.Page tab = pages.get(i);
            int index = i;
            set(i, tabWithGlow(tab, i == page), event -> {
                if (index == page) {
                    return;
                }
                click();
                page = index;
                refresh();
            });
        }

        RushShopData.Page current = shop.pages().get(page);
        List<RushShopData.Entry> entries = current.entries();
        if (entries.size() > GRID_SIZE && !warnedEntryCap) {
            warnedEntryCap = true;
            plugin.getLogger().warning("Shop page '" + current.name() + "' has "
                    + entries.size() + " items; only the first " + GRID_SIZE
                    + " fit the grid — the rest are hidden.");
        }
        for (int i = 0; i < Math.min(GRID_SIZE, entries.size()); i++) {
            RushShopData.Entry entry = entries.get(i);
            set(GRID_START + i, entryIcon(entry), event -> buy(entry));
        }

        ItemStack filler = ItemBuilder.of(
                        plugin.guis().material("filler.material", Material.GRAY_STAINED_GLASS_PANE))
                .name(Component.empty())
                .build();
        for (int slot = 45; slot < 54; slot++) {
            set(slot, filler);
        }
        closeButton(plugin.guis().slot("rushshop.close", 49));
    }

    /** The page's own icon with our tab hint appended, glowing when selected. */
    private ItemStack tabWithGlow(RushShopData.Page tab, boolean selected) {
        ItemStack icon = tab.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<Component> lines = new ArrayList<>(
                    meta.hasLore() && meta.lore() != null ? meta.lore() : List.of());
            lines.addAll(lore(selected ? "gui.rushshop.tab-selected" : "gui.rushshop.tab-lore"));
            meta.lore(lines);
            if (selected) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack entryIcon(RushShopData.Entry entry) {
        ItemStack icon = entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            boolean affordable = entry.affordableWith(viewer.getInventory());
            List<Component> lines = new ArrayList<>(
                    meta.hasLore() && meta.lore() != null ? meta.lore() : List.of());
            for (RushShopData.Price price : entry.prices()) {
                lines.add(plugin.messages().name("gui.rushshop.price-line",
                        "amount", String.valueOf(price.amount()),
                        "currency", prettyMaterial(price.material())));
            }
            lines.addAll(lore(affordable
                    ? "gui.rushshop.click-to-buy" : "gui.rushshop.cannot-afford"));
            meta.lore(lines);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static String prettyMaterial(Material material) {
        String lower = material.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + lower.substring(1);
    }

    // ------------------------------------------------------------- purchase

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
        for (ItemStack product : entry.products()) {
            ItemStack give = plugin.settings().recolor(viewer.getUniqueId(), product.clone());
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
        sound(org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        refresh(); // afford states may have flipped
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
