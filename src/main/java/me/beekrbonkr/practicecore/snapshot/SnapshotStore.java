package me.beekrbonkr.practicecore.snapshot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
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
        return Optional.of(PlayerSnapshot.deserialize(YamlConfiguration.loadConfiguration(file)));
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
