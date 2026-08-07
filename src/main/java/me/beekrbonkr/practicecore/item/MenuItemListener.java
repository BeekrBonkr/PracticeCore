package me.beekrbonkr.practicecore.item;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Keeps the menu item where it belongs during a session — losing it would
 * strand a player with no way back out on servers that rely on the GUI.
 */
public final class MenuItemListener implements Listener {

    private final PracticeCorePlugin plugin;

    public MenuItemListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Player player) {
        return plugin.sessions().get(player.getUniqueId()) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !locked(player)) {
            return;
        }
        if (plugin.menuItems().isMenuItem(event.getCurrentItem())
                || plugin.menuItems().isMenuItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer())
                && (plugin.menuItems().isMenuItem(event.getMainHandItem())
                    || plugin.menuItems().isMenuItem(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.menuItems().isMenuItem(event.getItemDrop().getItemStack())) {
            return;
        }
        // Admins arranging a kit are the one case where dropping it is normal.
        if (locked(player) || (plugin.worldService().isPracticeWorld(player.getWorld())
                && !plugin.setup().isAdmin(player.getUniqueId()))) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "menu.item-locked");
        }
    }
}
