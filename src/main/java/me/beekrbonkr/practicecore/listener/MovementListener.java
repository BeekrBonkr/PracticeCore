package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class MovementListener implements Listener {

    private final PracticeCorePlugin plugin;

    public MovementListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Cheapest check first — most move events are sub-block and every
        // player fires them constantly.
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            return;
        }
        SessionState state = session.state();
        if (state != SessionState.READY && state != SessionState.ACTIVE) {
            return;
        }
        Location to = event.getTo();
        if (to.getY() < session.bounds().getMinY() + plugin.pcConfig().failYOffset()) {
            // Deferred a tick: the fail teleports, and teleporting out of a
            // move event mid-unwind fights the client's in-flight packets.
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.sessions().get(player.getUniqueId()) == session
                        && player.getLocation().getY()
                        < session.bounds().getMinY() + plugin.pcConfig().failYOffset()) {
                    plugin.sessions().fail(player, session);
                }
            });
            return;
        }
        if (session.isOutsideWalls(to)) {
            // Push back position only — snapping yaw/pitch too rubber-bands
            // the camera of a player turning while pressed against the wall.
            Location back = event.getFrom().clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            return;
        }
        if (state == SessionState.READY
                && session.mode().startsTimerOnMove(plugin.pcConfig())
                && !sameBlock(to, session.spawn())) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
