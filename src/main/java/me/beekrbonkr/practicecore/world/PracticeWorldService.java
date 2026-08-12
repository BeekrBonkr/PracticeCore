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
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false); // phantoms spawn even in void worlds
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setTime(6000L);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setAutoSave(false);
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
