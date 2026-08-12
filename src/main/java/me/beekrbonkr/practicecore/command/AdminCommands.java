package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.ReloadResult;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.setup.SetupManager;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Arena administration, personal-best wiping, the menu item and reload. */
final class AdminCommands {

    private static final List<String> ARENA_ACTIONS = List.of(
            "list", "info", "default", "delete", "permission", "display", "icon", "blocks");

    private final PracticeCorePlugin plugin;

    AdminCommands(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ---------------------------------------------------------------- arena

    void arena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.arena")) {
            msg().send(sender, "permission.arena-admin");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        if (action.equals("list")) {
            listArenas(sender);
            return;
        }
        if (action.equals("help")) {
            arenaHelp(sender);
            return;
        }
        if (action.equals("default")) {
            setDefaultArena(sender, args);
            return;
        }
        if (args.length < 3) {
            arenaHelp(sender);
            return;
        }
        ArenaTemplate template = plugin.templates().get(SetupManager.normalize(args[2]));
        if (template == null) {
            msg().send(sender, "arena.unknown", "arena", args[2]);
            return;
        }
        if (!action.equals("info") && template.name().equals(plugin.setup().activeName())) {
            msg().problem(sender, "'" + template.name() + "' is open in the setup wizard — "
                    + "finish or cancel that first, or use /practice setup " + action + ".");
            return;
        }
        switch (action) {
            case "info" -> arenaInfo(sender, template);
            case "delete" -> deleteArena(sender, template, args);
            case "permission" -> {
                if (args.length < 4) {
                    msg().send(sender, "general.usage", "usage", "/practice arena permission <arena> <node|default>");
                    return;
                }
                String node = args[3];
                boolean reset = node.equalsIgnoreCase("none") || node.equalsIgnoreCase("default");
                template.setPermission(reset ? null : node);
                persist(sender, template, "'" + template.name() + "' is now governed by "
                        + plugin.templates().permissionFor(template)
                        + (reset ? " (this arena's default node)" : ""));
            }
            case "display" -> {
                if (args.length < 4) {
                    msg().send(sender, "general.usage", "usage", "/practice arena display <arena> <text…>");
                    return;
                }
                template.setDisplayName(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                persist(sender, template, "Display name set to '" + template.displayName() + "'.");
            }
            case "icon" -> {
                if (args.length < 4) {
                    msg().send(sender, "general.usage", "usage", "/practice arena icon <arena> <material|auto>");
                    return;
                }
                if (args[3].equalsIgnoreCase("auto")) {
                    template.setIcon(null);
                    persist(sender, template, "Icon for '" + template.name() + "' back to automatic.");
                    return;
                }
                Material material = Material.matchMaterial(args[3]);
                if (material == null || !material.isItem()) {
                    msg().send(sender, "setup.icon-invalid", "material", args[3]);
                    return;
                }
                template.setIcon(material);
                persist(sender, template, "Icon for '" + template.name() + "' set to " + material + ".");
            }
            case "blocks" -> {
                boolean require = args.length > 3 && Boolean.parseBoolean(args[3]);
                template.setRequireBlocksForPb(require);
                persist(sender, template, require
                        ? "Personal bests on '" + template.name() + "' now require a placed block."
                        : "Personal bests on '" + template.name() + "' no longer require placed blocks.");
            }
            default -> arenaHelp(sender);
        }
    }

    /**
     * Writes default-arena.name into config.yml so admins do not have to edit
     * the file and reload to change where players land.
     */
    private void setDefaultArena(CommandSender sender, String[] args) {
        if (args.length < 3) {
            String current = plugin.pcConfig().defaultArenaName();
            msg().note(sender, "Default arena: " + (current.isEmpty() ? "none" : current)
                    + (plugin.templates().defaultArena() == null && !current.isEmpty()
                        ? " (not a finished arena — currently unused)" : ""));
            msg().note(sender, "Set it with /practice arena default <arena|none>.");
            return;
        }
        String name = SetupManager.normalize(args[2]);
        if (name.equals("none") || name.equals("clear")) {
            plugin.setConfigValue("default-arena.name", "");
            msg().done(sender, "Default arena cleared.");
            return;
        }
        ArenaTemplate template = plugin.templates().get(name);
        if (template == null) {
            msg().send(sender, "arena.unknown", "arena", args[2]);
            return;
        }
        if (!template.isComplete()) {
            msg().send(sender, "arena.incomplete", "arena", template.name());
            return;
        }
        plugin.setConfigValue("default-arena.name", template.name());
        msg().done(sender, "Default arena set to '" + template.name() + "'.");
        msg().note(sender, "Applies to: bare /practice join"
                + (plugin.pcConfig().defaultArenaOnWorldEnter() ? ", practice-world entry" : "")
                + (plugin.pcConfig().defaultArenaOnServerJoin() ? ", server join" : "")
                + ". Toggle those under default-arena in config.yml.");
    }

    private void listArenas(CommandSender sender) {
        if (plugin.templates().all().isEmpty()) {
            msg().note(sender, "No arenas exist. Create one with /practice setup start <name>.");
            return;
        }
        String preferred = plugin.pcConfig().defaultArenaName();
        msg().note(sender, "Arenas (" + plugin.templates().all().size() + "), access-mode "
                + plugin.pcConfig().arenaAccessMode() + ":");
        for (ArenaTemplate template : plugin.templates().all()) {
            msg().note(sender, "  " + template.name() + " — " + template.displayName()
                    + " [" + template.mode() + "]"
                    + (template.isComplete() ? "" : " (incomplete)")
                    + (template.name().equals(preferred) ? " (default)" : "")
                    + " {" + plugin.templates().permissionFor(template) + "}");
        }
    }

    private void arenaInfo(CommandSender sender, ArenaTemplate template) {
        msg().note(sender, "Arena '" + template.name() + "'");
        msg().note(sender, "  display-name: " + template.displayName());
        msg().note(sender, "  mode: " + template.mode());
        msg().note(sender, "  complete: " + template.isComplete());
        msg().note(sender, "  permission: " + plugin.templates().permissionFor(template)
                + (template.permission() == null ? " (default node)" : " (explicit)")
                + ", access-mode " + plugin.pcConfig().arenaAccessMode());
        msg().note(sender, "  default arena: "
                + template.name().equals(plugin.pcConfig().defaultArenaName()));
        msg().note(sender, "  icon: " + (template.icon() != null
                ? template.icon().name() : "auto → " + template.effectiveIcon()));
        msg().note(sender, "  kit: " + template.kit().size() + " stack(s)");
        msg().note(sender, "  pb requires blocks: " + template.requireBlocksForPb());
        msg().note(sender, "  triggers: " + (template.hasTriggers()
                ? template.triggers().size() + " placed" : "not set"));
        msg().note(sender, "  ranked players: " + plugin.leaderboards().size(template.name()));
        msg().note(sender, "  folder: " + template.dir().getPath());
    }

    private void deleteArena(CommandSender sender, ArenaTemplate template, String[] args) {
        boolean confirmed = args.length > 3 && args[3].equalsIgnoreCase("confirm");
        if (!confirmed) {
            msg().problem(sender, "This permanently deletes the arena folder, its schematic, "
                    + "its leaderboard and every recorded time on it.");
            msg().note(sender, "Run /practice arena delete " + template.name() + " confirm to go ahead.");
            return;
        }
        String name = template.name();
        if (!plugin.templates().deleteCompletely(name, wiped -> msg().done(sender,
                "Leaderboard for '" + name + "' cleared (" + wiped + " player record(s))."))) {
            msg().problem(sender, "Could not delete '" + name + "'.");
            return;
        }
        msg().done(sender, "Deleted arena '" + name + "'. Clearing its recorded times…");
    }

    private void persist(CommandSender sender, ArenaTemplate template, String message) {
        try {
            template.save();
        } catch (IOException e) {
            msg().problem(sender, "Could not write arena.yml: " + e.getMessage());
            return;
        }
        msg().done(sender, message);
    }

    private void arenaHelp(CommandSender sender) {
        msg().note(sender, "/practice arena list");
        msg().note(sender, "/practice arena info <arena>");
        msg().note(sender, "/practice arena default [arena|none]");
        msg().note(sender, "/practice arena display <arena> <text…>");
        msg().note(sender, "/practice arena icon <arena> <material|auto>");
        msg().note(sender, "/practice arena permission <arena> <node|none>");
        msg().note(sender, "/practice arena blocks <arena> <true|false>");
        msg().note(sender, "/practice arena delete <arena> confirm");
    }

    // ------------------------------------------------------------------- pb

    void pb(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            resetPb(sender, args);
            return;
        }
        msg().note(sender, "/practice pb reset <player> [arena|all] — wipe personal bests");
    }

