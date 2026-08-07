package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shared plumbing for the file migrators. */
public final class Backups {

    private Backups() {
    }

    /**
     * Copies a file into {@code backups/} before it is rewritten. Never
     * throws: a failed backup is worth a warning, not an aborted startup.
     *
     * @return the backup path, or null if it could not be taken
     */
    public static Path copy(PracticeCorePlugin plugin, File source, String label, int fromVersion) {
        if (!source.isFile()) {
            return null;
        }
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("backups");
            Files.createDirectories(dir);
            Path target = dir.resolve(label + "-v" + fromVersion + "-" + System.currentTimeMillis() + ".yml");
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up " + source.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /** The pristine copy of a resource as shipped inside the jar. */
    public static YamlConfiguration jarDefaults(PracticeCorePlugin plugin, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + resource + ": " + e.getMessage());
            return null;
        }
    }
}
