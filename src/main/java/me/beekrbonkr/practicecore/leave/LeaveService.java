package me.beekrbonkr.practicecore.leave;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The single exit path used by /practice leave and the GUI's leave button.
 *
 * A player is always restored first (inventory, location, gamemode, …) and
 * only then, when {@code leave.server} is configured, handed to the proxy.
 * That ordering matters: whatever the proxy does with them afterwards, their
 * pre-practice state is already what gets written to disk.
 */
public final class LeaveService {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final PracticeCorePlugin plugin;

    public LeaveService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** @return false when there was nothing to leave. */
    public boolean leave(Player player) {
        if (plugin.spectate().stop(player, true, "spectate.stopped")) {
            return true;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        boolean wasPracticing = session != null;
        boolean inPracticeWorld = plugin.worldService().isPracticeWorld(player.getWorld());
        if (wasPracticing) {
            plugin.sessions().leave(player, true);
        } else if (inPracticeWorld) {
            // Sessionless (bypass/spectating admin) — still needs a way out.
            player.teleport(fallback());
        }

        String server = plugin.pcConfig().leaveServer();
        if (!server.isEmpty()) {
            plugin.messages().send(player, "leave.sending", "server", server);
            // A tick of daylight between the restore and the transfer so the
            // restored inventory is what the proxy hand-off persists.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    connect(player, server);
                }
            }, plugin.pcConfig().leaveTransferDelayTicks());
            return true;
        }
        if (wasPracticing || inPracticeWorld) {
            return true;
        }
        if (!plugin.pcConfig().leaveFallbackWorld().isEmpty()) {
            player.teleport(fallback());
            return true;
        }
        plugin.messages().send(player, "session.not-practicing");
        return false;
    }

    private void connect(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }

    /** Configured fallback world spawn, else the server's main world spawn. */
    public org.bukkit.Location fallback() {
        String name = plugin.pcConfig().leaveFallbackWorld();
        if (!name.isEmpty()) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                return world.getSpawnLocation();
            }
            plugin.getLogger().warning("leave.fallback-world '" + name + "' does not exist");
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public static String channel() {
        return BUNGEE_CHANNEL;
    }
}
