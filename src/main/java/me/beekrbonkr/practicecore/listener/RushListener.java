package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

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
        RushState state = RushMode.state(session);
        if (state == null || !type.equals(state.selection().objective().id())) {
            return; // iron/gold pickup, or an item from another objective — fine
        }
        // The run ends here; the item never enters the inventory.
        event.setCancelled(true);
        if (session.state() != SessionState.ACTIVE) {
            return; // timer never started — leave it waiting on the generator
        }
        event.getItem().remove();
        mode.completePickup(plugin, player, session);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealerClick(PlayerInteractEntityEvent event) {
        if (!plugin.rush().isDealer(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true); // never the vanilla trading screen
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
