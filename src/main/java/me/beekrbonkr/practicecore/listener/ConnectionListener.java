package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.Msg;
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
        // Orphaned snapshot = the server crashed (or cleanup was missed) while
        // this player was practicing. Restoring it covers every such path.
        if (plugin.snapshots().has(id)) {
            plugin.snapshots().load(id).ifPresent(snapshot -> snapshot.apply(player, true));
            plugin.snapshots().delete(id);
            Msg.info(player, "Your pre-practice state was restored.");
            return;
        }
        if (plugin.worldService().isPracticeWorld(player.getWorld())) {
            // In the practice world with no session and no snapshot: evict.
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
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
