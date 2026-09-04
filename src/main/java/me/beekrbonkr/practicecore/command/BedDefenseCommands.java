package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /practice beddefense ...}: the command face of the gallery. Mostly
 * what the clickable chat actions run — play, like, favorite — plus the
 * admin's delete. Everything else lives in the menus.
 */
public final class BedDefenseCommands {

    private static final List<String> SUBS = List.of(
            "play", "like", "favorite", "publish", "unpublish", "delete", "list", "edit");

    private final PracticeCorePlugin plugin;

    public BedDefenseCommands(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private BedDefenseService service() {
        return plugin.bedDefenses();
    }

    public void beddefense(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "general.players-only");
            return;
        }
        if (!player.hasPermission("practicecore.use")) {
            msg().send(player, "permission.use");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> new me.beekrbonkr.practicecore.gui.BedDefenseArenaMenu(plugin, player, null).open();
            case "play" -> withDefense(player, args, defense -> service().play(player, defense));
            case "like" -> withDefense(player, args, defense -> service().toggleLike(player, defense));
            case "favorite", "fav" -> withDefense(player, args,
                    defense -> service().toggleFavorite(player, defense));
            case "publish", "unpublish" -> withDefense(player, args, defense -> {
                if (!defense.isAuthor(player.getUniqueId())) {
                    msg().send(player, "beddefense.not-owner");
                    return;
                }
                service().setPublished(player, defense, sub.equals("publish"));
            });
            case "delete" -> withDefense(player, args, defense -> {
                if (!defense.isAuthor(player.getUniqueId())
                        && !player.hasPermission("practicecore.arena")) {
                    msg().send(player, "beddefense.not-owner");
                    return;
                }
                service().delete(player, defense);
            });
            case "edit" -> {
                if (args.length < 3) {
                    service().edit(player, null);
                } else {
                    withDefense(player, args, defense -> service().edit(player, defense));
                }
            }
            case "list" -> list(player);
            default -> msg().usage(player,
                    "/practice beddefense <play|like|favorite|publish|unpublish|edit|delete> [id]");
        }
    }

    private void withDefense(Player player, String[] args, java.util.function.Consumer<BedDefense> action) {
        if (args.length < 3) {
            // The canonical spelling, whatever the player typed (style guide R14).
            msg().usage(player, "/practice beddefense " + args[1].toLowerCase(java.util.Locale.ROOT) + " <id>");
            return;
        }
        BedDefense defense = service().store().get(args[2]);
        if (defense == null || (!defense.published() && !defense.isAuthor(player.getUniqueId())
                && !player.hasPermission("practicecore.arena"))) {
            msg().send(player, "beddefense.unknown", "id", args[2]);
            return;
        }
        action.accept(defense);
    }

    private void list(Player player) {
        List<BedDefense> playable = service().store().playableBy(player.getUniqueId());
        if (playable.isEmpty()) {
            msg().send(player, "beddefense.list-empty");
            return;
        }
        msg().send(player, "beddefense.list-header", "count", String.valueOf(playable.size()));
        for (BedDefense defense : playable) {
            msg().send(player, "beddefense.list-entry",
                    "id", defense.id(),
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "likes", String.valueOf(defense.likeCount()),
                    "players", String.valueOf(defense.uniquePlayers()),
                    "blocks", String.valueOf(defense.blocks().size()));
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && sender instanceof Player player) {
            return service().store().playableBy(player.getUniqueId()).stream()
                    .map(BedDefense::id)
                    .filter(id -> id.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
