package me.beekrbonkr.practicecore.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Routes clicks in PracticeCore inventories to their owning {@link Menu}. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return; // click outside any inventory
        }
        if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
            menu.handleClick(event);
            return;
        }
        // Clicking your own inventory with a menu open: harmless, except for
        // shift-clicks and number swaps, which would push items into the menu.
        switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT, NUMBER_KEY, SWAP_OFFHAND, DOUBLE_CLICK -> event.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) {
            event.setCancelled(true);
        }
    }
}
