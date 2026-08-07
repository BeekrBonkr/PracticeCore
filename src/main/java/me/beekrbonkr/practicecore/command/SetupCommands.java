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
            "start", "spawn", "kit", "capture", "schematic", "icon", "display",
            "permission", "blocks", "mode", "info", "save", "cancel");

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
                wizard.start(admin, SetupManager.normalise(args[2]));
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
            case "blocks" -> wizard.setRequireBlocks(admin, args.length > 2 && Boolean.parseBoolean(args[2]));
            case "mode" -> {
                if (args.length < 3) {
                    plugin.messages().send(admin, "general.usage", "usage",
                            "/practice setup mode <" + String.join("|", plugin.modes().ids()) + ">");
                    return;
                }
                wizard.setMode(admin, args[2].toLowerCase(Locale.ROOT));
            }
            case "info" -> wizard.info(admin);
            case "save" -> wizard.save(admin);
            case "cancel" -> wizard.cancel(admin);
            default -> help(admin);
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
        plugin.setup().edit(admin, SetupManager.normalise(args[1]));
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
        if (args.length == 3) {
            return switch (action) {
                case "kit" -> PracticeCommand.filter(List.of("load"), args[2]);
                case "blocks" -> PracticeCommand.filter(List.of("true", "false"), args[2]);
                case "mode" -> PracticeCommand.filter(List.copyOf(plugin.modes().ids()), args[2]);
                case "permission" -> {
                    String open = plugin.setup().activeName();
                    yield PracticeCommand.filter(open == null
                            ? List.of("default")
                            : List.of("default", plugin.pcConfig().arenaPermissionPrefix() + open), args[2]);
                }
                case "icon" -> PracticeCommand.filter(materialNames(), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> materialNames() {
        return java.util.Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
