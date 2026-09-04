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
        if (session != null && session.mode() instanceof me.beekrbonkr.practicecore.mode.BedDefenseMode) {
            return; // bed defense generators: resources for the shop, nothing more
        }
        if (session == null || !(session.mode() instanceof RushMode mode)) {
            event.setCancelled(true); // someone else's objective, or no session
            return;
        }
        // Combat runs: generator items are just resources, exactly like a
        // real game — pick them up, spend them at the shop.
        if (session.modeState() instanceof me.beekrbonkr.practicecore.rush.RushState state
                && state.combat()) {
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
        if (session == null || !(session.mode() instanceof RushMode
                || session.mode() instanceof me.beekrbonkr.practicecore.mode.BedDefenseMode)) {
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

    /**
     * Right-clicking a shop-bought special item triggers its emulated use.
     * No {@code ignoreCancelled} here: an air right-click has no block to
     * interact with, so the event reports itself cancelled and the flag would
     * silently drop every fireball aimed at the sky. The item-use result is
     * what actually says whether another plugin denied the click.
     */
    @EventHandler
    public void onSpecialUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        String type = normalizeSpecial(plugin.rush().specialTypeOf(item));
        // A shop fireball configured as a plain item product carries no
        // special tag at all — but a fire charge in a rush session is a
        // fireball in every bedwars there is.
        if (type == null && item != null && item.getType() == Material.FIRE_CHARGE) {
            type = "fireball";
        }
        if (type == null) {
            return;
        }
        // A chest click opens the chest, like in a real game — the item only
        // fires on air or plain blocks (or when sneaking). Ender chests count:
        // their block state is no Container, but they open the run's chest.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null && !event.getPlayer().isSneaking()
                && (event.getClickedBlock().getState() instanceof org.bukkit.block.Container
                    || event.getClickedBlock().getType() == Material.ENDER_CHEST)) {
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
            case "rescueplatform" -> {
                if (plugin.rush().buildRescuePlatform(player, session)) {
                    plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                } else {
                    plugin.messages().actionBar(player, "build.out-of-bounds");
                }
            }
            case "minishop" -> plugin.rush().openShop(player);
            case "teleporter" -> plugin.rush().startTeleporter(player, session,
                    () -> plugin.rush().consumeHand(player, EquipmentSlot.HAND));
            case "tracker" -> {
                // Not consumed — a tracker is a compass you keep checking.
                if (!plugin.rush().useTracker(player, session)) {
                    plugin.messages().actionBar(player, "rush.tracker-nothing");
                }
            }
            case "tntsheep" -> {
                plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                plugin.rush().spawnTntSheep(player, session);
            }
            case "guarddog" -> {
                plugin.rush().consumeHand(player, EquipmentSlot.HAND);
                plugin.rush().spawnGuardDog(player, session);
            }
            // Traps, magic milk, magnet shoes, … genuinely need a real
            // multiplayer game around them.
            default -> {
                plugin.messages().actionBar(player, "rush.special-unsupported");
                plugin.sounds().play(player, "rush.special-unsupported");
            }
        }
    }

    /**
     * MBedwars type ids arrive CamelCase ("Fireball", "RescuePlatform");
     * older purchases were tagged with those raw spellings. Everything is
     * matched lowercase with separators stripped, so both generations of tag
     * — and hand-configured ids like "rescue_platform" — resolve the same.
     */
    private static String normalizeSpecial(String type) {
        return type == null ? null
                : type.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
    }

    /**
     * Chests, the bedwars way. Right-clicking an ender chest opens the run's
     * own 27-slot chest — survives combat respawns, wiped by the next reset,
     * never the player's real ender chest — from any ender chest block on
     * the map. Punching a chest or ender chest deposits the configured
     * resources into it in one hit; sneak-punch bypasses the deposit, so a
     * player-placed chest can still be broken.
     */
    @EventHandler
    public void onChestClick(PlayerInteractEvent event) {
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        boolean enderChest = block.getType() == Material.ENDER_CHEST;
        boolean container = block.getState() instanceof org.bukkit.block.Container;
        if (!enderChest && !container) {
            return;
        }
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof RushMode)
                || !(session.modeState()
                        instanceof me.beekrbonkr.practicecore.rush.RushState state)
                || !session.containsBlock(block.getLocation())) {
            return;
        }
        SessionState sessionState = session.state();
        if (sessionState != SessionState.READY && sessionState != SessionState.ACTIVE) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && enderChest) {
            // The event fires once per hand — one open per click.
            if (event.getHand() != EquipmentSlot.HAND) {
                event.setCancelled(true);
                return;
            }
            // Sneaking with a block in hand places against the chest, vanilla-style.
            if (player.isSneaking() && event.getItem() != null) {
                return;
            }
            event.setCancelled(true); // never the real ender chest
            player.openInventory(state.enderChest(plugin));
            plugin.sounds().playAt(block.getLocation(), "rush.ender-chest-open");
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && plugin.pcConfig().rushPunchToDeposit() && !player.isSneaking()) {
            event.setCancelled(true);
            org.bukkit.inventory.Inventory target = enderChest
                    ? state.enderChest(plugin)
                    : ((org.bukkit.block.Container) block.getState()).getInventory();
            plugin.rush().depositResources(player, target);
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
