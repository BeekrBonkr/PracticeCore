package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public final class BlockListener implements Listener {

    private final PracticeCorePlugin plugin;

    public BlockListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.setup().isAdmin(player.getUniqueId())) {
            plugin.setup().handlePlace(event);
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            if (plugin.worldService().isPracticeWorld(event.getBlock().getWorld())
                    && !player.hasPermission("practicecore.bypass")) {
                event.setCancelled(true);
            }
            return;
        }
        SessionState state = session.state();
        if (state != SessionState.READY && state != SessionState.ACTIVE) {
            // Blocks placed while resetting would survive the revert.
            event.setCancelled(true);
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (!session.containsBlock(loc) || session.isTrigger(loc)) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "build.out-of-bounds");
            return;
        }
        session.tracker().recordPlace(event.getBlock(), event.getBlockReplacedState().getBlockData());
        if (state == SessionState.READY
                && session.mode().startsTimerOnFirstBlock(plugin.pcConfig())) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.setup().isAdmin(player.getUniqueId())) {
            return; // creative admin reshaping during setup
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            if (plugin.worldService().isPracticeWorld(event.getBlock().getWorld())
                    && !player.hasPermission("practicecore.bypass")) {
                event.setCancelled(true);
            }
            return;
        }
        if (session.state() != SessionState.READY && session.state() != SessionState.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        // What is breakable is the mode's call. The default (blocks the player
        // placed this run) also protects the finish trigger and its support.
        if (!session.mode().canBreak(session, event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "build.break-own-only");
            return;
        }
        session.mode().onBlockBreak(plugin, player, session, event);
    }

    /**
     * Bucket liquids bypass BlockPlaceEvent, so they are tracked here — at
     * MONITOR, once nothing can cancel the empty anymore — or the water an
     * MLG clutch (or an allow-buckets kit) leaves behind would survive every
     * arena reset.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        PracticeSession session = plugin.sessions().get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Block block = event.getBlock();
        if (session.containsBlock(block.getLocation())) {
            // The liquid lands after the event completes, so the block still
            // holds its pre-liquid state.
            session.tracker().recordPlace(block, block.getBlockData());
        }
    }

    /** Liquid spreading inside an arena is tracked so resets dry it back up. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            return;
        }
        PracticeSession session = plugin.sessions().sessionAtBlock(event.getBlock().getLocation());
        if (session != null) {
            Block to = event.getToBlock();
            if (session.containsBlock(to.getLocation())) {
                session.tracker().recordPlace(to, to.getBlockData());
            }
        }
    }

    /** Gravel/sand landing outside the arena would survive every reset. */
    @EventHandler(ignoreCancelled = true)
    public void onFallingBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }
        if (!plugin.worldService().isPracticeWorld(event.getBlock().getWorld())) {
            return;
        }
        if (event.getTo().isAir()) {
            return; // block becoming an entity — the tracked source reverts fine
        }
        Location loc = event.getBlock().getLocation();
        PracticeSession session = plugin.sessions().sessionAtBlock(loc);
        if (session != null) {
            // Landed inside an arena: track it so the reset cleans it up.
            session.tracker().recordPlace(event.getBlock(), event.getBlock().getBlockData());
            return;
        }
        if (plugin.setup().containsBlock(loc)) {
            return; // gravel/sand must land normally while an admin builds
        }
        event.setCancelled(true);
        fallingBlock.setDropItem(false);
        fallingBlock.remove();
    }
}
