package me.beekrbonkr.practicecore.config;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every sound the plugin plays, named by what it means rather than by which
 * sound it is — {@code run.finish-pb}, not {@code ENTITY_PLAYER_LEVELUP}. The
 * mapping lives in sounds.yml as one line per cue:
 *
 * <pre>
 *   run.finish-pb: 'ENTITY_PLAYER_LEVELUP 0.8 1.4'   # sound, volume, pitch
 *   run.fail: ''                                     # silenced
 * </pre>
 *
 * Sounds are resolved through the registry rather than the {@code Sound} enum,
 * so a name added (or an enum turned into an interface) by a later Minecraft
 * release still works, and both {@code BLOCK_NOTE_BLOCK_HAT} and
 * {@code minecraft:block.note_block.hat} spellings are accepted.
 *
 * {@code effects.sounds: false} in config.yml silences everything at once;
 * an empty value silences one cue.
 */
public final class SoundConfig {

    /** One resolved cue. A null sound means "configured to stay silent". */
    public record Cue(Sound sound, float volume, float pitch) {
    }

    private static final Cue SILENT = new Cue(null, 0f, 0f);

    private final PracticeCorePlugin plugin;
    private final ConfigFile file;
    /** Parsed cues, cleared on every load — parsing is not worth doing per play. */
    private final Map<String, Cue> cues = new HashMap<>();
    /** Registry index built on first use: enum-style name and key → sound. */
    private Map<String, Sound> index;

    public SoundConfig(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, "sounds", "sounds.yml",
                Versions.SOUNDS, SoundConfig::steps);
    }

    /** Reshapes an older sounds.yml. See {@link Versions#SOUNDS}. */
    private static void steps(org.bukkit.configuration.file.FileConfiguration cfg, int from) {
        // v0 → v1 is the first versioned layout; nothing moved.
    }

    public String probe() {
        return file.probe();
    }

    public List<String> load() {
        List<String> notes = file.load();
        cues.clear();
        return notes;
    }

    // --------------------------------------------------------------- playing

    /** Plays a cue to one player, at their own position. */
    public void play(Player player, String key) {
        if (player == null || !player.isOnline()) {
            return;
        }
        play(player, key, player.getLocation());
    }

    /** Plays a cue to one player only, positioned somewhere in their world. */
    public void play(Player player, String key, Location at) {
        Cue cue = cue(key);
        if (cue.sound() == null || player == null || !player.isOnline() || at == null) {
            return;
        }
        player.playSound(at, cue.sound(), cue.volume(), cue.pitch());
    }

    /** Plays a cue out loud in the world — everyone nearby hears it. */
    public void playAt(Location at, String key) {
        Cue cue = cue(key);
        if (cue.sound() == null || at == null || at.getWorld() == null) {
            return;
        }
        at.getWorld().playSound(at, cue.sound(), cue.volume(), cue.pitch());
    }

    /** Plays a cue to every online player, at their own position. */
    public void broadcast(String key) {
        Cue cue = cue(key);
        if (cue.sound() == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), cue.sound(), cue.volume(), cue.pitch());
        }
    }

    // -------------------------------------------------------------- resolving

    /** The resolved cue for a key; never null, possibly silent. */
    public Cue cue(String key) {
        if (plugin.pcConfig() != null && !plugin.pcConfig().sounds()) {
            return SILENT;
        }
        Cue cached = cues.get(key);
        if (cached != null) {
            return cached;
        }
        Cue parsed = parse(key, file.string(key, ""));
        cues.put(key, parsed);
        return parsed;
    }

    /**
     * {@code SOUND [volume] [pitch]}. A blank line is a deliberate silence; a
     * name the server does not know is reported once and then treated as one.
     */
    private Cue parse(String key, String spec) {
        if (spec == null || spec.isBlank()) {
            return SILENT;
        }
        String[] parts = spec.trim().split("\\s+");
        Sound sound = resolve(parts[0]);
        if (sound == null) {
            plugin.getLogger().warning("sounds.yml: '" + parts[0] + "' at " + key
                    + " is not a sound this server knows — that cue stays silent.");
            return SILENT;
        }
        float volume = parts.length > 1 ? parseFloat(key, parts[1], 1.0f) : 1.0f;
        float pitch = parts.length > 2 ? parseFloat(key, parts[2], 1.0f) : 1.0f;
        // Vanilla clamps pitch to 0.5-2.0 anyway; volume above 1 only widens
        // the audible radius, which is a legitimate thing to want.
        return new Cue(sound, Math.max(0f, volume), Math.clamp(pitch, 0.5f, 2.0f));
    }

    private float parseFloat(String key, String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("sounds.yml: '" + raw + "' at " + key
                    + " is not a number — using " + fallback + ".");
            return fallback;
        }
    }

    /** Accepts {@code BLOCK_NOTE_BLOCK_HAT} or {@code minecraft:block.note_block.hat}. */
    private Sound resolve(String name) {
        if (name.indexOf(':') >= 0 || name.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            Sound direct = key == null ? null : Registry.SOUNDS.get(key);
            if (direct != null) {
                return direct;
            }
        }
        return index().get(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Registry walk, once. Bukkit's constant names are the registry key with
     * dots turned into underscores and upper-cased, so rebuilding that mapping
     * here keeps the familiar {@code ENTITY_PLAYER_LEVELUP} spelling working
     * without ever touching the enum.
     */
    @SuppressWarnings("deprecation") // Keyed#getKey is how the registry is walked
    private Map<String, Sound> index() {
        if (index == null) {
            index = new HashMap<>();
            for (Sound sound : Registry.SOUNDS) {
                NamespacedKey key = sound.getKey();
                index.put(key.getKey().replace('.', '_').toUpperCase(Locale.ROOT), sound);
            }
        }
        return index;
    }
}
