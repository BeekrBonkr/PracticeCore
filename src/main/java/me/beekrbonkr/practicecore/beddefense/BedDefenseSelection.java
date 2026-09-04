package me.beekrbonkr.practicecore.beddefense;

import java.util.Locale;

/**
 * Everything a player chose in the bed defense setup menu. Immutable; the
 * "with" methods hand back an adjusted copy. Persisted as
 * {@code prefs.beddefense.*}; the team base is shared with rush
 * ({@code prefs.rush.team.<map>}) since it is the same map and the same
 * base.
 *
 * <p><b>Competitive</b> is a real match opening: sword, armor, generators
 * and shop, blocks bought with what the generators give. Competitive pins
 * the timer to first movement, forbids shuffle and is the only mode whose
 * times are recorded and ranked. <b>Practice</b> is the same start with the
 * defense's exact blocks already in the kit.
 */
public record BedDefenseSelection(boolean competitive, String defense,
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
        return new BedDefenseSelection(false, null, Shuffle.OFF, TimerStart.MOVE);
    }

    /** Competitive pins the choices that would make times incomparable. */
    public BedDefenseSelection effective() {
        if (!competitive) {
            return this;
        }
        return new BedDefenseSelection(true, defense, Shuffle.OFF, TimerStart.MOVE);
    }

    public BedDefenseSelection withCompetitive(boolean competitive) {
        return new BedDefenseSelection(competitive, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withDefense(String defense) {
        return new BedDefenseSelection(competitive, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withShuffle(Shuffle shuffle) {
        return new BedDefenseSelection(competitive, defense, shuffle, timerStart);
    }

    public BedDefenseSelection withTimerStart(TimerStart timerStart) {
        return new BedDefenseSelection(competitive, defense, shuffle, timerStart);
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
