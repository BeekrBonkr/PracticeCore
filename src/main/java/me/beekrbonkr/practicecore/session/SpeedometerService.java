package me.beekrbonkr.practicecore.session;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Action-bar speedometer shown above the hotbar for modes that ask for it
 * (bridging): current speed in meters per second plus blocks moved and placed
 * this run. Positions are sampled on a fixed tick schedule and smoothed with a
 * short exponential average, so the reading is steady rather than jittering
 * with every knot in the player's path.
 */
public final class SpeedometerService {

    private static final double SMOOTHING = 0.55; // weight of the previous reading
    /** A per-sample jump this large is a teleport, not running — rebase, don't count. */
    private static final double TELEPORT_DISTANCE = 10;
    /** How long other action-bar text keeps the speedometer quiet. */
    private static final long HOLD_NANOS = 2_500_000_000L;

    private static final class Sample {
        PracticeSession session;
        Location last;
        long lastNanos;
        double speed;    // smoothed m/s
        double traveled; // total horizontal meters this run
        boolean wasActive; // to spot ACTIVE → READY (an arena reset)
    }

    private final PracticeCorePlugin plugin;
    private final Map<UUID, Sample> samples = new HashMap<>();
    /** Players whose action bar is showing something more important right now. */
    private final Map<UUID, Long> heldUntil = new HashMap<>();
    private BukkitTask task;

    public SpeedometerService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        if (!plugin.pcConfig().speedometerEnabled()) {
            return;
        }
        int ticks = plugin.pcConfig().speedometerTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, ticks, ticks);
    }

    /** Picks up changed speedometer settings after /practice reload. */
    public void restartTask() {
        shutdown();
        startTask();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        samples.clear();
        heldUntil.clear();
    }

    /**
     * Another action-bar message was just shown to this player. The
     * speedometer keeps sampling but stays silent long enough for the player
     * to actually read it — otherwise the next refresh would wipe it within
     * a few ticks.
     */
    public void yieldActionBar(UUID player) {
        heldUntil.put(player, System.nanoTime() + HOLD_NANOS);
    }

    /**
     * Drops everything tracked for a player. Called on quit — the hold map is
     * fed by every action-bar message, including ones to players who never
     * enter a speedometer mode, so quit is the only reliable eviction point.
     */
    public void forget(UUID player) {
        samples.remove(player);
        heldUntil.remove(player);
    }

    private void updateAll() {
        // Drop trackers whose session is gone (leave, quit, switch).
        for (Iterator<Map.Entry<UUID, Sample>> it = samples.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Sample> entry = it.next();
            if (plugin.sessions().get(entry.getKey()) != entry.getValue().session) {
                it.remove();
            }
        }
        for (PracticeSession session : plugin.sessions().all()) {
            if (!session.mode().showsSpeedometer()) {
                continue;
            }
            SessionState state = session.state();
            if (state != SessionState.READY && state != SessionState.ACTIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Sample sample = samples.computeIfAbsent(session.playerId(), id -> new Sample());
            // A run "starts over" on a new session or on an arena reset
            // (ACTIVE dropping back to READY) — not merely on being READY,
            // which under FIRST_BLOCK covers the whole sprint before the
            // first placement.
            if (sample.session != session
                    || (state == SessionState.READY && sample.wasActive)) {
                sample.session = session;
                sample.last = player.getLocation();
                sample.lastNanos = System.nanoTime();
                sample.speed = 0;
                sample.traveled = 0;
            } else {
                long now = System.nanoTime();
                Location loc = player.getLocation();
                double seconds = (now - sample.lastNanos) / 1_000_000_000.0;
                if (seconds > 0) {
                    double dx = loc.getX() - sample.last.getX();
                    double dz = loc.getZ() - sample.last.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance < TELEPORT_DISTANCE) {
                        sample.traveled += distance;
                        sample.speed = SMOOTHING * sample.speed
                                + (1 - SMOOTHING) * (distance / seconds);
                    }
                }
                sample.last = loc;
                sample.lastNanos = now;
            }
            sample.wasActive = state == SessionState.ACTIVE;
            Long held = heldUntil.get(session.playerId());
            if (held != null) {
                if (System.nanoTime() < held) {
                    continue; // something more important is on the action bar
                }
                heldUntil.remove(session.playerId());
            }
            plugin.messages().actionBar(player, "speedometer.bar",
                    "speed", String.format(Locale.ROOT, "%.1f", sample.speed),
                    "moved", String.valueOf(Math.round(sample.traveled)),
                    "placed", String.valueOf(session.tracker().count()));
        }
    }
}
