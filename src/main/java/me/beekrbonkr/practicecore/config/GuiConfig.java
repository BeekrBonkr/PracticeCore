package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu structure, loaded from guis.yml: which slot each button occupies, its
 * icon material, row counts, filler and nav items. All display <em>text</em>
 * stays in messages.yml — this file is layout only.
 *
 * Like messages.yml, the jar's copy is the safety net: the user's file is
 * loaded with the bundled defaults underneath, so a missing key can never
 * leave a menu without a slot or material.
 */
public final class GuiConfig {

    private static final String RESOURCE = "guis.yml";

    private final PracticeCorePlugin plugin;
    private final File file;
    private YamlConfiguration cfg = new YamlConfiguration();

    public GuiConfig(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), RESOURCE);
    }

    /** @return notes worth showing an admin (migrations, parse failures) */
    public List<String> load() {
        List<String> notes = new ArrayList<>();
        YamlConfiguration bundled = Backups.jarDefaults(plugin, RESOURCE);
        if (!file.exists()) {
            plugin.saveResource(RESOURCE, false);
        }
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
            notes.addAll(new YamlMigrator(plugin, "guis", RESOURCE, file,
                    Versions.GUIS, GuiConfig::steps).run(user));
        } catch (IOException | InvalidConfigurationException e) {
            notes.add(RESOURCE + " could not be parsed: " + e.getMessage()
                    + " — falling back to the built-in layout.");
            user = new YamlConfiguration();
        }
        if (bundled != null) {
            user.setDefaults(bundled);
        }
        cfg = user;
        return notes;
    }

    /** Reshapes an older guis.yml. See {@link Versions#GUIS}. */
    private static void steps(org.bukkit.configuration.file.FileConfiguration cfg, int from) {
        // v0 → v1 is the first versioned layout; nothing moved.
    }

    // ------------------------------------------------------------- lookups

    public int slot(String path, int def) {
        return cfg.getInt(path + ".slot", def);
    }

    public boolean buttonEnabled(String path) {
        return cfg.getBoolean(path + ".enabled", true) && cfg.getInt(path + ".slot", 0) >= 0;
    }

    public int rows(String path, int def) {
        return Math.clamp(cfg.getInt(path + ".rows", def), 1, 6);
    }

    public Material material(String path, Material def) {
        String name = cfg.getString(path);
        if (name == null || name.isBlank()) {
            return def;
        }
        Material parsed = Material.matchMaterial(name);
        return parsed != null && parsed.isItem() ? parsed : def;
    }

    /** A button's icon material ({@code <path>.material}). */
    public Material buttonMaterial(String path, Material def) {
        return material(path + ".material", def);
    }

    // ----------------------------------------------------------- categories

    /** Admin-configured display name for a category, or a tidied fallback. */
    public String categoryName(String category) {
        String configured = cfg.getString("categories.entries." + category + ".name");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String cleaned = category.replace('_', ' ').replace('-', ' ');
        return cleaned.isEmpty() ? category
                : cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /** Admin-configured icon for a category, or null to derive one. */
    public Material categoryIcon(String category) {
        return material("categories.entries." + category + ".icon", null);
    }

    public boolean categoriesEnabled() {
        return cfg.getBoolean("categories.enabled", true);
    }
}
