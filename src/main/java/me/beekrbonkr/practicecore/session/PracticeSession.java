package me.beekrbonkr.practicecore.session;

import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Location;

import java.util.UUID;

public final class PracticeSession {

    private final UUID playerId;
    private final ArenaTemplate template;
    private final Slot slot;
    private final Location origin;
    private final org.bukkit.util.BoundingBox bounds;
    private final Location spawn;
    private final Location trigger;

    private SessionState state = SessionState.PREPARING;
    private final BlockTracker tracker = new BlockTracker();
    private long startNanos = -1;
    private long lastTimeMs = -1;
    private long bestTimeMs = -1;

    public PracticeSession(UUID playerId, ArenaTemplate template, Slot slot,
                           Location origin, org.bukkit.util.BoundingBox bounds,
                           Location spawn, Location trigger) {
        this.playerId = playerId;
        this.template = template;
        this.slot = slot;
        this.origin = origin;
        this.bounds = bounds;
        this.spawn = spawn;
        this.trigger = trigger;
    }

    public UUID playerId() {
        return playerId;
    }

    public ArenaTemplate template() {
        return template;
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

    /** Block location of the finish button/plate. */
    public Location trigger() {
        return trigger;
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
    }

    public void resetTimer() {
        startNanos = -1;
    }

    /** Monotonic elapsed time — wall-clock honest, immune to server lag and clock jumps. */
    public long elapsedMs() {
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
