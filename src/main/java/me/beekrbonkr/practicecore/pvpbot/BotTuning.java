package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.ConfigFile;
import me.beekrbonkr.practicecore.config.Versions;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything about the PvP bot that used to be a number in the source:
 * difficulty tiers, the presets they add up to, the brain's probabilities and
 * timings, its body's attributes, and the kit gallery.
 *
 * Two ideas run through the file it reads (pvpbot.yml):
 *
 * <ul>
 *   <li><b>Tiers keep their names, not their numbers.</b> {@code EXTREME}
 *       evasiveness is still {@code EXTREME} — saved player preferences and
 *       message keys never move — but how fast it actually strafes is a line
 *       in the config. That is what makes retuning a difficulty safe on a
 *       live server.</li>
 *   <li><b>Behaviors scale by layer.</b> A knob like the combo-escape chance
 *       is written once with a {@code base} value and optional {@code cerebral}
 *       / {@code duellist} / {@code unfair} / {@code suffer} overrides; the
 *       best one a bot qualifies for wins. Admins edit one block instead of
 *       hunting four branches.</li>
 * </ul>
 *
 * Every lookup falls back to the value the plugin shipped with, so a key
 * deleted by hand degrades to the stock bot rather than to zero.
 */
public final class BotTuning {

    /** Layer names, weakest first — the order {@link #scaled} searches upward. */
    private static final String[] LAYERS = {"base", "cerebral", "duellist", "unfair", "suffer"};

    private final PracticeCorePlugin plugin;
    private final ConfigFile file;
    private final PvpKitRegistry kits;
    private final List<BotPreset> presets = new ArrayList<>();
    /**
     * Resolved layer lookups, keyed by path and the layers a bot qualifies for.
     * The brain asks the same few dozen questions twenty times a second per
     * fight; walking the YAML tree for each one is pure waste. Cleared on every
     * reload, which is the only time an answer can change.
     */
    private final Map<String, double[]> memo = new java.util.HashMap<>();
    /** Accuracy tier that unlocks each brain layer; null means switched off. */
    private BotSettings.Accuracy cerebralFrom;
    private BotSettings.Accuracy duellistFrom;
    private BotSettings.Accuracy unfairFrom;
    private BotSettings.Accuracy sufferFrom;

    public BotTuning(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        // kits and presets are lists the admin owns: one they delete must
        // stay deleted rather than reappearing on the next start.
        this.file = new ConfigFile(plugin, "pvpbot", "pvpbot.yml",
                Versions.PVPBOT, BotTuning::steps, java.util.Set.of("kits", "presets"));
        this.kits = new PvpKitRegistry(plugin);
    }

    /** Reshapes an older pvpbot.yml. See {@link Versions#PVPBOT}. */
    private static void steps(org.bukkit.configuration.file.FileConfiguration cfg, int from) {
        // v0 → v1 is the first versioned layout; nothing moved.
        if (from < 2) {
            // v2 smooths the difficulty ladder (Rookie walks instead of
            // crawling, Demon caps at a cracked human, the unfair tiers grade
            // in rather than leaping) and reworks the gapple into the
            // retreat-chew-recommit dance. Only values still at their old
            // shipped defaults are retuned — an admin's own numbers stand.
            retune(cfg, "tiers.evasiveness.LOW", 0.12, 0.17);
            retune(cfg, "tiers.evasiveness.MEDIUM", 0.18, 0.21);
            retune(cfg, "tiers.evasiveness.EXTREME", 0.30, 0.28);
            retune(cfg, "tiers.evasiveness.UNFAIR", 0.38, 0.34);
            retune(cfg, "tiers.evasiveness.SUFFER", 0.48, 0.44);
            retune(cfg, "tiers.accuracy.PERFECT", 0.95, 0.92);
            retune(cfg, "tiers.combos.UNFAIR", 0.5, 0.45);
            retune(cfg, "tiers.aggression.passive.speed", 1.0, 1.1);
            retune(cfg, "tiers.aggression.passive.gap", 3.2, 3.0);
            retune(cfg, "tiers.aggression.frenzied.gap", 2.3, 2.2);
            retune(cfg, "tiers.aggression.unfair.speed", 1.6, 1.55);
            retune(cfg, "tiers.aggression.unfair.gap", 2.0, 1.9);
            retune(cfg, "behavior.consumables.gapple.min-distance", 2.5, 6.0);
            // The in-your-face chew is gone; the retreat dance replaced it.
            cfg.set("behavior.consumables.gapple.pressured-chance", null);
        }
    }

