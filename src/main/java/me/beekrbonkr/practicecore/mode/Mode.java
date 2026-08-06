package me.beekrbonkr.practicecore.mode;

/**
 * A practice mode. Templates declare the mode they belong to; future modes
 * (clutching, PvP warmups, …) register here and can hook their own rules
 * onto the shared session/arena machinery.
 */
public interface Mode {

    /** Stable identifier used in template configs (e.g. "bridging"). */
    String id();

    /** Human-readable name for scoreboards and messages. */
    String displayName();
}
