package me.beekrbonkr.practicecore.beddefense;

import java.util.Locale;

/**
 * Everything a player chose in the bed defense setup menu. Immutable; the
 * "with" methods hand back an adjusted copy. Persisted as
 * {@code prefs.beddefense.*}; the team base is kept under the historical
 * {@code prefs.rush.team.<map>} key, which both base-layout modes read.
 *
 * <p><b>Competitive</b> is a real match opening: sword, armor, generators
 * and shop, blocks bought with what the generators give. Competitive pins
 * the timer to first movement and is the only plain-round mode whose times
 * are recorded and ranked. <b>Practice</b> is the same start with the
 * defense's exact blocks already in the kit.
 *
 * <p><b>Obsidian</b> overrides both for the round: the defense stands
 * already built, and the kit is tools and eight obsidian to break in,
 * seal the bed and build it back. Nothing is bought, so the kit is fixed
 * and the times are comparable — obsidian rounds are always ranked, on a
 * board of their own. The competitive choice is kept underneath so
 * switching obsidian off restores it.
 *
 * <p><b>Repair</b> (bed repair) overrides both the same way: the defense
 * stands, lit TNT and fireballs keep wrecking it, and the round is rebuilt
 * with exactly the blocks the damage took. There is no finish — the run goes
 * on until the bed has been exposed for too long — and it is scored in
 * rounds survived, on a board of its own. Obsidian and repair are exclusive:
 * the menu switches one off when the other goes on, and repair wins if a
 * file somehow holds both.
 */
public record BedDefenseSelection(boolean competitive, boolean obsidian, boolean repair,
                                  String defense, TimerStart timerStart) {

    /** Which of the round variants the choices amount to. */
    public enum Variant {
        NORMAL, OBSIDIAN, REPAIR
    }

    /**
     * The four ways a round can be started — what the start buttons offer.
     * Each is one combination of the flags underneath: practice and
     * competitive are the plain round with competitive off or on, obsidian
     * and repair the two drills.
     */
    public enum Mode {
        PRACTICE, COMPETITIVE, OBSIDIAN, REPAIR;

        /** The lowercase key used under gui.beddefense.start.* and guis.yml. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Whether rounds in this mode are ranked. */
        public boolean ranked() {
            return this != PRACTICE;
        }
    }

    public enum TimerStart {
        MOVE, FIRST_BLOCK;

        public TimerStart next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String messageKey() {
            return "gui.beddefense.timer.option." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static BedDefenseSelection defaults() {
        return new BedDefenseSelection(false, false, false, null, TimerStart.MOVE);
    }

    /**
     * The choices as they play. Repair and obsidian set competitive aside
     * (the kit is fixed, nothing is bought) and, like competitive, pin the
     * timer to the first move so their ranked results compare.
     */
    public BedDefenseSelection effective() {
        if (repair) {
            return new BedDefenseSelection(false, false, true, defense, TimerStart.MOVE);
        }
        if (obsidian) {
            return new BedDefenseSelection(false, true, false, defense, TimerStart.MOVE);
        }
        if (!competitive) {
            return this;
        }
        return new BedDefenseSelection(true, false, false, defense, TimerStart.MOVE);
    }

    /** Whether rounds under these choices are ranked: competitive, obsidian or repair. */
    public boolean ranked() {
        return competitive || obsidian || repair;
    }

    /** The mode these choices start in: repair over obsidian over competitive over practice. */
    public Mode mode() {
        return repair ? Mode.REPAIR : obsidian ? Mode.OBSIDIAN
                : competitive ? Mode.COMPETITIVE : Mode.PRACTICE;
    }

    /** The choices with the flags set for one mode; everything else is kept. */
    public BedDefenseSelection withMode(Mode mode) {
        return new BedDefenseSelection(mode == Mode.COMPETITIVE, mode == Mode.OBSIDIAN,
                mode == Mode.REPAIR, defense, timerStart);
    }

    /** The variant these choices play: repair over obsidian over the plain round. */
    public Variant variant() {
        return repair ? Variant.REPAIR : obsidian ? Variant.OBSIDIAN : Variant.NORMAL;
    }

    /** Switches to one variant, switching the other special one off. */
    public BedDefenseSelection withVariant(Variant variant) {
        return new BedDefenseSelection(competitive, variant == Variant.OBSIDIAN,
                variant == Variant.REPAIR, defense, timerStart);
    }

    public BedDefenseSelection withCompetitive(boolean competitive) {
        return new BedDefenseSelection(competitive, obsidian, repair, defense, timerStart);
    }

    /** Obsidian on switches repair off: the two cannot run at once. */
    public BedDefenseSelection withObsidian(boolean obsidian) {
        return new BedDefenseSelection(competitive, obsidian, obsidian ? false : repair,
                defense, timerStart);
    }

    /** Repair on switches obsidian off: the two cannot run at once. */
    public BedDefenseSelection withRepair(boolean repair) {
        return new BedDefenseSelection(competitive, repair ? false : obsidian, repair,
                defense, timerStart);
    }

    public BedDefenseSelection withDefense(String defense) {
        return new BedDefenseSelection(competitive, obsidian, repair, defense, timerStart);
    }

    public BedDefenseSelection withTimerStart(TimerStart timerStart) {
        return new BedDefenseSelection(competitive, obsidian, repair, defense, timerStart);
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
