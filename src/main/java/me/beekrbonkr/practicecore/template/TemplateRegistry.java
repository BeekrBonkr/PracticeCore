package me.beekrbonkr.practicecore.template;

import me.beekrbonkr.practicecore.PCConfig;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.Backups;
import me.beekrbonkr.practicecore.config.Versions;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class TemplateRegistry {

    private final PracticeCorePlugin plugin;
    private final File templatesDir;
    private final Map<String, ArenaTemplate> templates = new LinkedHashMap<>();

    public TemplateRegistry(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "templates");
    }

    /**
     * Rebuilds the registry from disk, upgrading any outdated arena.yml on the
     * way. Templates are collected into a local map and swapped in only once
     * the whole pass succeeds, so a single unreadable folder can never leave
     * the registry half-populated — which matters most on /practice reload.
     *
     * @return notes worth showing an admin (upgrades, skips, failures)
     */
    public List<String> loadAll() {
        List<String> notes = new ArrayList<>();
        if (!templatesDir.exists() && !templatesDir.mkdirs()) {
            notes.add("Could not create the templates directory.");
            return log(notes);
        }
        new BundledTemplate(plugin).installIfAbsent(templatesDir.toPath());
        new GeneratedArenas(plugin).installMissing(templatesDir.toPath());
        File[] dirs = templatesDir.listFiles(File::isDirectory);
        Map<String, ArenaTemplate> loaded = new LinkedHashMap<>();
        if (dirs != null) {
            // Alphabetical, so menu and list ordering is stable across restarts.
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File dir : dirs) {
                ArenaTemplate template = read(dir, notes);
                if (template != null) {
                    // Lowercased like every lookup — a hand-made uppercase
                    // folder must not load into an unreachable registry key.
                    loaded.put(template.name().toLowerCase(Locale.ROOT), template);
                }
            }
        }
        templates.clear();
        templates.putAll(loaded);
        String preferred = plugin.pcConfig().defaultArenaName();
        if (!preferred.isEmpty() && defaultArena() == null) {
            notes.add("default-arena.name is '" + preferred
                    + "' but no finished arena by that name exists — falling back per player.");
        }
        plugin.getLogger().info("Loaded " + templates.size() + " arena template(s), "
                + completeTemplates().size() + " complete");
        return log(notes);
    }

    private ArenaTemplate read(File dir, List<String> notes) {
        ArenaTemplate template;
        try {
            template = ArenaTemplate.load(dir);
        } catch (RuntimeException e) {
            notes.add("Arena '" + dir.getName() + "' could not be read (" + e + ") — skipped.");
            return null;
        }
        if (!template.schematicFile().exists()) {
            notes.add("Arena '" + template.name() + "' has no arena.schem — skipped.");
            return null;
        }
        if (plugin.modes().get(template.mode()).isEmpty()) {
            notes.add("Arena '" + template.name() + "' uses unknown mode '"
                    + template.mode() + "' — skipped.");
            return null;
        }
        if (template.fromFuture()) {
            notes.add("Arena '" + template.name() + "' is arena.yml v" + template.loadedVersion()
                    + ", newer than this build understands (v" + Versions.ARENA + ") — skipped.");
            return null;
        }
        if (template.needsUpgrade()) {
            upgrade(template, notes);
        }
        return template;
    }

    private void upgrade(ArenaTemplate template, List<String> notes) {
        int from = template.loadedVersion();
        Path backup = Backups.copy(plugin, new File(template.dir(), "arena.yml"),
                "arena-" + template.name(), from);
        try {
            template.save();
            notes.add("Upgraded arena '" + template.name() + "' arena.yml v" + from
                    + " → v" + Versions.ARENA
                    + (backup != null ? " (backup: backups/" + backup.getFileName() + ")" : ""));
        } catch (IOException e) {
            // Still usable this run — it just gets re-upgraded next start.
            notes.add("Could not upgrade arena '" + template.name() + "': " + e.getMessage());
        }
    }

    private List<String> log(List<String> notes) {
        notes.forEach(note -> plugin.getLogger().info(note));
        return notes;
    }

    public ArenaTemplate get(String name) {
        return name == null ? null : templates.get(name.toLowerCase(Locale.ROOT));
    }

    public void register(ArenaTemplate template) {
        templates.put(template.name().toLowerCase(Locale.ROOT), template);
    }

    public Collection<ArenaTemplate> all() {
        return templates.values();
    }

    public List<ArenaTemplate> completeTemplates() {
        return templates.values().stream().filter(ArenaTemplate::isComplete).toList();
    }

    public List<String> names() {
        return List.copyOf(templates.keySet());
    }

    public File dirFor(String name) {
        return new File(templatesDir, name);
    }

    // ---------------------------------------------------------- permissions

    /**
     * The node that governs this arena — whatever its arena.yml names, or the
     * configured prefix plus its name. Every arena always has one, so access
     * can be revoked per-player without editing anything.
     */
    public String permissionFor(ArenaTemplate template) {
        if (template.permission() != null) {
            return template.permission();
        }
        return plugin.pcConfig().arenaPermissionPrefix() + template.name();
    }

    /**
     * Arenas are open by default: a player is refused only where the arena's
     * node has been explicitly set to false for them. An explicit setting
     * always wins — {@code arenas.access-mode} only decides the answer for
     * players who have no setting at all, so switching to ALLOW turns the same
     * node into a whitelist without any of them changing meaning.
     */
    public boolean canUse(Permissible who, ArenaTemplate template) {
        String node = permissionFor(template);
        if (who.isPermissionSet(node)) {
            return who.hasPermission(node);
        }
        if (plugin.pcConfig().arenaAccessMode() == PCConfig.AccessMode.DENY) {
            return true;
        }
        // Nothing set and the arena is a whitelist. hasPermission still answers
        // true for operators, who hold undeclared nodes implicitly — locking
        // admins out of their own arenas would be nobody's idea of ALLOW.
        return who.hasPermission(node);
    }

    /** Complete arenas the player may actually join. */
    public List<ArenaTemplate> availableTo(Player player) {
        return completeTemplates().stream().filter(t -> canUse(player, t)).toList();
    }

    /**
     * What a player should see listed: everything complete, minus locked
     * arenas when {@code arenas.hide-locked} is on.
     */
    public List<ArenaTemplate> visibleTo(Player player) {
        if (plugin.pcConfig().hideLockedArenas()) {
            return availableTo(player);
        }
        return completeTemplates();
    }

    /** Visible arenas belonging to one category. */
    public List<ArenaTemplate> visibleTo(Player player, String category) {
        return visibleTo(player).stream()
                .filter(t -> t.effectiveCategory().equalsIgnoreCase(category))
                .toList();
    }

    /** The categories this player can see, in arena order. */
    public List<String> categoriesFor(Player player) {
        java.util.LinkedHashSet<String> categories = new java.util.LinkedHashSet<>();
        for (ArenaTemplate template : visibleTo(player)) {
            categories.add(template.effectiveCategory());
        }
        return List.copyOf(categories);
    }

    /**
     * The configured default arena, or null when none is set, it does not
     * exist, or it is not finished. Never returns something unplayable.
     */
    public ArenaTemplate defaultArena() {
        String name = plugin.pcConfig().defaultArenaName();
        if (name.isEmpty()) {
            return null;
        }
        ArenaTemplate template = get(name);
        return template != null && template.isComplete() ? template : null;
    }

    /** The default arena if this player may use it, else their first option. */
    public ArenaTemplate defaultFor(Player player) {
        ArenaTemplate preferred = defaultArena();
        if (preferred != null && canUse(player, preferred)) {
            return preferred;
        }
        List<ArenaTemplate> available = availableTo(player);
        return available.isEmpty() ? null : available.get(0);
    }

    // ------------------------------------------------------------- deletion

    /**
     * Deletes an arena everywhere: live sessions on it are ended and their
     * players restored, the folder is removed, and the leaderboard is wiped
     * from memory and from every playerdata file (or the startup scan would
     * resurrect it). Shared by the admin command and the setup GUI.
     *
     * @param whenPurged called on the main thread with the player-record count
     * @return false when no arena by that name existed or its folder resisted
     */
    public boolean deleteCompletely(String name, java.util.function.IntConsumer whenPurged) {
        ArenaTemplate template = get(name);
        if (template == null) {
            return false;
        }
        for (var session : List.copyOf(plugin.sessions().all())) {
            if (session.template().name().equals(template.name())) {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(session.playerId());
                if (player != null) {
                    plugin.messages().note(player, "This arena was removed by an admin.");
                    plugin.sessions().leave(player, true);
                }
            }
        }
        if (!delete(template.name())) {
            return false;
        }
        // Modes with several boards per arena (rush) record under composite
        // keys — every one has to go, or the startup scan resurrects them.
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        keys.add(template.name());
        keys.addAll(plugin.modes().of(template).statsKeys(template));
        java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(keys.size());
        for (String key : keys) {
            plugin.leaderboards().forget(key);
            plugin.stats().purgeTemplate(key, purged -> {
                total.addAndGet(purged);
                if (pending.decrementAndGet() == 0) {
                    whenPurged.accept(total.get());
                }
            });
        }
        return true;
    }

    /** Removes the arena from the registry and deletes its folder. */
    public boolean delete(String name) {
        ArenaTemplate template = templates.remove(name.toLowerCase(Locale.ROOT));
        if (template == null) {
            return false;
        }
        plugin.schematics().evict(template.schematicFile());
        deleteRecursively(template.dir().toPath());
        return true;
    }

    /**
     * Deletes an arena folder that never made it into the registry — a failed
     * import that already captured its schematic. Registered arenas must go
     * through {@link #delete} so the registry and clipboard cache stay honest.
     */
    public void deleteUnregistered(String name) {
        if (templates.containsKey(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        deleteRecursively(dirFor(name).toPath());
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
