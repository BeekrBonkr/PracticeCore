package me.beekrbonkr.practicecore.rush;

import org.bukkit.Material;

import java.util.Locale;

/**
 * What ends a rush run. The player picks exactly one before joining; each
 * map+objective pair keeps its own personal bests and leaderboard.
 */
public enum RushObjective {

    /** Break any enemy team's bed. */
    BED(Material.RED_BED),
    /** Pick up an emerald from an emerald generator. */
    EMERALD(Material.EMERALD),
    /** Pick up a diamond from a diamond generator. */
    DIAMOND(Material.DIAMOND);

    private final Material icon;

    RushObjective(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** messages.yml key for this objective's display name. */
    public String messageKey() {
        return "gui.rush.objective.option." + id();
    }

    /** The stats/leaderboard key for one arena under this objective. */
    public String statsKey(String arenaName) {
        return arenaName + "#" + id();
    }

    public static RushObjective byId(String id, RushObjective def) {
        if (id == null) {
            return def;
        }
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    /**
     * Splits a stats key produced by {@link #statsKey}. Returns null when the
     * key is not a rush composite key.
     */
    public static java.util.Map.Entry<String, RushObjective> parseStatsKey(String key) {
        int hash = key.lastIndexOf('#');
        if (hash <= 0) {
            return null;
        }
        RushObjective objective = byId(key.substring(hash + 1), null);
        return objective == null ? null : java.util.Map.entry(key.substring(0, hash), objective);
    }
}
