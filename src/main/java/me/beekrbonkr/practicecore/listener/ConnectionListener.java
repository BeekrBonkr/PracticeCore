package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class ConnectionListener implements Listener {

    private final PracticeCorePlugin plugin;

    public ConnectionListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        // Keeps the name index current so admin commands can tab-complete and
        // resolve this player long after they have gone offline.
        plugin.stats().touch(player);
        // Bot disguise profiles must reach this client before it tracks any
        // bot entity, or running fights' bots stay invisible to them.
        plugin.pvpBot().handleJoin(player);
        // Orphaned snapshot = the server crashed (or cleanup was missed) while
        // this player was practicing. Restoring it covers every such path.
        if (plugin.snapshots().has(id)) {
            plugin.snapshots().load(id).ifPresent(snapshot -> snapshot.apply(player, true));
            plugin.snapshots().delete(id);
            plugin.messages().send(player, "session.state-restored");
            return;
        }
        if (plugin.worldService().isPracticeWorld(player.getWorld())) {
            // In the practice world with no session and no snapshot: evict.
            player.teleport(plugin.leaveService().fallback());
        }
        autoJoin(player);
    }

    /**
     * Drops the player straight into the default arena when the server is set
     * up for that. Deferred a tick so every other join handler — permissions,
     * scoreboard, cosmetics — has finished with the player first.
     */
    private void autoJoin(Player player) {
        if (!plugin.pcConfig().defaultArenaOnServerJoin()) {
            return;
        }
        ArenaTemplate arena = plugin.templates().defaultArena();
        if (arena == null || !player.hasPermission("practicecore.use")
                || !plugin.templates().canUse(player, arena)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(player.getUniqueId()) == null
                    && !plugin.spectate().isSpectator(player.getUniqueId())) {
                plugin.sessions().join(player, arena);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.setup().isAdmin(player.getUniqueId())) {
            plugin.setup().handleQuit(player);
        }
        plugin.sessions().handleQuit(player);
    }
}
