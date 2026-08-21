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
     * config.yml — admin-editable. v5 retunes the untouched gold generator
     * interval to the 4:1 forge ratio and adds the rush combat keys.
     */
    public static final int CONFIG = 5;

    /**
     * messages.yml — admin-editable. v6 extends the PvP bot sidebar with the
     * session-stat lines (accuracy, K/D, dodged).
     */
    public static final int MESSAGES = 6;

    /**
     * guis.yml — admin-editable menu layout. v2 grew the rush menu to five
     * rows for the defender-bot buttons; v3 re-laid it into a row per
     * decision and added the bed-defense gallery.
     */
    public static final int GUIS = 3;

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
