package me.beekrbonkr.practicecore.rush;

import me.beekrbonkr.practicecore.PCConfig;
import org.bukkit.Material;

import java.util.Locale;

/**
 * The one-click loadouts on the bottom row of the rush config menu: each
 * writes a known-good set of modifiers over the player's stored selection and
 * starts the run immediately, so getting into a specific kind of practice is
 * one click instead of five dials.
 *
 * The two competitive presets touch only the bots toggle — everything else is
 * pinned by the ranked loadout at join anyway (see
 * {@link RushService#selection}), and the player's stored casual modifiers
 * deserve to survive a ranked detour. The casual presets overwrite the dials
 * for real; the menu shows the new values the next time it opens.
 */
public enum RushPreset {

    /** The ranked race: no starting items, defenses on, no defenders. */
    COMPETITIVE("competitive", Material.DIAMOND, true, false, null),

    /** The ranked team wipe: same pinned loadout, configured defender lineup. */
    COMPETITIVE_WIPE("competitive-wipe", Material.DIAMOND_SWORD, true, true, null),

    /** Bridging routes on repeat: a full stack of wool and nothing in the way. */
    BRIDGE("bridge", Material.SCAFFOLDING, false, false,
            new Loadout(RushSelection.BlockTier.SIXTY_FOUR, RushSelection.CurrencyTier.NONE,
                    RushSelection.PickaxeTier.NONE, RushSelection.TntTier.NONE, false, 0)),

    /** Cracking a defended bed: pickaxe, TNT, wool, the standard defense up. */
    BED_BREAK("bed-break", Material.TNT, false, false,
            new Loadout(RushSelection.BlockTier.SIXTY_FOUR, RushSelection.CurrencyTier.NONE,
                    RushSelection.PickaxeTier.IRON, RushSelection.TntTier.FOUR, true, 0)),

    /** The ranked race's exact conditions, without the run going on the books. */
    WARMUP("warmup", Material.FEATHER, false, false,
            new Loadout(RushSelection.BlockTier.NONE, RushSelection.CurrencyTier.NONE,
                    RushSelection.PickaxeTier.NONE, RushSelection.TntTier.NONE, true, 0)),

    /** Fighting through defenders casually, with enough gear to reach them. */
    SKIRMISH("skirmish", Material.IRON_SWORD, false, false,
            new Loadout(RushSelection.BlockTier.THIRTY_TWO, RushSelection.CurrencyTier.SMALL,
                    RushSelection.PickaxeTier.IRON, RushSelection.TntTier.NONE, false, 2)),

    /** Everything on: mess-around mode with every starter the menu can give. */
    SANDBOX("sandbox", Material.CHEST, false, false,
            new Loadout(RushSelection.BlockTier.SIXTY_FOUR, RushSelection.CurrencyTier.LARGE,
                    RushSelection.PickaxeTier.DIAMOND, RushSelection.TntTier.FOUR, false, 0));

    /**
     * A casual preset's dial positions. {@code standardDefense} raises the
     * configured competitive defense preset — the "standard" pyramid — over
     * every enemy bed; false leaves the beds bare. {@code bots} is clamped to
     * the configured per-team ceiling at apply time.
     */
    private record Loadout(RushSelection.BlockTier blocks, RushSelection.CurrencyTier currency,
                           RushSelection.PickaxeTier pickaxe, RushSelection.TntTier tnt,
                           boolean standardDefense, int bots) {
    }

    private final String id;
    private final Material icon;
    private final boolean competitive;
    private final boolean combat;
    private final Loadout loadout;

    RushPreset(String id, Material icon, boolean competitive, boolean combat, Loadout loadout) {
        this.id = id;
        this.icon = icon;
        this.competitive = competitive;
        this.combat = combat;
        this.loadout = loadout;
    }

    /** Stable id used in guis.yml ("preset-&lt;id&gt;") and messages.yml keys. */
    public String id() {
        return id;
    }

    /** The default tile material; guis.yml may override it per preset. */
    public Material icon() {
        return icon;
    }

    public boolean competitive() {
        return competitive;
    }

    /** The messages.yml key under {@code gui.rush.presets.}. */
    public String messageKey(String leaf) {
        return "gui.rush.presets." + id + "." + leaf;
    }

    /**
     * False for presets the config has switched off — a competitive team wipe
     * with a zero-defender lineup, a skirmish on a server with bots capped at
     * none. A tile that would silently start something else is not rendered.
     */
    public boolean available(PCConfig config) {
        if (this == COMPETITIVE_WIPE) {
            return config.rushBotsCompetitivePerTeam() > 0;
        }
        return loadout == null || loadout.bots() == 0 || config.rushBotsMaxPerTeam() > 0;
    }

    /** The stored selection with this preset's dials written over it. */
    public RushSelection apply(RushSelection current, PCConfig config) {
        if (loadout == null) {
            // Competitive presets: only the bots toggle matters — the ranked
            // loadout pins everything else at join, and the stored casual
            // modifiers stay the player's own.
            return current
                    .withBots(combat ? config.rushBotsCompetitivePerTeam() : 0)
                    .withCompetitive(true);
        }
        return current
                .withBlocks(loadout.blocks())
                .withCurrency(loadout.currency())
                .withPickaxe(loadout.pickaxe())
                .withTnt(loadout.tnt())
                .withDefense(loadout.standardDefense()
                        ? config.rushCompetitiveDefense() : RushDefense.NONE)
                .withBaseGenerators(true)
                .withBots(Math.min(loadout.bots(), config.rushBotsMaxPerTeam()))
                .withCompetitive(false);
    }

    /** The guis.yml button key: {@code rush.buttons.preset-<id>}. */
    public String buttonKey() {
        return "rush.buttons.preset-" + id.toLowerCase(Locale.ROOT);
    }

    /** Bottom border row, between the back and close buttons: 46 through 52. */
    public int defaultSlot() {
        return 46 + ordinal();
    }
}
