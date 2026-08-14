package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Rush-specific events: objective pickups, dealer NPC clicks, and the
 * emulated MBedwars combat items — auto-igniting TNT and the shop's special
 * items (fireball, bridge egg, rescue platform, mini shop).
 */
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

    /**
     * TNT auto-ignites on place, like in MBedwars: the place is cancelled,
     * one TNT is taken from the hand and a primed entity spawns instead.
     * Runs before BlockListener's HIGHEST handler so the block is never
     * tracked; the bounds check stays with BlockListener for out-of-bounds
     * attempts (this handler steps aside for those).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTntPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof RushMode)
                || event.getBlock().getType() != Material.TNT
                || !session.containsBlock(event.getBlock().getLocation())) {
            return;
        }
        SessionState state = session.state();
        if (state != SessionState.READY && state != SessionState.ACTIVE) {
            return;
        }
        event.setCancelled(true);
        plugin.rush().consumeHand(player, event.getHand());
        plugin.rush().primeTnt(player, event.getBlock().getLocation());
    }

    /** Right-clicking a shop-bought special item triggers its emulated use. */
    @EventHandler(ignoreCancelled = true)
    public void onSpecialUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        String type = plugin.rush().specialTypeOf(item);
        if (type == null) {
            return;
        }
        // A chest click opens the chest, like in a real game — the item only
        // fires on air or plain blocks (or when sneaking).
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null && !event.getPlayer().isSneaking()
                && event.getClickedBlock().getState() instanceof org.bukkit.block.Container) {
            return;
        }
        event.setCancelled(true); // never the vanilla use (fire, thrown egg, …)
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof RushMode)) {
            return;
        }
        SessionState state = session.state();
        if (state != SessionState.READY && state != SessionState.ACTIVE) {
            return;
        }
        switch (type) {
            case "fireball" -> {
                plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                plugin.rush().launchFireball(player);
            }
            case "bridge" -> {
                plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                plugin.rush().throwBridgeEgg(player, session);
            }
            case "rescue_platform" -> {
                if (plugin.rush().buildRescuePlatform(player, session)) {
                    plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                } else {
                    plugin.messages().actionBar(player, "build.out-of-bounds");
                }
            }
            case "mini_shop" -> plugin.rush().openShop(player);
            // Trackers, guard dogs, traps, … need enemies a solo practice
            // run doesn't have.
            default -> {
                plugin.messages().actionBar(player, "rush.special-unsupported");
                if (plugin.pcConfig().sounds()) {
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                }
            }
        }
    }

    /**
     * Objective items must survive the explosions the shop now sells — a
     * fireball detonating on a generator would otherwise destroy the diamond
     * and leave the run unfinishable until the next reset.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDropDamage(EntityDamageEvent event) {
        if (plugin.rush().isRushDrop(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
