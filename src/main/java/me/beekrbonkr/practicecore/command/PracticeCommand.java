package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class PracticeCommand implements CommandExecutor, TabCompleter {

    private final PracticeCorePlugin plugin;

    public PracticeCommand(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Players only.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "join" -> join(player, args);
            case "leave" -> leave(player);
            case "list" -> list(player);
            case "setup" -> setup(player, args);
            default -> help(player);
        }
        return true;
    }

    private void join(Player player, String[] args) {
        if (!player.hasPermission("practicecore.use")) {
            Msg.error(player, "You may not use practice arenas.");
            return;
        }
        ArenaTemplate template;
        if (args.length >= 2) {
            template = plugin.templates().get(args[1]);
            if (template == null) {
                Msg.error(player, "No template named '" + args[1] + "'. See /practice list.");
                return;
            }
        } else {
            List<ArenaTemplate> complete = plugin.templates().completeTemplates();
            if (complete.isEmpty()) {
                Msg.error(player, "No arenas are configured yet.");
                return;
            }
            template = complete.get(0);
        }
        plugin.sessions().join(player, template);
    }

    private void leave(Player player) {
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "You are not practicing.");
            return;
        }
        plugin.sessions().leave(player, true);
    }

    private void list(Player player) {
        if (plugin.templates().all().isEmpty()) {
            Msg.info(player, "No templates exist yet. Admins: /practice setup start <name>");
            return;
        }
        Msg.info(player, "Arena templates:");
        for (ArenaTemplate template : plugin.templates().all()) {
            Msg.info(player, "  " + template.name() + " [" + template.mode() + "]"
                    + (template.isComplete() ? "" : " (incomplete)"));
        }
    }

    private void setup(Player player, String[] args) {
        if (!player.hasPermission("practicecore.admin")) {
            Msg.error(player, "You may not configure arenas.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "start" -> {
                if (args.length < 3) {
                    Msg.error(player, "Usage: /practice setup start <name>  (with your arena on the WorldEdit clipboard)");
                    return;
                }
                plugin.setup().start(player, args[2].toLowerCase(Locale.ROOT));
            }
            case "spawn" -> plugin.setup().setSpawn(player);
            case "kit" -> plugin.setup().saveKit(player);
            case "save" -> plugin.setup().save(player);
            case "cancel" -> plugin.setup().cancel(player);
            default -> {
                Msg.info(player, "/practice setup start <name> — begin (needs //copy'd build)");
                Msg.info(player, "/practice setup spawn — set the player spawn where you stand");
                Msg.info(player, "/practice setup kit — save your current inventory as the kit");
                Msg.info(player, "/practice setup save | cancel");
            }
        }
    }

    private void help(Player player) {
        Msg.info(player, "/practice join [template] — start practicing");
        Msg.info(player, "/practice leave — return to where you were");
        Msg.info(player, "/practice list — available arenas");
        if (player.hasPermission("practicecore.admin")) {
            Msg.info(player, "/practice setup — configure arena templates");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("join", "leave", "list", "setup"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return filter(plugin.templates().completeTemplates().stream()
                    .map(ArenaTemplate::name).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return filter(List.of("start", "spawn", "kit", "save", "cancel"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
