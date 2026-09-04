package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * A practice mode. Templates declare the mode they belong to; each mode hooks
 * its own rules onto the shared session/arena machinery through the defaults
 * below. Bridging is the baseline: every default implements exactly what
 * bridging needs, so a mode overrides only what it does differently.
 *
 * All hooks run on the main thread. Hooks that fire on teardown paths may be
 * called with a null (offline) player and must tolerate being called twice.
 */
public interface Mode {

    /** Stable identifier used in template configs (e.g. "bridging"). */
    String id();

    /** Human-readable name for scoreboards and messages. */
    String displayName();

    /** Whether templates of this mode finish runs on a button/plate trigger. */
    default boolean requiresTrigger() {
        return true;
    }

    /** Whether the configured timer start (MOVE / FIRST_BLOCK) applies. */
    default boolean usesStandardTimerStart() {
        return true;
    }

    /**
     * Whether the timer starts when the player first leaves the spawn block.
     * The default follows the configured start mode; a mode may pin one start
     * regardless of config (rush always times from first movement).
     */
    default boolean startsTimerOnMove(me.beekrbonkr.practicecore.PCConfig config) {
        return usesStandardTimerStart()
                && config.timerStartMode() == me.beekrbonkr.practicecore.PCConfig.TimerStartMode.MOVE;
    }

    /** Whether the timer starts on the first placed block. See {@link #startsTimerOnMove}. */
    default boolean startsTimerOnFirstBlock(me.beekrbonkr.practicecore.PCConfig config) {
        return usesStandardTimerStart()
                && config.timerStartMode() == me.beekrbonkr.practicecore.PCConfig.TimerStartMode.FIRST_BLOCK;
    }

    /**
     * The session-aware form of {@link #startsTimerOnMove(me.beekrbonkr.practicecore.PCConfig)}:
     * modes whose timer rule depends on what the player is doing right now
     * (a preview flies around without starting anything) answer here. The
     * default is the config-only answer.
     */
    default boolean startsTimerOnMove(PracticeCorePlugin plugin, PracticeSession session) {
        return startsTimerOnMove(plugin.pcConfig());
    }

    /** Session-aware form of {@link #startsTimerOnFirstBlock(me.beekrbonkr.practicecore.PCConfig)}. */
    default boolean startsTimerOnFirstBlock(PracticeCorePlugin plugin, PracticeSession session) {
        return startsTimerOnFirstBlock(plugin.pcConfig());
    }

    /**
     * Whether this run may set a personal best and rank on the leaderboard.
     * The default is the template's blocks-required rule; modes with
     * advantage-granting modifiers tighten it.
     */
    default boolean pbEligible(PracticeCorePlugin plugin, PracticeSession session) {
        return !session.template().requireBlocksForPb() || session.tracker().count() > 0;
    }

    /**
     * Whether this finish is written to stats at all — last time, finish
     * count, personal best. Rush casual runs record nothing; only
     * competitive runs put times on the books.
     */
    default boolean recordsRun(PracticeCorePlugin plugin, PracticeSession session) {
        return true;
    }

    /** Whether the action-bar speedometer runs for sessions of this mode. */
    default boolean showsSpeedometer() {
        return false;
    }

    /**
     * Whether arenas of this mode have shared time leaderboards at all.
     * Modes that score differently (MLG streaks, PvP bot session stats)
     * return false so leaderboard menus and commands never show their
     * permanently empty boards.
     */
    default boolean hasLeaderboards() {
        return true;
    }

    /**
     * Whether bucket emptying is allowed in sessions of this mode regardless
     * of the {@code session.allow-buckets} config (modes whose whole point is
     * the bucket).
     */
    default boolean allowsBuckets() {
        return false;
    }

    /**
     * Whether the periodic inventory sweep applies. Modes with an open-ended
     * economy (shop purchases, generator pickups) opt out.
     */
    default boolean validatesInventory() {
        return true;
    }

    /**
     * Last chance to refuse a join after the standard checks, e.g. a missing
     * soft dependency or an unconfigured per-mode section.
     *
     * @return a messages.yml key to send the player, or null to proceed
     */
    default String validateJoin(PracticeCorePlugin plugin, Player player, ArenaTemplate template) {
        return null;
    }

    /**
     * Where the player spawns in a freshly pasted arena. Modes whose spawn
     * depends on a pre-join choice (rush team bases) resolve it here.
     */
    default org.bukkit.Location spawnLocation(PracticeCorePlugin plugin, Player player,
                                              ArenaTemplate template, org.bukkit.Location origin) {
        return template.spawnLocation(origin);
    }

    /**
     * The key this session's times are recorded and ranked under. The default
     * is the arena name; modes with several boards per arena qualify it.
     */
    default String statsKey(PracticeCorePlugin plugin, PracticeSession session) {
        return session.template().name();
    }

    /**
     * Every stats key an arena of this mode can produce — what has to be
     * purged when the arena is deleted.
     */
    default List<String> statsKeys(ArenaTemplate template) {
        return List.of(template.name());
    }

    /** The arena name shown in finish messages and broadcasts for this run. */
    default String runDisplayName(PracticeCorePlugin plugin, PracticeSession session) {
        return session.template().displayName();
    }

    /**
     * The player scrolled or swapped to another hotbar slot. {@code held} is
     * the item now in hand, possibly null/air.
     */
    default void onHeldItemChange(PracticeCorePlugin plugin, Player player,
                                  PracticeSession session, ItemStack held) {
    }

    /**
     * The session reached READY: right after the join teleport, and again at
     * the end of every arena reset. Regenerate mode-owned blocks and schedule
     * mode-owned tasks here.
     */
    default void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
    }

    /**
     * The arena is about to reset (finish, fail, restart) while the session
     * continues. The player still holds their in-run inventory.
     */
    default void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
    }

    /**
     * The session is ending for good (leave, quit, switch, shutdown). Called
     * before the player's snapshot is restored, so their in-run inventory is
     * still visible. Cancel tasks and persist anything worth keeping.
     */
    default void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
    }

    /**
     * The player fell below the arena floor. Return true when the mode
     * handled it itself (PvP sparring counts it as a ring-out death); false
     * runs the standard fail-and-reset.
     */
    default boolean onVoidFall(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        return false;
    }

    /** May the player break this block? Default: only blocks they placed. */
    default boolean canBreak(PracticeSession session, Block block) {
        return session.tracker().isTracked(block.getLocation());
    }

    /** A permitted block break is about to happen. */
    default void onBlockBreak(PracticeCorePlugin plugin, Player player, PracticeSession session,
                              BlockBreakEvent event) {
    }

    /**
     * The kit about to be handed to the player. Modes that remember per-player
     * layouts remap slots here; the returned map is not retained.
     */
    default Map<Integer, ItemStack> arrangeKit(PracticeCorePlugin plugin, Player player,
                                               ArenaTemplate template) {
        return template.kit();
    }

    /** Custom sidebar lines, or null for the standard timer board. */
    default List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        return null;
    }

    /**
     * The material player-facing menus show for an arena of this mode. The
     * default is the template's own configured/derived icon; modes with a
     * better source (rush mirrors the MBedwars arena icon) override this.
     */
    default org.bukkit.Material menuIcon(PracticeCorePlugin plugin, ArenaTemplate template) {
        return template.effectiveIcon();
    }
}
