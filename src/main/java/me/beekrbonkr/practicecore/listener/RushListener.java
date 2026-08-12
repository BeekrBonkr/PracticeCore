package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Rush-specific events: objective pickups and dealer NPC clicks. */
public final class RushListener implements Listener {

    private final PracticeCorePlugin plugin;

    public RushListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            if (plugin.rush().isRushDrop(event.getItem())) {
                event.setCancelled(true); // no mob may walk off with an objective
            }
            return;
        }
        String type = plugin.rush().dropTypeOf(event.getItem());
        if (type == null) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof RushMode mode)) {
            event.setCancelled(true); // someone else's objective, or no session
            return;
        }
        RushObjective objective = switch (type) {
            case "emerald" -> RushObjective.EMERALD;
            case "diamond" -> RushObjective.DIAMOND;
            default -> null;
        };
        if (objective == null) {
            return; // iron/gold pickup — fine
        }
        // Every objective is armed: the first one completed ends the run, and
        // the item never enters the inventory.
        event.setCancelled(true);
        if (session.state() != SessionState.ACTIVE) {
            return; // timer never started — leave it waiting on the generator
        }
        event.getItem().remove();
        mode.completePickup(plugin, player, session, objective);
    }

    /** Hoppers and hopper minecarts must never vacuum an objective item. */
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (plugin.rush().isRushDrop(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealerClick(PlayerInteractEntityEvent event) {
        if (!plugin.rush().isDealer(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true); // never the vanilla trading screen
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the event fires once per hand — one shop open per click
        }
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof RushMode)) {
            return;
        }
        SessionState state = session.state();
        if (state == SessionState.READY || state == SessionState.ACTIVE) {
            plugin.rush().openShop(player);
        }
    }
}
