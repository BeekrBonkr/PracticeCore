package me.beekrbonkr.practicecore.command;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.mode.BedDefenseMode;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /practice beddefense ...}: the command face of the gallery — what
 * the clickable chat actions run (play, like, favorite, report) — plus the
 * moderator's tools (review, reports, dismiss, publish or hide anyone's,
 * delete) and the admin's map import. Everything else lives in the menus.
 */
public final class BedDefenseCommands {

    private static final List<String> PLAYER_SUBS = List.of(
            "play", "like", "favorite", "publish", "unpublish", "edit", "delete", "list", "report");
    private static final List<String> MODERATOR_SUBS = List.of(
            "moderate", "review", "reports", "dismiss", "info", "all");
    private static final List<String> ADMIN_SUBS = List.of("import", "importall", "maps");

    private final PracticeCorePlugin plugin;
    private final RushCommands importer;

    public BedDefenseCommands(PracticeCorePlugin plugin, RushCommands importer) {
        this.plugin = plugin;
        this.importer = importer;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private BedDefenseService service() {
        return plugin.bedDefenses();
    }

    public void beddefense(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        // The admin branch works from the console too; everything else is a
        // player's own business.
        switch (sub) {
            case "import", "importall", "maps" -> {
                if (!sender.hasPermission("practicecore.setup")) {
                    msg().send(sender, "permission.setup");
                    return;
                }
                if (sub.equals("maps")) {
                    listMaps(sender);
                } else {
                    importer.importFor(sender, args, BedDefenseMode.ID);
                }
                return;
            }
            case "help" -> {
                sendHelp(sender);
                return;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player player)) {
            msg().send(sender, "general.players-only");
            return;
        }
        if (!player.hasPermission("practicecore.use")) {
            msg().send(player, "permission.use");
            return;
        }
        switch (sub) {
            case "" -> new me.beekrbonkr.practicecore.gui.BedDefenseArenaMenu(plugin, player, null).open();
            case "play" -> withDefense(player, args, defense -> play(player, args, defense));
            case "like" -> withDefense(player, args, defense -> service().toggleLike(player, defense));
            case "favorite", "fav" -> withDefense(player, args,
                    defense -> service().toggleFavorite(player, defense));
            case "publish", "unpublish" -> withDefense(player, args,
                    defense -> service().setPublished(player, defense, sub.equals("publish")));
            case "delete" -> withDefense(player, args, defense -> delete(player, args, defense));
            case "edit" -> {
                if (args.length < 3) {
                    service().edit(player, null);
                } else {
                    withDefense(player, args, defense -> service().edit(player, defense));
                }
            }
            case "list" -> list(player);
            case "report" -> withDefense(player, args, defense -> report(player, args, defense));
            case "moderate", "mod" -> moderator(player, () ->
                    new me.beekrbonkr.practicecore.gui.BedDefenseModerationMenu(plugin, player, null).open());
            case "review" -> moderator(player, () -> withDefense(player, args, defense ->
                    new me.beekrbonkr.practicecore.gui.BedDefenseActionsMenu(plugin, player, null, defense, null)
                            .open()));
            case "reports" -> moderator(player, () -> listReports(player));
            case "dismiss" -> moderator(player, () -> withDefense(player, args,
                    defense -> service().dismissReports(player, defense)));
            case "info" -> moderator(player, () -> withDefense(player, args, defense -> info(player, defense)));
            case "all" -> moderator(player, () -> listAll(player));
            default -> sendHelp(player);
        }
    }

    // ------------------------------------------------------------- players

    /**
     * {@code play <id> [competitive|practice|obsidian|repair]} — the mode
     * word sets the round mode first: competitive and practice switch
     * obsidian practice and bed repair off, obsidian or repair switches
     * that one on over whichever of the two is set.
     */
    private void play(Player player, String[] args, BedDefense defense) {
        if (args.length > 3) {
            String mode = args[3].toLowerCase(Locale.ROOT);
            if (mode.equals("competitive") || mode.equals("practice")) {
                var selection = service().rawSelection(player.getUniqueId());
                service().saveSelection(player.getUniqueId(),
                        selection.withCompetitive(mode.equals("competitive")));
                service().play(player, defense, BedDefenseSelection.Variant.NORMAL);
                return;
            }
            if (mode.equals("obsidian")) {
                service().play(player, defense, BedDefenseSelection.Variant.OBSIDIAN);
                return;
            }
            if (mode.equals("repair")) {
                service().play(player, defense, BedDefenseSelection.Variant.REPAIR);
                return;
            }
        }
        service().play(player, defense);
    }

    /** Deleting from chat asks for {@code confirm} like every other destructive command (R58). */
    private void delete(Player player, String[] args, BedDefense defense) {
        if (!defense.isAuthor(player.getUniqueId()) && !service().isModerator(player)) {
            msg().send(player, "beddefense.not-owner");
            return;
        }
        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            msg().send(player, "beddefense.delete-confirm", "name", defense.name(), "id", defense.id());
            return;
        }
        service().delete(player, defense);
    }

