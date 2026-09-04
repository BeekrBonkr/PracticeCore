package me.beekrbonkr.practicecore.beddefense;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.util.Vector;

import java.util.Set;

/**
 * Converts between a bed in the world and a defense's own coordinate frame.
 *
 * <p>Every defense is stored as if its bed faced <b>north</b> with the head
 * block at the origin, so the same defense fits any bed on any map: the frame
 * rotates offsets and directional block states by the bed's real facing.
 * Quarter turns go clockwise seen from above (north → east → south → west),
 * and a bed's foot always sits at local (0, 0, 1).
 */
public final class DefenseFrame {

    private static final BlockFace[] CLOCKWISE = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final Location head;
    /** Quarter turns from north to the bed's facing. */
    private final int turns;

    public DefenseFrame(Location head, BlockFace facing) {
        this.head = new Location(head.getWorld(), head.getBlockX(), head.getBlockY(), head.getBlockZ());
        this.turns = turnsOf(facing);
    }

    public Location head() {
        return head.clone();
    }

    public Location foot() {
        return toWorld(0, 0, 1);
    }

    /** The world block a defense block lands on for this bed. */
    public Location toWorld(DefenseBlock block) {
        return toWorld(block.x(), block.y(), block.z());
    }

    public Location toWorld(int x, int y, int z) {
        Vector offset = rotate(new Vector(x, y, z), turns);
        return new Location(head.getWorld(),
                head.getBlockX() + offset.getBlockX(),
                head.getBlockY() + offset.getBlockY(),
                head.getBlockZ() + offset.getBlockZ());
    }

    /** A world block as a defense-frame offset. */
    public Vector toLocal(Location loc) {
        Vector offset = new Vector(loc.getBlockX() - head.getBlockX(),
                loc.getBlockY() - head.getBlockY(),
                loc.getBlockZ() - head.getBlockZ());
        return rotate(offset, -turns);
    }

    /** True for the head or foot block of this bed. */
    public boolean isBed(Location loc) {
        Location foot = foot();
        return loc.getBlockX() == head.getBlockX() && loc.getBlockY() == head.getBlockY()
                && loc.getBlockZ() == head.getBlockZ()
                || loc.getBlockX() == foot.getBlockX() && loc.getBlockY() == foot.getBlockY()
                && loc.getBlockZ() == foot.getBlockZ();
    }

    /** Chebyshev distance from the nearer of the two bed blocks. */
    public int distance(Location loc) {
        Location foot = foot();
        return Math.min(chebyshev(loc, head), chebyshev(loc, foot));
    }

    private static int chebyshev(Location a, Location b) {
        return Math.max(Math.abs(a.getBlockX() - b.getBlockX()),
                Math.max(Math.abs(a.getBlockY() - b.getBlockY()),
                        Math.abs(a.getBlockZ() - b.getBlockZ())));
    }

    /** A placed block's state, turned into the defense frame for storage. */
    public BlockData toLocal(BlockData data) {
        return rotateData(BlockKinds.storable(data), -turns);
    }

    /**
     * A stored block state turned back into the world. Wool follows the
     * viewer's color choice when one is given, so previews match the kit.
     */
    public BlockData toWorld(DefenseBlock block, Material woolOverride) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(block.data());
        } catch (IllegalArgumentException e) {
            data = block.kind().createBlockData();
        }
        if (woolOverride != null && block.kind() == Material.WHITE_WOOL) {
            data = woolOverride.createBlockData();
        }
        return rotateData(data, turns);
    }

    // ------------------------------------------------------------ rotation

    static int turnsOf(BlockFace facing) {
        for (int i = 0; i < CLOCKWISE.length; i++) {
            if (CLOCKWISE[i] == facing) {
                return i;
            }
        }
        return 0;
    }

    /** Rotates a horizontal offset by quarter turns clockwise (negative = counter). */
    static Vector rotate(Vector v, int quarterTurns) {
        int q = Math.floorMod(quarterTurns, 4);
        int x = v.getBlockX();
        int z = v.getBlockZ();
        for (int i = 0; i < q; i++) {
            int nx = -z;
            int nz = x;
            x = nx;
            z = nz;
        }
        return new Vector(x, v.getBlockY(), z);
    }

    static BlockFace rotate(BlockFace face, int quarterTurns) {
        int index = turnsOf(face);
        if (CLOCKWISE[index] != face) {
            return face; // up, down, diagonals — not a horizontal facing
        }
        return CLOCKWISE[Math.floorMod(index + quarterTurns, 4)];
    }

    private static BlockData rotateData(BlockData data, int quarterTurns) {
        int q = Math.floorMod(quarterTurns, 4);
        if (q == 0) {
            return data;
        }
        BlockData copy = data.clone();
        if (copy instanceof Directional directional) {
            BlockFace rotated = rotate(directional.getFacing(), q);
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        } else if (copy instanceof Orientable orientable && q % 2 == 1) {
            if (orientable.getAxis() == org.bukkit.Axis.X) {
                orientable.setAxis(org.bukkit.Axis.Z);
            } else if (orientable.getAxis() == org.bukkit.Axis.Z) {
                orientable.setAxis(org.bukkit.Axis.X);
            }
        } else if (copy instanceof MultipleFacing multiple) {
            Set<BlockFace> on = new java.util.HashSet<>(multiple.getFaces());
            for (BlockFace face : multiple.getAllowedFaces()) {
                multiple.setFace(face, false);
            }
            for (BlockFace face : on) {
                BlockFace rotated = rotate(face, q);
                if (multiple.getAllowedFaces().contains(rotated)) {
                    multiple.setFace(rotated, true);
                }
            }
        }
        return copy;
    }
}
