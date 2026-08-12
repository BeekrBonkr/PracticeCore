package me.beekrbonkr.practicecore.session;

import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.mode.Mode;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.template.TriggerType;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public final class PracticeSession {

    private final UUID playerId;
    private final ArenaTemplate template;
    private final Mode mode;
    private final Slot slot;
    private final Location origin;
    private final org.bukkit.util.BoundingBox bounds;
    private final Location spawn;
    /** Finish trigger block locations → their kind; empty for trigger-less modes. */
    private final Map<Location, TriggerType> triggers;

    private SessionState state = SessionState.PREPARING;
    private final BlockTracker tracker = new BlockTracker();
    private long startNanos = -1;
    private long frozenMs = -1;
    private long lastTimeMs = -1;
    private long bestTimeMs = -1;
    /** Guards the one-shot end-of-session mode hook. */
    private boolean endNotified;
    /** Guards the one-shot arena teardown (quit and an in-flight join abort can both reach it). */
    private boolean cleaned;
    /** Per-mode scratch state; owned entirely by the session's mode. */
    private Object modeState;

    public PracticeSession(UUID playerId, ArenaTemplate template, Mode mode, Slot slot,
                           Location origin, org.bukkit.util.BoundingBox bounds,
                           Location spawn, Map<Location, TriggerType> triggers) {
        this.playerId = playerId;
        this.template = template;
        this.mode = mode;
        this.slot = slot;
        this.origin = origin;
        this.bounds = bounds;
        this.spawn = spawn;
        this.triggers = Map.copyOf(triggers);
    }

    public UUID playerId() {
        return playerId;
    }

    public ArenaTemplate template() {
        return template;
    }

    public Mode mode() {
        return mode;
    }

    public Slot slot() {
        return slot;
    }

    public Location origin() {
        return origin;
    }

    public org.bukkit.util.BoundingBox bounds() {
        return bounds;
    }

    public Location spawn() {
        return spawn;
    }

    /** True when this block location holds one of the finish buttons/plates. */
    public boolean isTrigger(Location loc) {
        return triggers.containsKey(loc);
    }

    /** The trigger kind at this block, or null when it is not a trigger. */
    public TriggerType triggerTypeAt(Location loc) {
        return triggers.get(loc);
    }

    public SessionState state() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public BlockTracker tracker() {
        return tracker;
    }

    public boolean timerRunning() {
        return startNanos >= 0;
    }

    public void startTimer() {
        startNanos = System.nanoTime();
        frozenMs = -1;
    }

    public void resetTimer() {
        startNanos = -1;
        frozenMs = -1;
    }

    /**
     * Pins the elapsed time at this instant. For finishes that must be
     * processed a tick later (e.g. a pearl landing mid-teleport), so the
     * recorded time is when the run actually ended, not when the scheduler
     * got around to it.
     */
    public void freezeTimer() {
        if (timerRunning() && frozenMs < 0) {
            frozenMs = (System.nanoTime() - startNanos) / 1_000_000L;
        }
    }

    /** Monotonic elapsed time — wall-clock honest, immune to server lag and clock jumps. */
    public long elapsedMs() {
        if (frozenMs >= 0) {
            return frozenMs;
        }
        return timerRunning() ? (System.nanoTime() - startNanos) / 1_000_000L : 0L;
    }

    public long lastTimeMs() {
        return lastTimeMs;
    }

    public void setLastTimeMs(long lastTimeMs) {
        this.lastTimeMs = lastTimeMs;
    }

    public long bestTimeMs() {
        return bestTimeMs;
    }

    public void setBestTimeMs(long bestTimeMs) {
        this.bestTimeMs = bestTimeMs;
    }

    /** True the first time only — makes the end-of-session mode hook one-shot. */
    public boolean markEndNotified() {
        if (endNotified) {
            return false;
        }
        endNotified = true;
        return true;
    }

    /** True the first time only — makes the arena teardown one-shot, so the slot is never released twice. */
    public boolean markCleaned() {
        if (cleaned) {
            return false;
        }
        cleaned = true;
        return true;
    }

    public Object modeState() {
        return modeState;
    }

    public void setModeState(Object modeState) {
        this.modeState = modeState;
    }

    /** True when the location is beyond the arena's walls or ceiling (not the floor — that's a fail). */
    public boolean isOutsideWalls(Location loc) {
        return loc.getX() < bounds.getMinX() || loc.getX() >= bounds.getMaxX()
                || loc.getZ() < bounds.getMinZ() || loc.getZ() >= bounds.getMaxZ()
                || loc.getY() >= bounds.getMaxY();
    }

    public boolean containsBlock(Location loc) {
        return bounds.contains(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5, loc.getBlockZ() + 0.5);
    }
}
