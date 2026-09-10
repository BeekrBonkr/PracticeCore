package me.beekrbonkr.practicecore.stats;

import me.beekrbonkr.practicecore.util.TimeFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/**
 * Per-arena ranking of personal bests, kept fully in memory so ranks are
 * exact and free to read. Built once from disk on enable (see
 * {@link StatsStore#scanAsync()}) and maintained incrementally afterwards —
 * a personal best is the only thing that can change a player's rank.
 *
 * <p>Most boards rank times, lowest first. A <b>score board</b> ranks a
 * count instead — bed repair's rounds survived — highest first; the value
 * still travels in {@link Entry#millis()} and the {@code best-ms} field, so
 * every store, scan and menu handles both alike, and only the order and
 * the wording differ. Which keys are scored is registered by the mode that
 * owns them ({@link #registerScoreKeys}), so the scan can sort off-thread
 * without knowing any mode.
 *
 * Main-thread only, save for {@link #scored} and {@link #order}.
 */
public final class LeaderboardService {

    public record Entry(UUID uuid, String name, long millis) {

        public String displayName() {
            return name != null ? name : uuid.toString().substring(0, 8);
        }
    }

    private static final Comparator<Entry> BY_TIME = Comparator.comparingLong(Entry::millis);
    private static final Comparator<Entry> BY_SCORE = BY_TIME.reversed();

    /** A family of score boards: which keys, and how their values read. */
    private record Scoring(Predicate<String> keys, LongFunction<String> format) {
    }

    private final Map<String, List<Entry>> boards = new HashMap<>();
    private final List<Scoring> scorings = new CopyOnWriteArrayList<>();

    // ------------------------------------------------------------- scoring

    /**
     * Marks every key the predicate accepts as a score board — higher is
     * better — whose values are shown through {@code format} instead of as
     * times.
     */
    public void registerScoreKeys(Predicate<String> keys, LongFunction<String> format) {
        scorings.add(new Scoring(keys, format));
    }

    private Scoring scoring(String key) {
        if (key == null) {
            return null;
        }
        for (Scoring scoring : scorings) {
            if (scoring.keys().test(key)) {
                return scoring;
            }
        }
        return null;
    }

    /** True for a board that ranks a score (higher first) rather than a time. */
    public boolean scored(String key) {
        return scoring(key) != null;
    }

    /** A board value as the player reads it: a time, or the score's own wording. */
    public String format(String key, long value) {
        Scoring scoring = scoring(key);
        return scoring != null ? scoring.format().apply(value) : TimeFormat.precise(value);
    }

    /** The distance between two values on a board, always as a positive amount. */
    public String formatGap(String key, long a, long b) {
        return format(key, Math.abs(a - b));
    }

    /** The order a board's entries rank in: best first. */
    public Comparator<Entry> order(String key) {
        return scored(key) ? BY_SCORE : BY_TIME;
    }

    /** True when {@code candidate} beats {@code best} on this board (a first result always does). */
    public boolean better(String key, long candidate, long best) {
        if (best < 0) {
            return true;
        }
        return scored(key) ? candidate > best : candidate < best;
    }

    // -------------------------------------------------------------- boards

    /** Replaces everything with a freshly scanned snapshot. */
    public void load(Map<String, List<Entry>> scanned) {
        boards.clear();
        scanned.forEach((template, entries) -> {
            List<Entry> copy = new ArrayList<>(entries);
            copy.sort(order(template));
            boards.put(template, copy);
        });
    }

    /** Records a personal best, replacing any previous entry for that player. */
    public void submit(UUID player, String name, String template, long millis) {
        List<Entry> entries = boards.computeIfAbsent(template, k -> new ArrayList<>());
        entries.removeIf(e -> e.uuid().equals(player));
        Entry entry = new Entry(player, name, millis);
        Comparator<Entry> order = order(template);
        int index = 0;
        while (index < entries.size() && order.compare(entries.get(index), entry) <= 0) {
            index++;
        }
        entries.add(index, entry);
    }

    public void remove(UUID player, String template) {
        List<Entry> entries = boards.get(template);
        if (entries != null) {
            entries.removeIf(e -> e.uuid().equals(player));
        }
    }

    public void removeAll(UUID player) {
        boards.values().forEach(entries -> entries.removeIf(e -> e.uuid().equals(player)));
    }

    /** Drops an arena's board entirely — used when the arena is deleted. */
    public void forget(String template) {
        boards.remove(template);
    }

    public List<Entry> top(String template, int limit) {
        List<Entry> entries = boards.get(template);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries.subList(0, Math.min(limit, entries.size())));
    }

    public Entry record(String template) {
        List<Entry> entries = boards.get(template);
        return entries == null || entries.isEmpty() ? null : entries.get(0);
    }

    /** 1-based position, or 0 when the player has no time on this arena. */
    public int rank(String template, UUID player) {
        List<Entry> entries = boards.get(template);
        if (entries == null) {
            return 0;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).uuid().equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    public int size(String template) {
        List<Entry> entries = boards.get(template);
        return entries == null ? 0 : entries.size();
    }
}
