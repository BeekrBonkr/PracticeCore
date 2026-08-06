package me.beekrbonkr.practicecore.world;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Owns the ephemeral practice world. The world folder is deleted and
 * recreated on every plugin enable, so no stale arena blocks can ever
 * survive a crash.
 */
public final class PracticeWorldService {

    private final PracticeCorePlugin plugin;
    private World world;

    public PracticeWorldService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void recreate() {
        String name = plugin.pcConfig().worldName();
        if (Bukkit.getWorld(name) != null) {
            // Can happen after /reload with players inside; onDisable evicts
            // them, but the world object may still be loaded.
            Bukkit.unloadWorld(name, false);
        }
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

    public World world() {
        return world;
    }

    public boolean isPracticeWorld(World other) {
        return world != null && world.equals(other);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete practice world folder", e);
        }
    }
}
