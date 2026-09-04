package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps one admin-editable YAML file current across plugin upgrades.
 *
 * Two independent jobs, both non-destructive:
 * <ol>
 *   <li><b>Version steps</b> — renames and reshapes keys whose meaning changed
 *       between version bumps, after taking a backup.</li>
 *   <li><b>Top-up</b> — merges in any key present in the jar's copy but
 *       missing from the admin's, so a newly added setting or message never
 *       silently falls back to a hard-coded default. Existing values are
 *       untouched, and a new key arrives with the comments that explain it.</li>
 * </ol>
 *
 * <p><b>Curated sections</b> are the exception to the top-up. A file that
 * defines a <em>collection</em> — the PvP kits, the difficulty presets, the
 * world's gamerules — hands that list to the admin outright: deleting an entry
 * has to mean it is gone, not that it reappears on the next start. Those roots
 * are named by the caller and the top-up never reaches inside them (though it
 * will still write the whole section out if the file has lost it entirely).
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
    private final Set<String> curated;

    public YamlMigrator(PracticeCorePlugin plugin, String label, String resource,
                        File target, int current, Steps steps) {
        this(plugin, label, resource, target, current, steps, Set.of());
    }

    /**
     * @param curated section paths whose contents are the admin's to curate —
     *                entries deleted there stay deleted
     */
    public YamlMigrator(PracticeCorePlugin plugin, String label, String resource,
                        File target, int current, Steps steps, Set<String> curated) {
        this.plugin = plugin;
        this.label = label;
        this.resource = resource;
        this.target = target;
        this.current = current;
        this.steps = steps;
        this.curated = curated;
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
            // The steps may have satisfied (or newly created) some of these.
            missing = missingKeys(cfg, defaults);
        }
        if (!missing.isEmpty()) {
            topUp(cfg, defaults, missing);
            notes.add("Added " + missing.size() + " missing " + resource + " key(s): "
                    + summarize(missing));
        }

        cfg.set(Versions.KEY, current);
        try {
            cfg.save(target);
        } catch (IOException e) {
            notes.add("Could not write " + resource + ": " + e.getMessage()
                    + " — the upgrade will be retried next start.");
        }
        return notes;
    }

    /**
     * Writes the missing keys in, one at a time rather than through
     * {@code copyDefaults}: that would also resurrect every entry the admin
     * deliberately deleted from a curated section, and it carries no comments.
     * Doing it by hand lets a new setting arrive with the paragraph that
     * explains it, which is the whole reason those comments exist.
     */
    private void topUp(FileConfiguration cfg, YamlConfiguration defaults, List<String> missing) {
        Set<String> newSections = new LinkedHashSet<>();
        for (String key : missing) {
            for (String parent : parents(key)) {
                if (!cfg.isConfigurationSection(parent)) {
                    newSections.add(parent);
                }
            }
        }
        for (String key : missing) {
            cfg.set(key, defaults.get(key));
        }
        // Comments only after every value exists — setting a comment on a path
        // that is not there yet has nowhere to attach.
        for (String section : newSections) {
            copyComments(cfg, defaults, section);
        }
        for (String key : missing) {
            copyComments(cfg, defaults, key);
        }
    }

    private static void copyComments(FileConfiguration cfg, YamlConfiguration defaults, String path) {
        List<String> comments = defaults.getComments(path);
        if (!comments.isEmpty()) {
            cfg.setComments(path, comments);
        }
        List<String> inline = defaults.getInlineComments(path);
        if (!inline.isEmpty()) {
            cfg.setInlineComments(path, inline);
        }
    }

    /** Every ancestor path of a key, outermost first. */
    private static List<String> parents(String key) {
        List<String> parents = new ArrayList<>();
        int dot = key.indexOf('.');
        while (dot >= 0) {
            parents.add(key.substring(0, dot));
            dot = key.indexOf('.', dot + 1);
        }
        return parents;
    }

    /**
     * Drops every leaf still equal to what an older build shipped, so the
     * top-up rewrites it with the current default (comments included). A
     * value the admin edited never matches and therefore stands. Keys the
     * old build shipped that the new one no longer has are dropped too, when
     * untouched. Used when a release rewords a whole file — the alternative
     * is an admin's file silently keeping every old string forever.
     *
     * @param old the previous version's bundled copy, from {@code migrations/}
     */
    public static void resetUntouched(FileConfiguration cfg, YamlConfiguration old) {
        if (old == null) {
            return;
        }
        for (String key : old.getKeys(true)) {
            if (old.isConfigurationSection(key) || key.equals(Versions.KEY)) {
                continue;
            }
            if (!cfg.contains(key, true)) {
                continue;
            }
            Object mine = cfg.get(key);
            Object theirs = old.get(key);
            boolean same = mine == null ? theirs == null
                    : mine instanceof List<?> a && theirs instanceof List<?> b ? a.equals(b)
                    : String.valueOf(mine).equals(String.valueOf(theirs));
            if (same) {
                cfg.set(key, null);
            }
        }
        // A section emptied out by the resets would otherwise linger as "{}".
        for (String key : new ArrayList<>(cfg.getKeys(true))) {
            if (cfg.isConfigurationSection(key)
                    && cfg.getConfigurationSection(key).getKeys(false).isEmpty()) {
                cfg.set(key, null);
            }
        }
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

    /**
     * Leaf keys the jar's copy defines that the admin's file lacks — skipping
     * anything inside a curated section the admin's file already has, since
     * what is in those is their call and not ours.
     */
    private List<String> missingKeys(FileConfiguration cfg, YamlConfiguration defaults) {
        return defaults.getKeys(true).stream()
                .filter(key -> !defaults.isConfigurationSection(key))
                .filter(key -> !key.equals(Versions.KEY))
                .filter(key -> !cfg.contains(key, true))
                .filter(key -> !insideExistingCuratedSection(cfg, key))
                .sorted()
                .toList();
    }

    private boolean insideExistingCuratedSection(FileConfiguration cfg, String key) {
        for (String root : curated) {
            if (!key.startsWith(root + ".")) {
                continue;
            }
            // Only hands-off once the admin actually has the section; a file
            // that lost it entirely still gets the shipped one back.
            ConfigurationSection section = cfg.getConfigurationSection(root);
            if (section != null && !section.getKeys(false).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String summarize(List<String> keys) {
        if (keys.size() <= MAX_LISTED_KEYS) {
            return String.join(", ", keys);
        }
        return String.join(", ", keys.subList(0, MAX_LISTED_KEYS)) + ", … (+"
                + (keys.size() - MAX_LISTED_KEYS) + " more)";
    }
}
