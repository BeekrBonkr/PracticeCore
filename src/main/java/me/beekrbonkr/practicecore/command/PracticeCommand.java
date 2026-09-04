package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.ArenaMenu;
import me.beekrbonkr.practicecore.gui.CategoryMenu;
import me.beekrbonkr.practicecore.gui.LeaderboardMenu;
import me.beekrbonkr.practicecore.gui.MainMenu;
import me.beekrbonkr.practicecore.gui.SettingsMenu;
import me.beekrbonkr.practicecore.gui.StatsMenu;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PracticeCommand implements CommandExecutor, TabCompleter {

    /** Every menu a player can open directly with /practice menu <menu>. */
    private static final List<String> MENUS = List.of(
            "main", "arenas", "categories", "leaderboards", "stats", "settings", "beddefense");

    private final PracticeCorePlugin plugin;
    private final SetupCommands setup;
    private final AdminCommands admin;
    private final RushCommands rush;
    private final BedDefenseCommands bedDefense;

    public PracticeCommand(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.setup = new SetupCommands(plugin);
        this.admin = new AdminCommands(plugin);
        this.rush = new RushCommands(plugin);
        this.bedDefense = new BedDefenseCommands(plugin);
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "spectate", "spec", "watch" -> spectate(sender, args);
            case "list", "arenas" -> list(sender);
            case "menu", "gui" -> menu(sender, args);
            case "top", "leaderboard" -> top(sender, args);
            case "stats" -> stats(sender, args);
            case "sidebar", "scoreboard" -> sidebar(sender);
            case "setup" -> setup.setup(sender, args);
            case "edit" -> setup.edit(sender, args);
            case "rush" -> rush.rush(sender, args);
            case "beddefense", "bd", "defense" -> bedDefense.beddefense(sender, args);
            case "arena" -> admin.arena(sender, args);
            case "pb" -> admin.pb(sender, args);
            case "item" -> admin.item(sender, args);
            case "world" -> admin.world(sender, args);
            case "reload" -> admin.reload(sender, args);
            default -> sendHelp(plugin, sender);
        }
        return true;
    }

    // ------------------------------------------------------------ gameplay

    private void join(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("practicecore.use")) {
            msg().send(player, "permission.use");
            return;
        }
        ArenaTemplate template;
        if (args.length >= 2) {
            template = plugin.templates().get(args[1]);
            if (template == null) {
                msg().send(player, "arena.unknown", "arena", args[1]);
                return;
            }
        } else {
            // A bare /practice join lands on the configured default when there
            // is one and the player may use it, else their first option.
            template = plugin.pcConfig().defaultArenaOnBareJoin()
                    ? plugin.templates().defaultFor(player)
                    : firstAvailable(player);
            if (template == null) {
                msg().send(player, "arena.none-available");
                return;
            }
        }
        plugin.sessions().join(player, template);
    }

    private ArenaTemplate firstAvailable(Player player) {
        List<ArenaTemplate> available = plugin.templates().availableTo(player);
        return available.isEmpty() ? null : available.get(0);
    }

    private void leave(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null && !plugin.worldService().isPracticeWorld(player.getWorld())) {
            msg().send(player, "session.not-practicing");
            return;
        }
        plugin.leaveService().leave(player);
    }

    /**
     * /practice spectate [player|leave] — bare opens the target picker, a name
     * starts (or switches) watching, "leave" stops and restores.
     */
    private void spectate(CommandSender sender, String[] args) {
        runSpectate(plugin, sender, args.length < 2 ? null : args[1]);
    }

    /**
     * The spectate flow itself, shared with the standalone /spectate command:
     * null opens the target picker, "leave"/"stop"/"off" stops watching, a
     * name starts (or switches) watching that player.
     */
    public static void runSpectate(PracticeCorePlugin plugin, CommandSender sender, String arg) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.players-only");
            return;
        }
        if (!player.hasPermission("practicecore.spectate")) {
            msg.send(player, "permission.spectate");
            return;
        }
        if (!plugin.pcConfig().spectateEnabled()) {
            msg.send(player, "spectate.disabled");
            return;
        }
        if (arg == null || arg.isBlank()) {
            new me.beekrbonkr.practicecore.gui.SpectateMenu(plugin, player, null).open();
            return;
        }
        if (arg.equalsIgnoreCase("leave") || arg.equalsIgnoreCase("stop")
                || arg.equalsIgnoreCase("off")) {
            if (!plugin.spectate().stopIntoDefaultArena(player, "spectate.stopped")) {
                msg.send(player, "spectate.not-spectating");
            }
            return;
        }
        Player target = Bukkit.getPlayerExact(arg);
        if (target == null) {
            msg.send(player, "spectate.unknown-player", "player", arg);
            return;
        }
        plugin.spectate().start(player, target);
    }

    private void list(CommandSender sender) {
        List<ArenaTemplate> templates = sender instanceof Player player
                ? plugin.templates().visibleTo(player)
                : List.copyOf(plugin.templates().completeTemplates());
        if (templates.isEmpty()) {
            msg().send(sender, "arena.list-empty");
            return;
        }
        msg().send(sender, "arena.list-header");
        for (ArenaTemplate template : templates) {
            boolean locked = sender instanceof Player player && !plugin.templates().canUse(player, template);
            // The suffix is formatted text, so it goes in as a component —
            // an unparsed placeholder would print its tags literally.
            msg().send(sender, "arena.list-entry",
                    locked ? msg().ref("locked", "arena.list-locked-suffix")
                           : msg().ref("locked", Component.empty()),
                    "arena", template.name(),
                    "display", template.displayName(),
                    "mode", template.mode());
        }
    }

    private void menu(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("practicecore.menu")) {
            msg().send(player, "permission.menu");
            return;
        }
        String which = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "main";
        switch (which) {
            case "main" -> new MainMenu(plugin, player).open();
            case "arenas" -> new ArenaMenu(plugin, player, null, null).open();
            case "categories" -> {
                // With the picker disabled every arena lives in one flat menu,
                // exactly like the Play button.
                if (plugin.guis().categoriesEnabled()) {
                    new CategoryMenu(plugin, player, null).open();
                } else {
                    new ArenaMenu(plugin, player, null, null).open();
                }
            }
            case "leaderboards" -> {
                if (!player.hasPermission("practicecore.leaderboard")) {
                    msg().send(player, "permission.leaderboard");
                    return;
                }
                openLeaderboards(player);
            }
            case "stats" -> new StatsMenu(plugin, player, null).open();
            case "settings" -> new SettingsMenu(plugin, player, null).open();
            case "beddefense" -> new me.beekrbonkr.practicecore.gui.BedDefenseArenaMenu(plugin, player, null).open();
            default -> msg().send(player, "menu.unknown",
                    "menu", args[1],
                    "menus", String.join(", ", MENUS));
        }
    }

    private void sidebar(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        boolean on = !plugin.stats().scoreboardEnabled(player.getUniqueId());
        plugin.stats().setScoreboardEnabled(player.getUniqueId(), on);
        plugin.boards().applyPreference(player);
        msg().send(player, on ? "sidebar.shown" : "sidebar.hidden");
    }

    // ---------------------------------------------------------- leaderboard

    /** Category picker first — unless the admin has flattened the menus. */
    private void openLeaderboards(Player player) {
        if (plugin.guis().categoriesEnabled()) {
            new me.beekrbonkr.practicecore.gui.LeaderboardCategoryMenu(plugin, player, null).open();
        } else {
            new LeaderboardMenu(plugin, player, null).open();
        }
    }

    private void top(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.leaderboard")) {
            msg().send(sender, "permission.leaderboard");
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                openLeaderboards(player);
            } else {
                msg().usage(sender, "/practice top <arena>");
            }
            return;
        }
        String key;
        String display;
        ArenaTemplate template = plugin.templates().get(args[1]);
        if (template != null) {
            if (template.mode().equals(me.beekrbonkr.practicecore.mode.RushMode.ID)) {
                // A rush arena has one board per objective — point at one.
                msg().send(sender, "leaderboard.rush-pick-board", "arena", template.name());
                return;
            }
            if (!plugin.modes().of(template).hasLeaderboards()) {
                // MLG scores streaks, the PvP bot keeps session stats —
                // pointing at their empty time board would just confuse.
                msg().send(sender, "leaderboard.not-ranked", "arena", template.displayName());
                return;
            }
            key = template.name();
            display = template.displayName();
        } else if (args[1].equalsIgnoreCase(me.beekrbonkr.practicecore.mode.BedDefenseMode.ID)) {
            msg().send(sender, "leaderboard.beddefense-pick-board");
            return;
        } else {
            var rushBoard = plugin.rush().resolveStatsKey(args[1].toLowerCase(Locale.ROOT));
            var defenseBoard = plugin.bedDefenses().resolveStatsKey(args[1].toLowerCase(Locale.ROOT));
            if (rushBoard == null && defenseBoard == null) {
                msg().send(sender, "arena.unknown", "arena", args[1]);
                return;
            }
            key = args[1].toLowerCase(Locale.ROOT);
            display = rushBoard != null
                    ? plugin.rush().displayFor(rushBoard.getKey(), rushBoard.getValue())
                    : plugin.bedDefenses().displayFor(defenseBoard.getKey(), defenseBoard.getValue());
        }
        List<LeaderboardService.Entry> top = plugin.leaderboards()
                .top(key, plugin.pcConfig().leaderboardSize());
        if (top.isEmpty()) {
            msg().send(sender, "leaderboard.empty", "arena", display);
            return;
        }
        msg().send(sender, "leaderboard.header", "arena", display);
        for (int i = 0; i < top.size(); i++) {
            LeaderboardService.Entry entry = top.get(i);
            msg().send(sender, "leaderboard.entry",
                    "rank", String.valueOf(i + 1),
                    "player", entry.displayName(),
                    "time", TimeFormat.precise(entry.millis()));
        }
        if (sender instanceof Player player) {
            int rank = plugin.leaderboards().rank(key, player.getUniqueId());
            if (rank > top.size()) {
                msg().send(sender, "leaderboard.your-rank",
                        "rank", String.valueOf(rank),
                        "total", String.valueOf(plugin.leaderboards().size(key)));
            }
        }
    }

    private void stats(CommandSender sender, String[] args) {
        UUID subject;
        String name;
        if (args.length >= 2) {
            // Naming yourself is still a self-lookup — no extra permission.
            boolean self = sender instanceof Player player && args[1].equalsIgnoreCase(player.getName());
            if (!self && !sender.hasPermission("practicecore.stats.other")) {
                msg().send(sender, "permission.stats-other");
                return;
            }
            Optional<UUID> resolved = plugin.stats().uuidOf(args[1]);
            if (resolved.isEmpty()) {
                msg().send(sender, "stats.unknown-player", "player", args[1]);
                return;
            }
            subject = resolved.get();
            name = Optional.ofNullable(plugin.stats().nameOf(subject)).orElse(args[1]);
        } else {
            Player player = asPlayer(sender);
            if (player == null) {
                return;
            }
            subject = player.getUniqueId();
            name = player.getName();
        }
        if (sender instanceof Player player && subject.equals(player.getUniqueId())) {
            new StatsMenu(plugin, player, null).open();
            return;
        }
        printStats(sender, subject, name);
    }

    private void printStats(CommandSender sender, UUID subject, String name) {
        Map<String, Long> bests = plugin.stats().bests(subject);
        if (bests.isEmpty()) {
            msg().send(sender, "stats.empty", "player", name);
        } else {
            msg().send(sender, "stats.header", "player", name);
            bests.forEach((arena, best) -> {
                int rank = plugin.leaderboards().rank(arena, subject);
                msg().send(sender, rank > 0 ? "stats.entry" : "stats.entry-unranked",
                        "arena", arena,
                        "time", TimeFormat.precise(best),
                        "rank", String.valueOf(rank),
                        "finishes", String.valueOf(plugin.stats().finishes(subject, arena)));
            });
        }
        // Reading an offline player's file should not pin it in the cache.
        plugin.stats().unloadIfOffline(subject);
    }

    // ---------------------------------------------------------------- help

    public static void sendHelp(PracticeCorePlugin plugin, CommandSender sender) {
        Messages msg = plugin.messages();
        msg.send(sender, "help.player");
        if (sender.hasPermission("practicecore.setup")) {
            msg.send(sender, "help.setup");
        }
        if (sender.hasPermission("practicecore.arena")) {
            msg.send(sender, "help.arena");
        }
        if (sender.hasPermission("practicecore.item")) {
            msg.send(sender, "help.item");
        }
        if (sender.hasPermission("practicecore.pb.reset")) {
            msg.send(sender, "help.pb");
        }
        if (sender.hasPermission("practicecore.world")) {
            msg.send(sender, "help.world");
        }
        if (sender.hasPermission("practicecore.reload")) {
            msg.send(sender, "help.reload");
        }
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        msg().send(sender, "general.players-only");
        return null;
    }

    // ------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "join", "leave", "spectate", "list", "menu", "top", "stats", "sidebar", "help"));
            if (sender.hasPermission("practicecore.setup")) {
                subs.add("setup");
                subs.add("edit");
                subs.add("rush");
            }
            if (sender.hasPermission("practicecore.arena")) {
                subs.add("arena");
            }
            if (sender.hasPermission("practicecore.pb.reset")) {
                subs.add("pb");
            }
            if (sender.hasPermission("practicecore.item")) {
                subs.add("item");
            }
            if (sender.hasPermission("practicecore.world")) {
                subs.add("world");
            }
            if (sender.hasPermission("practicecore.reload")) {
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> args.length == 2
                    ? filter(sender instanceof Player player
                            ? plugin.templates().availableTo(player).stream().map(ArenaTemplate::name).toList()
                            : plugin.templates().names(), args[1])
                    : List.of();
            case "spectate", "spec", "watch" -> args.length == 2
                    ? filter(spectatableNames(sender), args[1]) : List.of();
            case "menu", "gui" -> args.length == 2 ? filter(MENUS, args[1]) : List.of();
            case "top", "leaderboard" -> args.length == 2
                    ? filter(completeNames(), args[1]) : List.of();
            case "stats" -> args.length == 2 && sender.hasPermission("practicecore.stats.other")
                    ? filter(plugin.stats().knownNames(), args[1]) : List.of();
            case "edit" -> args.length == 2 && sender.hasPermission("practicecore.setup")
                    ? filter(plugin.templates().names(), args[1]) : List.of();
            case "setup" -> setup.complete(sender, args);
            case "rush" -> rush.complete(sender, args);
            case "beddefense", "bd", "defense" -> bedDefense.complete(sender, args);
            case "arena" -> admin.completeArena(sender, args);
            case "pb" -> admin.completePb(sender, args);
            case "item" -> args.length == 2 && sender.hasPermission("practicecore.item")
                    ? filter(onlineNames(), args[1]) : List.of();
            case "world" -> admin.completeWorld(sender, args);
            case "reload" -> args.length == 2 && sender.hasPermission("practicecore.reload")
                    ? filter(List.of("confirm"), args[1]) : List.of();
            default -> List.of();
        };
    }

    private List<String> completeNames() {
        List<String> names = new ArrayList<>();
        for (ArenaTemplate template : plugin.templates().completeTemplates()) {
            if (template.mode().equals(me.beekrbonkr.practicecore.mode.RushMode.ID)) {
                // /practice top rejects the bare name — offer the boards.
                for (me.beekrbonkr.practicecore.rush.RushObjective objective
                        : me.beekrbonkr.practicecore.rush.RushObjective.values()) {
                    names.add(objective.statsKey(template.name()));
                }
            } else {
                names.add(template.name());
            }
        }
        return names;
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /** Everyone currently practicing (minus yourself), plus the leave keyword. */
    private List<String> spectatableNames(CommandSender sender) {
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
        return names;
    }

    static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
