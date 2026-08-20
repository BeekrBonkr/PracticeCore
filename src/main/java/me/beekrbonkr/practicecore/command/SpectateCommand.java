package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The standalone {@code /spectate <player>} command — the exact flow of
 * {@code /practice spectate}, one word shorter. Bare opens the target picker,
 * "leave" stops watching.
 */
public final class SpectateCommand implements CommandExecutor, TabCompleter {

    private final PracticeCorePlugin plugin;

    public SpectateCommand(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PracticeCommand.runSpectate(plugin, sender, args.length < 1 ? null : args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        names.add("leave");
        for (PracticeSession session : plugin.sessions().all()) {
            if (sender instanceof Player player
                    && session.playerId().equals(player.getUniqueId())) {
                continue;
            }
            Player practicing = Bukkit.getPlayer(session.playerId());
            if (practicing != null) {
                names.add(practicing.getName());
            }
        }
        return PracticeCommand.filter(names, args[0]);
    }
}