    /** Rewrites a value only while it still says what v(n-1) shipped. */
    private static void retune(org.bukkit.configuration.file.FileConfiguration cfg,
                               String path, double old, double now) {
        if (cfg.isSet(path) && Math.abs(cfg.getDouble(path, old) - old) < 1e-9) {
            cfg.set(path, now);
        }
    }

    public String probe() {
        return file.probe();
    }

    public List<String> load() {
        List<String> notes = file.load();
        memo.clear();
        loadLayers();
        notes.addAll(kits.load(file));
        loadPresets();
        return notes;
    }

    public PvpKitRegistry kits() {
        return kits;
    }

    // ------------------------------------------------------------- lifecycle

    /** Ticks either fighter stays down before respawning. */
    public int respawnTicks() {
        return file.integer("bot.respawn-ticks", 60, 1, 20 * 60);
    }

    /** Ticks between kit-consumable top-ups. */
    public int refillTicks() {
        return file.integer("bot.refill-ticks", 60, 1, 20 * 60);
    }

    /** Ticks between refreshes of the health text over the bot's head. */
    public int nameRefreshTicks() {
        return file.integer("bot.name-refresh-ticks", 5, 1, 100);
    }

    /** Ticks of a completely still player before the bot stands down. */
    public int afkTicks() {
        return file.integer("bot.afk-ticks", 60, 20, 20 * 600);
    }

    /** Passive ticks the bot grants after a stock reset. */
    public int stockGraceTicks() {
        return file.integer("bot.grace-ticks.stock", 20, 0, 200);
    }

    /** Passive ticks after a respawn, a menu closing or an AFK wake. */
    public int shortGraceTicks() {
        return file.integer("bot.grace-ticks.short", 10, 0, 200);
    }

    public int wakeGraceTicks() {
        return file.integer("bot.grace-ticks.wake", 15, 0, 200);
    }

    /** Blocks ahead of the player spawn the bot appears when no marker is set. */
    public double spawnDistance() {
        return file.number("bot.spawn-distance", 5.0, 0.5, 64.0);
    }

    /** How far the pathfinder may plan — a zombie's own 35 is too short. */
    public double followRange() {
        return file.number("bot.follow-range", 128.0, 16.0, 512.0);
    }

    /** Base melee damage before the held weapon's own attribute is added. */
    public double baseAttackDamage() {
        return file.number("bot.base-attack-damage", 1.0, 0.0, 20.0);
    }

    /** Husk scale used only when no player-model disguise is available. */
    public double undisguisedScale() {
        return file.number("bot.undisguised-scale", 0.925, 0.1, 2.0);
    }

    /** Height above the bot the floating health tag sits. */
    public double nameTagOffset() {
        return file.number("bot.name-tag-offset", 0.35, -2.0, 4.0);
    }

    public boolean damageIndicators() {
        return file.bool("bot.damage-indicators.enabled", true);
    }

    public int damageIndicatorTicks() {
        return file.integer("bot.damage-indicators.lifetime-ticks", 16, 1, 200);
    }

    public double damageIndicatorRise() {
        return file.number("bot.damage-indicators.rise", 0.7, 0.0, 4.0);
    }

    /**
     * What the refill pass throws away: the emptied containers a long spar
     * accumulates. Never part of a kit, so nothing of value is lost.
     */
    public List<Material> discardedEmpties() {
        return file.materials("bot.discard-empties",
                List.of(Material.GLASS_BOTTLE, Material.BOWL));
    }

    /** How still a player must be to count as away, and for how long. */
    public double afkMoveThreshold() {
        return file.number("bot.afk.move-threshold", 0.0004, 0.0, 4.0);
    }

    public double afkLookThreshold() {
        return file.number("bot.afk.look-threshold", 0.5, 0.0, 90.0);
    }

    /** Multiplier applied to the bot's own jump-crit hits. */
    public double critMultiplier() {
        return file.number("bot.crit-multiplier", 1.5, 1.0, 4.0);
    }

