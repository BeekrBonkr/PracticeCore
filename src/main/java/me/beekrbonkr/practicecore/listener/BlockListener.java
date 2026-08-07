package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PCConfig;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

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
        if (!session.containsBlock(loc) || loc.equals(session.trigger())) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "build.out-of-bounds");
            return;
        }
        session.tracker().recordPlace(event.getBlock(), event.getBlockReplacedState().getBlockData());
        if (state == SessionState.READY
                && plugin.pcConfig().timerStartMode() == PCConfig.TimerStartMode.FIRST_BLOCK) {
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
        // Only blocks the player placed this run — which also protects the
        // finish trigger and its supporting block.
        if (!session.tracker().isTracked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "build.break-own-only");
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
        for (PracticeSession session : plugin.sessions().all()) {
            if (session.containsBlock(loc)) {
                // Landed inside an arena: track it so the reset cleans it up.
                session.tracker().recordPlace(event.getBlock(), event.getBlock().getBlockData());
                return;
            }
        }
        event.setCancelled(true);
        fallingBlock.setDropItem(false);
        fallingBlock.remove();
    }
}
