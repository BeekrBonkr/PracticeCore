package me.beekrbonkr.practicecore.command;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.MBedwarsHook;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The /practice rush branch: pulling maps out of MBedwars.
 *
 * {@code import} captures one MBedwars arena's region as a schematic and
 * writes a complete rush template — team spawns, beds, generators and dealer
 * spots resolved to paste-origin offsets — so the map is playable immediately.
 * {@code importall} does the same for every arena matching a shape filter
 * (team count and/or players per team) and files them under one category.
 */
final class RushCommands {

    private final PracticeCorePlugin plugin;

    RushCommands(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    void rush(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.setup")) {
            msg().send(sender, "permission.setup");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "list" -> list(sender);
            case "import" -> importArena(sender, args);
            case "importall" -> importAll(sender, args);
            default -> {
                msg().note(sender, "/practice rush list — MBedwars arenas available to import");
                msg().note(sender, "/practice rush import <mbedwars-arena> [name] [overwrite]");
                msg().note(sender, "/practice rush importall teams:<n> size:<n> "
                        + "[category:<name>] [overwrite] — either or both filters");
            }
        }
    }

    private void list(CommandSender sender) {
        if (!MBedwarsHook.available()) {
            msg().send(sender, "rush.mbedwars-missing");
            return;
        }
        List<MBedwarsHook.ArenaSummary> summaries = MBedwarsHook.arenaSummaries();
        if (summaries.isEmpty()) {
            msg().note(sender, "MBedwars has no arenas.");
            return;
        }
        List<String> entries = new ArrayList<>();
        for (MBedwarsHook.ArenaSummary summary : summaries) {
            entries.add(summary.name() + " (" + summary.teams() + "x" + summary.playersPerTeam() + ")");
        }
        msg().note(sender, "MBedwars arenas (" + entries.size() + "): " + String.join(", ", entries));
    }

    // --------------------------------------------------------- single import

