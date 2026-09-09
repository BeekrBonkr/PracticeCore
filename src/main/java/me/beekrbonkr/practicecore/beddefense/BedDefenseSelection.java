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
 * the timer to first movement, forbids shuffle and is the only mode whose
 * times are recorded and ranked. <b>Practice</b> is the same start with the
 * defense's exact blocks already in the kit.
 *
 * <p><b>Obsidian</b> overrides both for the round: the defense stands
 * already built, and the kit is tools and eight obsidian to break in,
 * seal the bed and build it back. Nothing is bought, so the kit is fixed
 * and the times are comparable — obsidian rounds are always ranked, on a
 * board of their own. The competitive choice is kept underneath so
 * switching obsidian off restores it.
 */
public record BedDefenseSelection(boolean competitive, boolean obsidian, String defense,
                                  Shuffle shuffle, TimerStart timerStart) {

    /** Which pool a fresh defense is drawn from every round. */
    public enum Shuffle {
        OFF, FAVORITES, PUBLIC;

        public Shuffle next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String messageKey() {
            return "gui.beddefense.shuffle.option." + name().toLowerCase(Locale.ROOT);
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
        return new BedDefenseSelection(false, false, null, Shuffle.OFF, TimerStart.MOVE);
    }

    /**
     * The choices as they play. Obsidian sets competitive aside (the kit is
     * fixed, nothing is bought) and, like competitive, pins the timer to the
     * first move so its ranked times compare; shuffle stays open to it.
     * Competitive pins shuffle off as well.
     */
    public BedDefenseSelection effective() {
        if (obsidian) {
            return new BedDefenseSelection(false, true, defense, shuffle, TimerStart.MOVE);
        }
        if (!competitive) {
            return this;
        }
        return new BedDefenseSelection(true, false, defense, Shuffle.OFF, TimerStart.MOVE);
    }

    /** Whether rounds under these choices are ranked: competitive or obsidian. */
    public boolean ranked() {
        return competitive || obsidian;
    }

    public BedDefenseSelection withCompetitive(boolean competitive) {
        return new BedDefenseSelection(competitive, obsidian, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withObsidian(boolean obsidian) {
        return new BedDefenseSelection(competitive, obsidian, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withDefense(String defense) {
        return new BedDefenseSelection(competitive, obsidian, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withShuffle(Shuffle shuffle) {
        return new BedDefenseSelection(competitive, obsidian, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withTimerStart(TimerStart timerStart) {
        return new BedDefenseSelection(competitive, obsidian, defense, shuffle, timerStart);
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
