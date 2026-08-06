package me.beekrbonkr.practicecore.session;

/**
 * Single source of truth for a session. Every event handler checks this
 * before acting; transitions happen only on the main thread. This one enum
 * kills the double-finish, quit-during-reset, and place-during-reset races.
 */
public enum SessionState {
    /** Slot allocated, paste/teleport in flight. Player state untouched yet. */
    PREPARING,
    /** Standing at spawn, timer not started. */
    READY,
    /** Timer running. */
    ACTIVE,
    /** Finish or fail being processed; arena reverting. */
    RESETTING,
    /** Session tearing down; ignore everything. */
    ENDING
}
