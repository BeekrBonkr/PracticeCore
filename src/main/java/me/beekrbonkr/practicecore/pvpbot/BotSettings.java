package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.stats.StatsStore;
import org.bukkit.Material;

import java.util.Locale;
import java.util.UUID;

/**
 * A player's PvP bot configuration exactly as stored in playerdata
 * ({@code prefs.pvpbot.*}). Immutable snapshot: the settings GUI writes each
 * change straight to prefs and re-loads, so there are no wither methods —
 * {@link #load} is the single source of truth.
 */
public record BotSettings(PvpKit kit, GearTier gear, Evasiveness evasiveness,
                          Cps cps, Accuracy accuracy, Combos combos,
                          Reach reach, Aggression aggression,
                          boolean rod, boolean bow, boolean block) {

    /** How fast the bot strafes out of the player's crosshair, blocks/tick. */
    public enum Evasiveness {
        LOW(0.12), MEDIUM(0.18), HIGH(0.26), EXTREME(0.34), UNFAIR(0.45),
        SUFFER(0.48);

        private final double speed;

        Evasiveness(double speed) {
            this.speed = speed;
        }

        public double speed() {
            return speed;
        }

        public Evasiveness next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Attack rate. Tick resolution caps the real rate near the top end. */
    public enum Cps {
        FOUR(4), SIX(6), EIGHT(8), TWELVE(12);

        private final int clicks;

        Cps(int clicks) {
            this.clicks = clicks;
        }

        public int clicks() {
            return clicks;
        }

        /**
         * Ticks between swings, never below one. Floored, not rounded: the
         * service's 40%-chance extra tick supplies the fraction, so each tier
         * averages out near its nominal rate. TWELVE floors to a single tick —
         * the swing lands the instant the target's immunity expires, which is
         * exactly what a 1.8-style spam-clicker feels like.
         */
        public int intervalTicks() {
            return Math.max(1, 20 / clicks);
        }

        public Cps next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * Chance each swing in range actually connects. UNFAIR and SUFFER land
     * like PERFECT — there is no headroom above 1.0 — but the accuracy knob
     * has always been where the brain lives (reaction time, the cerebral
     * layer), and these tiers unlock its dirtiest layers: see
     * {@link #unfair()} and {@link #suffer()}.
     */
    public enum Accuracy {
        LOW(0.5), MEDIUM(0.7), HIGH(0.85), PERFECT(1.0), UNFAIR(1.0),
        SUFFER(1.0);

        private final double chance;

        Accuracy(double chance) {
            this.chance = chance;
        }

        public double chance() {
            return chance;
        }

        public Accuracy next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** How often the bot goes for jump-crits and W-tap knockback resets. */
    public enum Combos {
        OFF(0), SOME(0.2), FULL(0.4), UNFAIR(0.65), SUFFER(0.85);

        private final double chance;

        Combos(double chance) {
            this.chance = chance;
        }

        public double chance() {
            return chance;
        }

        public Combos next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Reach {
        SHORT(2.6), NORMAL(3.0), LONG(3.4);

        private final double blocks;

        Reach(double blocks) {
            this.blocks = blocks;
        }

        public double blocks() {
            return blocks;
        }

        public Reach next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Approach speed and the spacing the bot tries to hold while strafing. */
    public enum Aggression {
        PASSIVE(1.0, 3.2), BALANCED(1.2, 2.8), RELENTLESS(1.4, 2.3),
        FRENZIED(1.6, 2.0), UNFAIR(1.8, 1.7), SUFFER(1.85, 1.5);

        private final double speed;
        private final double gap;

        Aggression(double speed, double gap) {
            this.speed = speed;
            this.gap = gap;
        }

        public double speed() {
            return speed;
        }

        public double gap() {
            return gap;
        }

        public Aggression next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * Named difficulty presets: one click sets all six AI knobs. The knobs
     * stay individually editable afterward — a settings mix matching no
     * preset reads as "Custom" in the GUI.
     */
    public enum Preset {
        ROOKIE(Evasiveness.LOW, Cps.FOUR, Accuracy.LOW,
                Combos.OFF, Reach.SHORT, Aggression.PASSIVE),
        BRAWLER(Evasiveness.MEDIUM, Cps.SIX, Accuracy.MEDIUM,
                Combos.SOME, Reach.NORMAL, Aggression.BALANCED),
        VETERAN(Evasiveness.HIGH, Cps.EIGHT, Accuracy.HIGH,
                Combos.SOME, Reach.NORMAL, Aggression.RELENTLESS),
        DEMON(Evasiveness.EXTREME, Cps.TWELVE, Accuracy.PERFECT,
                Combos.FULL, Reach.LONG, Aggression.FRENZIED),
        // Deliberately no reach cheat past LONG on the top presets: they must
        // stay beatable by comboing and edge play, not turn into an aimbot
        // with a lightsaber.
        UNFAIR(Evasiveness.UNFAIR, Cps.TWELVE, Accuracy.UNFAIR,
                Combos.UNFAIR, Reach.LONG, Aggression.UNFAIR),
        // Everything UNFAIR does, done meaner and read faster — but still
        // inside a player's movement envelope: no extra reach, no extra CPS,
        // barely any extra speed. The cruelty is all in the brain.
        SUFFER(Evasiveness.SUFFER, Cps.TWELVE, Accuracy.SUFFER,
                Combos.SUFFER, Reach.LONG, Aggression.SUFFER);

        private final Evasiveness evasiveness;
        private final Cps cps;
        private final Accuracy accuracy;
        private final Combos combos;
        private final Reach reach;
        private final Aggression aggression;

        Preset(Evasiveness evasiveness, Cps cps, Accuracy accuracy,
               Combos combos, Reach reach, Aggression aggression) {
            this.evasiveness = evasiveness;
            this.cps = cps;
            this.accuracy = accuracy;
            this.combos = combos;
            this.reach = reach;
            this.aggression = aggression;
        }

        /** The prefs this preset writes, ready for one batched save. */
        public java.util.Map<String, Object> prefs() {
            return java.util.Map.of(
                    "pvpbot.evasiveness", evasiveness.name(),
                    "pvpbot.cps", cps.name(),
                    "pvpbot.accuracy", accuracy.name(),
                    "pvpbot.combos", combos.name(),
                    "pvpbot.reach", reach.name(),
                    "pvpbot.aggression", aggression.name());
        }

        public boolean matches(BotSettings settings) {
            return settings.evasiveness() == evasiveness && settings.cps() == cps
                    && settings.accuracy() == accuracy && settings.combos() == combos
                    && settings.reach() == reach && settings.aggression() == aggression;
        }

        public Preset next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** The preset these settings currently equal, or null for a custom mix. */
    public Preset matchingPreset() {
        for (Preset preset : Preset.values()) {
            if (preset.matches(this)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Perception lag in ticks: how stale the bot's picture of the player's
     * position and motion may be. The top tiers effectively see live.
     */
    public int reactionTicks() {
        return switch (accuracy) {
            case LOW -> 6;
            case MEDIUM -> 4;
            case HIGH -> 2;
            case PERFECT, UNFAIR, SUFFER -> 0;
        };
    }

    /**
     * The high accuracy tiers fight with their head, not just their aim:
     * immunity-timed hits, motion prediction, habit reads, feints, kiting
     * when losing, and edge play. Difficulty scales by thinking better,
     * not by hitting harder.
     */
    public boolean cerebral() {
        return accuracy.ordinal() >= Accuracy.HIGH.ordinal();
    }

    /**
     * The duellist layer, one step above cerebral: reach discipline, combo
     * follow-ups, s-taps, block-hits and strafe jukes — the techniques a good
     * 1.8 player has in their hands rather than in their head. Demon fights
     * with the honest version of each; {@link #unfair()} turns every one of
     * them up rather than adding new ones.
     */
    public boolean duellist() {
        return accuracy.ordinal() >= Accuracy.PERFECT.ordinal();
    }

    /**
     * The gloves-off layer above cerebral: faster stance reads, earlier
     * combo escapes, sidestep bursts off the crosshair, freer feints,
     * whiff-punishes, crit-fishing and rod pressure, and shoves that lean
     * harder toward the rim. Everything it does a human could do on a
     * blessed day — it just does all of it, every exchange.
     */
    public boolean unfair() {
        return accuracy.ordinal() >= Accuracy.UNFAIR.ordinal();
    }

    /**
     * The tier above unfair: the same dirty playbook with every dial at its
     * ceiling — near-instant stance reads, immediate retaliation out of a
     * slipped combo, relentless crit-fishing and rod pressure. Movement and
     * combat stay inside what a player could physically do; only the
     * decisions are inhuman.
     */
    public boolean suffer() {
        return accuracy == Accuracy.SUFFER;
    }

    /** Bot gear override; MIRROR wears whatever the player's kit preset wears. */
    public enum GearTier {
        MIRROR(null, null),
        NONE(null, Material.WOODEN_SWORD),
        LEATHER(Material.LEATHER_HELMET, Material.STONE_SWORD),
        IRON(Material.IRON_HELMET, Material.IRON_SWORD),
        DIAMOND(Material.DIAMOND_HELMET, Material.DIAMOND_SWORD);

        private final Material helmet;
        private final Material sword;

        GearTier(Material helmet, Material sword) {
            this.helmet = helmet;
            this.sword = sword;
        }

        /** [helmet, chestplate, leggings, boots, sword], nulls for bare slots. */
        public Material[] pieces() {
            if (helmet == null) {
                return new Material[]{null, null, null, null, sword};
            }
            String prefix = helmet.name().replace("_HELMET", "");
            return new Material[]{
                    helmet,
                    Material.valueOf(prefix + "_CHESTPLATE"),
                    Material.valueOf(prefix + "_LEGGINGS"),
                    Material.valueOf(prefix + "_BOOTS"),
                    sword};
        }

        public GearTier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static BotSettings load(StatsStore stats, UUID player) {
        return new BotSettings(
                enumOr(PvpKit.class, stats.pref(player, "pvpbot.kit", null), PvpKit.SWORD),
                enumOr(GearTier.class, stats.pref(player, "pvpbot.gear", null), GearTier.MIRROR),
                enumOr(Evasiveness.class, stats.pref(player, "pvpbot.evasiveness", null),
                        Evasiveness.MEDIUM),
                enumOr(Cps.class, stats.pref(player, "pvpbot.cps", null), Cps.EIGHT),
                enumOr(Accuracy.class, stats.pref(player, "pvpbot.accuracy", null),
                        Accuracy.MEDIUM),
                enumOr(Combos.class, stats.pref(player, "pvpbot.combos", null), Combos.SOME),
                enumOr(Reach.class, stats.pref(player, "pvpbot.reach", null), Reach.NORMAL),
                enumOr(Aggression.class, stats.pref(player, "pvpbot.aggression", null),
                        Aggression.BALANCED),
                stats.prefBool(player, "pvpbot.rod", false),
                stats.prefBool(player, "pvpbot.bow", false),
                stats.prefBool(player, "pvpbot.block", false));
    }

    /** The armor+sword the bot actually wears under these settings. */
    public org.bukkit.inventory.ItemStack[] botGear() {
        if (gear == GearTier.MIRROR) {
            return kit.botGear();
        }
        Material[] pieces = gear.pieces();
        org.bukkit.inventory.ItemStack[] stacks = new org.bukkit.inventory.ItemStack[5];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i] != null) {
                stacks[i] = new org.bukkit.inventory.ItemStack(pieces[i]);
            }
        }
        return stacks;
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
