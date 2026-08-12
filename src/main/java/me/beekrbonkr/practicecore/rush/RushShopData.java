package me.beekrbonkr.practicecore.rush;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A Bukkit-only snapshot of the MBedwars item shop, so the shop menu never
 * touches MBedwars classes itself. Built by {@link MBedwarsHook} at open time.
 */
public final class RushShopData {

    /** One shop tab. */
    public record Page(String name, ItemStack icon, List<Entry> entries) {
    }

    /** One purchasable item. */
    public record Entry(ItemStack icon, List<Price> prices, List<ItemStack> products) {

        /** True when the player's inventory covers every price line. */
        public boolean affordableWith(org.bukkit.inventory.PlayerInventory inventory) {
            // Sum per material first — two price lines of the same material
            // must not both count the same items.
            Map<Material, Integer> needed = new EnumMap<>(Material.class);
            for (Price price : prices) {
                needed.merge(price.material(), price.amount(), Integer::sum);
            }
            for (Map.Entry<Material, Integer> entry : needed.entrySet()) {
                if (count(inventory, entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }
            return true;
        }

        public static int count(org.bukkit.inventory.PlayerInventory inventory, Material material) {
            int total = 0;
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() == material) {
                    total += item.getAmount();
                }
            }
            return total;
        }
    }

    /** A cost line: {@code amount} items of {@code material}. */
    public record Price(Material material, int amount) {
    }

    private final List<Page> pages;

    public RushShopData(List<Page> pages) {
        this.pages = List.copyOf(pages);
    }

    public List<Page> pages() {
        return pages;
    }
}
