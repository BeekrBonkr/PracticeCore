package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Menu structure, loaded from guis.yml: which slot each button occupies, its
 * icon material, row counts, filler and nav items. All display <em>text</em>
 * stays in messages.yml — this file is layout only.
 *
 * Like every admin-editable file, the jar's copy is the safety net: the user's
 * file is loaded with the bundled defaults underneath, so a missing key can
 * never leave a menu without a slot or material.
 */
public final class GuiConfig {

    private static final String RESOURCE = "guis.yml";

    private final ConfigFile file;

    public GuiConfig(PracticeCorePlugin plugin) {
        this.file = new ConfigFile(plugin, "guis", RESOURCE, Versions.GUIS, GuiConfig::steps,
                java.util.Set.of("categories.entries"));
    }

    /** @return null when the file parses, or the problem to report otherwise */
    public String probe() {
        return file.probe();
    }

    /** @return notes worth showing an admin (migrations, parse failures) */
    public List<String> load() {
        return file.load();
    }

    /** Reshapes an older guis.yml. See {@link Versions#GUIS}. */
    private static void steps(org.bukkit.configuration.file.FileConfiguration cfg, int from) {
        // v0 → v1 is the first versioned layout; nothing moved.
    }

    // ------------------------------------------------------------- lookups

    public int slot(String path, int def) {
        return file.integer(path + ".slot", def);
    }

    public boolean buttonEnabled(String path) {
        return file.bool(path + ".enabled", true) && file.integer(path + ".slot", 0) >= 0;
    }

    public int rows(String path, int def) {
        return file.integer(path + ".rows", def, 1, 6);
    }

    public Material material(String path, Material def) {
        return file.material(path, def);
    }

    /** A button's icon material ({@code <path>.material}). */
    public Material buttonMaterial(String path, Material def) {
        return material(path + ".material", def);
    }

    // ----------------------------------------------------------- categories

    /** Admin-configured display name for a category, or a tidied fallback. */
    public String categoryName(String category) {
        String configured = file.string("categories.entries." + category + ".name", "");
        if (!configured.isBlank()) {
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
        return file.bool("categories.enabled", true);
    }
}