    /** Damage the bot's raised 1.8 sword block lets through. */
    public double swordBlockDamageFactor() {
        return file.number("bot.sword-block-damage-factor", 0.5, 0.0, 1.0);
    }

    /** Fraction of incoming knockback a crouching bot still takes. */
    public double crouchKnockbackFactor() {
        return file.number("bot.knockback.crouch-factor", 0.6, 0.0, 1.0);
    }

    /** Fraction an s-tapping bot takes — the backward tap, not a stance. */
    public double stapKnockbackFactor() {
        return file.number("bot.knockback.stap-factor", 0.75, 0.0, 1.0);
    }

    /** The upward kick every hop, jump-crit and escape leap uses. */
    public double jumpVelocity() {
        return file.number("bot.jump-velocity", 0.42, 0.0, 2.0);
    }

    /**
     * Hearts a mushroom stew heals instantly on right-click — classic soup
     * PvP, for the player and the bot alike. 0 disables soup healing.
     */
    public double soupHealHearts() {
        return file.number("bot.soup-heal-hearts", 3.5, 0.0, 10.0);
    }

    /** Whether the bot heals with its kit's own pots, stew and gapples. */
    public boolean consumablesEnabled() {
        return file.bool("behavior.consumables.enabled", true);
    }

    /**
     * The kill-first read: low enough to want a gapple, but the player is
     * hurt at least as badly — go for the finish instead of healing.
     */
    public boolean gappleRiskyFinish() {
        return file.bool("behavior.consumables.gapple.risky-finish", true);
    }

    /**
     * Main-hand items that count as a projectile threat — what unlocks the
     * full-speed crosshair dodge. Drawn bows, crossbows and tridents always
     * count; this list covers the throwables that need no draw.
     */
    public List<Material> projectileThreats() {
        return file.materials("behavior.strafe.projectile-threats", List.of(
                Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL,
                Material.SPLASH_POTION, Material.LINGERING_POTION,
                Material.FISHING_ROD, Material.TRIDENT));
    }

    /**
     * The classic 1.8 rod hit for the player's hook against the bot —
     * OldCombatMechanics' fishing knockback only covers hooked players.
     */
    public boolean rodKnockback() {
        return file.bool("bot.rod-knockback.enabled", true);
    }

    public double rodKnockbackStrength() {
        return file.number("bot.rod-knockback.strength", 0.4, 0.0, 2.0);
    }

    public double rodKnockbackLift() {
        return file.number("bot.rod-knockback.lift", 0.3, 0.0, 2.0);
    }

    /** Ticks of steering a landed hook knocks out of the bot. */
    public int rodKnockbackHitstun() {
        return file.integer("bot.rod-knockback.hitstun-ticks", 4, 0, 40);
    }

    // ------------------------------------------------------------- the tiers

    public double strafeSpeed(BotSettings.Evasiveness tier) {
        return tierValue("tiers.evasiveness", tier.name(), switch (tier) {
            case LOW -> 0.17;
            case MEDIUM -> 0.21;
            case HIGH -> 0.26;
            case EXTREME -> 0.28;
            case UNFAIR -> 0.34;
            case SUFFER -> 0.44;
        });
    }

    public int clicksPerSecond(BotSettings.Cps tier) {
        return (int) Math.max(1, tierValue("tiers.cps", tier.name(), switch (tier) {
            case FOUR -> 4;
            case SIX -> 6;
            case EIGHT -> 8;
            case TWELVE -> 12;
        }));
    }

    /**
     * Ticks between swings, never below one. Floored, not rounded: the extra
     * tick the service adds by chance supplies the fraction, so each tier
     * averages out near its nominal rate.
     */
    public int attackIntervalTicks(BotSettings.Cps tier) {
        return Math.max(1, 20 / clicksPerSecond(tier));
    }

    /** Chance a swing inside reach actually connects. */
    public double hitChance(BotSettings.Accuracy tier) {
        return tierValue("tiers.accuracy", tier.name(), switch (tier) {
            case LOW -> 0.5;
            case MEDIUM -> 0.7;
            case HIGH -> 0.85;
            case PERFECT -> 0.92;
            case UNFAIR -> 0.97;
            case SUFFER -> 1.0;
        });
    }

