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
        // STYLE-GUIDE: needs logic change (R23) — name/lore should move to
        // messages.yml item.menu.* with a config.yml migration step.
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
        if (settleIntoSlot(inventory)) {
            return;
        }
        ItemStack existing = inventory.getItem(slot);
        inventory.setItem(slot, create());
        if (existing != null && !existing.getType().isAir()) {
            inventory.addItem(existing).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /**
     * Puts the menu item in its configured slot, always: an item a kit saved
     * elsewhere is swapped into place, a missing one is inserted with the
     * slot's occupant relocated to the first free slot instead. The one case
     * that keeps the item out is a kit with genuinely no room for it — the
     * kit wins, and the menu stays reachable through /practice.
     */
    public void forceIntoInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (settleIntoSlot(inventory)) {
            return;
        }
        int preferred = plugin.pcConfig().menuItemSlot();
        ItemStack occupant = inventory.getItem(preferred);
        if (isFree(occupant)) {
            inventory.setItem(preferred, create());
            return;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (isFree(inventory.getItem(slot))) {
                inventory.setItem(slot, occupant);
                inventory.setItem(preferred, create());
                return;
            }
        }
    }

    /**
     * Moves an already-present menu item into the configured slot, swapping
     * whatever sits there into the item's old spot.
     *
     * @return true when the inventory now has the item in its slot
     */
    private boolean settleIntoSlot(PlayerInventory inventory) {
        int preferred = plugin.pcConfig().menuItemSlot();
        if (isMenuItem(inventory.getItem(preferred))) {
            return true;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isMenuItem(item)) {
                inventory.setItem(slot, inventory.getItem(preferred));
                inventory.setItem(preferred, item);
                return true;
            }
        }
        return false;
    }

    private static boolean isFree(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    public NamespacedKey key() {
        return key;
    }
}
