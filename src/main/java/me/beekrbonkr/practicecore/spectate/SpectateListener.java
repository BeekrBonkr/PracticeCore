package me.beekrbonkr.practicecore.spectate;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.gui.SpectateMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Everything a spectator must not do, plus their three hotbar tools.
 *
 * The one non-negotiable invariant: a spectator can never influence the run
 * they are watching — no block edits, no trigger presses (PHYSICAL interacts
 * included, so pressure plates stay silent), no damage dealt or taken, no
 * item pickups stealing rush objectives, no projectiles.
 */
public final class SpectateListener implements Listener {

    private final PracticeCorePlugin plugin;

    public SpectateListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean spectating(Player player) {
        return plugin.spectate().isSpectator(player.getUniqueId());
    }

    // ---------------------------------------------------------------- tools

    /**
     * All world interaction is swallowed; right-clicks on the tagged hotbar
     * tools act. No {@code ignoreCancelled}: an air right-click reports itself
     * cancelled (no block), and air is exactly where these tools get clicked.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!spectating(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        String type = plugin.spectate().itemTypeOf(event.getItem());
        if (type == null) {
            return;
        }
        switch (type) {
            case SpectateService.ITEM_TELEPORT -> plugin.spectate().teleportToTarget(player);
            case SpectateService.ITEM_MENU -> new SpectateMenu(plugin, player, null).open();
            case SpectateService.ITEM_LEAVE ->
                    plugin.spectate().stopIntoDefaultArena(player, "spectate.stopped");
            default -> {
            }
        }
    }

    /** No dealer shops, no NPC clicks, no leashing the PvP bot. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------- world locks

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A spectator hovering a generator must never vacuum the objective. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunchProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------- combat

    /** Belt to the invulnerable flag's braces — nothing hurts a spectator. */
    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------- inventory

    /** The hotbar tools stay put; clicks inside our own menus still route. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !spectating(player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Menu
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // Menu#handleClick owns these
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && spectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ lifecycle

    /**
     * A teleport that actually left the practice world ends spectating —
     * /spawn, /tpa, a warp — with everything restored except location, which
     * the outbound teleport already decided. Our own stop() restore removes
     * the player from the spectator map before it teleports, so it can never
     * re-enter here.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (spectating(player)
                && !plugin.worldService().isPracticeWorld(event.getTo().getWorld())) {
            plugin.spectate().stop(player, false, "spectate.ended");
        }
    }

    /** Restore during the quit event, so the pre-spectate state is what persists. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.spectate().stop(event.getPlayer(), true, null);
    }

    /** Spectators already flying around must be invisible to a fresh joiner. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.spectate().hideAllFrom(event.getPlayer());
    }
}