    public double comboChance(BotSettings.Combos tier) {
        return tierValue("tiers.combos", tier.name(), switch (tier) {
            case OFF -> 0.0;
            case SOME -> 0.2;
            case FULL -> 0.32;
            case UNFAIR -> 0.45;
            case SUFFER -> 0.85;
        });
    }

    public double reachBlocks(BotSettings.Reach tier) {
        return tierValue("tiers.reach", tier.name(), switch (tier) {
            case SHORT -> 2.6;
            case NORMAL -> 3.0;
            case LONG -> 3.4;
        });
    }

    public double approachSpeed(BotSettings.Aggression tier) {
        return tierValue("tiers.aggression." + tier.name().toLowerCase(Locale.ROOT) + ".speed",
                null, switch (tier) {
                    case PASSIVE -> 1.1;
                    case BALANCED -> 1.2;
                    case RELENTLESS -> 1.4;
                    case FRENZIED -> 1.45;
                    case UNFAIR -> 1.55;
                    case SUFFER -> 1.85;
                });
    }

    /** The spacing this aggression tries to hold while strafing. */
    public double spacingGap(BotSettings.Aggression tier) {
        return tierValue("tiers.aggression." + tier.name().toLowerCase(Locale.ROOT) + ".gap",
                null, switch (tier) {
                    case PASSIVE -> 3.0;
                    case BALANCED -> 2.8;
                    case RELENTLESS -> 2.3;
                    case FRENZIED -> 2.2;
                    case UNFAIR -> 1.9;
                    case SUFFER -> 1.5;
                });
    }

    /**
     * Perception lag in ticks: how stale the bot's picture of the player may
     * be. Keyed off accuracy, because that knob has always been where the
     * brain lives.
     */
    public int reactionTicks(BotSettings.Accuracy tier) {
        return (int) Math.max(0, tierValue("tiers.reaction-ticks", tier.name(), switch (tier) {
            case LOW -> 6;
            case MEDIUM -> 4;
            case HIGH -> 2;
            case PERFECT, UNFAIR, SUFFER -> 0;
        }));
    }

    /** Ticks the bot rides incoming knockback before steering again. */
    public int hitstunTicks(BotSettings.Evasiveness tier) {
        return (int) Math.max(0, tierValue("tiers.hitstun-ticks", tier.name(), switch (tier) {
            case SUFFER -> 6;
            case UNFAIR -> 7;
            case EXTREME -> 8;
            default -> 10;
        }));
    }

