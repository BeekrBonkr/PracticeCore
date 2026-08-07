package me.beekrbonkr.practicecore.item;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * The hotbar GUI item. Tagged with a persistent-data key rather than matched
 * on name or material, so it survives being saved into an arena kit (kits are
 * serialized ItemStacks) and can never be spoofed by a look-alike item.
 */
public final class MenuItemService {

    private final PracticeCorePlugin plugin;
    private final NamespacedKey key;

    public MenuItemService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "menu-item");
    }

    /**
     * The item's text stays in config.yml next to its material and slot —
     * it defines the item rather than being something the plugin says.
     */
    public ItemStack create() {
        return ItemBuilder.of(plugin.pcConfig().menuItemMaterial())
                .name(Text.item(plugin.pcConfig().menuItemName()))
                .lore(Text.itemLore(plugin.pcConfig().menuItemLore()))
                .hideAttributes()
                .edit(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1))
                .build();
    }

    public boolean isMenuItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(key, PersistentDataType.BYTE);
    }

    public boolean kitContainsMenuItem(Map<Integer, ItemStack> kit) {
        return kit.values().stream().anyMatch(this::isMenuItem);
    }

    /**
     * Drops a menu item into the configured hotbar slot, moving whatever was
     * there elsewhere in the inventory rather than destroying it.
     */
    public void give(Player player) {
        PlayerInventory inventory = player.getInventory();
        int slot = plugin.pcConfig().menuItemSlot();
        ItemStack existing = inventory.getItem(slot);
        inventory.setItem(slot, create());
        if (existing != null && !existing.getType().isAir() && !isMenuItem(existing)) {
            inventory.addItem(existing).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /** Used by {@code menu-item.force-in-kit} when a kit predates the item. */
    public void forceIntoInventory(Player player) {
        player.getInventory().setItem(plugin.pcConfig().menuItemSlot(), create());
    }

    public NamespacedKey key() {
        return key;
    }
}
