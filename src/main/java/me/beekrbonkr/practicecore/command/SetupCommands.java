package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.setup.SetupManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** The /practice setup and /practice edit branches. */
final class SetupCommands {

    private static final List<String> ACTIONS = List.of(
            "start", "edit", "gui", "spawn", "kit", "trigger", "capture", "schematic", "icon",
            "display", "permission", "blocks", "mode", "category", "rush", "pvpbot",
            "info", "save", "cancel");

    /** Every item material name, computed once — the registry is large. */
    private static final List<String> MATERIAL_NAMES = java.util.Arrays.stream(Material.values())
            .filter(Material::isItem)
            .map(material -> material.name().toLowerCase(Locale.ROOT))
            .toList();

    private static final List<String> RUSH_ACTIONS = List.of(
            "team", "bed", "gen", "dealer", "clear");

    private static final List<String> PVPBOT_ACTIONS = List.of("bot", "clear");

    private static final List<String> RUSH_TEAMS = List.of(
            "red", "blue", "green", "yellow", "aqua", "white", "pink", "gray");

    private final PracticeCorePlugin plugin;

    SetupCommands(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    void setup(CommandSender sender, String[] args) {
        Player admin = require(sender);
        if (admin == null) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        SetupManager wizard = plugin.setup();
        switch (action) {
            case "start" -> {
                if (args.length < 3) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup start <name> (with your arena on the WorldEdit clipboard)");
                    return;
                }
                wizard.start(admin, SetupManager.normalize(args[2]));
            }
            case "edit" -> edit(sender, shift(args));
            case "spawn" -> wizard.setSpawn(admin);
            case "kit" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("load")) {
                    wizard.loadKit(admin);
                } else {
                    wizard.saveKit(admin);
                }
            }
            case "capture" -> wizard.capture(admin);
            case "schematic" -> wizard.replaceSchematic(admin);
            case "icon" -> {
                if (args.length > 2) {
                    Material material = material(admin, args[2]);
                    if (material != null) {
                        wizard.setIcon(admin, material);
                    }
                } else {
                    wizard.setIcon(admin, null); // falls back to the held item
                }
            }
            case "display" -> {
                if (args.length < 3) {
                    plugin.messages().send(admin, "general.usage", "usage", "/practice setup display <text…>");
                    return;
                }
                wizard.setDisplayName(admin, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            }
            case "permission" -> wizard.setPermission(admin, args.length > 2 ? args[2] : null);
            case "blocks" -> {
                // Boolean.parseBoolean would read any typo as false — insist
                // on a real answer instead of confidently saving the wrong one.
                if (args.length < 3 || !(args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("false"))) {
                    plugin.messages().send(admin, "general.usage", "usage", "/practice setup blocks <true|false>");
                    return;
                }
                wizard.setRequireBlocks(admin, args[2].equalsIgnoreCase("true"));
            }
            case "mode" -> {
                if (args.length < 3) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup mode <" + String.join("|", plugin.modes().ids()) + ">");
                    return;
                }
                wizard.setMode(admin, args[2].toLowerCase(Locale.ROOT));
            }
            case "category" -> wizard.setCategory(admin, args.length > 2 ? args[2] : null);
            case "trigger" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("clear")) {
                    wizard.clearTriggers(admin);
                } else {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup trigger clear (placing a button or plate adds one)");
                }
            }
            case "rush" -> rush(admin, args);
            case "pvpbot" -> pvpbot(admin, args);
            case "gui" -> me.beekrbonkr.practicecore.gui.admin.SetupGui.open(plugin, admin);
            case "info" -> wizard.info(admin);
            case "save" -> wizard.save(admin);
            case "cancel" -> wizard.cancel(admin);
            default -> help(admin);
        }
    }

    /** The rush layout steps: team spawns, beds, generators, dealer spots. */
    private void rush(Player admin, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "help";
        SetupManager wizard = plugin.setup();
        switch (action) {
            case "team" -> {
                if (args.length < 4) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup rush team <color> (stand at that base's spawn)");
                    return;
                }
                wizard.rushTeamSpawn(admin, args[3]);
            }
            case "bed" -> {
                if (args.length < 4) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup rush bed <color> (look at that team's bed)");
                    return;
                }
                wizard.rushBed(admin, args[3]);
            }
            case "gen" -> {
                if (args.length < 4) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup rush gen <iron|gold|diamond|emerald> (stand on the spawner block)");
                    return;
                }
                wizard.rushGenerator(admin, args[3]);
            }
            case "dealer" -> wizard.rushDealer(admin);
            case "clear" -> wizard.rushClear(admin);
            default -> plugin.messages().send(admin, "help.setup-rush");
        }
    }

    /** The PvP bot layout: the bot's spawn marker (spawn stays the player's). */
    private void pvpbot(Player admin, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "bot" -> plugin.setup().pvpBotSpawn(admin);
            case "clear" -> plugin.setup().pvpBotClear(admin);
            default -> plugin.messages().send(admin, "help.setup-pvpbot");
        }
    }

    void edit(CommandSender sender, String[] args) {
        Player admin = require(sender);
        if (admin == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(admin, "general.usage", "usage", "/practice edit <arena>");
            return;
        }
        plugin.setup().edit(admin, SetupManager.normalize(args[1]));
    }

    private Material material(Player admin, String name) {
        Material parsed = Material.matchMaterial(name);
        if (parsed == null || !parsed.isItem()) {
            plugin.messages().send(admin, "setup.icon-invalid", "material", name);
            return null;
        }
        return parsed;
    }

    private void help(Player admin) {
        plugin.messages().send(admin, "help.setup-detail");
    }

    private Player require(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return null;
        }
        if (!player.hasPermission("practicecore.setup")) {
            plugin.messages().send(player, "permission.setup");
            return null;
        }
        return player;
    }

    private static String[] shift(String[] args) {
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, shifted.length);
        return shifted;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.setup")) {
            return List.of();
        }
        if (args.length == 2) {
            return PracticeCommand.filter(ACTIONS, args[1]);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("rush")) {
            if (args.length == 3) {
                return PracticeCommand.filter(RUSH_ACTIONS, args[2]);
            }
            if (args.length == 4) {
                return switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "team", "bed" -> PracticeCommand.filter(RUSH_TEAMS, args[3]);
                    case "gen" -> PracticeCommand.filter(
                            List.of("iron", "gold", "diamond", "emerald"), args[3]);
                    default -> List.of();
                };
            }
            return List.of();
        }
        if (action.equals("pvpbot")) {
            return args.length == 3
                    ? PracticeCommand.filter(PVPBOT_ACTIONS, args[2]) : List.of();
        }
        if (args.length == 3) {
            return switch (action) {
                case "edit" -> PracticeCommand.filter(plugin.templates().names(), args[2]);
                case "kit" -> PracticeCommand.filter(List.of("load"), args[2]);
                case "trigger" -> PracticeCommand.filter(List.of("clear"), args[2]);
                case "blocks" -> PracticeCommand.filter(List.of("true", "false"), args[2]);
                case "mode" -> PracticeCommand.filter(List.copyOf(plugin.modes().ids()), args[2]);
                case "category" -> {
                    // Only real category folders: suggesting a mode id would
                    // create a folder that duplicates the mode's own group.
                    java.util.LinkedHashSet<String> known = new java.util.LinkedHashSet<>();
                    known.add("default");
                    known.addAll(plugin.templates().categoryFolders());
                    yield PracticeCommand.filter(List.copyOf(known), args[2]);
                }
                case "permission" -> {
                    String open = plugin.setup().activeName();
                    yield PracticeCommand.filter(open == null
                            ? List.of("default")
                            : List.of("default", plugin.pcConfig().arenaPermissionPrefix() + open), args[2]);
                }
                case "icon" -> PracticeCommand.filter(MATERIAL_NAMES, args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }
}