    private void importArena(CommandSender sender, String[] args) {
        if (!MBedwarsHook.available()) {
            msg().send(sender, "rush.mbedwars-missing");
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "general.usage", "usage",
                    "/practice rush import <mbedwars-arena> [name] [overwrite]");
            return;
        }
        boolean overwrite = args[args.length - 1].equalsIgnoreCase("overwrite");
        MBedwarsHook.ImportedArena imported;
        try {
            imported = MBedwarsHook.read(args[2]);
        } catch (IllegalStateException e) {
            msg().problem(sender, e.getMessage());
            return;
        } catch (LinkageError e) {
            // available() proves MBedwars is enabled, not API-compatible.
            msg().problem(sender, "The installed MBedwars version is incompatible with this hook.");
            plugin.getLogger().severe("MBedwars import hook failed: " + e);
            return;
        }
        String name = args.length > 3 && !args[3].equalsIgnoreCase("overwrite")
                ? sanitize(args[3]) : sanitize(imported.name());
        if (name.isEmpty()) {
            msg().send(sender, "setup.bad-name");
            return;
        }
        if (plugin.templates().get(name) != null && !overwrite) {
            msg().problem(sender, "Arena '" + name + "' already exists. Append 'overwrite' to replace "
                    + "its schematic and rush layout (times and kit are kept).");
            return;
        }
        importOne(sender, imported, name, null, ok -> {
            if (ok) {
                msg().note(sender, "Players find it under the Rush category — /practice join " + name);
            }
        });
    }

    // ----------------------------------------------------------- mass import

    /**
     * Imports every MBedwars arena matching a shape filter — team count
     * ({@code teams:<n>}), players per team ({@code size:<n>}) or both — and
     * files the results under one arena category so they group together in
     * the menu. The category defaults to the filter ("4x2", "8-teams",
     * "solo") and can be overridden with {@code category:<name>}.
     */
    private void importAll(CommandSender sender, String[] args) {
        if (!MBedwarsHook.available()) {
            msg().send(sender, "rush.mbedwars-missing");
            return;
        }
        Integer teams = null;
        Integer size = null;
        String category = null;
        boolean overwrite = false;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            try {
                if (arg.startsWith("teams:")) {
                    teams = Integer.parseInt(arg.substring("teams:".length()));
                } else if (arg.startsWith("size:")) {
                    size = Integer.parseInt(arg.substring("size:".length()));
                } else if (arg.startsWith("category:")) {
                    category = sanitize(arg.substring("category:".length()));
                } else if (arg.equals("overwrite")) {
                    overwrite = true;
                } else {
                    msg().problem(sender, "Unknown filter '" + args[i] + "'.");
                    return;
                }
            } catch (NumberFormatException e) {
                msg().problem(sender, "'" + args[i] + "' needs a number after the colon.");
                return;
            }
        }
        if (teams == null && size == null) {
            msg().send(sender, "general.usage", "usage",
                    "/practice rush importall teams:<n> size:<n> [category:<name>] [overwrite]"
                            + " — either or both filters");
            return;
        }
        List<MBedwarsHook.ArenaSummary> matches = new ArrayList<>();
        int total = 0;
        for (MBedwarsHook.ArenaSummary summary : MBedwarsHook.arenaSummaries()) {
            total++;
            if ((teams == null || summary.teams() == teams)
                    && (size == null || summary.playersPerTeam() == size)) {
                matches.add(summary);
            }
        }
        if (matches.isEmpty()) {
            msg().problem(sender, "None of the " + total + " MBedwars arena(s) match "
                    + filterLabel(teams, size) + ".");
            return;
        }
        if (category == null || category.isEmpty()) {
            category = deriveCategory(teams, size);
        }
        msg().note(sender, "Importing " + matches.size() + " arena(s) matching "
                + filterLabel(teams, size) + " into category '" + category + "'…");
        if (!plugin.schematics().supportsAsyncEdits()) {
            msg().note(sender, "Without FastAsyncWorldEdit each capture runs on the main "
                    + "thread — expect stalls. Installing FAWE makes this lag-free.");
        }
        runBatch(sender, new ArrayDeque<>(matches), category, overwrite, new int[3]);
    }

    /**
     * Imports the queue one arena at a time — sequentially, so a 30-map batch
     * never piles thirty clipboard copies into memory at once. Each arena's
     * heavy work runs off-thread (see {@link #importOne}); this method is
     * always entered on the main thread.
     */
    private void runBatch(CommandSender sender, ArrayDeque<MBedwarsHook.ArenaSummary> queue,
                          String category, boolean overwrite, int[] counters) {
        MBedwarsHook.ArenaSummary summary = queue.poll();
        if (summary == null) {
            msg().done(sender, "Mass import finished: " + counters[0] + " imported, "
                    + counters[1] + " skipped, " + counters[2] + " failed — category '"
                    + category + "'.");
            if (counters[0] > 0) {
                msg().note(sender, "Players find them under the '" + category + "' category. "
                        + "Give it an icon and display name in guis.yml (categories.entries).");
            }
            return;
        }
        String name = sanitize(summary.name());
        if (name.isEmpty()) {
            msg().problem(sender, " • " + summary.name() + " — unusable name, skipped.");
            counters[2]++;
            runBatch(sender, queue, category, overwrite, counters);
            return;
        }
        if (plugin.templates().get(name) != null && !overwrite) {
            msg().note(sender, " • " + summary.name() + " — '" + name
                    + "' already exists, skipped (append 'overwrite' to replace).");
            counters[1]++;
            runBatch(sender, queue, category, overwrite, counters);
            return;
        }
        MBedwarsHook.ImportedArena read;
        try {
            read = MBedwarsHook.read(summary.name());
        } catch (IllegalStateException e) {
            msg().problem(sender, " • " + summary.name() + " — " + e.getMessage());
            counters[2]++;
            runBatch(sender, queue, category, overwrite, counters);
            return;
        } catch (LinkageError e) {
            msg().problem(sender, " • " + summary.name()
                    + " — the installed MBedwars version is incompatible with this hook.");
            plugin.getLogger().severe("MBedwars import hook failed: " + e);
            counters[2]++;
            runBatch(sender, queue, category, overwrite, counters);
            return;
        }
        importOne(sender, read, name, category, ok -> {
            counters[ok ? 0 : 2]++;
            runBatch(sender, queue, category, overwrite, counters);
        });
    }

    private static String filterLabel(Integer teams, Integer size) {
        if (teams != null && size != null) {
            return teams + " team(s) of " + size;
        }
        return teams != null ? teams + " team(s)" : size + " player(s) per team";
    }

    /** A menu-worthy category name derived from the filter. */
    private static String deriveCategory(Integer teams, Integer size) {
        if (teams != null && size != null) {
            return teams + "x" + size;
        }
        if (teams != null) {
            return teams + "-teams";
        }
        return switch (size) {
            case 1 -> "solo";
            case 2 -> "doubles";
            case 3 -> "triples";
            case 4 -> "quads";
            default -> size + "-per-team";
        };
    }

    // ------------------------------------------------------------- the work

    /**
     * Captures one already-read MBedwars arena as a rush template. The region
     * copy and schematic write — the parts the attached thread dumps showed
     * stalling the server for tens of seconds — run off the main thread when
     * FAWE is present; template building resumes on the main thread and
     * {@code whenDone} is always called there with the outcome.
     */
    private void importOne(CommandSender sender, MBedwarsHook.ImportedArena imported,
                           String name, String category, Consumer<Boolean> whenDone) {
        int width = (int) imported.region().getWidthX();
        int length = (int) imported.region().getWidthZ();
        int max = plugin.pcConfig().maxSchematicSize();
        if (width > max || length > max) {
            msg().send(sender, "setup.too-big",
                    "width", String.valueOf(width),
                    "length", String.valueOf(length),
                    "max", String.valueOf(max));
            whenDone.accept(false);
            return;
        }
        if (!imported.status().equals("STOPPED") && !imported.status().equals("LOBBY")) {
            msg().note(sender, "MBedwars arena '" + imported.name() + "' is " + imported.status()
                    + " — the captured blocks may include mid-game changes.");
        }
        Location origin = new Location(imported.world(),
                (int) imported.region().getMinX(),
                (int) imported.region().getMinY(),
                (int) imported.region().getMinZ());
        File dir = plugin.templates().dirFor(name);
        if (!dir.exists() && !dir.mkdirs()) {
            msg().send(sender, "setup.folder-failed");
            whenDone.accept(false);
            return;
        }
        // The capture can run for seconds on a big map — say so before the
        // silence, or the admin is left wondering whether anything happened.
        msg().note(sender, "Capturing '" + imported.name() + "' (" + width + "×" + length
                + " blocks)…");
        Runnable capture = () -> {
            try {
                Clipboard clipboard = plugin.schematics()
                        .copyRegion(imported.world(), imported.region(), origin);
                plugin.schematics().save(clipboard, new File(dir, "arena.schem"));
            } catch (WorldEditException | IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        };
        if (plugin.schematics().supportsAsyncEdits()) {
            java.util.concurrent.CompletableFuture.runAsync(capture)
                    .whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                            writeTemplate(sender, imported, name, category, origin,
                                    width, length, error, whenDone)));
        } else {
            Throwable error = null;
            try {
                capture.run();
            } catch (RuntimeException e) {
                error = e;
            }
            writeTemplate(sender, imported, name, category, origin, width, length, error, whenDone);
        }
    }

    /** Main-thread tail of {@link #importOne}: arena.yml, registry, feedback. */
    private void writeTemplate(CommandSender sender, MBedwarsHook.ImportedArena imported,
                               String name, String category, Location origin,
                               int width, int length, Throwable error, Consumer<Boolean> whenDone) {
        if (error != null) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            msg().send(sender, "setup.schematic-save-failed", "error", String.valueOf(cause.getMessage()));
            whenDone.accept(false);
            return;
        }

        // Everything is written into a scratch map first and validated there:
        // a failed re-import must leave the registered template — memory and
        // disk — exactly as it was, not deleted or half-mutated.
        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        RushMapData.writeSource(settings, imported.name());

        int beds = 0;
        for (MBedwarsHook.ImportedTeam team : imported.teams()) {
            Location spawn = team.spawn();
            RushMapData.writeTeamSpawn(settings, team.name(),
                    new Vector(spawn.getX() - origin.getBlockX(),
                            spawn.getY() - origin.getBlockY(),
                            spawn.getZ() - origin.getBlockZ()),
                    spawn.getYaw(), spawn.getPitch());
            Block bedBlock = resolveBed(team.bedBlock());
            if (bedBlock == null) {
                msg().note(sender, "Team " + team.name() + ": no bed block at its bed position "
                        + "(mid-game?) — that base can't be a target until re-imported.");
                continue;
            }
            Bed bed = (Bed) bedBlock.getBlockData();
            Block head = bed.getPart() == Bed.Part.HEAD
                    ? bedBlock : bedBlock.getRelative(bed.getFacing());
            RushMapData.writeTeamBed(settings, team.name(),
                    new Vector(head.getX() - origin.getBlockX(),
                            head.getY() - origin.getBlockY(),
                            head.getZ() - origin.getBlockZ()),
                    bed.getFacing(), bedBlock.getType());
            beds++;
        }
        for (MBedwarsHook.ImportedSpawner spawner : imported.spawners()) {
            Location loc = spawner.location();
            RushMapData.addGenerator(settings, spawner.type(),
                    new Vector(loc.getBlockX() - origin.getBlockX(),
                            loc.getBlockY() - origin.getBlockY(),
                            loc.getBlockZ() - origin.getBlockZ()));
        }
        for (Location dealer : imported.dealers()) {
            RushMapData.addDealer(settings,
                    new Vector(dealer.getX() - origin.getBlockX(),
                            dealer.getY() - origin.getBlockY(),
                            dealer.getZ() - origin.getBlockZ()),
                    dealer.getYaw());
        }

        RushMapData parsed = RushMapData.parseSettings(settings);
        List<RushMapData.TeamBase> playable = parsed.playableTeams();
        ArenaTemplate existing = plugin.templates().get(name);
        if (playable.isEmpty()) {
            if (existing != null) {
                // The registered arena survives untouched — only the schematic
                // on disk was already re-captured, which a later re-import
                // will overwrite again.
                msg().problem(sender, "No team of '" + imported.name()
                        + "' has both a spawn and a standing bed — nothing to practice. "
                        + "'" + name + "' was left as it was; re-import once the map is whole.");
            } else {
                msg().problem(sender, "No team of '" + imported.name()
                        + "' has both a spawn and a standing bed — nothing to practice. Not saved.");
                plugin.templates().deleteUnregistered(name);
            }
            whenDone.accept(false);
            return;
        }

        ArenaTemplate template = existing != null ? existing
                : new ArenaTemplate(name, plugin.templates().dirFor(name));
        template.setMode(RushMode.ID);
        if (existing == null) {
            template.setDisplayName(stripLegacy(imported.displayName()));
            template.setIcon(Material.RED_BED);
        }
        if (category != null && !category.isEmpty()) {
            template.setCategory(category);
        }
        template.settings().put("rush", settings.get("rush"));
        // The plain spawn is only the fallback the join validation requires;
        // rush sessions spawn at the chosen team base.
        RushMapData.TeamBase first = playable.get(0);
        template.setSpawn(first.spawn().clone(), first.yaw(), first.pitch());
        template.setComplete(true);
        try {
            template.save();
        } catch (IOException e) {
            msg().send(sender, "setup.save-failed", "error", String.valueOf(e.getMessage()));
            whenDone.accept(false);
            return;
        }
        plugin.templates().register(template);
        msg().done(sender, (existing != null ? "Re-imported" : "Imported") + " '" + imported.name()
                + "' as rush arena '" + name + "': " + width + "×" + length + " blocks, "
                + imported.teams().size() + " team(s) (" + beds + " with beds), "
                + imported.spawners().size() + " generator(s), "
                + imported.dealers().size() + " dealer(s).");
        whenDone.accept(true);
    }

    /** The bed block MBedwars points at, or a neighbor when it points beside it. */
    private static Block resolveBed(Location loc) {
        Block block = loc.getBlock();
        if (block.getBlockData() instanceof Bed) {
            return block;
        }
        for (int dy : new int[]{-1, 1}) {
            Block near = block.getRelative(0, dy, 0);
            if (near.getBlockData() instanceof Bed) {
                return near;
            }
        }
        return null;
    }

    private static String sanitize(String name) {
        String cleaned = stripLegacy(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-").replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static String stripLegacy(String text) {
        return text == null ? "" : text.replaceAll("[§&][0-9a-fk-orx]", "");
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicecore.setup")) {
            return List.of();
        }
        if (args.length == 2) {
            return PracticeCommand.filter(List.of("import", "importall", "list"), args[1]);
        }
        if (!MBedwarsHook.available()) {
            return List.of();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("import")) {
            return PracticeCommand.filter(MBedwarsHook.arenaNames(), args[2]);
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("importall")) {
            Set<String> options = new LinkedHashSet<>();
            for (MBedwarsHook.ArenaSummary summary : MBedwarsHook.arenaSummaries()) {
                options.add("teams:" + summary.teams());
                options.add("size:" + summary.playersPerTeam());
            }
            options.add("category:");
            options.add("overwrite");
            return PracticeCommand.filter(List.copyOf(options), args[args.length - 1]);
        }
        return List.of();
    }
}
