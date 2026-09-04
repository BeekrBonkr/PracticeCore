package me.beekrbonkr.practicecore.beddefense;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;

/**
 * How a bed-defense block is judged. Colors and wood types never matter —
 * a rusher meets "wool", not "lime wool" — so every material folds onto one
 * representative <em>kind</em> before it is stored, compared or counted.
 *
 * <p>Water is the one block with rules of its own: only a <b>source</b>
 * block counts (flowing water is a side effect, never a building block),
 * and a waterlogged block counts as the block it is, not as water.
 */
public final class BlockKinds {

    private BlockKinds() {
    }

    /** The representative material of a block's kind. */
    public static Material normalize(Material material) {
        if (material == null) {
            return Material.AIR;
        }
        if (Tag.WOOL.isTagged(material)) {
            return Material.WHITE_WOOL;
        }
        if (Tag.PLANKS.isTagged(material)) {
            return Material.OAK_PLANKS;
        }
        if (Tag.TERRACOTTA.isTagged(material)) {
            return Material.TERRACOTTA;
        }
        if (Tag.IMPERMEABLE.isTagged(material)) {
            return Material.GLASS; // every stained glass, and plain glass
        }
        if (material.name().endsWith("_STAINED_GLASS_PANE")) {
            return Material.GLASS_PANE;
        }
        if (Tag.WOOL_CARPETS.isTagged(material)) {
            return Material.WHITE_CARPET;
        }
        if (material == Material.WATER_BUCKET) {
            return Material.WATER;
        }
        return material;
    }

    /**
     * The kind of a block as it stands in the world, or AIR when it holds
     * nothing a defense could be made of: air, flowing water, and anything
     * else that is not a source block.
     */
    public static Material kindOf(Block block) {
        return kindOf(block.getBlockData());
    }

    public static Material kindOf(BlockData data) {
        Material material = data.getMaterial();
        if (material == Material.WATER) {
            return data instanceof Levelled levelled && levelled.getLevel() == 0
                    ? Material.WATER : Material.AIR;
        }
        if (material.isAir()) {
            return Material.AIR;
        }
        return normalize(material);
    }

    /** The kit item that places this kind: water comes in a bucket. */
    public static Material itemFor(Material kind) {
        return kind == Material.WATER ? Material.WATER_BUCKET : kind;
    }

    /**
     * The block data a defense records for a placed block: waterlogging
     * stripped (a waterlogged ladder is a ladder), water reduced to a plain
     * source.
     */
    public static BlockData storable(BlockData data) {
        BlockData copy = data.clone();
        if (copy instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(false);
        }
        if (copy.getMaterial() == Material.WATER) {
            return Material.WATER.createBlockData();
        }
        return copy;
    }

    /** WHITE_WOOL → "Wool", END_STONE → "End stone". Kinds read as categories. */
    public static String pretty(Material kind) {
        String name = switch (kind) {
            case WHITE_WOOL -> "Wool";
            case OAK_PLANKS -> "Planks";
            case WHITE_CARPET -> "Carpet";
            default -> {
                String lower = kind.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
                yield lower.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + lower.substring(1);
            }
        };
        return name;
    }
}
