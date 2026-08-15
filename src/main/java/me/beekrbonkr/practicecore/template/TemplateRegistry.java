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

/**
 * Every arena on disk, and the folder layout that defines the menu grouping:
 *
 * <pre>
 *   templates/&lt;arena&gt;/              — grouped under the arena's mode
 *   templates/&lt;category&gt;/&lt;arena&gt;/   — grouped under that folder's name
 * </pre>
 *
 * Categories are exactly one folder deep, and a folder is an arena when it
 * holds an arena.yml or arena.schem — so moving an arena between categories
 * is a drag-and-drop plus a reload, with nothing to edit.
 */
public final class TemplateRegistry {

    private final PracticeCorePlugin plugin;
    private final File templatesDir;
    private final Map<String, ArenaTemplate> templates = new LinkedHashMap<>();

    /** One arena folder on disk with the category folder it was found in. */
    private record ArenaFolder(File dir, String category) {
    }

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
        Map<String, ArenaTemplate> loaded = new LinkedHashMap<>();
        for (ArenaFolder folder : arenaFolders(notes)) {
            ArenaTemplate template = read(folder, notes);
            if (template == null) {
                continue;
            }
            // Lowercased like every lookup — a hand-made uppercase folder must
            // not load into an unreachable registry key.
            String key = template.name().toLowerCase(Locale.ROOT);
            ArenaTemplate clash = loaded.get(key);
            if (clash != null) {
                // Names are the join key, so two folders cannot share one:
                // whichever is already in wins and the second is left alone.
                notes.add("Arena '" + template.name() + "' exists in two places ("
                        + relative(clash.dir()) + " and " + relative(template.dir())
                        + ") — the second was skipped. Rename one of them.");
                continue;
            }
            loaded.put(key, template);
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

    /**
     * Every arena folder on disk with the category it belongs to, walking one
     * level into each category folder. Alphabetical throughout, so menu and
     * list ordering is stable across restarts.
     */
    private List<ArenaFolder> arenaFolders(List<String> notes) {
        List<ArenaFolder> found = new ArrayList<>();
        for (File entry : childDirs(templatesDir)) {
            if (isArenaFolder(entry)) {
                found.add(new ArenaFolder(entry, null));
                continue;
            }
            String category = entry.getName().toLowerCase(Locale.ROOT);
            for (File dir : childDirs(entry)) {
                if (isArenaFolder(dir)) {
                    found.add(new ArenaFolder(dir, category));
                } else {
                    notes.add("Folder " + relative(dir) + " has no arena.yml or arena.schem, and "
                            + "categories are only one folder deep — ignored.");
                }
            }
        }
        return found;
    }

    private static List<File> childDirs(File parent) {
        File[] dirs = parent.listFiles(File::isDirectory);
        if (dirs == null) {
            return List.of();
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        return Arrays.asList(dirs);
    }

    /** An arena folder holds its arena.yml or its schematic; a category folder does not. */
    private static boolean isArenaFolder(File dir) {
        return new File(dir, "arena.yml").isFile() || new File(dir, "arena.schem").isFile();
    }

    /** A path an admin can find on disk, e.g. {@code templates/rush/nova}. */
    private String relative(File dir) {
        Path path = templatesDir.toPath().toAbsolutePath()
                .relativize(dir.toPath().toAbsolutePath());
        return "templates/" + path.toString().replace(File.separatorChar, '/');
    }

    private ArenaTemplate read(ArenaFolder folder, List<String> notes) {
        File dir = folder.dir();
        ArenaTemplate template;
        try {
            template = ArenaTemplate.load(dir, folder.category());
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
        boolean stuck = false;
        if (template.legacyCategory() != null) {
            if (template.category() == null) {
                // A failed move must not be followed by the upgrade that drops
                // the key, or the grouping is lost for good rather than
                // retried on the next load.
                stuck = !fileUnderLegacyCategory(template, notes);
            } else if (!template.legacyCategory().equals(template.category())) {
                notes.add("Arena '" + template.name() + "' says category '"
                        + template.legacyCategory() + "' in its arena.yml but sits in "
                        + relative(dir) + " — the folder decides now, so it is listed under '"
                        + template.category() + "'.");
            }
        }
        if (!stuck && template.needsUpgrade()) {
            upgrade(template, notes);
        }
        return template;
    }

    /**
     * Pre-v3 arenas named their category in arena.yml. Move the folder into
     * the matching category folder once, so the layout on disk now says what
     * the file used to; the key itself goes with the upgrade that follows.
     *
     * @return true when the arena is where its old category says it should be
     */
    private boolean fileUnderLegacyCategory(ArenaTemplate template, List<String> notes) {
        String category = template.legacyCategory();
        try {
            moveToCategory(template, category);
        } catch (IOException e) {
            notes.add("Arena '" + template.name() + "' still has category '" + category
                    + "' in its arena.yml but could not be moved into templates/" + category
                    + "/ (" + e.getMessage() + ") — it groups under its mode until you move it.");
            return false;
        }
        notes.add("Arena '" + template.name() + "' had category '" + category
                + "' in its arena.yml — moved to " + relative(template.dir())
                + ", which is where the category now comes from.");
        return true;
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

    /**
     * A cheap summary of what the arena folder holds right now: every arena
     * file's path, size and last-modified stamp.
     *
     * A reload compares this against the previous one to decide whether the
     * arenas themselves changed — which is the difference between a reload
     * that can be applied under running sessions and one that has to end them
     * first, because an edited arena.yml no longer describes the copy of the
     * arena already pasted into the world.
     */
    public String fingerprint() {
        StringBuilder digest = new StringBuilder();
        for (ArenaFolder folder : arenaFolders(new ArrayList<>())) {
            for (String name : new String[]{"arena.yml", "arena.schem"}) {
                File entry = new File(folder.dir(), name);
                digest.append(relative(folder.dir())).append('/').append(name).append(':')
                        .append(entry.length()).append('@')
                        .append(entry.lastModified()).append(';');
            }
        }
        return digest.toString();
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

    /** Where a new uncategorised arena's folder goes. */
    public File dirFor(String name) {
        return dirFor(name, null);
    }

    /** Where a new arena's folder goes; a null category means straight in templates/. */
    public File dirFor(String name, String category) {
        String slug = ArenaTemplate.normalizeCategory(category);
        return slug == null ? new File(templatesDir, name)
                : new File(new File(templatesDir, slug), name);
    }

    /**
     * Moves an arena's folder so its category becomes {@code category} (null
     * files it back under its mode). The template follows the move, so this
     * works on one that is not registered yet — a save or an import in
     * flight. Nothing is left half-moved: on failure it stays where it was.
     *
     * @return the folder the arena now lives in
     * @throws IOException when the target is taken or the move itself fails
     */
    public File moveToCategory(ArenaTemplate template, String category) throws IOException {
        String slug = ArenaTemplate.normalizeCategory(category);
        File from = template.dir();
        if (java.util.Objects.equals(slug, template.category())) {
            return from;
        }
        File to = dirFor(template.name(), slug);
        if (to.exists()) {
            throw new IOException(relative(to) + " already exists");
        }
        File parent = to.getParentFile();
        if (parent != null && !parent.equals(templatesDir) && isArenaFolder(parent)) {
            throw new IOException(relative(parent) + " is an arena, not a category folder");
        }
        Files.createDirectories(to.toPath().getParent());
        Files.move(from.toPath(), to.toPath());
        // The clipboard cache is keyed by absolute path; the old one is gone.
        plugin.schematics().evict(new File(from, "arena.schem"));
        template.relocate(to, slug);
        pruneCategory(from.getParentFile());
        return to;
    }

    /** Removes a category folder that the arena just moved out of emptied. */
    private void pruneCategory(File dir) {
        if (dir == null || dir.equals(templatesDir) || !dir.isDirectory()) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir.toPath())) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir.toPath());
            }
        } catch (IOException ignored) {
            // An empty folder left behind only shows up as an empty category,
            // which the menus already skip.
        }
    }

    /**
     * The folder holding this arena — categorised or not — or null when no
     * such arena exists on disk. Used before the registry has been built.
     */
    static Path findArenaFolder(Path templatesDir, String name) {
        Path direct = templatesDir.resolve(name);
        if (Files.exists(direct)) {
            return direct;
        }
        for (File category : childDirs(templatesDir.toFile())) {
            Path nested = category.toPath().resolve(name);
            if (Files.exists(nested) && !isArenaFolder(category)) {
                return nested;
            }
        }
        return null;
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

    /** The category folders currently in use, in arena order. */
    public List<String> categoryFolders() {
        java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
        for (ArenaTemplate template : templates.values()) {
            if (template.category() != null) {
                found.add(template.category());
            }
        }
        return List.copyOf(found);
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
        pruneCategory(template.dir().getParentFile());
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
        Path dir = findArenaFolder(templatesDir.toPath(), name);
        if (dir == null) {
            return;
        }
        deleteRecursively(dir);
        pruneCategory(dir.toFile().getParentFile());
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
