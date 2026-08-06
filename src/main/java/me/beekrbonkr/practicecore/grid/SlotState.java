package me.beekrbonkr.practicecore.grid;

public enum SlotState {
    /** Clean and assignable. */
    FREE,
    /** Handed out, arena being prepared. */
    RESERVED,
    /** Session running. */
    OCCUPIED,
    /** Session ended, cleanup in flight — never assignable. */
    DIRTY
}
