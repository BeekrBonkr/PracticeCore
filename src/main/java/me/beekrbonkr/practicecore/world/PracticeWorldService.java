package me.beekrbonkr.practicecore.world;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Owns the ephemeral practice world. The world folder is deleted and
 * recreated on every plugin enable, so no stale arena blocks can ever
 * survive a crash. The same routine backs /practice world regen.
 */
public final class PracticeWorldService {

    private final PracticeCorePlugin plugin;
    private World world;

    public PracticeWorldService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Unloads, deletes and rebuilds the practice world.
     *
     * @throws IllegalStateException if the old world will not unload or the
     *         new one cannot be created — callers decide whether that is
     *         fatal (enable) or reportable (regen command)
     */
    public void recreate() {
        String name = plugin.pcConfig().worldName();
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            // Can happen after /reload or on regen with players inside.
            evict(existing);
            if (!Bukkit.unloadWorld(existing, false)) {
                throw new IllegalStateException("Could not unload practice world '" + name
                        + "'. Something else is holding it open — a restart is needed.");
            }
        }
        // From here the old world is gone. Null it now so that if the rebuild
        // fails, nothing hands out arenas in an unloaded world.
        world = null;
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (folder.exists()) {
            deleteRecursively(folder.toPath());
            plugin.getLogger().info("Deleted stale practice world folder '" + name + "'");
        }
        world = new WorldCreator(name)
                .generator(new VoidGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .keepSpawnLoaded(TriState.FALSE)
                .createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create practice world '" + name + "'");
        }
        applyWorldSettings();
        world.setAutoSave(false);
    }

    /**
     * Pushes the configured gamerules, difficulty and time onto the live
     * world. Called when the world is built and again after every reload, so
     * changing any of them is a {@code /practice reload} rather than a world
     * regeneration — let alone a restart.
     */
    public void applyWorldSettings() {
        if (world == null) {
            return;
        }
        applyGameRules(world);
        world.setTime(plugin.pcConfig().worldTime());
        // NORMAL by default, not PEACEFUL: peaceful removes hostile mobs on
        // the spot and zeroes their damage — the PvP bot (a husk) needs both
        // to exist. Natural spawning stays off via doMobSpawning; only
        // plugin-spawned entities (bots, dealers) ever appear.
        world.setDifficulty(difficulty());
    }

    /**
     * Applies the gamerules from config.yml. Names are matched against the
     * server's own registry rather than a fixed list, so a rule added by a
     * later Minecraft release can be set without a plugin update — and one
     * this server does not have is reported instead of silently ignored.
     */
    private void applyGameRules(World world) {
        plugin.pcConfig().worldGameRuleFlags().forEach((name, value) -> {
            GameRule<Boolean> rule = booleanRule(name);
            if (rule == null) {
                warnUnknownRule(name, "a true/false rule");
            } else {
                world.setGameRule(rule, value);
            }
        });
        plugin.pcConfig().worldGameRuleNumbers().forEach((name, value) -> {
            GameRule<Integer> rule = integerRule(name);
            if (rule == null) {
                warnUnknownRule(name, "a numeric rule");
            } else {
                world.setGameRule(rule, value);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Boolean> booleanRule(String name) {
        GameRule<?> rule = GameRule.getByName(name);
        return rule != null && rule.getType() == Boolean.class ? (GameRule<Boolean>) rule : null;
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Integer> integerRule(String name) {
        GameRule<?> rule = GameRule.getByName(name);
        return rule != null && rule.getType() == Integer.class ? (GameRule<Integer>) rule : null;
    }

    private void warnUnknownRule(String name, String expected) {
        plugin.getLogger().warning("config.yml: world.gamerules." + name
                + " is not " + expected + " this server knows — skipped.");
    }

    private Difficulty difficulty() {
        String name = plugin.pcConfig().worldDifficulty();
        try {
            return Difficulty.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("config.yml: world.difficulty '" + name
                    + "' is not a difficulty — using NORMAL.");
            return Difficulty.NORMAL;
        }
    }

    /**
     * Full regeneration on demand: every session is ended and restored, the
     * setup wizard is canceled, the world is rebuilt from nothing and the
     * grid starts again at slot zero.
     *
     * @return how many players were practicing when it started
     */
    public int regenerate() {
        int practicing = plugin.sessions().all().size();
        // Order matters — both of these erase their regions in the world that
        // is about to be unloaded, and restore their players.
        plugin.setup().cancelAll();
        plugin.sessions().endAllSync();
        recreate();
        plugin.allocator().reset();
        plugin.getLogger().info("Practice world '" + plugin.pcConfig().worldName() + "' regenerated");
        return practicing;
    }

    /** Moves anyone still standing in the doomed world somewhere real. */
    private void evict(World doomed) {
        List<Player> inside = List.copyOf(doomed.getPlayers());
        if (inside.isEmpty()) {
            return;
        }
        Location safe = safeSpawnOutside(doomed);
        inside.forEach(player -> player.teleport(safe));
        plugin.getLogger().info("Moved " + inside.size()
                + " player(s) out of the practice world before unloading it");
    }

    private Location safeSpawnOutside(World doomed) {
        String configured = plugin.pcConfig().leaveFallbackWorld();
        if (!configured.isEmpty()) {
            World fallback = Bukkit.getWorld(configured);
            if (fallback != null && !fallback.equals(doomed)) {
                return fallback.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().stream()
                .filter(candidate -> !candidate.equals(doomed))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No world to move practice players into — refusing to unload"))
                .getSpawnLocation();
    }

    public World world() {
        return world;
    }

    public boolean isPracticeWorld(World other) {
        return world != null && world.equals(other);
    }

    private void deleteRecursively(Path dir) {
        java.util.List<Path> failed = new java.util.ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    failed.add(p);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete practice world folder", e);
        }
        if (!failed.isEmpty()) {
            // A surviving region file means createWorld reloads old chunks —
            // stale arena blocks reappearing at reused grid slots. Say so
            // loudly instead of silently breaking the "nothing persists" rule.
            plugin.getLogger().severe("Could not fully delete the practice world folder — "
                    + failed.size() + " file(s) survived (locked by a backup tool or AV?), "
                    + "e.g. " + failed.get(0) + ". Old chunks may reappear; "
                    + "run /practice world regen once the files are released.");
        }
    }
}
