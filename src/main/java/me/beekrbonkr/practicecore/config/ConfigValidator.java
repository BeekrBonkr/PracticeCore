package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PCConfig;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The eager value sweep behind config validation at server start and on
 * {@code /practice reload}: every admin-editable file is checked for values
 * that parse as YAML but will not resolve in game — unknown materials and
 * sounds, misspelled mode and tier names, slots outside a menu, MiniMessage
 * that would render as raw text, numbers a silent clamp would rewrite.
 *
 * Syntax is guarded elsewhere ({@code probe()} refuses a reload, {@code
 * load()} falls back to the bundled copy at start), and every finding here is
 * one the runtime survives by falling back — so validation reports, it never
 * refuses. The point is that a typo becomes a console line at boot and a
 * chat line on reload instead of a surprise discovered mid-fight.
 *
 * Only values the admin actually wrote are checked; a key served by the
 * bundled defaults can never be reported, so a clean install validates clean.
 */
public final class ConfigValidator {

    private final PracticeCorePlugin plugin;

    public ConfigValidator(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Every value problem worth an admin's attention, empty when clean. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        validateConfigYml(problems);
        problems.addAll(plugin.messages().validate());
        problems.addAll(plugin.guis().validate());
        problems.addAll(plugin.sounds().validate());
        problems.addAll(plugin.botTuning().validate());
        return problems;
    }

    // ------------------------------------------------------------ config.yml

    private void validateConfigYml(List<String> problems) {
        FileConfiguration cfg = plugin.getConfig();

        if (set(cfg, "world.name") && cfg.getString("world.name", "").isBlank()) {
            problems.add("config.yml: world.name is blank — 'practice_world' is used.");
        }
        constant(problems, cfg, "world.difficulty", Difficulty.class);
        constant(problems, cfg, "timer.start-mode", PCConfig.TimerStartMode.class);
        constant(problems, cfg, "arenas.access-mode", PCConfig.AccessMode.class);
        constant(problems, cfg, "rush.competitive-defense",
                me.beekrbonkr.practicecore.rush.RushSelection.DefensePreset.class);
        constant(problems, cfg, "bedbreak.orientation", Orientation.class);
        profession(problems, cfg);

        material(problems, cfg, "menu-item.material", false);
        material(problems, cfg, "mlg.platform-material", true);
        material(problems, cfg, "mlg.pad-material", true);
        material(problems, cfg, "rush.rescue-platform.material", true);
        material(problems, cfg, "bedbreak.bed-material", true);
        for (String tool : new String[]{"teleport", "menu", "leave"}) {
            material(problems, cfg, "spectate.items." + tool + ".material", false);
            range(problems, cfg, "spectate.items." + tool + ".slot", 0, 8);
        }

        range(problems, cfg, "menu-item.slot", 0, 8);
        range(problems, cfg, "leaderboard.size", 1, 45);
        atLeast(problems, cfg, "grid.spacing", 16);
        atLeast(problems, cfg, "scoreboard.update-ticks", 1);
        atLeast(problems, cfg, "session.validate-inventory-ticks", 1);
        atLeast(problems, cfg, "speedometer.update-ticks", 1);
        atLeast(problems, cfg, "spectate.update-ticks", 1);
        atLeast(problems, cfg, "mlg.min-drop", 2);

        // Cross-checks: each key can be fine on its own and still not fit
        // the other. These are the ones a per-key clamp cannot catch.
        int spacing = cfg.getInt("grid.spacing", 1000);
        int schematic = cfg.getInt("grid.max-schematic-size", 800);
        if (schematic >= spacing) {
            problems.add("config.yml: grid.max-schematic-size (" + schematic
                    + ") must stay under grid.spacing (" + spacing
                    + ") or neighboring arenas can overlap.");
        }
        if (cfg.getInt("mlg.max-drop", 100) < cfg.getInt("mlg.min-drop", 20)) {
            problems.add("config.yml: mlg.max-drop is below mlg.min-drop —"
                    + " the min is used for both.");
        }
    }

    /** The bed-break wall directions; config.yml's only free-text mode name. */
    private enum Orientation { VERTICAL, HORIZONTAL }

    /** Mirrors RushService's registry lookup, so the two can never disagree. */
    private void profession(List<String> problems, FileConfiguration cfg) {
        if (!set(cfg, "rush.dealer-profession")) {
            return;
        }
        String name = cfg.getString("rush.dealer-profession", "");
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        if (name.isBlank() || key == null || Registry.VILLAGER_PROFESSION.get(key) == null) {
            problems.add("config.yml: rush.dealer-profession '" + name
                    + "' is not a villager profession this server knows —"
                    + " dealers spawn unemployed.");
        }
    }

    // -------------------------------------------------------------- helpers

    /** Explicitly written by the admin — defaults never get reported. */
    private static boolean set(FileConfiguration cfg, String path) {
        return cfg.isSet(path);
    }

    private static <E extends Enum<E>> void constant(List<String> problems,
                                                     FileConfiguration cfg, String path,
                                                     Class<E> type) {
        if (!set(cfg, path)) {
            return;
        }
        String name = cfg.getString(path, "");
        if (name.isBlank()) {
            return;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name.trim())) {
                return;
            }
        }
        problems.add("config.yml: " + path + " '" + name + "' is not one of "
                + java.util.Arrays.toString(type.getEnumConstants())
                + " — the default is used.");
    }

    private static void material(List<String> problems, FileConfiguration cfg,
                                 String path, boolean blockOnly) {
        if (!set(cfg, path)) {
            return;
        }
        String name = cfg.getString(path, "");
        if (name.isBlank()) {
            return;
        }
        Material parsed = Material.matchMaterial(name);
        if (parsed == null || (blockOnly ? !parsed.isBlock() : !parsed.isItem())) {
            problems.add("config.yml: '" + name + "' at " + path + " is not "
                    + (blockOnly ? "a block" : "an item")
                    + " this server knows — the default is used.");
        }
    }

    private static void range(List<String> problems, FileConfiguration cfg,
                              String path, int min, int max) {
        if (!set(cfg, path)) {
            return;
        }
        int value = cfg.getInt(path, min);
        if (value < min || value > max) {
            problems.add("config.yml: " + path + " is " + value
                    + " but must be " + min + "-" + max + " — it is clamped.");
        }
    }

    private static void atLeast(List<String> problems, FileConfiguration cfg,
                                String path, int min) {
        if (set(cfg, path) && cfg.getInt(path, min) < min) {
            problems.add("config.yml: " + path + " is " + cfg.getInt(path, min)
                    + " but must be at least " + min + " — " + min + " is used.");
        }
    }
}
