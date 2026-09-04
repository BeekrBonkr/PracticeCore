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
     * config.yml — admin-editable. v7 adds the bed defense practice section
     * (additive); v6 raised the untouched competitive defender lineup from 0
     * to 4 per team, so the competitive team-wipe preset has a lineup to pin.
     */
    public static final int CONFIG = 7;

    /**
     * messages.yml — admin-editable. v11 rewords the bed defense practice
     * notice now that practice rounds keep a personal best. v10 drops
     * strict order: its toggle,
     * board name and wrong-order warning are gone, and the lines that listed
     * it lose that entry. v9 is the UI style guide: every menu name, lore and
     * chat line was reworded, shared labels moved under {@code label.} and
     * lore click-hints under {@code gui.hint.}. Values an admin never touched
     * are reset to the new wording; edited ones stand. v8 added bed defense
     * practice; v7 the rush TNT modifier.
     */
    public static final int MESSAGES = 11;

    /**
     * guis.yml — admin-editable menu layout. v7 removes the bed defense
     * strict-order button and closes the gap it left in that row. v6 is the
     * UI style guide: back and close now default to the bottom corners of
     * every menu, computed from its row count, and icons were reassigned so
     * one material means one thing. Values an admin never touched are reset;
     * edited ones stand. v5 added the bed defense menus.
     */
    public static final int GUIS = 7;

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

    /** defenses/&lt;id&gt;.yml — plugin-owned, player-authored bed defenses. */
    public static final int DEFENSE = 1;

    /** Key used in admin-facing files. */
    public static final String KEY = "config-version";

    /** Key used in plugin-owned data files. */
    public static final String DATA_KEY = "data-version";

    private Versions() {
    }
}
