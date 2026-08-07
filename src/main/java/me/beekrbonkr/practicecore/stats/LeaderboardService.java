package me.beekrbonkr.practicecore.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-arena ranking of personal bests, kept fully in memory so ranks are
 * exact and free to read. Built once from disk on enable (see
 * {@link StatsStore#scanAsync()}) and maintained incrementally afterwards —
 * a personal best is the only thing that can change a player's rank.
 *
 * Main-thread only.
 */
public final class LeaderboardService {

    public record Entry(UUID uuid, String name, long millis) {

        public String displayName() {
            return name != null ? name : uuid.toString().substring(0, 8);
        }
    }

    private static final Comparator<Entry> BY_TIME = Comparator.comparingLong(Entry::millis);

    private final Map<String, List<Entry>> boards = new HashMap<>();

    /** Replaces everything with a freshly scanned snapshot. */
    public void load(Map<String, List<Entry>> scanned) {
        boards.clear();
        scanned.forEach((template, entries) -> {
            List<Entry> copy = new ArrayList<>(entries);
            copy.sort(BY_TIME);
            boards.put(template, copy);
        });
    }

    /** Records a personal best, replacing any previous entry for that player. */
    public void submit(UUID player, String name, String template, long millis) {
        List<Entry> entries = boards.computeIfAbsent(template, k -> new ArrayList<>());
        entries.removeIf(e -> e.uuid().equals(player));
        int index = 0;
        while (index < entries.size() && entries.get(index).millis() <= millis) {
            index++;
        }
        entries.add(index, new Entry(player, name, millis));
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
