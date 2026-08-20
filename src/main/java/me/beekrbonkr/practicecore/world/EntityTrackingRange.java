package me.beekrbonkr.practicecore.world;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Widens the practice world's entity tracking ranges so the PvP and rush
 * bots (server-side monsters), their floating tags (displays) and the shop
 * dealers render from much further away than spigot.yml's defaults — a bot
 * that pops into view at 48 blocks reads as broken on a big arena.
 *
 * There is no Bukkit API for tracking ranges, but Spigot attaches its world
 * config to every level as plain, unobfuscated public fields
 * ({@code ServerLevel.spigotConfig} → {@code SpigotWorldConfig.*TrackingRange}),
 * which have been stable for a decade. Tracked entities read the value when
 * they are added to the tracker, so setting it right after the world is
 * built covers every bot the plugin will ever spawn. One failure logs once
 * and leaves the server defaults — the bots still work, just at stock range.
 *
 * Note: clients still cap rendering at their own render distance and the
 * server's send view distance; this lifts the server-side ceiling only.
 */
public final class EntityTrackingRange {

    private static boolean warned;

    private EntityTrackingRange() {
    }

    /** Applies {@code range} (blocks) to the world's monster/display/other tracking. */
    public static void apply(Plugin plugin, World world, int range) {
        if (world == null || range <= 0) {
            return;
        }
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            Object level = getHandle.invoke(world);
            Field configField = level.getClass().getField("spigotConfig");
            Object config = configField.get(level);
            int applied = 0;
            for (String field : new String[]{
                    "monsterTrackingRange",   // the bots themselves (husks)
                    "displayTrackingRange",   // name tags and damage indicators
                    "animalTrackingRange",    // shop dealer villagers on some builds
                    "otherTrackingRange"}) {  // …and on the rest
                try {
                    Field f = config.getClass().getField(field);
                    if (f.getInt(config) < range) {
                        f.setInt(config, range);
                    }
                    applied++;
                } catch (NoSuchFieldException ignored) {
                    // A future rename of one category is survivable.
                }
            }
            if (applied == 0) {
                warnOnce(plugin, "no tracking-range fields found");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(plugin, e.toString());
        }
    }

    private static void warnOnce(Plugin plugin, String reason) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("Could not raise the practice world's entity"
                    + " tracking range (" + reason + ") — bots render at the server's"
                    + " stock distance. Raise entity-tracking-range in spigot.yml instead.");
        }
    }
}
