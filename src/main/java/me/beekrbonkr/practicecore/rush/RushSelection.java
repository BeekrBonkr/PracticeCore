package me.beekrbonkr.practicecore.rush;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Everything a player chose in the rush config menu: which base they start
 * from, what ends the run, and the difficulty modifiers. Immutable; the
 * "with" methods hand back an adjusted copy.
 */
public record RushSelection(String team, RushObjective objective, BlockTier blocks,
                            CurrencyTier currency, PickaxeTier pickaxe,
                            DefensePreset defense, boolean baseGenerators) {

    /** Free building blocks in the starter kit. */
    public enum BlockTier {
        NONE(0), SIXTEEN(16), THIRTY_TWO(32), SIXTY_FOUR(64);

        private final int amount;

        BlockTier(int amount) {
            this.amount = amount;
        }

        public int amount() {
            return amount;
        }

        public BlockTier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Iron/gold the player starts with, ready to spend at the shop. */
    public enum CurrencyTier {
        NONE(0, 0), SMALL(16, 0), MEDIUM(32, 8), LARGE(64, 16);

        private final int iron;
        private final int gold;

        CurrencyTier(int iron, int gold) {
            this.iron = iron;
            this.gold = gold;
        }

        public int iron() {
            return iron;
        }

        public int gold() {
            return gold;
        }

        public CurrencyTier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum PickaxeTier {
        NONE(null), WOODEN(Material.WOODEN_PICKAXE),
        IRON(Material.IRON_PICKAXE), DIAMOND(Material.DIAMOND_PICKAXE);

        private final Material item;

        PickaxeTier(Material item) {
            this.item = item;
        }

        /** The kit item, or null for none. */
        public Material item() {
            return item;
        }

        public PickaxeTier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * Auto-generated defense layers over every enemy bed. Each layer is a
     * shell of one material, innermost first.
     */
    public enum DefensePreset {
        NONE(), WOOL(Material.WHITE_WOOL),
        ENDSTONE(Material.WHITE_WOOL, Material.END_STONE),
        OBSIDIAN(Material.OBSIDIAN, Material.END_STONE);

        private final Material[] layers;

        DefensePreset(Material... layers) {
            this.layers = layers;
        }

        public Material[] layers() {
            return layers.clone();
        }

        public DefensePreset next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static RushSelection defaults() {
        return new RushSelection(null, RushObjective.BED, BlockTier.NONE,
                CurrencyTier.NONE, PickaxeTier.NONE, DefensePreset.NONE, true);
    }

    public RushSelection withTeam(String team) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withObjective(RushObjective objective) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withBlocks(BlockTier blocks) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withCurrency(CurrencyTier currency) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withPickaxe(PickaxeTier pickaxe) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withDefense(DefensePreset defense) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    public RushSelection withBaseGenerators(boolean baseGenerators) {
        return new RushSelection(team, objective, blocks, currency, pickaxe, defense, baseGenerators);
    }

    static <E extends Enum<E>> E enumOr(Class<E> type, String name, E def) {
        if (name == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
