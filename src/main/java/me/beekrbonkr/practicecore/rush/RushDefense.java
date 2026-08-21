package me.beekrbonkr.practicecore.rush;

import org.bukkit.Material;

import java.util.List;

/**
 * One named bed-defense preset from config.yml's {@code rush.defense-presets}
 * — the shell of blocks the mode builds over every enemy bed before a run.
 *
 * <p>The shape is always the stepped pyramid real bedwars players build: the
 * footprint is widest at bed level and loses a ring for every block of height,
 * so the structure tapers to a cap directly over the bed instead of standing
 * as a box. {@link #layers()} is that pyramid's materials, <em>innermost
 * first</em> — the first entry is what touches the bed, the last is the skin
 * a rusher meets — and the list's length is therefore also the pyramid's
 * height and reach. An empty list is "no defenses at all".
 */
public record RushDefense(String id, String displayName, Material icon,
                          List<Material> layers) {

    /** The id every map falls back to and the one that builds nothing. */
    public static final String NONE = "none";

    public RushDefense {
        layers = List.copyOf(layers);
    }

    /** The bare "no defenses" preset, used when config.yml defines none. */
    public static RushDefense none() {
        return new RushDefense(NONE, "None", Material.BARRIER, List.of());
    }

    public boolean builds() {
        return !layers.isEmpty();
    }

    /** How far out (and how high) the pyramid reaches from the bed. */
    public int reach() {
        return layers.size();
    }

    /** The icon a menu shows: the outermost material, unless one is configured. */
    public Material menuIcon() {
        if (icon != null) {
            return icon;
        }
        return layers.isEmpty() ? Material.BARRIER : layers.get(layers.size() - 1);
    }
}
