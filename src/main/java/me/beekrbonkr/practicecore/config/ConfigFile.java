package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One admin-editable YAML file, kept current and always complete.
 *
 * Every file the plugin ships is handled the same way, which is what makes a
 * reload safe on a live server:
 * <ol>
 *   <li>the jar's copy is written out the first time, then never overwritten;</li>
 *   <li>{@link YamlMigrator} reshapes older versions and tops up keys the
 *       admin's file is missing;</li>
 *   <li>the jar's copy is laid <em>underneath</em> the admin's as defaults, so
 *       a key deleted by hand still resolves instead of collapsing to whatever
 *       hard-coded fallback the call site happened to pass;</li>
 *   <li>{@link #probe} parses a candidate file on a throwaway object, so a
 *       reload can refuse a syntax error before anything live is replaced.</li>
 * </ol>
 */
public class ConfigFile {

    private final PracticeCorePlugin plugin;
    private final String resource;
    private final String label;
    private final int version;
    private final YamlMigrator.Steps steps;
    private final java.util.Set<String> curated;
    private final File file;

    private YamlConfiguration cfg = new YamlConfiguration();

    public ConfigFile(PracticeCorePlugin plugin, String label, String resource,
                      int version, YamlMigrator.Steps steps) {
        this(plugin, label, resource, version, steps, java.util.Set.of());
    }

    /**
     * @param curated sections whose entries are the admin's to curate — the
     *                top-up never puts back one they deleted
     */
    public ConfigFile(PracticeCorePlugin plugin, String label, String resource,
                      int version, YamlMigrator.Steps steps, java.util.Set<String> curated) {
        this.plugin = plugin;
        this.label = label;
        this.resource = resource;
        this.version = version;
        this.steps = steps;
        this.curated = curated;
        this.file = new File(plugin.getDataFolder(), resource);
    }

    public String resource() {
        return resource;
    }

    public File file() {
        return file;
    }

    /**
     * Parses the file on a throwaway object without touching the live one.
     *
     * @return null when it parses, or the problem to report otherwise
     */
    public String probe() {
        if (!file.isFile()) {
            return null; // load() writes a fresh copy from the jar
        }
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (IOException | InvalidConfigurationException e) {
            return resource + " could not be parsed: " + e.getMessage();
        }
    }

    /** @return notes worth showing an admin (migrations, parse failures) */
    public List<String> load() {
        List<String> notes = new ArrayList<>();
        YamlConfiguration bundled = Backups.jarDefaults(plugin, resource);
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
            notes.addAll(new YamlMigrator(plugin, label, resource, file, version, steps, curated)
                    .run(user));
        } catch (IOException | InvalidConfigurationException e) {
            notes.add(resource + " could not be parsed: " + e.getMessage()
                    + " — falling back to the bundled copy.");
            user = new YamlConfiguration();
        }
        if (bundled != null) {
            user.setDefaults(bundled);
        }
        cfg = user;
        return notes;
    }

    public YamlConfiguration raw() {
        return cfg;
    }

    // -------------------------------------------------------------- lookups

    public boolean contains(String path) {
        return cfg.contains(path);
    }

    public boolean bool(String path, boolean def) {
        return cfg.getBoolean(path, def);
    }

    public int integer(String path, int def) {
        return cfg.getInt(path, def);
    }

    /** An int clamped into a sane range — config typos must not break a task. */
    public int integer(String path, int def, int min, int max) {
        return Math.clamp(cfg.getInt(path, def), min, max);
    }

    public double number(String path, double def) {
        return cfg.getDouble(path, def);
    }

    public double number(String path, double def, double min, double max) {
        return Math.clamp(cfg.getDouble(path, def), min, max);
    }

    public String string(String path, String def) {
        String value = cfg.getString(path, def);
        return value == null ? def : value;
    }

    public List<String> strings(String path) {
        return cfg.getStringList(path);
    }

    public ConfigurationSection section(String path) {
        return cfg.getConfigurationSection(path);
    }

    /** A material, warning once about a name that does not resolve. */
    public Material material(String path, Material def) {
        return material(path, def, false);
    }

    public Material material(String path, Material def, boolean blockOnly) {
        String name = cfg.getString(path);
        if (name == null || name.isBlank()) {
            return def;
        }
        Material parsed = Material.matchMaterial(name);
        if (parsed == null || (blockOnly ? !parsed.isBlock() : !parsed.isItem())) {
            warn(path, name, def == null ? "nothing" : def.name());
            return def;
        }
        return parsed;
    }

    /** A list of materials; unresolvable entries are dropped with a warning. */
    public List<Material> materials(String path, List<Material> def) {
        return materials(path, def, false);
    }

    public List<Material> materials(String path, List<Material> def, boolean blockOnly) {
        if (!cfg.contains(path)) {
            return def;
        }
        List<Material> parsed = new ArrayList<>();
        for (String name : cfg.getStringList(path)) {
            Material material = Material.matchMaterial(name);
            if (material == null || (blockOnly ? !material.isBlock() : !material.isItem())) {
                warn(path, name, "skipped");
                continue;
            }
            parsed.add(material);
        }
        return parsed;
    }

    /** An enum constant by name, falling back (with a warning) when unknown. */
    public <E extends Enum<E>> E constant(String path, Class<E> type, E def) {
        String name = cfg.getString(path);
        if (name == null || name.isBlank()) {
            return def;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(path, name, def == null ? "nothing" : def.name());
            return def;
        }
    }

    /** A list of ints — tick schedules and the like. Empty falls back. */
    public List<Long> ticks(String path, List<Long> def) {
        List<Integer> raw = cfg.getIntegerList(path);
        if (raw.isEmpty()) {
            return def;
        }
        List<Long> parsed = new ArrayList<>(raw.size());
        for (int value : raw) {
            parsed.add((long) Math.max(0, value));
        }
        return parsed;
    }

    /**
     * A section read as a name → double table, e.g. the PvP bot's per-tier
     * values. Missing entries keep the caller's default.
     */
    public Map<String, Double> numberTable(String path, Map<String, Double> def) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) {
            return def;
        }
        Map<String, Double> table = new LinkedHashMap<>(def);
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            table.put(key.toUpperCase(Locale.ROOT), section.getDouble(key));
        }
        return table;
    }

    /** A section read as a name → int table. */
    public Map<String, Integer> integerTable(String path, Map<String, Integer> def) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) {
            return def;
        }
        Map<String, Integer> table = new LinkedHashMap<>(def);
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            table.put(key.toUpperCase(Locale.ROOT), section.getInt(key));
        }
        return table;
    }

    private void warn(String path, String value, String fallback) {
        plugin.getLogger().warning(resource + ": '" + value + "' at " + path
                + " is not something this server knows — using " + fallback + ".");
    }
}
