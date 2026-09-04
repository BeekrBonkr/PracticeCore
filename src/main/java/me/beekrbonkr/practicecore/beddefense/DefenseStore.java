package me.beekrbonkr.practicecore.beddefense;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.Versions;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every saved bed defense on the server, one YAML per defense under
 * {@code defenses/}. This is the plugin's only shared, player-authored data:
 * unlike playerdata it is read by everyone (the public gallery) and written
 * by many (likes, play counts), so it lives in its own folder with its own
 * writer. Cached in memory; writes are rendered on the main thread and
 * flushed by one background thread in order.
 */
public final class DefenseStore {

    private final PracticeCorePlugin plugin;
    private final File dir;
    private final Map<String, BedDefense> defenses = new LinkedHashMap<>();
    private final java.util.concurrent.ExecutorService writer =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "PracticeCore-defense-writer");
                thread.setDaemon(true);
                return thread;
            });

    public DefenseStore(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "defenses");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the defenses directory");
        }
    }

    // ------------------------------------------------------------- loading

    /** Reads every defense file; returns notes worth logging. */
    public List<String> load() {
        List<String> notes = new ArrayList<>();
        defenses.clear();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return notes;
        }
        int bad = 0;
        for (File file : files) {
            BedDefense defense = read(file);
            if (defense == null) {
                bad++;
                continue;
            }
            defenses.put(defense.id(), defense);
        }
        if (bad > 0) {
            notes.add(bad + " bed defense file(s) under defenses/ could not be read and were skipped.");
        }
        return notes;
    }

    private BedDefense read(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String id = yml.getString("id", file.getName().replace(".yml", ""));
        String authorRaw = yml.getString("author", "");
        UUID author;
        try {
            author = UUID.fromString(authorRaw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("defenses/" + file.getName() + " has no valid author — skipped.");
            return null;
        }
        List<DefenseBlock> blocks = new ArrayList<>();
        for (Map<?, ?> raw : yml.getMapList("blocks")) {
            Material kind = Material.matchMaterial(String.valueOf(raw.get("kind")));
            if (kind == null) {
                continue;
            }
            Object data = raw.get("data");
            blocks.add(new DefenseBlock(
                    toInt(raw.get("x")), toInt(raw.get("y")), toInt(raw.get("z")),
                    BlockKinds.normalize(kind),
                    data == null ? kind.getKey().toString() : String.valueOf(data)));
        }
        if (blocks.isEmpty()) {
            plugin.getLogger().warning("defenses/" + file.getName() + " holds no blocks — skipped.");
            return null;
        }
        BedDefense defense = new BedDefense(id, yml.getString("name", id), author,
                yml.getString("author-name", "?"), yml.getLong("created", file.lastModified()),
                yml.getBoolean("published", false), blocks);
        for (String like : yml.getStringList("likes")) {
            try {
                defense.likes().add(UUID.fromString(like));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String played : yml.getStringList("played")) {
            try {
                defense.played().add(UUID.fromString(played));
            } catch (IllegalArgumentException ignored) {
            }
        }
        defense.setCompletions(yml.getInt("completions", 0));
        return defense;
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    // -------------------------------------------------------------- saving

    public void save(BedDefense defense) {
        if (!defenses.containsKey(defense.id())) {
            return; // deleted meanwhile — a late like must not bring it back
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set(Versions.DATA_KEY, Versions.DEFENSE);
        yml.set("id", defense.id());
        yml.set("name", defense.name());
        yml.set("author", defense.author().toString());
        yml.set("author-name", defense.authorName());
        yml.set("created", defense.created());
        yml.set("published", defense.published());
        yml.set("fingerprint", defense.fingerprint());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (DefenseBlock block : defense.blocks()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", block.x());
            entry.put("y", block.y());
            entry.put("z", block.z());
            entry.put("kind", block.kind().name());
            entry.put("data", block.data());
            blocks.add(entry);
        }
        yml.set("blocks", blocks);
        yml.set("likes", defense.likes().stream().map(UUID::toString).toList());
        yml.set("played", defense.played().stream().map(UUID::toString).toList());
        yml.set("completions", defense.completions());
        String rendered = yml.saveToString();
        File file = fileOf(defense.id());
        if (!plugin.isEnabled()) {
            write(file, rendered);
            return;
        }
        writer.execute(() -> write(file, rendered));
    }

    private void write(File file, String rendered) {
        try {
            java.nio.file.Files.writeString(file.toPath(), rendered);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    private File fileOf(String id) {
        return new File(dir, id + ".yml");
    }

    /** Blocks until every queued write has landed — onDisable only. */
    public void flushSync() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------ mutation

    /** Creates and persists a new defense under a fresh id. */
    public BedDefense create(String name, UUID author, String authorName, boolean published,
                             List<DefenseBlock> blocks) {
        String id = newId();
        BedDefense defense = new BedDefense(id, name, author, authorName,
                System.currentTimeMillis(), published, blocks);
        defenses.put(id, defense);
        save(defense);
        return defense;
    }

    /**
     * Replaces the blocks of an existing defense with a reshaped copy under
     * the same id, keeping its likes, plays and visibility. The shape is
     * immutable on the object, so a new instance takes the old one's place.
     */
    public BedDefense reshape(BedDefense old, List<DefenseBlock> blocks) {
        BedDefense fresh = new BedDefense(old.id(), old.name(), old.author(), old.authorName(),
                old.created(), old.published(), blocks);
        fresh.likes().addAll(old.likes());
        fresh.played().addAll(old.played());
        fresh.setCompletions(old.completions());
        defenses.put(fresh.id(), fresh);
        save(fresh);
        return fresh;
    }

    public void delete(BedDefense defense) {
        defenses.remove(defense.id());
        File file = fileOf(defense.id());
        // Same queue as the saves, so a save queued a moment ago lands first
        // and the delete wins.
        Runnable remove = () -> {
            if (file.isFile() && !file.delete()) {
                plugin.getLogger().warning("Could not delete " + file.getName());
            }
        };
        if (plugin.isEnabled()) {
            writer.execute(remove);
        } else {
            remove.run();
        }
    }

    private String newId() {
        String id;
        do {
            id = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffffffffL);
            while (id.length() < 10) {
                id = "0" + id;
            }
        } while (defenses.containsKey(id) || fileOf(id).exists());
        return id;
    }

    // ------------------------------------------------------------- queries

    public BedDefense get(String id) {
        return id == null ? null : defenses.get(id.toLowerCase(Locale.ROOT));
    }

    public java.util.Collection<BedDefense> all() {
        return List.copyOf(defenses.values());
    }

    /**
     * The public gallery: published defenses, most liked first, then most
     * played, then oldest — the order the community has voted on.
     */
    public List<BedDefense> published() {
        return defenses.values().stream()
                .filter(BedDefense::published)
                .sorted(Comparator.comparingInt(BedDefense::likeCount).reversed()
                        .thenComparing(Comparator.comparingInt(BedDefense::uniquePlayers).reversed())
                        .thenComparingLong(BedDefense::created))
                .toList();
    }

    /** One player's own defenses, newest first. */
    public List<BedDefense> ownedBy(UUID player) {
        return defenses.values().stream()
                .filter(defense -> defense.isAuthor(player))
                .sorted(Comparator.comparingLong(BedDefense::created).reversed())
                .toList();
    }

    /** Every defense this player may pick to play: public ones plus their own. */
    public List<BedDefense> playableBy(UUID player) {
        List<BedDefense> playable = new ArrayList<>(published());
        for (BedDefense own : ownedBy(player)) {
            if (!own.published()) {
                playable.add(own);
            }
        }
        return playable;
    }

    /**
     * An existing defense with exactly this shape that the player can see —
     * any published one, or one of their own — or null. Someone else's
     * private defense never counts: the player has no way of knowing it
     * exists, and refusing on its account would be baffling.
     */
    public BedDefense duplicateOf(List<DefenseBlock> blocks, UUID player, String exceptId) {
        String fingerprint = BedDefense.fingerprintOf(blocks);
        for (BedDefense defense : defenses.values()) {
            if (defense.id().equals(exceptId) || !defense.fingerprint().equals(fingerprint)) {
                continue;
            }
            if (defense.published() || defense.isAuthor(player)) {
                return defense;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return defenses.isEmpty();
    }

    /** Called on a player join so gallery tiles show current names. */
    public void updateAuthorName(UUID player, String name) {
        for (BedDefense defense : defenses.values()) {
            if (defense.isAuthor(player) && !name.equals(defense.authorName())) {
                defense.setAuthorName(name);
                save(defense);
            }
        }
    }
}
