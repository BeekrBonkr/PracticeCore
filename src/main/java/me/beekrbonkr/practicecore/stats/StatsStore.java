package me.beekrbonkr.practicecore.stats;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.Versions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Per-player times and preferences, one YAML per UUID. Cached in memory; file
 * I/O happens off-thread (the YAML string is rendered on the main thread
 * first).
 *
 * The store also owns the name index: every file records the player's last
 * known name, so admin commands can resolve and tab-complete players the
 * plugin has seen before without them being online.
 */
public final class StatsStore {

    private final PracticeCorePlugin plugin;
    private final File dir;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();
    /** uuid → last known name, and its lower-cased inverse. */
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public StatsStore(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create playerdata directory");
        }
    }

    // ------------------------------------------------------------ run times

    public long bestMs(UUID player, String template) {
        return data(player).getLong("templates." + template + ".best-ms", -1);
    }

    public long lastMs(UUID player, String template) {
        return data(player).getLong("templates." + template + ".last-ms", -1);
    }

    public int finishes(UUID player, String template) {
        return data(player).getInt("templates." + template + ".finishes", 0);
    }

    /** Every arena this player has a recorded best on, fastest first. */
    public Map<String, Long> bests(UUID player) {
        ConfigurationSection section = data(player).getConfigurationSection("templates");
        if (section == null) {
            return Map.of();
        }
        Map<String, Long> found = new TreeMap<>();
        for (String template : section.getKeys(false)) {
            long best = section.getLong(template + ".best-ms", -1);
            if (best >= 0) {
                found.put(template, best);
            }
        }
        return found.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    /** Records a finished run; returns true if it set a new personal best. */
    public boolean record(UUID player, String template, long millis, boolean pbEligible) {
        YamlConfiguration yml = data(player);
        String base = "templates." + template + ".";
        yml.set(base + "last-ms", millis);
        yml.set(base + "finishes", yml.getInt(base + "finishes") + 1);
        boolean pb = false;
        if (pbEligible) {
            long best = yml.getLong(base + "best-ms", -1);
            if (best < 0 || millis < best) {
                yml.set(base + "best-ms", millis);
                plugin.leaderboards().submit(player, names.get(player), template, millis);
                pb = true;
            }
        }
        saveAsync(player, yml);
        return pb;
    }

    // ------------------------------------------------------------- wiping

    /** Wipes one arena's times for a player. Returns false if there were none. */
    public boolean resetTemplate(UUID player, String template) {
        YamlConfiguration yml = data(player);
        if (!yml.isConfigurationSection("templates." + template)) {
            unloadIfOffline(player);
            return false;
        }
        yml.set("templates." + template, null);
        plugin.leaderboards().remove(player, template);
        saveAsync(player, yml);
        unloadIfOffline(player);
        return true;
    }

    /** Wipes every arena's times for a player. Returns the arena count wiped. */
    public int resetAll(UUID player) {
        YamlConfiguration yml = data(player);
        ConfigurationSection section = yml.getConfigurationSection("templates");
        int count = section == null ? 0 : section.getKeys(false).size();
        if (count > 0) {
            yml.set("templates", null);
            plugin.leaderboards().removeAll(player);
            saveAsync(player, yml);
        }
        unloadIfOffline(player);
        return count;
    }

    /**
     * Erases every recorded time for one arena, across cached players and
     * every file on disk. Used when an arena is deleted — without it the
     * startup scan would resurrect the leaderboard from playerdata.
     *
     * @param whenDone called on the main thread with the number of players wiped
     */
    public void purgeTemplate(String template, IntConsumer whenDone) {
        String path = "templates." + template;
        Set<UUID> handled = new HashSet<>(cache.keySet());
        int cached = purgeCached(path);

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0 || !plugin.isEnabled()) {
            whenDone.accept(cached);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int onDisk = 0;
            for (File file : files) {
                UUID id = uuidOf(file);
                if (id == null || handled.contains(id)) {
                    continue; // owned by the cache; already dealt with above
                }
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                if (!yml.isConfigurationSection(path)) {
                    continue;
                }
                yml.set(path, null);
                migrate(yml);
                try {
                    yml.save(file);
                    onDisk++;
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not purge '" + template
                            + "' from " + file.getName() + ": " + e.getMessage());
                }
            }
            int total = cached + onDisk;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Anyone who logged in mid-purge loaded their file before it
                // was rewritten — clear them too, or a later save brings the
                // times back.
                whenDone.accept(total + purgeCached(path));
            });
        });
    }

    private int purgeCached(String path) {
        int purged = 0;
        for (Map.Entry<UUID, YamlConfiguration> entry : cache.entrySet()) {
            if (!entry.getValue().isConfigurationSection(path)) {
                continue;
            }
            entry.getValue().set(path, null);
            saveAsync(entry.getKey(), entry.getValue());
            purged++;
        }
        return purged;
    }

    private static UUID uuidOf(File file) {
        try {
            return UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------- preferences

    public boolean scoreboardEnabled(UUID player) {
        return data(player).getBoolean("prefs.scoreboard", true);
    }

    public void setScoreboardEnabled(UUID player, boolean enabled) {
        YamlConfiguration yml = data(player);
        yml.set("prefs.scoreboard", enabled);
        saveAsync(player, yml);
    }

    // -------------------------------------------------------- name index

    /**
     * Called on join: keeps the index current through name changes. Nothing
     * is written to disk here — a player who never practices should not cost
     * a file. The name is stamped into the YAML the first time there is
     * something worth saving (see {@link #ensureName}).
     */
    public void touch(Player player) {
        index(player.getUniqueId(), player.getName());
    }

    /** Keeps the persisted name in step with the index before every write. */
    private void ensureName(UUID player, YamlConfiguration yml) {
        String name = names.get(player);
        if (name != null && !name.equals(yml.getString("name"))) {
            yml.set("name", name);
        }
    }

    private void index(UUID id, String name) {
        String previous = names.put(id, name);
        if (previous != null) {
            byName.remove(previous.toLowerCase(Locale.ROOT), id);
        }
        if (name != null) {
            byName.put(name.toLowerCase(Locale.ROOT), id);
        }
    }

    public String nameOf(UUID player) {
        return names.get(player);
    }

    /** Resolves a name the plugin has seen before — online status irrelevant. */
    public Optional<UUID> uuidOf(String name) {
        if (name == null) {
            return Optional.empty();
        }
        UUID known = byName.get(name.toLowerCase(Locale.ROOT));
        if (known != null) {
            return Optional.of(known);
        }
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? Optional.of(online.getUniqueId()) : Optional.empty();
    }

    /** Every player name in the plugin's memory, for tab completion. */
    public List<String> knownNames() {
        List<String> all = new ArrayList<>(names.values());
        all.sort(String.CASE_INSENSITIVE_ORDER);
        return all;
    }

    public int knownPlayerCount() {
        return names.size();
    }

    // ------------------------------------------------------------- scanning

    /**
     * Reads every playerdata file once on enable to build the name index and
     * the leaderboards. Runs off-thread; the result is applied on the main
     * thread and merged with anything recorded in the meantime.
     */
    public void scanAsync() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        long started = System.nanoTime();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, String> scannedNames = new HashMap<>();
            Map<String, List<LeaderboardService.Entry>> boards = new HashMap<>();
            for (File file : files) {
                String base = file.getName().substring(0, file.getName().length() - 4);
                UUID id;
                try {
                    id = UUID.fromString(base);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                migrate(yml);
                String name = yml.getString("name");
                if (name != null) {
                    scannedNames.put(id, name);
                }
                ConfigurationSection templates = yml.getConfigurationSection("templates");
                if (templates == null) {
                    continue;
                }
                for (String template : templates.getKeys(false)) {
                    long best = templates.getLong(template + ".best-ms", -1);
                    if (best >= 0) {
                        boards.computeIfAbsent(template, k -> new ArrayList<>())
                                .add(new LeaderboardService.Entry(id, name, best));
                    }
                }
            }
            boards.values().forEach(list -> list.sort(Comparator.comparingLong(LeaderboardService.Entry::millis)));
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyScan(scannedNames, boards, started));
        });
    }

    private void applyScan(Map<UUID, String> scannedNames,
                           Map<String, List<LeaderboardService.Entry>> boards,
                           long started) {
        scannedNames.forEach((id, name) -> {
            if (!names.containsKey(id)) {
                index(id, name);
            }
        });
        plugin.leaderboards().load(boards);
        // Anything recorded while the scan was in flight belongs to a cached
        // (online) player — re-submit so the snapshot can't lose it.
        for (Map.Entry<UUID, YamlConfiguration> entry : cache.entrySet()) {
            ConfigurationSection templates = entry.getValue().getConfigurationSection("templates");
            if (templates == null) {
                continue;
            }
            for (String template : templates.getKeys(false)) {
                long best = templates.getLong(template + ".best-ms", -1);
                if (best >= 0) {
                    plugin.leaderboards().submit(entry.getKey(), names.get(entry.getKey()), template, best);
                }
            }
        }
        plugin.getLogger().info("Indexed " + names.size() + " player(s) and "
                + boards.size() + " leaderboard(s) in "
                + ((System.nanoTime() - started) / 1_000_000L) + "ms");
    }

    // -------------------------------------------------------------- storage

    public void unload(UUID player) {
        cache.remove(player);
    }

    /** Drops an offline player's data after a one-off admin read. */
    public void unloadIfOffline(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online == null || !online.isOnline()) {
            cache.remove(player);
        }
    }

    public void flushSync() {
        for (Map.Entry<UUID, YamlConfiguration> entry : cache.entrySet()) {
            try {
                entry.getValue().save(file(entry.getKey()));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save stats for " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private YamlConfiguration data(UUID player) {
        return cache.computeIfAbsent(player, id -> {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file(id));
            migrate(yml);
            String name = yml.getString("name");
            if (name != null && !names.containsKey(id)) {
                index(id, name);
            }
            return yml;
        });
    }

    /**
     * Brings a playerdata file up to {@link Versions#PLAYERDATA} in memory.
     * Pure YAML work with no Bukkit calls, so the startup scan can run it
     * off-thread too. The stamp is written out by the next save rather than
     * eagerly, so reading someone's stats never rewrites their file.
     */
    static void migrate(YamlConfiguration yml) {
        int from = yml.getInt(Versions.DATA_KEY, 0);
        if (from >= Versions.PLAYERDATA) {
            return;
        }
        // v0 → v1 is the first versioned layout; nothing moved. Later steps:
        //   if (from < 2) { … }
        yml.set(Versions.DATA_KEY, Versions.PLAYERDATA);
    }

    private void saveAsync(UUID player, YamlConfiguration yml) {
        yml.set(Versions.DATA_KEY, Versions.PLAYERDATA);
        ensureName(player, yml);
        String rendered = yml.saveToString(); // snapshot on main thread; YamlConfiguration is not thread-safe
        File target = file(player);
        if (!plugin.isEnabled()) {
            writeQuietly(target, rendered, player);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeQuietly(target, rendered, player));
    }

    private void writeQuietly(File target, String contents, UUID player) {
        try {
            java.nio.file.Files.writeString(target.toPath(), contents);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stats for " + player + ": " + e.getMessage());
        }
    }

    private File file(UUID player) {
        return new File(dir, player + ".yml");
    }
}
