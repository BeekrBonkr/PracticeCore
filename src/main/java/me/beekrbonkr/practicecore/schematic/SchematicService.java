package me.beekrbonkr.practicecore.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Thin wrapper over the WorldEdit 7 API. Compiled against vanilla WorldEdit;
 * with FAWE installed the same calls are internally accelerated.
 */
public final class SchematicService {

    public Clipboard load(File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + file.getName());
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        }
    }

    public void save(Clipboard clipboard, File file) throws IOException {
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                .getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        }
    }

    /** The clipboard the player last //copy'd, or null if empty. */
    public Clipboard playerClipboard(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        try {
            return session.getClipboard().getClipboard();
        } catch (EmptyClipboardException e) {
            return null;
        }
    }

    /**
     * Pastes the clipboard with its origin at {@code origin} and returns the
     * bounding box the paste actually occupies (WorldEdit positions the
     * clipboard origin — the copy point — at the paste target, not the
     * minimum corner).
     */
    public BoundingBox paste(Clipboard clipboard, Location origin) throws WorldEditException {
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSession(BukkitAdapter.adapt(origin.getWorld()))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
        }
        return boundsAt(clipboard, origin);
    }

    public static BoundingBox boundsAt(Clipboard clipboard, Location origin) {
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        BlockVector3 min = to.add(clipboard.getMinimumPoint().subtract(clipboard.getOrigin()));
        BlockVector3 max = min.add(clipboard.getDimensions()).subtract(BlockVector3.ONE);
        return new BoundingBox(min.x(), min.y(), min.z(), max.x() + 1, max.y() + 1, max.z() + 1);
    }

    /**
     * Copies a region of the world back into a clipboard whose origin is
     * {@code origin} — the inverse of {@link #paste}. This is what lets an
     * admin reshape a live arena in-world and save the result over the
     * template's schematic without ever leaving the practice world.
     */
    public Clipboard copyRegion(World world, BoundingBox box, Location origin) throws WorldEditException {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(weWorld,
                BlockVector3.at((int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ()),
                BlockVector3.at((int) box.getMaxX() - 1, (int) box.getMaxY() - 1, (int) box.getMaxZ() - 1));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()));
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    editSession, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);
            Operations.complete(copy);
        }
        return clipboard;
    }

    /** Fills the region with air — a full reset in a void world. */
    public void erase(World world, BoundingBox box) {
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSession(BukkitAdapter.adapt(world))) {
            CuboidRegion region = new CuboidRegion(
                    BukkitAdapter.adapt(world),
                    BlockVector3.at((int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ()),
                    BlockVector3.at((int) box.getMaxX() - 1, (int) box.getMaxY() - 1, (int) box.getMaxZ() - 1));
            editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
        } catch (WorldEditException e) {
            throw new IllegalStateException("Failed to erase arena region", e);
        }
    }
}
