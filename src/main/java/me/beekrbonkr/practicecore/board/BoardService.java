package me.beekrbonkr.practicecore.board;

import fr.mrmicky.fastboard.adventure.FastBoard;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        board.updateTitle(Component.text("Practice", NamedTextColor.GOLD, TextDecoration.BOLD));
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
        for (Map.Entry<UUID, FastBoard> entry : boards.entrySet()) {
            PracticeSession session = plugin.sessions().get(entry.getKey());
            if (session == null) {
                continue;
            }
            int rank = plugin.leaderboards().rank(session.template().name(), entry.getKey());
            entry.getValue().updateLines(
                    Component.empty(),
                    Component.text("Arena: ", NamedTextColor.GRAY)
                            .append(Component.text(session.template().displayName(), NamedTextColor.WHITE)),
                    Component.empty(),
                    timerLine(session),
                    Component.text("Last: ", NamedTextColor.GRAY)
                            .append(time(session.lastTimeMs())),
                    Component.text("Best: ", NamedTextColor.GRAY)
                            .append(time(session.bestTimeMs())),
                    Component.text("Rank: ", NamedTextColor.GRAY)
                            .append(rank > 0
                                    ? Component.text("#" + rank, NamedTextColor.WHITE)
                                    : Component.text("—", NamedTextColor.DARK_GRAY)),
                    Component.empty(),
                    Component.text("Blocks: ", NamedTextColor.GRAY)
                            .append(Component.text(session.tracker().count(), NamedTextColor.WHITE)));
        }
    }

    private Component timerLine(PracticeSession session) {
        if (session.state() == SessionState.ACTIVE) {
            return Component.text("Time: ", NamedTextColor.GRAY)
                    .append(Component.text(TimeFormat.tenths(session.elapsedMs()), NamedTextColor.YELLOW));
        }
        return Component.text("Time: ", NamedTextColor.GRAY)
                .append(Component.text("ready", NamedTextColor.DARK_GRAY));
    }

    private Component time(long millis) {
        return millis >= 0
                ? Component.text(TimeFormat.tenths(millis), NamedTextColor.WHITE)
                : Component.text("—", NamedTextColor.DARK_GRAY);
    }
}
