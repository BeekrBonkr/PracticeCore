package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public final class TeleportListener implements Listener {

    private final PracticeCorePlugin plugin;

    public TeleportListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.sessions().isInternalTeleport(id)) {
            return;
        }
        PracticeSession session = plugin.sessions().get(id);
        Location to = event.getTo();
        boolean toPractice = plugin.worldService().isPracticeWorld(to.getWorld());

        if (session != null) {
            switch (event.getCause()) {
                case ENDER_PEARL, CHORUS_FRUIT -> {
                    event.setCancelled(true);
                    return;
                }
                default -> {
                }
            }
            boolean insideArena = toPractice && session.bounds().contains(to.toVector());
            if (!insideArena) {
                // /spawn, /tpa, /home from another plugin: never fight the
                // teleport (that causes loops) — end the session, restore
                // everything except location, and let the destination win.
                plugin.sessions().leave(player, false);
            }
            return;
        }

        if (plugin.setup().isAdmin(id)) {
            return;
        }
        if (toPractice && !player.hasPermission("practicecore.bypass")) {
            // Blocks /back into a freed arena and any other sessionless entry.
            event.setCancelled(true);
            Msg.error(player, "Use /practice join to enter the practice world.");
        }
    }

    /** Backup for any world-change path that skipped PlayerTeleportEvent. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session != null && !plugin.worldService().isPracticeWorld(player.getWorld())) {
            plugin.sessions().leave(player, false);
        }
    }
}
