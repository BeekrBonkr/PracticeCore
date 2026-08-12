package me.beekrbonkr.practicecore.template;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.BedBreakMode;
import me.beekrbonkr.practicecore.mode.MlgMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ready-to-play arenas for the non-bridging modes, built block by block in
 * code and written out as ordinary templates — so bedbreak works with zero
 * configuration, and the result is a normal folder an admin can edit,
 * re-shape or delete like any hand-made arena.
 *
 * Like the bundled bridging arena, each is installed exactly once: a marker
 * file records which kinds have been installed, so deleting or renaming one
 * does not resurrect it on the next restart.
 */
public final class GeneratedArenas {

    private static final String MARKER = ".generated-installed";

    private final PracticeCorePlugin plugin;

    public GeneratedArenas(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void installMissing(Path templatesDir) {
        if (!plugin.pcConfig().generatedArenasEnabled()) {
            return;
        }
        Path marker = plugin.getDataFolder().toPath().resolve(MARKER);
        Set<String> installed = readMarker(marker);
        boolean changed = install(installed, templatesDir, BedBreakMode.ID,
                plugin.pcConfig().generatedArenaName(BedBreakMode.ID), this::writeBedBreak);
        changed |= install(installed, templatesDir, "bedbreak-horizontal",
                plugin.pcConfig().generatedArenaName("bedbreak-horizontal"),
                this::writeBedBreakHorizontal);
        changed |= install(installed, templatesDir, MlgMode.ID,
                plugin.pcConfig().generatedArenaName(MlgMode.ID), this::writeMlg);
        if (changed) {
            writeMarker(marker, installed);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(String name, File dir) throws IOException, WorldEditException;
    }

    /** @return true when the marker needs rewriting (installed or claimed). */
    private boolean install(Set<String> installed, Path templatesDir, String kind,
                            String name, Writer writer) {
        if (name.isEmpty() || installed.contains(kind)) {
            return false;
        }
        Path target = templatesDir.resolve(name);
        if (TemplateRegistry.findArenaFolder(templatesDir, name) != null) {
            // An arena by this name already exists — in a category folder or
            // not — so never touch it, but stop re-checking every boot.
            installed.add(kind);
            return true;
        }
        try {
            Files.createDirectories(target);
            writer.write(name, target.toFile());
        } catch (IOException | WorldEditException e) {
            plugin.getLogger().warning("Could not generate the default " + kind
                    + " arena: " + e.getMessage());
            // Remove the partial folder, or the exists() check above would
            // claim the kind next boot and the broken arena would stay broken
            // forever.
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
            return false;
        }
        installed.add(kind);
        plugin.getLogger().info("Generated the default " + kind + " arena as '" + name
                + "' — /practice join " + name);
        return true;
    }

    // ----------------------------------------------------------- bedbreak

    /**
     * A sealed barrier shaft: bed at the bottom, a 17-block column of
     * defenses above its head, the player on top looking straight down. The
     * space above the bed's foot is barrier too, so the only way to the bed
     * is through the column.
     */
    private void writeBedBreak(String name, File dir) throws IOException, WorldEditException {
        Builder build = new Builder(3, 23, 4, BlockVector3.at(1, 1, 1));
        build.fill(0, 0, 0, 2, 22, 3, Material.BARRIER);            // solid shell…
        build.fill(1, 1, 1, 1, 21, 2, Material.AIR);                // …hollowed inside
        build.fill(1, 2, 2, 1, 21, 2, Material.BARRIER);            // seal above the foot
        build.set(1, 1, 1, Bukkit.createBlockData(Material.RED_BED, "[part=head,facing=north]"));
        build.set(1, 1, 2, Bukkit.createBlockData(Material.RED_BED, "[part=foot,facing=north]"));
        // The column above the head stays air — the mode rolls it every run.
        build.save(plugin, dir);

        ArenaTemplate template = new ArenaTemplate(name, dir);
        template.setMode(BedBreakMode.ID);
        template.setDisplayName("Bed Break");
        template.setIcon(Material.RED_BED);
        // On top of the 17-block column, looking straight down.
        template.setSpawn(new Vector(0.5, 18.0, 0.5), 0f, 90f);
        toolKit(template);
        template.settings().put("bedbreak", bedbreakSettings("VERTICAL"));
        template.setComplete(true);
        template.save();
    }

    /**
     * The horizontal variant: a sealed two-high corridor with the bed at the
     * far end and the defense wall filling the passage. Each defense step is a
     * two-high column, so the only way to the bed is straight through.
     */
    private void writeBedBreakHorizontal(String name, File dir) throws IOException, WorldEditException {
        Builder build = new Builder(3, 4, 23, BlockVector3.at(1, 1, 20));
        build.fill(0, 0, 0, 2, 3, 22, Material.BARRIER);            // solid shell…
        build.fill(1, 1, 1, 1, 2, 21, Material.AIR);                // …hollowed corridor
        build.set(1, 1, 20, Bukkit.createBlockData(Material.RED_BED, "[part=head,facing=north]"));
        build.set(1, 1, 21, Bukkit.createBlockData(Material.RED_BED, "[part=foot,facing=north]"));
        // The wall between z=3 and z=19 stays air — the mode rolls it every run.
        build.save(plugin, dir);

        ArenaTemplate template = new ArenaTemplate(name, dir);
        template.setMode(BedBreakMode.ID);
        template.setDisplayName("Bed Break Horizontal");
        template.setIcon(Material.RED_BED);
        // At the corridor's near end, facing the bed (+z is yaw 0).
        template.setSpawn(new Vector(0.5, 0.0, -18.5), 0f, 0f);
        toolKit(template);
        template.settings().put("bedbreak", bedbreakSettings("HORIZONTAL"));
        template.setComplete(true);
        template.save();
    }

    private static void toolKit(ArenaTemplate template) {
        template.kit().put(0, new ItemStack(Material.DIAMOND_SWORD));
        template.kit().put(1, new ItemStack(Material.DIAMOND_PICKAXE));
        template.kit().put(2, new ItemStack(Material.DIAMOND_AXE));
        template.kit().put(3, new ItemStack(Material.SHEARS));
    }

    private static Map<String, Object> bedbreakSettings(String orientation) {
        Map<String, Object> bedbreak = new LinkedHashMap<>();
        bedbreak.put("orientation", orientation);
        bedbreak.put("bed-x", 0);
        bedbreak.put("bed-y", 0);
        bedbreak.put("bed-z", 0);
        bedbreak.put("bed-facing", "NORTH");
        bedbreak.put("bed-material", "RED_BED");
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("WHITE_WOOL", 8);
        blocks.put("OAK_PLANKS", 4);
        blocks.put("END_STONE_BRICKS", 4);
        blocks.put("OBSIDIAN", 1);
        bedbreak.put("blocks", blocks);
        return bedbreak;
    }

    // ------------------------------------------------------------------ mlg

    /**
     * An 11×11 shaft: a grass backstop floor, barrier walls running the full
     * height of the arena, and open air inside — the glass start platform and
     * the landing pad are built (and re-rolled) by the mode itself every
     * reset, with the pad landing anywhere from 20 to 100 blocks down.
     */
    private void writeMlg(String name, File dir) throws IOException, WorldEditException {
        Builder build = new Builder(11, 110, 11, BlockVector3.at(5, 1, 5));
        build.fill(0, 0, 0, 10, 0, 10, Material.GRASS_BLOCK);       // backstop floor
        build.fill(0, 1, 0, 10, 109, 0, Material.BARRIER);          // full-height walls: north…
        build.fill(0, 1, 10, 10, 109, 10, Material.BARRIER);        // …south…
        build.fill(0, 1, 0, 0, 109, 10, Material.BARRIER);          // …west…
        build.fill(10, 1, 0, 10, 109, 10, Material.BARRIER);        // …and east
        // The platform (relative y 104) and pad stay air — mode-built per round.
        build.save(plugin, dir);

        ArenaTemplate template = new ArenaTemplate(name, dir);
        template.setMode(MlgMode.ID);
        template.setDisplayName("MLG");
        template.setIcon(Material.WATER_BUCKET);
        // On the glass platform, looking out over the edge.
        template.setSpawn(new Vector(0.5, 104.0, 0.5), 0f, 30f);
        template.kit().put(0, new ItemStack(Material.WATER_BUCKET));
        Map<String, Object> mlg = new LinkedHashMap<>();
        mlg.put("platform-radius", 1);
        mlg.put("pad-radius", 5);
        mlg.put("pad-material", "GRASS_BLOCK");
        mlg.put("min-drop", 20);
        mlg.put("max-drop", 100);
        mlg.put("fuse-min-ticks", 30);
        mlg.put("fuse-max-ticks", 100);
        template.settings().put("mlg", mlg);
        template.setComplete(true);
        template.save();
    }

    // -------------------------------------------------------------- marker

    private Set<String> readMarker(Path marker) {
        Set<String> installed = new HashSet<>();
        if (Files.exists(marker)) {
            try {
                for (String line : Files.readAllLines(marker)) {
                    String kind = line.trim();
                    if (!kind.isEmpty() && !kind.startsWith("#")) {
                        installed.add(kind);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not read " + MARKER + ": " + e.getMessage());
            }
        }
        return installed;
    }

    private void writeMarker(Path marker, Set<String> installed) {
        StringBuilder text = new StringBuilder("""
                # PracticeCore generated these default mode arenas once.
                # Remove a line to have that arena generated again on the next start.
                """);
        installed.stream().sorted().forEach(kind -> text.append(kind).append('\n'));
        try {
            Files.writeString(marker, text.toString());
        } catch (IOException e) {
            // Worst case the check runs again next boot and finds the folders.
            plugin.getLogger().warning("Could not write " + MARKER + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- builder

    /** A clipboard under construction; everything not set stays air. */
    private static final class Builder {

        private final BlockArrayClipboard clipboard;

        Builder(int width, int height, int length, BlockVector3 origin) {
            this.clipboard = new BlockArrayClipboard(new CuboidRegion(
                    BlockVector3.ZERO, BlockVector3.at(width - 1, height - 1, length - 1)));
            this.clipboard.setOrigin(origin);
        }

        void set(int x, int y, int z, Material material) throws WorldEditException {
            set(x, y, z, material.createBlockData());
        }

        void set(int x, int y, int z, org.bukkit.block.data.BlockData data) throws WorldEditException {
            clipboard.setBlock(BlockVector3.at(x, y, z), BukkitAdapter.adapt(data));
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material)
                throws WorldEditException {
            org.bukkit.block.data.BlockData data = material.createBlockData();
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        set(x, y, z, data);
                    }
                }
            }
        }

        void save(PracticeCorePlugin plugin, File dir) throws IOException {
            plugin.schematics().save(clipboard, new File(dir, "arena.schem"));
        }
    }
}
