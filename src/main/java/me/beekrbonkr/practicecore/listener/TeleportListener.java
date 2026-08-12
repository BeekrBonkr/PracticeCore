package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Bukkit;
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

    /**
     * Veto pass: the only teleports this cancels are ones that would break a
     * run or smuggle a sessionless player into the practice world.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.sessions().isInternalTeleport(id) || plugin.setup().isAdmin(id)) {
            return;
        }
        if (plugin.sessions().get(id) != null) {
            switch (event.getCause()) {
                case ENDER_PEARL, CHORUS_FRUIT -> event.setCancelled(true);
                default -> {
                }
            }
            return;
        }
        if (!plugin.worldService().isPracticeWorld(event.getTo().getWorld())
                || player.hasPermission("practicecore.bypass")) {
            return;
        }
        // Sessionless arrival in the practice world. Either drop them into the
        // default arena or turn them away — never leave them in the void.
        event.setCancelled(true);
        ArenaTemplate fallback = plugin.templates().defaultArena();
        if (plugin.pcConfig().defaultArenaOnWorldEnter()
                && fallback != null
                && player.hasPermission("practicecore.use")
                && plugin.templates().canUse(player, fallback)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.sessions().get(id) == null) {
                    plugin.sessions().join(player, fallback);
                }
            });
            return;
        }
        plugin.messages().send(player, "world.entry-denied");
    }

    /**
     * Outcome pass: a teleport that actually went through and left the arena
     * behind ends the session. Running at MONITOR means a teleport some other
     * plugin canceled later can never end a run that never moved.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (plugin.sessions().isInternalTeleport(id)) {
            return;
        }
        PracticeSession session = plugin.sessions().get(id);
        if (session == null) {
            return;
        }
        Location to = event.getTo();
        boolean insideArena = plugin.worldService().isPracticeWorld(to.getWorld())
                && session.bounds().contains(to.toVector());
        if (!insideArena) {
            // /spawn, /tpa, /home, a warp out of the world — never fight the
            // teleport (that causes loops). End the session, restore everything
            // except location, and let the destination win.
            plugin.sessions().leave(player, false);
        }
    }

    /** Backup for any world-change path that skipped PlayerTeleportEvent. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessions().get(player.getUniqueId()) != null
                && !plugin.worldService().isPracticeWorld(player.getWorld())) {
            plugin.sessions().leave(player, false);
        }
    }
}
