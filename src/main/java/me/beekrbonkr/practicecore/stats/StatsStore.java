package me.beekrbonkr.practicecore.stats;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player times, one YAML per UUID. Cached in memory; file I/O happens
 * off-thread (the YAML string is rendered on the main thread first).
 */
public final class StatsStore {

    private final PracticeCorePlugin plugin;
    private final File dir;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

    public StatsStore(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create playerdata directory");
        }
    }

    public long bestMs(UUID player, String template) {
        return data(player).getLong("templates." + template + ".best-ms", -1);
    }

    public long lastMs(UUID player, String template) {
        return data(player).getLong("templates." + template + ".last-ms", -1);
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
                pb = true;
            }
        }
        saveAsync(player, yml);
        return pb;
    }

    public void unload(UUID player) {
        cache.remove(player);
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
        return cache.computeIfAbsent(player, id -> YamlConfiguration.loadConfiguration(file(id)));
    }

    private void saveAsync(UUID player, YamlConfiguration yml) {
        String rendered = yml.saveToString(); // snapshot on main thread; YamlConfiguration is not thread-safe
        File target = file(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.writeString(target.toPath(), rendered);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save stats for " + player + ": " + e.getMessage());
            }
        });
    }

    private File file(UUID player) {
        return new File(dir, player + ".yml");
    }
}
