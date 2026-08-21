package me.beekrbonkr.practicecore.config;

/**
 * Format versions for every file PracticeCore writes or an admin edits.
 *
 * Bump the relevant constant in the same commit that changes a file's shape,
 * and add the matching step to that file's migrator. A file stamped with a
 * version <em>higher</em> than the constant here came from a newer build and
 * is left strictly alone — downgrading is never silently "fixed up".
 */
public final class Versions {

    /**
     * config.yml — admin-editable. v6 raises the untouched competitive
     * defender lineup from 0 to 4 per team, so the competitive team-wipe
     * preset has a lineup to pin.
     */
    public static final int CONFIG = 6;

    /**
     * messages.yml — admin-editable. v7 adds the rush TNT modifier, the rush
     * preset tiles and the leaderboard category picker (all additive).
     */
    public static final int MESSAGES = 7;

    /**
     * guis.yml — admin-editable menu layout. v4 adds the rush TNT modifier
     * and the preset row along the rush menu's bottom border (additive).
     */
    public static final int GUIS = 4;

    /** sounds.yml — admin-editable sound cues. */
    public static final int SOUNDS = 1;

    /**
     * pvpbot.yml — admin-editable bot tuning and PvP kits. v2 smoothed the
     * difficulty ladder and reworked the gapple into a retreat-chew-recommit.
     */
    public static final int PVPBOT = 2;

    /** templates/[&lt;category&gt;/]&lt;name&gt;/arena.yml — admin-editable. */
    public static final int ARENA = 3;

    /** playerdata/&lt;uuid&gt;.yml — plugin-owned, but hand-edited often enough. */
    public static final int PLAYERDATA = 1;

    /** snapshots/&lt;uuid&gt;.yml — plugin-owned crash-recovery state. */
    public static final int SNAPSHOT = 1;

    /** Key used in admin-facing files. */
    public static final String KEY = "config-version";

    /** Key used in plugin-owned data files. */
    public static final String DATA_KEY = "data-version";

    private Versions() {
    }
}
