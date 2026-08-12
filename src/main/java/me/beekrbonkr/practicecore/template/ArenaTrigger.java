package me.beekrbonkr.practicecore.template;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * One finish trigger of an arena: a button or pressure plate at a fixed offset
 * from the paste origin. An arena may have any number of them; touching any
 * one finishes the run.
 */
public record ArenaTrigger(Vector offset, TriggerType type, String blockData) {

    /** The trigger's block location for an arena pasted at {@code origin}. */
    public Location location(Location origin) {
        return new Location(origin.getWorld(),
                origin.getBlockX() + offset.getBlockX(),
                origin.getBlockY() + offset.getBlockY(),
                origin.getBlockZ() + offset.getBlockZ());
    }
}
