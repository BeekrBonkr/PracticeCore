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

    private final PracticeCorePlugin plugin;
    private final ConfigFile file;

    public GuiConfig(PracticeCorePlugin plugin) {
        this.plugin = plugin;
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
        if (from < 2) {
            // v2 grows the rush menu to five rows so the defender-bot buttons
            // fit. Only values still at their old shipped defaults move — an
            // admin's own layout stands (and may need a manual look if the
            // new buttons land outside their rows).
            if (cfg.getInt("rush.rows", 4) == 4) {
                cfg.set("rush.rows", 5);
            }
            if (cfg.getInt("rush.back.slot", 27) == 27) {
                cfg.set("rush.back.slot", 36);
            }
            if (cfg.getInt("rush.close.slot", 35) == 35) {
                cfg.set("rush.close.slot", 44);
            }
        }
        if (from < 3) {
            // v3 gives the rush menu a row per decision — base, match
            // modifiers, defenders, go — and hands the defense choice to its
            // own gallery. Chains off v2 above, so a file coming all the way
            // from v1 lands here with v2's slots already in place. Only slots
            // still sitting where the last version put them are moved; a
            // layout the admin arranged themselves is theirs and stands.
            relocate(cfg, "rush.rows", 5, 6);
            relocate(cfg, "rush.back.slot", 36, 45);
            relocate(cfg, "rush.close.slot", 44, 53);
            relocate(cfg, "rush.buttons.team.slot", 11, 13);
            relocate(cfg, "rush.buttons.blocks.slot", 19, 20);
            relocate(cfg, "rush.buttons.currency.slot", 20, 21);
            relocate(cfg, "rush.buttons.pickaxe.slot", 21, 22);
            relocate(cfg, "rush.buttons.defense.slot", 22, 23);
            relocate(cfg, "rush.buttons.generators.slot", 23, 24);
            relocate(cfg, "rush.buttons.bots.slot", 28, 29);
            relocate(cfg, "rush.buttons.bot-difficulty.slot", 29, 30);
            relocate(cfg, "rush.buttons.bot-armor.slot", 30, 31);
            relocate(cfg, "rush.buttons.bot-sword.slot", 31, 32);
            relocate(cfg, "rush.buttons.start.slot", 13, 39);
            relocate(cfg, "rush.buttons.competitive.slot", 15, 41);
            // The gallery picks the icon per preset now, so a leftover fixed
            // material would override every one of them.
            if ("END_STONE".equalsIgnoreCase(cfg.getString("rush.buttons.defense.material", ""))) {
                cfg.set("rush.buttons.defense.material", null);
            }
        }
    }

    /** Moves a slot only while it still sits where the previous version put it. */
    private static void relocate(org.bukkit.configuration.file.FileConfiguration cfg,
                                 String path, int old, int now) {
        if (cfg.isSet(path) && cfg.getInt(path, old) == old) {
            cfg.set(path, now);
        }
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
        if (category.equals(me.beekrbonkr.practicecore.mode.BedDefenseMode.ID)
                && plugin.messages() != null) {
            // The synthetic bed defense category names itself from messages.yml.
            String named = plugin.messages().raw("beddefense.category-name");
            if (!named.isBlank()) {
                return named;
            }
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

    // ------------------------------------------------------------ validation

    /**
     * The whole layout swept eagerly: every material and icon must resolve,
     * every slot fit a menu, every row count fit a chest. Play time falls
     * back per lookup; this reports the typos all at once instead.
     */
    public List<String> validate() {
        List<String> problems = new java.util.ArrayList<>();
        var raw = file.raw();
        for (String key : raw.getKeys(true)) {
            if (raw.isConfigurationSection(key) || key.equals(Versions.KEY)) {
                continue;
            }
            String leaf = key.substring(key.lastIndexOf('.') + 1);
            switch (leaf) {
                case "material", "empty-material", "icon" -> {
                    String name = raw.getString(key, "");
                    if (!name.isBlank() && (Material.matchMaterial(name) == null
                            || !Material.matchMaterial(name).isItem())) {
                        problems.add(RESOURCE + ": '" + name + "' at " + key
                                + " is not an item this server knows.");
                    }
                }
                case "slot" -> {
                    int slot = raw.getInt(key, 0);
                    if (slot < -1 || slot > 53) { // -1 hides the button on purpose
                        problems.add(RESOURCE + ": slot " + slot + " at " + key
                                + " is outside a menu (0-53, or -1 to hide).");
                    }
                }
                case "rows" -> {
                    int rows = raw.getInt(key, 1);
                    if (rows < 1 || rows > 6) {
                        problems.add(RESOURCE + ": " + rows + " rows at " + key
                                + " does not fit a chest menu (1-6).");
                    }
                }
                default -> {
                }
            }
        }
        return problems;
    }
}