    /** {@code report <id> [reason…]} — without a reason, the reason is asked for in an anvil. */
    private void report(Player player, String[] args, BedDefense defense) {
        if (args.length > 3) {
            service().report(player, defense, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            return;
        }
        if (defense.isAuthor(player.getUniqueId()) || !defense.published()) {
            // Refused with the right reason before any prompt.
            service().report(player, defense, "");
            return;
        }
        service().promptReport(player, defense);
    }

    private void withDefense(Player player, String[] args, java.util.function.Consumer<BedDefense> action) {
        if (args.length < 3) {
            // The canonical spelling, whatever the player typed (style guide R14).
            String sub = args[1].toLowerCase(Locale.ROOT);
            msg().usage(player, "/practice beddefense " + (sub.equals("fav") ? "favorite" : sub) + " <id>");
            return;
        }
        BedDefense defense = service().store().get(args[2]);
        if (!service().canSee(player, defense)) {
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

    // ---------------------------------------------------------- moderators

    private void moderator(Player player, Runnable action) {
        if (!service().isModerator(player)) {
            msg().send(player, "permission.beddefense-moderate");
            return;
        }
        action.run();
    }

    private void listReports(Player player) {
        List<BedDefense> reported = service().store().reported();
        if (reported.isEmpty()) {
            msg().send(player, "beddefense.report.list-empty");
            return;
        }
        msg().send(player, "beddefense.report.list-header", "count", String.valueOf(reported.size()));
        for (BedDefense defense : reported) {
            BedDefense.Report latest = defense.reports().get(defense.reports().size() - 1);
            msg().send(player, "beddefense.report.list-entry",
                    "id", defense.id(),
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "count", String.valueOf(defense.reportCount()),
                    "reporter", latest.reporterName(),
                    "reason", latest.reason());
        }
    }

    private void listAll(Player player) {
        List<BedDefense> all = service().store().forModeration();
        if (all.isEmpty()) {
            msg().send(player, "beddefense.moderate.all-empty");
            return;
        }
        msg().send(player, "beddefense.moderate.all-header", "count", String.valueOf(all.size()));
        for (BedDefense defense : all) {
            msg().send(player, "beddefense.moderate.all-entry",
                    TagResolver.resolver(
                            msg().ref("visibility", defense.published()
                                    ? "label.state.public" : "label.state.private")),
                    "id", defense.id(),
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "reports", String.valueOf(defense.reportCount()));
        }
    }

    private void info(Player player, BedDefense defense) {
        service().markReportsSeen(defense);
        msg().send(player, "beddefense.moderate.info-header", "name", defense.name(), "id", defense.id());
        line(player, "author", defense.authorName());
        styledLine(player, "visibility", defense.published() ? "label.state.public" : "label.state.private");
        styledLine(player, "cleared", defense.authorCleared()
                ? "beddefense.moderate.cleared-yes" : "beddefense.moderate.cleared-no");
        if (defense.autoHidden()) {
            styledLine(player, "auto-hidden", "beddefense.moderate.auto-hidden-yes");
        }
        line(player, "blocks", String.valueOf(defense.blocks().size()));
        line(player, "likes", String.valueOf(defense.likeCount()));
        line(player, "players", String.valueOf(defense.uniquePlayers()));
        line(player, "runs", String.valueOf(defense.completions()));
        line(player, "reports", String.valueOf(defense.reportCount()));
        line(player, "builder-reports", String.valueOf(defense.reportsFromBuilders())
                + "/" + defense.buildersBesidesAuthor());
        for (BedDefense.Report report : defense.reports()) {
            msg().send(player, "beddefense.moderate.info-report",
                    "reporter", report.reporterName(), "reason", report.reason());
        }
        msg().send(player, "beddefense.moderate.info-actions", "name", defense.name(), "id", defense.id());
    }

    private void line(Player player, String key, String value) {
        msg().send(player, "beddefense.moderate.info-line",
                msg().ref("key", "beddefense.moderate.info-key." + key), "value", value);
    }

    /** A line whose value is itself a formatted messages.yml fragment (a colored state word). */
    private void styledLine(Player player, String key, String valueKey) {
        msg().send(player, "beddefense.moderate.info-line", TagResolver.resolver(
                msg().ref("key", "beddefense.moderate.info-key." + key),
                msg().ref("value", valueKey)));
    }

    // --------------------------------------------------------------- admin

    private void listMaps(CommandSender sender) {
        List<String> maps = new ArrayList<>();
        for (var template : plugin.templates().all()) {
            if (template.mode().equals(BedDefenseMode.ID)) {
                maps.add(template.name() + (service().supports(template) ? "" : " (no playable base)"));
            }
        }
        if (maps.isEmpty()) {
            msg().note(sender, "No bed defense maps yet. Import one with /practice beddefense import "
                    + "<mbedwars-arena>, or build one: /practice setup start <name>, "
                    + "/practice setup mode beddefense, then /practice setup beddefense ….");
            return;
        }
        msg().note(sender, "Bed defense maps (" + maps.size() + "): " + String.join(", ", maps));
    }

    private void sendHelp(CommandSender sender) {
        msg().send(sender, "help.beddefense-detail");
        if (sender.hasPermission(BedDefenseService.MODERATE_PERMISSION)) {
            msg().send(sender, "help.beddefense-moderate-detail");
        }
        if (sender.hasPermission("practicecore.setup")) {
            msg().send(sender, "help.beddefense-admin-detail");
        }
    }

    // ------------------------------------------------------ tab completion

    public List<String> complete(CommandSender sender, String[] args) {
        boolean player = sender instanceof Player && sender.hasPermission("practicecore.use");
        boolean moderator = sender.hasPermission(BedDefenseService.MODERATE_PERMISSION);
        boolean admin = sender.hasPermission("practicecore.setup");
        if (args.length == 2) {
            List<String> subs = new ArrayList<>();
            if (player) {
                subs.addAll(PLAYER_SUBS);
            }
            if (moderator) {
                subs.addAll(MODERATOR_SUBS);
            }
            if (admin) {
                subs.addAll(ADMIN_SUBS);
            }
            subs.add("help");
            return PracticeCommand.filter(subs, args[1]);
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("import") || sub.equals("importall")) {
            return admin ? importer.completeImport(sender, args) : List.of();
        }
        if (!(sender instanceof Player viewer)) {
            return List.of();
        }
        if (args.length == 3) {
            if (MODERATOR_SUBS.contains(sub) && !moderator) {
                return List.of();
            }
            if (sub.equals("list") || sub.equals("moderate") || sub.equals("reports")
                    || sub.equals("all") || sub.equals("maps")) {
                return List.of();
            }
            List<BedDefense> pool = sub.equals("dismiss")
                    ? service().store().reported()
                    : service().visibleTo(viewer);
            return PracticeCommand.filter(pool.stream().map(BedDefense::id).toList(), args[2]);
        }
        if (args.length == 4) {
            return switch (sub) {
                case "play" -> PracticeCommand.filter(
                        List.of("competitive", "practice", "obsidian", "repair"), args[3]);
                case "delete" -> PracticeCommand.filter(List.of("confirm"), args[3]);
                default -> List.of();
            };
        }
        return List.of();
    }
}
