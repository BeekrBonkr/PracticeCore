package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps one admin-editable YAML file current across plugin upgrades.
 *
 * Two independent jobs, both non-destructive:
 * <ol>
 *   <li><b>Version steps</b> — renames and reshapes keys whose meaning changed
 *       between version bumps, after taking a backup.</li>
 *   <li><b>Top-up</b> — merges in any key present in the jar's copy but
 *       missing from the admin's, so a newly added setting or message never
 *       silently falls back to a hard-coded default. Existing values and
 *       comments are preserved.</li>
 * </ol>
 */
public final class YamlMigrator {

    /** Reshapes a file that was written at version {@code from}. */
    @FunctionalInterface
    public interface Steps {
        void apply(FileConfiguration cfg, int from);
    }

    private static final int MAX_LISTED_KEYS = 6;

    private final PracticeCorePlugin plugin;
    private final String label;
    private final String resource;
    private final File target;
    private final int current;
    private final Steps steps;

    public YamlMigrator(PracticeCorePlugin plugin, String label, String resource,
                        File target, int current, Steps steps) {
        this.plugin = plugin;
        this.label = label;
        this.resource = resource;
        this.target = target;
        this.current = current;
        this.steps = steps;
    }

    /** @return human-readable notes; empty when the file was already current. */
    public List<String> run(FileConfiguration cfg) {
        List<String> notes = new ArrayList<>();
        int found = cfg.getInt(Versions.KEY, 0);

        if (found > current) {
            notes.add(resource + " is version " + found + ", newer than this build understands (v"
                    + current + "). Left untouched — downgrading is not supported.");
            return notes;
        }

        YamlConfiguration defaults = Backups.jarDefaults(plugin, resource);
        if (defaults == null) {
            notes.add("The bundled " + resource + " is missing from the jar — skipped the version check.");
            return notes;
        }

        List<String> missing = missingKeys(cfg, defaults);
        if (found == current && missing.isEmpty()) {
            return notes;
        }

        if (found < current) {
            Path backup = Backups.copy(plugin, target, label, found);
            steps.apply(cfg, found);
            notes.add("Upgraded " + resource + " v" + found + " → v" + current
                    + (backup != null ? " (backup: backups/" + backup.getFileName() + ")" : ""));
        }
        if (!missing.isEmpty()) {
            notes.add("Added " + missing.size() + " missing " + resource + " key(s): " + summarise(missing));
        }

        cfg.setDefaults(defaults);
        cfg.options().copyDefaults(true);
        cfg.set(Versions.KEY, current);
        try {
            cfg.save(target);
        } catch (IOException e) {
            notes.add("Could not write " + resource + ": " + e.getMessage()
                    + " — the upgrade will be retried next start.");
        }
        return notes;
    }

    /** Moves a value to a new path, leaving the old one removed. */
    public static void move(FileConfiguration cfg, String from, String to) {
        if (!cfg.contains(from, true)) {
            return;
        }
        if (!cfg.contains(to, true)) {
            cfg.set(to, cfg.get(from));
        }
        cfg.set(from, null);
    }

    /** Leaf keys the jar's copy defines that the admin's file lacks. */
    private static List<String> missingKeys(FileConfiguration cfg, YamlConfiguration defaults) {
        return defaults.getKeys(true).stream()
                .filter(key -> !defaults.isConfigurationSection(key))
                .filter(key -> !key.equals(Versions.KEY))
                .filter(key -> !cfg.contains(key, true))
                .sorted()
                .toList();
    }

    private static String summarise(List<String> keys) {
        if (keys.size() <= MAX_LISTED_KEYS) {
            return String.join(", ", keys);
        }
        return String.join(", ", keys.subList(0, MAX_LISTED_KEYS)) + ", … (+"
                + (keys.size() - MAX_LISTED_KEYS) + " more)";
    }
}
