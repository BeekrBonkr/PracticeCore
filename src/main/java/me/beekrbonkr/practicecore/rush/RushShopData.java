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

    /**
     * One stack a purchase hands over. {@code autoWear} mirrors the MBedwars
     * product flag (armor that equips itself on purchase); {@code specialType}
     * is the MBedwars special-item type id ("fireball", "bridge", …) for
     * special-item products, null for plain ones — the given stack is tagged
     * with it so use-time listeners can recognize the item.
     */
    public record Product(ItemStack stack, boolean autoWear, String specialType) {
    }

    /**
     * One purchasable item. {@code forceSlot} is the slot MBedwars pins the
     * item to in its own GUI (null when unset), mirrored so the layout matches.
     */
    public record Entry(ItemStack icon, Integer forceSlot, List<Price> prices,
                        List<Product> products) {

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
