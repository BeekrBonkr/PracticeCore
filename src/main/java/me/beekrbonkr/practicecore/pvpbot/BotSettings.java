package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.UUID;

/**
 * A player's PvP bot configuration exactly as stored in playerdata
 * ({@code prefs.pvpbot.*}). Immutable snapshot: the settings GUI writes each
 * change straight to prefs and re-loads, so there are no wither methods —
 * {@link #load} is the single source of truth.
 *
 * The tiers below are <em>names</em>, not numbers. What EXTREME evasiveness or
 * TWELVE cps actually mean lives in pvpbot.yml and is resolved through
 * {@link BotTuning}, which is why every accessor here takes the detour through
 * {@link #tuning()}: retuning a difficulty must never invalidate the
 * preferences players already saved.
 */
public record BotSettings(BotTuning tuning, PvpKit kit, GearTier gear, Evasiveness evasiveness,
                          Cps cps, Accuracy accuracy, Combos combos,
                          Reach reach, Aggression aggression,
                          boolean rod, boolean bow, boolean block) {

    /** How hard the bot works to stay out of the player's crosshair. */
    public enum Evasiveness {
        LOW, MEDIUM, HIGH, EXTREME, UNFAIR, SUFFER;

        public Evasiveness next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Attack rate. Tick resolution caps the real rate near the top end. */
    public enum Cps {
        FOUR, SIX, EIGHT, TWELVE;

        public Cps next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * How often a swing in range connects — and, above all, how well the bot
     * thinks: the accuracy tier is what unlocks the cerebral, duellist, unfair
     * and suffer layers (see {@link BotTuning#cerebral}).
     */
    public enum Accuracy {
        LOW, MEDIUM, HIGH, PERFECT, UNFAIR, SUFFER;

        public Accuracy next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** How often the bot goes for jump-crits and W-tap knockback resets. */
    public enum Combos {
        OFF, SOME, FULL, UNFAIR, SUFFER;

        public Combos next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Reach {
        SHORT, NORMAL, LONG;

        public Reach next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Approach speed and the spacing the bot tries to hold while strafing. */
    public enum Aggression {
        PASSIVE, BALANCED, RELENTLESS, FRENZIED, UNFAIR, SUFFER;

        public Aggression next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Bot gear override; MIRROR wears whatever the player's kit wears. */
    public enum GearTier {
        MIRROR, NONE, LEATHER, IRON, DIAMOND;

        public GearTier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // ------------------------------------------------------- resolved values

    /** Strafe speed in blocks per tick. */
    public double strafeSpeed() {
        return tuning.strafeSpeed(evasiveness);
    }

    /** Ticks between swings. */
    public int attackIntervalTicks() {
        return tuning.attackIntervalTicks(cps);
    }

    /** Chance a swing inside reach connects. */
    public double hitChance() {
        return tuning.hitChance(accuracy);
    }

    /** How readily the bot goes for jump-crits and W-taps. */
    public double comboChance() {
        return tuning.comboChance(combos);
    }

    /** Melee range in blocks. */
    public double reachBlocks() {
        return tuning.reachBlocks(reach);
    }

    /** Pathfinder speed multiplier while closing. */
    public double approachSpeed() {
        return tuning.approachSpeed(aggression);
    }

    /** The spacing the bot holds while strafing. */
    public double spacingGap() {
        return tuning.spacingGap(aggression);
    }

    /**
     * Perception lag in ticks: how stale the bot's picture of the player's
     * position and motion may be. The top tiers effectively see live.
     */
    public int reactionTicks() {
        return tuning.reactionTicks(accuracy);
    }

    /** Ticks the bot rides incoming knockback — the window combos live in. */
    public int hitstunTicks() {
        return tuning.hitstunTicks(evasiveness);
    }

    /**
     * The high accuracy tiers fight with their head, not just their aim:
     * immunity-timed hits, motion prediction, habit reads, feints, kiting
     * when losing, and edge play. Difficulty scales by thinking better,
     * not by hitting harder.
     */
    public boolean cerebral() {
        return tuning.cerebral(accuracy);
    }

    /**
     * The duellist layer, one step above cerebral: reach discipline, combo
     * follow-ups, s-taps, block-hits and strafe jukes — the techniques a good
     * 1.8 player has in their hands rather than in their head.
     */
    public boolean duellist() {
        return tuning.duellist(accuracy);
    }

    /**
     * The gloves-off layer: faster stance reads, earlier combo escapes,
     * sidestep bursts off the crosshair, freer feints, whiff-punishes,
     * crit-fishing and rod pressure. Everything a human could do on a blessed
     * day — it just does all of it, every exchange.
     */
    public boolean unfair() {
        return tuning.unfair(accuracy);
    }

    /** The tier above unfair: the same playbook with every dial at its ceiling. */
    public boolean suffer() {
        return tuning.suffer(accuracy);
    }

    /**
     * The bot rods whenever its kit actually carries one — a BuildUHC bot
     * never leaves its rod in its pocket. The {@code rod} toggle remains as a
     * way to force the option onto kits that lack the item.
     */
    public boolean usesRod() {
        return rod || (kit != null && kit.carries(Material.FISHING_ROD));
    }

    public boolean usesBow() {
        return bow || (kit != null && kit.carries(Material.BOW));
    }

    /** A layer-scaled knob from pvpbot.yml's {@code behavior} section. */
    public double knob(String path, double fallback) {
        return tuning.scaled("behavior." + path, this, fallback);
    }

    /** The same, rounded to whole ticks. */
    public int knobTicks(String path, int fallback) {
        return tuning.scaledTicks("behavior." + path, this, fallback);
    }

    /** A layer-scaled {@code {min, max}} window, rolled fresh. */
    public int knobRoll(String path, int minFallback, int maxFallback) {
        return tuning.scaledRoll("behavior." + path, this, minFallback, maxFallback);
    }

    /** True on this tick with the probability the named knob resolves to. */
    public boolean chance(String path, double fallback) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                < knob(path, fallback);
    }

    // -------------------------------------------------------------- presets

    /** The preset these settings currently equal, or null for a custom mix. */
    public BotPreset matchingPreset() {
        for (BotPreset preset : tuning.presets()) {
            if (preset.matches(this)) {
                return preset;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- gear

    /** The armor+sword the bot actually wears under these settings. */
    public ItemStack[] botGear() {
        if (gear == GearTier.MIRROR) {
            return kit == null ? new ItemStack[5] : kit.botGear();
        }
        Material[] pieces = tuning.gearPieces(gear);
        ItemStack[] stacks = new ItemStack[5];
        for (int i = 0; i < pieces.length && i < stacks.length; i++) {
            if (pieces[i] != null) {
                stacks[i] = new ItemStack(pieces[i]);
            }
        }
        return stacks;
    }

    // --------------------------------------------------------------- loading

    public static BotSettings load(PracticeCorePlugin plugin, UUID player) {
        BotTuning tuning = plugin.botTuning();
        var stats = plugin.stats();
        PvpKit kit = tuning.kits().get(stats.pref(player, "pvpbot.kit", null));
        if (kit == null) {
            kit = tuning.defaultKit();
        }
        return new BotSettings(tuning, kit,
                enumOr(GearTier.class, stats.pref(player, "pvpbot.gear", null),
                        tuning.defaultTier("gear", GearTier.class, GearTier.MIRROR)),
                enumOr(Evasiveness.class, stats.pref(player, "pvpbot.evasiveness", null),
                        tuning.defaultTier("evasiveness", Evasiveness.class, Evasiveness.MEDIUM)),
                enumOr(Cps.class, stats.pref(player, "pvpbot.cps", null),
                        tuning.defaultTier("cps", Cps.class, Cps.EIGHT)),
                enumOr(Accuracy.class, stats.pref(player, "pvpbot.accuracy", null),
                        tuning.defaultTier("accuracy", Accuracy.class, Accuracy.MEDIUM)),
                enumOr(Combos.class, stats.pref(player, "pvpbot.combos", null),
                        tuning.defaultTier("combos", Combos.class, Combos.SOME)),
                enumOr(Reach.class, stats.pref(player, "pvpbot.reach", null),
                        tuning.defaultTier("reach", Reach.class, Reach.NORMAL)),
                enumOr(Aggression.class, stats.pref(player, "pvpbot.aggression", null),
                        tuning.defaultTier("aggression", Aggression.class, Aggression.BALANCED)),
                stats.prefBool(player, "pvpbot.rod", tuning.defaultToggle("rod", false)),
                stats.prefBool(player, "pvpbot.bow", tuning.defaultToggle("bow", false)),
                stats.prefBool(player, "pvpbot.block", tuning.defaultToggle("block", false)));
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E def) {
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
