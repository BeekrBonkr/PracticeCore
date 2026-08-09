package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PCConfig;
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
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !event.hasChangedBlock()) {
            return;
        }
        SessionState state = session.state();
        if (state != SessionState.READY && state != SessionState.ACTIVE) {
            return;
        }
        Location to = event.getTo();
        if (to.getY() < session.bounds().getMinY() + plugin.pcConfig().failYOffset()) {
            plugin.sessions().fail(player, session);
            return;
        }
        if (session.isOutsideWalls(to)) {
            event.setTo(event.getFrom());
            return;
        }
        if (state == SessionState.READY
                && session.mode().usesStandardTimerStart()
                && plugin.pcConfig().timerStartMode() == PCConfig.TimerStartMode.MOVE
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