    private void resetPb(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.pb.reset")) {
            msg().send(sender, "permission.pb-reset");
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "general.usage", "usage", "/practice pb reset <player> [arena|all]");
            return;
        }
        Optional<UUID> resolved = plugin.stats().uuidOf(args[2]);
        if (resolved.isEmpty()) {
            msg().problem(sender, "No player named '" + args[2] + "' in the plugin's records "
                    + "(" + plugin.stats().knownPlayerCount() + " known).");
            return;
        }
        UUID target = resolved.get();
        String name = Optional.ofNullable(plugin.stats().nameOf(target)).orElse(args[2]);
        String scope = args.length > 3 ? SetupManager.normalize(args[3]) : "all";

        if (scope.equals("all")) {
            int wiped = plugin.stats().resetAll(target);
            msg().done(sender, wiped == 0
                    ? name + " had no recorded times."
                    : "Wiped " + name + "'s times on " + wiped + " arena(s).");
        } else {
            // Rush composite keys ("map#bed") are valid wipe scopes too.
            if (plugin.templates().get(scope) == null
                    && plugin.rush().resolveStatsKey(scope) == null) {
                msg().problem(sender, "No arena named '" + scope + "'. Use 'all' to wipe everything.");
                return;
            }
            boolean wiped = plugin.stats().resetTemplate(target, scope);
            msg().done(sender, wiped
                    ? "Wiped " + name + "'s times on '" + scope + "'."
                    : name + " had no times on '" + scope + "'.");
        }
        refreshBoards(target);
        Player online = Bukkit.getPlayer(target);
        if (online != null && !online.equals(sender)) {
            msg().send(online, "stats.reset-notice");
        }
    }

    /** A live session caches its best time — resync it after a wipe. */
    private void refreshBoards(UUID target) {
        PracticeSession session = plugin.sessions().get(target);
        if (session != null) {
            String key = session.mode().statsKey(plugin, session);
            session.setBestTimeMs(plugin.stats().bestMs(target, key));
            session.setLastTimeMs(plugin.stats().lastMs(target, key));
        }
    }

    // ----------------------------------------------------------------- item

    void item(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.item")) {
            msg().send(sender, "permission.item");
            return;
        }
        if (!plugin.pcConfig().menuItemEnabled()) {
            msg().send(sender, "menu.item-disabled");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg().send(sender, "menu.player-offline", "player", args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            msg().send(sender, "general.usage", "usage", "/practice item <player>");
            return;
        }
        plugin.menuItems().give(target);
        msg().send(target, "menu.item-given", "slot", String.valueOf(plugin.pcConfig().menuItemSlot() + 1));
        if (!target.equals(sender)) {
            msg().send(sender, "menu.item-given-other", "player", target.getName());
        } else {
            msg().send(target, "menu.item-given-hint");
        }
    }

    // --------------------------------------------------------------- reload

    void reload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.reload")) {
            msg().send(sender, "permission.reload");
            return;
        }
        boolean force = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        ReloadResult result = plugin.reload(force);
        result.notes().forEach(note -> msg().note(sender, note));
        if (result.ok()) {
            msg().done(sender, "PracticeCore reloaded.");
        } else if (result.needsConfirm()) {
            msg().problem(sender, "Nothing was changed — confirmation required.");
        } else {
            msg().problem(sender, "Reload failed; the previous settings are still running.");
            plugin.getLogger().warning("Reload requested by " + sender.getName()
                    + " failed: " + String.join(" | ", result.notes()));
        }
    }

    // ---------------------------------------------------------------- world

    void world(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.world")) {
            msg().send(sender, "permission.world");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "info" -> worldInfo(sender);
            case "regen", "regenerate" -> regenerateWorld(sender, args);
            default -> {
                msg().note(sender, "/practice world info — practice world and grid status");
                msg().note(sender, "/practice world regen confirm — delete and rebuild it from nothing");
            }
        }
    }

    private void worldInfo(CommandSender sender) {
        World world = plugin.worldService().world();
        msg().note(sender, "Practice world '" + plugin.pcConfig().worldName() + "'");
        if (world == null) {
            msg().problem(sender, "  not loaded — run /practice world regen confirm");
            return;
        }
        msg().note(sender, "  players inside: " + world.getPlayers().size()
                + " (" + plugin.sessions().all().size() + " in a session)");
        msg().note(sender, "  grid slots allocated: " + plugin.allocator().size()
                + " at " + plugin.pcConfig().gridSpacing() + "-block spacing, y="
                + plugin.pcConfig().baseY());
        msg().note(sender, "  loaded chunks: " + world.getLoadedChunks().length);
        msg().note(sender, "  setup wizard: " + Optional.ofNullable(plugin.setup().activeName())
                .map(name -> "open on '" + name + "'").orElse("idle"));
    }

    private void regenerateWorld(CommandSender sender, String[] args) {
        boolean confirmed = args.length > 2 && args[2].equalsIgnoreCase("confirm");
        int running = plugin.sessions().all().size();
        String wizard = plugin.setup().activeName();
        if (!confirmed) {
            msg().problem(sender, "This unloads, deletes and rebuilds '"
                    + plugin.pcConfig().worldName() + "' from nothing.");
            if (running > 0) {
                msg().note(sender, running + " player(s) are practicing — they are restored to "
                        + "where they came from first, so nothing is lost.");
            }
            if (wizard != null) {
                msg().note(sender, "The setup wizard on '" + wizard
                        + "' will be cancelled; unsaved changes to it are lost.");
            }
            msg().note(sender, "Arenas, kits and leaderboards are untouched — only the world is rebuilt.");
            msg().note(sender, "Run /practice world regen confirm to go ahead.");
            return;
        }
        try {
            int ended = plugin.worldService().regenerate();
            msg().done(sender, "Practice world regenerated; the grid starts again at slot 0."
                    + (ended > 0 ? " " + ended + " session(s) were ended and restored." : ""));
        } catch (RuntimeException e) {
            msg().problem(sender, "Regeneration failed: " + e.getMessage());
            msg().problem(sender, "The practice world is unavailable until the server restarts.");
            plugin.getLogger().severe("Practice world regeneration failed: " + e);
        }
    }

    // ------------------------------------------------------ tab completion

    List<String> completeArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.arena")) {
            return List.of();
        }
        if (args.length == 2) {
            return PracticeCommand.filter(ARENA_ACTIONS, args[1]);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && !action.equals("list")) {
            List<String> names = new ArrayList<>(plugin.templates().names());
            if (action.equals("default")) {
                names.add("none");
            }
            return PracticeCommand.filter(names, args[2]);
        }
        if (args.length == 4) {
            return switch (action) {
                case "delete" -> PracticeCommand.filter(List.of("confirm"), args[3]);
                case "blocks" -> PracticeCommand.filter(List.of("true", "false"), args[3]);
                case "permission" -> PracticeCommand.filter(
                        List.of("default", plugin.pcConfig().arenaPermissionPrefix() + args[2]), args[3]);
                case "icon" -> PracticeCommand.filter(itemMaterials(), args[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    List<String> completeWorld(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.world")) {
            return List.of();
        }
        if (args.length == 2) {
            return PracticeCommand.filter(List.of("info", "regen"), args[1]);
        }
        if (args.length == 3 && args[1].toLowerCase(Locale.ROOT).startsWith("regen")) {
            return PracticeCommand.filter(List.of("confirm"), args[2]);
        }
        return List.of();
    }

    List<String> completePb(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.pb.reset")) {
            return List.of();
        }
        if (args.length == 2) {
            return PracticeCommand.filter(List.of("reset"), args[1]);
        }
        if (args.length == 3) {
            // Every player the plugin remembers — not just the online ones.
            return PracticeCommand.filter(plugin.stats().knownNames(), args[2]);
        }
        if (args.length == 4) {
            List<String> scopes = new ArrayList<>(plugin.templates().names());
            scopes.add("all");
            return PracticeCommand.filter(scopes, args[3]);
        }
        return List.of();
    }

    private static List<String> itemMaterials() {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