    /**
     * A path of the form {@code section.TIER}, or a full path when
     * {@code constant} is null. Kept lenient about case so admins can write
     * the tier names either way.
     */
    private double tierValue(String path, String constant, double fallback) {
        if (constant == null) {
            return file.number(path, fallback);
        }
        ConfigurationSection section = file.section(path);
        if (section == null) {
            return fallback;
        }
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(constant)) {
                return section.getDouble(key, fallback);
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------ the layers

    /**
     * Which accuracy tier unlocks each brain layer. Admins can hand the
     * thinking layers to a lower tier (or lock them away entirely) without
     * touching any of the behavior knobs below.
     */
    public boolean cerebral(BotSettings.Accuracy accuracy) {
        return atLeast(accuracy, cerebralFrom);
    }

    public boolean duellist(BotSettings.Accuracy accuracy) {
        return atLeast(accuracy, duellistFrom);
    }

    public boolean unfair(BotSettings.Accuracy accuracy) {
        return atLeast(accuracy, unfairFrom);
    }

    public boolean suffer(BotSettings.Accuracy accuracy) {
        return atLeast(accuracy, sufferFrom);
    }

    private static boolean atLeast(BotSettings.Accuracy accuracy, BotSettings.Accuracy threshold) {
        return threshold != null && accuracy.ordinal() >= threshold.ordinal();
    }

    /**
     * Resolved once per load, not per call: these four are asked several times
     * for every layer lookup the brain makes, which is dozens of times a tick
     * per fight — parsing an enum name out of the YAML each time would be a
     * real cost for a value that can only change on a reload.
     */
    private void loadLayers() {
        cerebralFrom = layerThreshold("layers.cerebral", BotSettings.Accuracy.HIGH);
        duellistFrom = layerThreshold("layers.duellist", BotSettings.Accuracy.PERFECT);
        unfairFrom = layerThreshold("layers.unfair", BotSettings.Accuracy.UNFAIR);
        sufferFrom = layerThreshold("layers.suffer", BotSettings.Accuracy.SUFFER);
    }

    /** @return the tier that unlocks this layer, or null when it is switched off */
    private BotSettings.Accuracy layerThreshold(String path, BotSettings.Accuracy fallback) {
        String name = file.string(path, fallback.name()).trim();
        if (name.equalsIgnoreCase("never") || name.equalsIgnoreCase("off")) {
            return null;
        }
        try {
            return BotSettings.Accuracy.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("pvpbot.yml: " + path + " is '" + name
                    + "', which is not an accuracy tier — using " + fallback + ".");
            return fallback;
        }
    }

    // --------------------------------------------------------- behavior knobs

    /**
     * A layer-scaled number: the best of {@code base} / {@code cerebral} /
     * {@code duellist} / {@code unfair} / {@code suffer} this bot qualifies
     * for. A scalar instead of a section is read as the base value, so a knob
     * that should not scale can just be written as one line.
     */
    public double scaled(String path, BotSettings settings, double fallback) {
        return resolve(path, "", layers(settings), fallback)[0];
    }

    /** Same lookup, rounded to whole ticks. */
    public int scaledTicks(String path, BotSettings settings, int fallback) {
        return (int) Math.round(scaled(path, settings, fallback));
    }

    /**
     * A layer-scaled {@code {min, max}} range, rolled fresh each time — the
     * cooldowns and windows that must not fall into a rhythm a player can
     * learn. Each layer may override the whole range or just one end.
     */
    public int scaledRoll(String path, BotSettings settings, int minFallback, int maxFallback) {
        int mask = layers(settings);
        int min = (int) Math.round(resolve(path, ".min", mask, minFallback)[0]);
        int max = (int) Math.round(resolve(path, ".max", mask, maxFallback)[0]);
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * The layer walk itself: start at {@code base} (or the scalar written in
     * its place) and let every layer this bot qualifies for override it, from
     * weakest to strongest. Memoized per path and layer set.
     */
    private double[] resolve(String path, String suffix, int mask, double fallback) {
        String key = path + suffix + '#' + mask + '#' + fallback;
        double[] cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        double value;
        ConfigurationSection section = file.section(path);
        if (section == null) {
            // Written as a plain scalar, or absent entirely.
            value = file.number(path + suffix, fallback);
        } else {
            // A range may be written per layer ({base: {min: …}}) or flat
            // ({min: …}) when it does not scale; both spellings resolve here.
            value = section.isSet("base" + suffix)
                    ? section.getDouble("base" + suffix, fallback)
                    : section.getDouble(suffix.isEmpty() ? "base" : suffix.substring(1), fallback);
            for (int i = 1; i < LAYERS.length; i++) {
                if ((mask & (1 << i)) == 0) {
                    continue;
                }
                String layer = LAYERS[i] + suffix;
                if (section.isSet(layer)) {
                    value = section.getDouble(layer, value);
                }
            }
        }
        double[] resolved = {value};
        memo.put(key, resolved);
        return resolved;
    }

    /** Bit per layer this bot has unlocked; bit 0 ({@code base}) is always on. */
    private static int layers(BotSettings settings) {
        int mask = 1;
        if (settings.cerebral()) {
            mask |= 1 << 1;
        }
        if (settings.duellist()) {
            mask |= 1 << 2;
        }
        if (settings.unfair()) {
            mask |= 1 << 3;
        }
        if (settings.suffer()) {
            mask |= 1 << 4;
        }
        return mask;
    }

    /** A flat number that does not scale with the difficulty layers. */
    public double flat(String path, double fallback) {
        return file.number(path, fallback);
    }

    public int flatTicks(String path, int fallback) {
        return file.integer(path, fallback);
    }

    /** A flat {@code {min, max}} range, rolled fresh each time. */
    public int roll(String path, int minFallback, int maxFallback) {
        int min = file.integer(path + ".min", minFallback);
        int max = file.integer(path + ".max", maxFallback);
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * A value keyed by evasiveness rather than by brain layer — the knobs that
     * belong to how the bot <em>moves</em>, not to how well it thinks.
     */
    public double byEvasiveness(String path, BotSettings.Evasiveness tier, double fallback) {
        return tierValue(path, tier.name(), file.number(path + ".default", fallback));
    }

    /** Whether an evasiveness tier reaches the threshold named at {@code path}. */
    public boolean evasiveAtLeast(String path, BotSettings.Evasiveness tier,
                                  BotSettings.Evasiveness fallback) {
        String name = file.string(path, fallback.name()).trim();
        if (name.equalsIgnoreCase("never") || name.equalsIgnoreCase("off")) {
            return false;
        }
        BotSettings.Evasiveness threshold;
        try {
            threshold = BotSettings.Evasiveness.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            threshold = fallback;
        }
        return tier.ordinal() >= threshold.ordinal();
    }

    /** Whether an aggression tier reaches the threshold named at {@code path}. */
    public boolean aggressiveAtLeast(String path, BotSettings.Aggression tier,
                                     BotSettings.Aggression fallback) {
        String name = file.string(path, fallback.name()).trim();
        if (name.equalsIgnoreCase("never") || name.equalsIgnoreCase("off")) {
            return false;
        }
        BotSettings.Aggression threshold;
        try {
            threshold = BotSettings.Aggression.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            threshold = fallback;
        }
        return tier.ordinal() >= threshold.ordinal();
    }

    // ----------------------------------------------------------- bot defaults

    /** A per-player default for one of the AI knobs, from {@code defaults}. */
    public <E extends Enum<E>> E defaultTier(String key, Class<E> type, E fallback) {
        return file.constant("defaults." + key, type, fallback);
    }

    public boolean defaultToggle(String key, boolean fallback) {
        return file.bool("defaults." + key, fallback);
    }

    /** The kit a player gets before they ever open the gallery. */
    public PvpKit defaultKit() {
        PvpKit configured = kits.get(file.string("defaults.kit", ""));
        return configured != null ? configured : kits.first();
    }

    // -------------------------------------------------------------- gear tiers

    /**
     * What a gear-tier override dresses the bot in:
     * {@code [helmet, chestplate, leggings, boots, weapon]}, nulls for bare
     * slots. {@code MIRROR} is resolved by the caller from the player's kit.
     */
    public Material[] gearPieces(BotSettings.GearTier tier) {
        String path = "gear-tiers." + tier.name().toLowerCase(Locale.ROOT);
        Material[] pieces = new Material[5];
        String armor = file.string(path + ".armor", defaultArmor(tier));
        if (armor != null && !armor.isBlank() && !armor.equalsIgnoreCase("none")) {
            String prefix = armor.trim().toUpperCase(Locale.ROOT);
            pieces[0] = Material.matchMaterial(prefix + "_HELMET");
            pieces[1] = Material.matchMaterial(prefix + "_CHESTPLATE");
            pieces[2] = Material.matchMaterial(prefix + "_LEGGINGS");
            pieces[3] = Material.matchMaterial(prefix + "_BOOTS");
        }
        // Explicit per-slot overrides win over the armor shorthand.
        pieces[0] = file.material(path + ".helmet", pieces[0]);
        pieces[1] = file.material(path + ".chestplate", pieces[1]);
        pieces[2] = file.material(path + ".leggings", pieces[2]);
        pieces[3] = file.material(path + ".boots", pieces[3]);
        pieces[4] = file.material(path + ".weapon", defaultWeapon(tier));
        return pieces;
    }

    private static String defaultArmor(BotSettings.GearTier tier) {
        return switch (tier) {
            case MIRROR, NONE -> null;
            case LEATHER -> "LEATHER";
            case IRON -> "IRON";
            case DIAMOND -> "DIAMOND";
        };
    }

    private static Material defaultWeapon(BotSettings.GearTier tier) {
        return switch (tier) {
            case MIRROR -> null;
            case NONE -> Material.WOODEN_SWORD;
            case LEATHER -> Material.STONE_SWORD;
            case IRON -> Material.IRON_SWORD;
            case DIAMOND -> Material.DIAMOND_SWORD;
        };
    }

    // ---------------------------------------------------------------- presets

    /** The named difficulty presets, in the order the GUI cycles them. */
    public List<BotPreset> presets() {
        return presets;
    }

    public BotPreset preset(String id) {
        for (BotPreset preset : presets) {
            if (preset.id().equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return null;
    }

    /** The preset after this one, wrapping; null input starts at the first. */
    public BotPreset nextPreset(BotPreset current) {
        if (presets.isEmpty()) {
            return null;
        }
        if (current == null) {
            return presets.get(0);
        }
        int index = presets.indexOf(current);
        return presets.get(index < 0 ? 0 : (index + 1) % presets.size());
    }

    // ------------------------------------------------------------ validation

    /**
     * The whole file swept eagerly for names that will not resolve: tier
     * tables keyed by misspelled constants, layer blocks with a typo'd layer,
     * defaults naming a kit or tier that does not exist, gear built from
     * unknown materials. Every one of these already degrades safely at play
     * time — to the shipped value — which is exactly why it deserves a line
     * at start and reload instead of silence.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        tierTable(problems, "tiers.evasiveness", BotSettings.Evasiveness.class);
        tierTable(problems, "tiers.cps", BotSettings.Cps.class);
        tierTable(problems, "tiers.accuracy", BotSettings.Accuracy.class);
        tierTable(problems, "tiers.combos", BotSettings.Combos.class);
        tierTable(problems, "tiers.reach", BotSettings.Reach.class);
        tierTable(problems, "tiers.reaction-ticks", BotSettings.Accuracy.class);
        tierTable(problems, "tiers.hitstun-ticks", BotSettings.Evasiveness.class);
        aggressionTable(problems);
        for (String layer : new String[]{"cerebral", "duellist", "unfair", "suffer"}) {
            constantOrNever(problems, "layers." + layer, BotSettings.Accuracy.class);
        }
        validateDefaults(problems);
        validateGearTiers(problems);
        validateBehavior(problems);
        return problems;
    }

    private <E extends Enum<E>> void tierTable(List<String> problems, String path,
                                               Class<E> type) {
        ConfigurationSection section = file.section(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (!isConstant(key, type)) {
                problems.add("pvpbot.yml: '" + key + "' under " + path + " is not a "
                        + type.getSimpleName().toLowerCase(Locale.ROOT) + " tier — ignored.");
            } else if (!(section.get(key) instanceof Number)) {
                problems.add("pvpbot.yml: " + path + "." + key
                        + " is not a number — the built-in value is used.");
            }
        }
    }

    private void aggressionTable(List<String> problems) {
        ConfigurationSection section = file.section("tiers.aggression");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (!isConstant(key, BotSettings.Aggression.class)) {
                problems.add("pvpbot.yml: '" + key
                        + "' under tiers.aggression is not an aggression tier — ignored.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                problems.add("pvpbot.yml: tiers.aggression." + key
                        + " must be a {speed, gap} block — the built-in values are used.");
                continue;
            }
            for (String part : new String[]{"speed", "gap"}) {
                if (entry.isSet(part) && !(entry.get(part) instanceof Number)) {
                    problems.add("pvpbot.yml: tiers.aggression." + key + "." + part
                            + " is not a number — the built-in value is used.");
                }
            }
        }
    }

    private void validateDefaults(List<String> problems) {
        if (file.contains("defaults.kit")) {
            String kit = file.string("defaults.kit", "");
            if (!kit.isBlank() && kits.get(kit) == null) {
                problems.add("pvpbot.yml: defaults.kit '" + kit
                        + "' is not a kit defined under kits: — the first kit is used.");
            }
        }
        constantCheck(problems, "defaults.gear", BotSettings.GearTier.class);
        constantCheck(problems, "defaults.evasiveness", BotSettings.Evasiveness.class);
        constantCheck(problems, "defaults.cps", BotSettings.Cps.class);
        constantCheck(problems, "defaults.accuracy", BotSettings.Accuracy.class);
        constantCheck(problems, "defaults.combos", BotSettings.Combos.class);
        constantCheck(problems, "defaults.reach", BotSettings.Reach.class);
        constantCheck(problems, "defaults.aggression", BotSettings.Aggression.class);
    }

    private void validateGearTiers(List<String> problems) {
        ConfigurationSection section = file.section("gear-tiers");
        if (section == null) {
            return;
        }
        for (String tier : section.getKeys(false)) {
            String path = "gear-tiers." + tier;
            String armor = file.string(path + ".armor", "");
            if (!armor.isBlank() && !armor.equalsIgnoreCase("none")
                    && Material.matchMaterial(
                            armor.trim().toUpperCase(Locale.ROOT) + "_HELMET") == null) {
                problems.add("pvpbot.yml: " + path + ".armor '" + armor
                        + "' is not an armor set (LEATHER, CHAINMAIL, IRON, GOLDEN,"
                        + " DIAMOND, NETHERITE) — that tier goes bare.");
            }
            for (String slot : new String[]{"weapon", "helmet", "chestplate",
                    "leggings", "boots"}) {
                String name = file.string(path + "." + slot, "");
                if (!name.isBlank() && Material.matchMaterial(name) == null) {
                    problems.add("pvpbot.yml: '" + name + "' at " + path + "." + slot
                            + " is not an item this server knows.");
                }
            }
        }
    }

    /** The layer-block typo sweep, plus the handful of named thresholds. */
    private void validateBehavior(List<String> problems) {
        ConfigurationSection behavior = file.section("behavior");
        if (behavior != null) {
            java.util.Set<String> allowed = java.util.Set.of(
                    "base", "cerebral", "duellist", "unfair", "suffer", "min", "max");
            for (String key : behavior.getKeys(true)) {
                ConfigurationSection section = behavior.getConfigurationSection(key);
                if (section == null || !section.isSet("base")) {
                    continue; // only blocks that scale by layer have a fixed key set
                }
                for (String layer : section.getKeys(false)) {
                    if (!allowed.contains(layer.toLowerCase(Locale.ROOT))) {
                        problems.add("pvpbot.yml: '" + layer + "' under behavior." + key
                                + " is not a brain layer (base, cerebral, duellist,"
                                + " unfair, suffer) — ignored.");
                    }
                }
            }
        }
        evasivenessTable(problems, "behavior.strafe.burst");
        evasivenessTable(problems, "behavior.strafe.hop.chance");
        constantOrNever(problems, "behavior.strafe.hop.from", BotSettings.Evasiveness.class);
        constantOrNever(problems, "behavior.combo-escape.evasive-from",
                BotSettings.Evasiveness.class);
        constantOrNever(problems, "behavior.approach.fast-from", BotSettings.Aggression.class);
    }

    /** A {default, TIER: …} table keyed by evasiveness. */
    private void evasivenessTable(List<String> problems, String path) {
        ConfigurationSection section = file.section(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (!key.equalsIgnoreCase("default")
                    && !isConstant(key, BotSettings.Evasiveness.class)) {
                problems.add("pvpbot.yml: '" + key + "' under " + path
                        + " is not an evasiveness tier — ignored.");
            }
        }
    }

    private <E extends Enum<E>> void constantCheck(List<String> problems, String path,
                                                   Class<E> type) {
        String name = file.string(path, "");
        if (!name.isBlank() && !isConstant(name, type)) {
            problems.add("pvpbot.yml: " + path + " '" + name + "' is not a "
                    + type.getSimpleName().toLowerCase(Locale.ROOT)
                    + " tier — the built-in default is used.");
        }
    }

    private <E extends Enum<E>> void constantOrNever(List<String> problems, String path,
                                                     Class<E> type) {
        String name = file.string(path, "").trim();
        if (!name.isBlank() && !name.equalsIgnoreCase("never")
                && !name.equalsIgnoreCase("off") && !isConstant(name, type)) {
            problems.add("pvpbot.yml: " + path + " '" + name + "' is not a "
                    + type.getSimpleName().toLowerCase(Locale.ROOT)
                    + " tier (or 'never') — the built-in default is used.");
        }
    }

    private static <E extends Enum<E>> boolean isConstant(String name, Class<E> type) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    private void loadPresets() {
        presets.clear();
        ConfigurationSection section = file.section("presets");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Map<String, String> knobs = new LinkedHashMap<>();
            for (String knob : List.of("evasiveness", "cps", "accuracy",
                    "combos", "reach", "aggression", "rod", "bow", "block")) {
                if (entry.isSet(knob)) {
                    knobs.put(knob, String.valueOf(entry.get(knob)));
                }
            }
            BotPreset preset = BotPreset.parse(plugin, id.toLowerCase(Locale.ROOT),
                    entry.getString("name", ""), knobs);
            if (preset != null) {
                presets.add(preset);
            }
        }
    }
}
