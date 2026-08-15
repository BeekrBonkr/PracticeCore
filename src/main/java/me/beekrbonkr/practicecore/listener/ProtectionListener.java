package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/** Blanket rules that make the practice world inert and runs honest. */
public final class ProtectionListener implements Listener {

    private final PracticeCorePlugin plugin;

    public ProtectionListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !plugin.worldService().isPracticeWorld(player.getWorld())) {
            return;
        }
        // PvP sparring is the one sanctioned damage source: the session's own
        // bot and its projectiles go through — PvpBotListener intercepts any
        // hit that would actually kill. Everything else keeps death
        // structurally impossible; falls are handled by the movement fail
        // check, not by void/fall damage.
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session != null && plugin.pvpBot().allowsDamage(session, event)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (plugin.pcConfig().freezeHunger()
                && event.getEntity() instanceof Player player
                && plugin.sessions().get(player.getUniqueId()) != null) {
            // Hunger would eventually break sprint-bridging.
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.pcConfig().blockItemDrops()
                && plugin.worldService().isPracticeWorld(event.getPlayer().getWorld())
                && !plugin.setup().isAdmin(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.worldService().isPracticeWorld(player.getWorld())
                && plugin.sessions().get(player.getUniqueId()) == null
                && !plugin.setup().isAdmin(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (plugin.worldService().isPracticeWorld(event.getEntity().getWorld())
                && !plugin.rush().isRushDrop(event.getEntity())
                // Without this, onDrop's admin exemption is dead code — the
                // allowed drop's item entity would be destroyed right here.
                && !plugin.setup().containsBlock(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Kits are exact: crafting could mint items no kit contains. */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (plugin.pcConfig().blockCrafting()
                && event.getWhoClicked() instanceof Player player
                && plugin.sessions().get(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)
                || !(pearl.getShooter() instanceof Player player)) {
            return;
        }
        if (plugin.sessions().get(player.getUniqueId()) != null && !plugin.pcConfig().allowPearls()) {
            event.setCancelled(true);
            plugin.messages().send(player, "protect.pearls-disabled");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (plugin.pcConfig().blockElytra()
                && event.isGliding() && event.getEntity() instanceof Player player
                && plugin.sessions().get(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        // Vehicle movement bypasses PlayerMoveEvent — the bounds backstop
        // would go blind, so vehicles are simply banned during sessions.
        if (plugin.pcConfig().blockVehicles()
                && event.getEntered() instanceof Player player
                && plugin.sessions().get(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (plugin.worldService().isPracticeWorld(event.getEntity().getWorld())
                && (event.getPlayer() == null
                    || !plugin.setup().isAdmin(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (plugin.setup().isAdmin(player.getUniqueId())) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session != null) {
            if (!plugin.pcConfig().allowBuckets() && !session.mode().allowsBuckets()) {
                event.setCancelled(true);
            }
        } else if (plugin.worldService().isPracticeWorld(player.getWorld())
                && !player.hasPermission("practicecore.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            return;
        }
        Location from = event.getBlock().getLocation();
        Location to = event.getToBlock().getLocation();
        PracticeSession session = plugin.sessions().sessionAtBlock(from);
        if (session != null) {
            if (!session.containsBlock(to)) {
                event.setCancelled(true); // liquid must not escape the resettable region
            }
            return;
        }
        if (plugin.setup().containsBlock(from)) {
            // The wizard's arena behaves like the real world, or the admin
            // would capture water that never flowed the way players see it.
            if (!plugin.setup().containsBlock(to)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Pistons could push blocks across an arena boundary, where neither the
     * block revert nor the end-of-session erase reaches them — permanent
     * litter in the shared world. The practice world has no moving parts.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.pcConfig().blockPistons()
                && plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.pcConfig().blockPistons()
                && plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Explosions (rush TNT and fireballs) behave the way bedwars players
     * expect: blocks the player placed and generated bed defenses break, the
     * map itself — beds included — never does, nothing drops, and the
     * knockback the cancelled damage event would have applied is re-added.
     */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!plugin.worldService().isPracticeWorld(event.getEntity().getWorld())) {
            return;
        }
        event.setYield(0);
        event.blockList().removeIf(block -> {
            PracticeSession session = plugin.sessions().sessionAtBlock(block.getLocation());
            return session == null
                    || !me.beekrbonkr.practicecore.mode.RushMode
                            .explosionCanBreak(session, block.getLocation());
        });
        plugin.rush().applyExplosionKnockback(event.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (plugin.worldService().isPracticeWorld(event.getBlock().getWorld())
                && (event.getPlayer() == null
                    || !plugin.setup().isAdmin(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** Thrown eggs (the rush bridge egg) must never hatch chickens. */
    @EventHandler(ignoreCancelled = true)
    public void onEggThrow(org.bukkit.event.player.PlayerEggThrowEvent event) {
        if (plugin.worldService().isPracticeWorld(event.getPlayer().getWorld())) {
            event.setHatching(false);
        }
    }

    /**
     * Ender chest contents are real player data that lives outside the arena:
     * a snapshot never captures them, so items stashed there would survive
     * resets and leak out of practice entirely. Rush maps imported from
     * MBedwars often contain ender chests — they stay closed.
     */
    @EventHandler(ignoreCancelled = true)
    public void onOpenEnderChest(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (plugin.pcConfig().blockEnderChests()
                && event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST
                && event.getPlayer() instanceof Player player
                && plugin.sessions().get(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        // A bed spawn set in a world that gets deleted every restart is a trap.
        if (plugin.worldService().isPracticeWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessions().isInternalGamemode(player.getUniqueId())) {
            return;
        }
        if (plugin.sessions().get(player.getUniqueId()) != null
                && event.getNewGameMode() != GameMode.SURVIVAL) {
            event.setCancelled(true);
            plugin.messages().send(player, "protect.gamemode-locked");
        }
    }

    /** Defensive: /kill and health-setting plugins can bypass damage events. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setRespawnLocation(session.spawn());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.sessions().fail(player, session));
    }
}
