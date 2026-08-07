package me.beekrbonkr.practicecore.snapshot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.Versions;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Disk-backed store of pre-practice snapshots. Written synchronously on
 * session start (small file; durability is the whole point — it must exist
 * before anything about the player is mutated).
 */
public final class SnapshotStore {

    private final PracticeCorePlugin plugin;
    private final File dir;

    public SnapshotStore(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "snapshots");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create snapshots directory");
        }
    }

    public void save(UUID player, PlayerSnapshot snapshot) {
        YamlConfiguration yml = new YamlConfiguration();
        snapshot.serialize(yml);
        try {
            yml.save(file(player));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to persist snapshot for " + player + ": " + e.getMessage());
        }
    }

    public boolean has(UUID player) {
        return file(player).exists();
    }

    public Optional<PlayerSnapshot> load(UUID player) {
        File file = file(player);
        if (!file.exists()) {
            return Optional.empty();
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        int version = yml.getInt(Versions.DATA_KEY, 0);
        if (version > Versions.SNAPSHOT) {
            // Written by a newer build. Restoring is still far better than
            // stranding the player, but the mismatch belongs in the log.
            plugin.getLogger().warning("Snapshot for " + player + " is v" + version
                    + ", newer than this build understands (v" + Versions.SNAPSHOT
                    + ") — restoring it anyway; some fields may be ignored.");
        }
        return Optional.of(PlayerSnapshot.deserialize(yml));
    }

    public void delete(UUID player) {
        File file = file(player);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete snapshot file for " + player);
        }
    }

    private File file(UUID player) {
        return new File(dir, player + ".yml");
    }
}
