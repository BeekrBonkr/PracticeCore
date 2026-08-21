package me.beekrbonkr.practicecore.rush;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Everything a player chose in the rush config menu: which base they start
 * from, the difficulty modifiers, the defender bots guarding enemy bases, and
 * whether the run is <b>competitive</b> — the fixed loadout (no starting
 * items, defenses on, generators on, bots pinned to the configured lineup)
 * that is the only way times are recorded and ranked. Immutable; the "with"
 * methods hand back an adjusted copy.
 *
 * With no bots, every objective the map supports is armed and whichever the
 * player completes first ends the run. With bots ({@code bots() > 0}), the
 * run becomes a combat run: only the team-wipe objective is armed, beds gate
 * the defenders' respawns, and the emerald/diamond generators produce on a
 * cycle like a real game instead of holding one objective item.
 */
public record RushSelection(String team, BlockTier blocks,
                            CurrencyTier currency, PickaxeTier pickaxe,
                            String defense, boolean baseGenerators,
                            int bots, String botDifficulty,
                            BotArmor botArmor, BotSword botSword,
                            boolean competitive) {

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

    /** What the defender bots wear. Leather is dyed the team's color. */
    public enum BotArmor {
        LEATHER("LEATHER"), CHAINMAIL("CHAINMAIL"), IRON("IRON"), DIAMOND("DIAMOND");

        private final String prefix;

        BotArmor(String prefix) {
            this.prefix = prefix;
        }

        public Material piece(String slot) {
            return Material.matchMaterial(prefix + "_" + slot);
        }

        public BotArmor next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** What the defender bots swing. */
    public enum BotSword {
        WOODEN(Material.WOODEN_SWORD), STONE(Material.STONE_SWORD),
        IRON(Material.IRON_SWORD), DIAMOND(Material.DIAMOND_SWORD);

        private final Material item;

        BotSword(Material item) {
            this.item = item;
        }

        public Material item() {
            return item;
        }

        public BotSword next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static RushSelection defaults() {
        return new RushSelection(null, BlockTier.NONE,
                CurrencyTier.NONE, PickaxeTier.NONE, RushDefense.NONE, true,
                0, "", BotArmor.LEATHER, BotSword.WOODEN, false);
    }

    /** Whether this run fights defender bots instead of racing static objectives. */
    public boolean combat() {
        return bots > 0;
    }

    public RushSelection withTeam(String team) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBlocks(BlockTier blocks) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withCurrency(CurrencyTier currency) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withPickaxe(PickaxeTier pickaxe) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    /** @param defense a {@link RushDefense} id from config.yml */
    public RushSelection withDefense(String defense) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBaseGenerators(boolean baseGenerators) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBots(int bots) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                Math.max(0, bots), botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBotDifficulty(String botDifficulty) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty == null ? "" : botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBotArmor(BotArmor botArmor) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withBotSword(BotSword botSword) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
    }

    public RushSelection withCompetitive(boolean competitive) {
        return new RushSelection(team, blocks, currency, pickaxe, defense, baseGenerators,
                bots, botDifficulty, botArmor, botSword, competitive);
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
