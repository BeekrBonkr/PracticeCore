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
     * config.yml — admin-editable. v9 adds the bed defense obsidian practice
     * tools (additive); v8 added the bed defense publishing gate and report
     * keys (additive); v7 added the bed defense practice section (additive);
     * v6 raised the untouched competitive defender lineup from 0 to 4 per
     * team, so the competitive team-wipe preset has a lineup to pin.
     */
    public static final int CONFIG = 9;

    /**
     * messages.yml — admin-editable. v13 adds bed defense obsidian practice:
     * its toggle, board name, sidebar line and notices arrive (additive),
     * and the mode lore, the boards' empty state and the command reference
     * now mention it — lists still at their old default are rewritten,
     * an admin's own wording stands. v12 gives bed defense its own maps:
     * the "needs a rush map" refusal is gone (replaced by not-a-map), the
     * map picker's empty state points at the new admin commands, and the
     * publishing gate, reports and moderation text arrive (additive). v11
     * rewords the bed defense practice
     * notice now that practice rounds keep a personal best. v10 drops
     * strict order: its toggle,
     * board name and wrong-order warning are gone, and the lines that listed
     * it lose that entry. v9 is the UI style guide: every menu name, lore and
     * chat line was reworded, shared labels moved under {@code label.} and
     * lore click-hints under {@code gui.hint.}. Values an admin never touched
     * are reset to the new wording; edited ones stand. v8 added bed defense
     * practice; v7 the rush TNT modifier.
     */
    public static final int MESSAGES = 13;

    /**
     * guis.yml — admin-editable menu layout. v9 adds the bed defense
     * Obsidian toggle to the setup menu's rules row, which spreads to
     * 19/21/23/25 (defense, mode, obsidian, timer); buttons still at
     * their v8 slots move, an admin's own row stands. v8 is the
     * hub/rush/pvpbot/bed defense re-layout: values still at their v7 defaults are reset so the
     * new defaults apply (the hub's Random and Sidebar buttons are off by
     * default now, the sidebar toggle having moved into Settings), the dead
     * rush objective-* keys are dropped, and the report button, review tab
     * and moderation menus arrive.
     * v7 removes the bed defense
     * strict-order button and closes the gap it left in that row. v6 is the
     * UI style guide: back and close now default to the bottom corners of
     * every menu, computed from its row count, and icons were reassigned so
     * one material means one thing. Values an admin never touched are reset;
     * edited ones stand. v5 added the bed defense menus.
     */
    public static final int GUIS = 9;

    /** sounds.yml — admin-editable sound cues. */
    public static final int SOUNDS = 1;

    /**
     * pvpbot.yml — admin-editable bot tuning and PvP kits. v2 smoothed the
     * difficulty ladder and reworked the gapple into a retreat-chew-recommit.
     */
    public static final int PVPBOT = 2;

    /** templates/[&lt;category&gt;/]&lt;name&gt;/arena.yml — admin-editable. */
    public static final int ARENA = 3;

    /**
     * playerdata/&lt;uuid&gt;.yml — plugin-owned, but hand-edited often enough.
     * v2 adds the {@code notices} list: messages queued for a player who was
     * offline when something of theirs changed, delivered on their next login.
     */
    public static final int PLAYERDATA = 2;

    /** snapshots/&lt;uuid&gt;.yml — plugin-owned crash-recovery state. */
    public static final int SNAPSHOT = 1;

    /**
     * defenses/&lt;id&gt;.yml — plugin-owned, player-authored bed defenses. v2
     * adds {@code cleared-fingerprint} (the shape the author finished
     * competitively — the publishing gate) and the {@code reports} list.
     */
    public static final int DEFENSE = 2;

    /** Key used in admin-facing files. */
    public static final String KEY = "config-version";

    /** Key used in plugin-owned data files. */
    public static final String DATA_KEY = "data-version";

    private Versions() {
    }
}
