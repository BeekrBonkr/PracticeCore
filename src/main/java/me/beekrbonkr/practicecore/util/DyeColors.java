package me.beekrbonkr.practicecore.util;

import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.Locale;

/**
 * The one place a color <em>name</em> becomes a {@link DyeColor}.
 *
 * <p>Color names arrive from several vocabularies that do not quite agree
 * with Bukkit's: MBedwars calls its lime team {@code LIGHT_GREEN}, chat
 * colors call light blue {@code AQUA}, and admins type whatever they like
 * into a setup command. Every wool, bed, glass, terracotta, or leather dye
 * decided from a string must go through {@link #parse} so those aliases
 * resolve the same way everywhere — never call {@code DyeColor.valueOf}
 * on user- or plugin-supplied text directly.
 */
public final class DyeColors {

    private DyeColors() {
    }

    /**
     * The dye color a name refers to, or null when it names none. Case,
     * spaces and hyphens are ignored, so {@code "Light Green"},
     * {@code "light-green"} and {@code "LIGHT_GREEN"} all resolve to
     * {@link DyeColor#LIME}.
     */
    public static DyeColor parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        key = switch (key) {
            case "LIGHT_GREEN", "LIGHTGREEN" -> "LIME";
            case "AQUA", "LIGHTBLUE" -> "LIGHT_BLUE";
            case "DARK_AQUA", "TEAL" -> "CYAN";
            case "DARK_PURPLE" -> "PURPLE";
            case "LIGHTGRAY", "SILVER", "LIGHT_GREY", "LIGHTGREY" -> "LIGHT_GRAY";
            case "GREY", "DARK_GRAY", "DARK_GREY" -> "GRAY";
            case "DARK_GREEN" -> "GREEN";
            case "DARK_RED" -> "RED";
            case "DARK_BLUE" -> "BLUE";
            case "GOLD" -> "ORANGE";
            case "LIGHT_PURPLE" -> "MAGENTA";
            default -> key;
        };
        try {
            return DyeColor.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@link #parse}, falling back to {@code fallback} for unknown names. */
    public static DyeColor parse(String name, DyeColor fallback) {
        DyeColor color = parse(name);
        return color != null ? color : fallback;
    }

    /** The wool block of a dye color, e.g. {@code LIME_WOOL}. */
    public static Material wool(DyeColor color) {
        return colored(color, "WOOL", Material.WHITE_WOOL);
    }

    /**
     * The {@code <COLOR>_<suffix>} material of a dye color ({@code WOOL},
     * {@code BED}, {@code STAINED_GLASS}, {@code TERRACOTTA}, ...), or the
     * fallback when no such material exists.
     */
    public static Material colored(DyeColor color, String suffix, Material fallback) {
        if (color == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(color.name() + "_" + suffix);
        return material != null ? material : fallback;
    }
}
