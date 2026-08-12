package me.beekrbonkr.practicecore.command;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.MBedwarsHook;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The /practice rush branch: pulling maps out of MBedwars.
 *
 * {@code import} captures an MBedwars arena's region as a schematic and
 * writes a complete rush template — team spawns, beds, generators and dealer
 * spots resolved to paste-origin offsets — so the map is playable immediately.
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
            default -> {
                msg().note(sender, "/practice rush list — MBedwars arenas available to import");
                msg().note(sender, "/practice rush import <mbedwars-arena> [name] [overwrite]");
            }
        }
    }

    private void list(CommandSender sender) {
        if (!MBedwarsHook.available()) {
            msg().send(sender, "rush.mbedwars-missing");
            return;
        }
        List<String> names = MBedwarsHook.arenaNames();
        if (names.isEmpty()) {
            msg().note(sender, "MBedwars has no arenas.");
            return;
        }
        msg().note(sender, "MBedwars arenas (" + names.size() + "): " + String.join(", ", names));
    }

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
        int width = (int) imported.region().getWidthX();
        int length = (int) imported.region().getWidthZ();
        int max = plugin.pcConfig().maxSchematicSize();
        if (width > max || length > max) {
            msg().send(sender, "setup.too-big",
                    "width", String.valueOf(width),
                    "length", String.valueOf(length),
                    "max", String.valueOf(max));
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
            return;
        }
        try {
            Clipboard clipboard = plugin.schematics()
                    .copyRegion(imported.world(), imported.region(), origin);
            plugin.schematics().save(clipboard, new File(dir, "arena.schem"));
        } catch (WorldEditException | IOException e) {
            msg().send(sender, "setup.schematic-save-failed", "error", String.valueOf(e.getMessage()));
            return;
        }

        ArenaTemplate existing = plugin.templates().get(name);
        ArenaTemplate template = existing != null ? existing : new ArenaTemplate(name, dir);
        template.setMode(RushMode.ID);
        if (existing == null) {
            template.setDisplayName(stripLegacy(imported.displayName()));
            template.setIcon(Material.RED_BED);
        }
        Map<String, Object> settings = template.settings();
        settings.remove("rush");
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

        // The plain spawn is only the fallback the join validation requires;
        // rush sessions spawn at the chosen team base.
        RushMapData parsed = RushMapData.parse(template);
        List<RushMapData.TeamBase> playable = parsed.playableTeams();
        if (playable.isEmpty()) {
            msg().problem(sender, "No team of '" + imported.name()
                    + "' has both a spawn and a standing bed — nothing to practice. Not saved.");
            plugin.templates().delete(name);
            return;
        }
        RushMapData.TeamBase first = playable.get(0);
        template.setSpawn(first.spawn().clone(), first.yaw(), first.pitch());
        template.setComplete(true);
        try {
            template.save();
        } catch (IOException e) {
            msg().send(sender, "setup.save-failed", "error", String.valueOf(e.getMessage()));
            return;
        }
        plugin.templates().register(template);
        msg().done(sender, (existing != null ? "Re-imported" : "Imported") + " '" + imported.name()
                + "' as rush arena '" + name + "': " + width + "×" + length + " blocks, "
                + imported.teams().size() + " team(s) (" + beds + " with beds), "
                + imported.spawners().size() + " generator(s), "
                + imported.dealers().size() + " dealer(s).");
        msg().note(sender, "Players find it under the Rush category — /practice join " + name);
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
            return PracticeCommand.filter(List.of("import", "list"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("import") && MBedwarsHook.available()) {
            return PracticeCommand.filter(MBedwarsHook.arenaNames(), args[2]);
        }
        return List.of();
    }
}
