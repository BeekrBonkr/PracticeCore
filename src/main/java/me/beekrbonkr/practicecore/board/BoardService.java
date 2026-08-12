package me.beekrbonkr.practicecore.board;

import fr.mrmicky.fastboard.adventure.FastBoard;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar per session via FastBoard (packet-based, flicker-free). One global
 * task refreshes all boards; the live timer shows tenths — the finest display
 * that reads honestly at 20 ticks/second.
 */
public final class BoardService {

    private final PracticeCorePlugin plugin;
    private final Map<UUID, FastBoard> boards = new HashMap<>();
    private BukkitTask task;

    public BoardService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll,
                plugin.pcConfig().scoreboardTicks(), plugin.pcConfig().scoreboardTicks());
    }

    /** Picks up a changed scoreboard.update-ticks after /practice reload. */
    public void restartTask() {
        if (task != null) {
            task.cancel();
        }
        startTask();
    }

    public void create(Player player) {
        remove(player);
        if (!plugin.stats().scoreboardEnabled(player.getUniqueId())) {
            return; // hidden by the player's own preference
        }
        FastBoard board = new FastBoard(player);
        board.updateTitle(plugin.messages().component("board.title"));
        boards.put(player.getUniqueId(), board);
    }

    /** Re-evaluates the player's sidebar preference (menu toggle, /practice sidebar). */
    public void applyPreference(Player player) {
        if (plugin.sessions().get(player.getUniqueId()) == null) {
            remove(player);
            return;
        }
        create(player);
    }

    public void remove(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) {
            board.delete();
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (FastBoard board : boards.values()) {
            if (!board.isDeleted()) {
                board.delete();
            }
        }
        boards.clear();
    }

    private void updateAll() {
        // Loop invariants: parsed once per pass, not once per player.
        Component ready = null;
        String none = null;
        for (Map.Entry<UUID, FastBoard> entry : boards.entrySet()) {
            PracticeSession session = plugin.sessions().get(entry.getKey());
            if (session == null) {
                continue;
            }
            if (none == null) {
                ready = plugin.messages().component("board.timer-ready");
                none = plugin.messages().raw("gui.none");
            }
            // Modes that rank something other than the plain timer draw their
            // own board (streak counters, course progress, blocks left).
            java.util.List<Component> custom = session.mode().boardLines(plugin, session);
            if (custom != null) {
                entry.getValue().updateLines(custom.toArray(Component[]::new));
                continue;
            }
            int rank = plugin.leaderboards().rank(session.template().name(), entry.getKey());
            var msg = plugin.messages();
            Component timer = session.state() == SessionState.ACTIVE
                    ? msg.component("board.timer-running",
                            "time", TimeFormat.tenths(session.elapsedMs()))
                    : ready;
            java.util.List<Component> lines = msg.lore("board.lines",
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                            msg.ref("time", timer)),
                    "arena", session.template().displayName(),
                    "last", time(session.lastTimeMs(), none),
                    "best", time(session.bestTimeMs(), none),
                    "rank", rank > 0 ? "#" + rank : none,
                    "blocks", String.valueOf(session.tracker().count()));
            entry.getValue().updateLines(lines.toArray(Component[]::new));
        }
    }

    private static String time(long millis, String none) {
        return millis >= 0 ? TimeFormat.tenths(millis) : none;
    }
}
