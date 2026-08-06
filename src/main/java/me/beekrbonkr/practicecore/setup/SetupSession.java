package me.beekrbonkr.practicecore.setup;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.template.TriggerType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable state of one in-progress arena configuration. */
final class SetupSession {

    final UUID admin;
    final String name;
    final File dir;
    final Clipboard clipboard;
    final Slot slot;
    final Location origin;
    final BoundingBox bounds;

    Vector spawnOffset;
    float spawnYaw;
    float spawnPitch;
    Vector triggerOffset;
    TriggerType triggerType;
    String triggerBlockData;
    final Map<Integer, ItemStack> kit = new HashMap<>();

    SetupSession(UUID admin, String name, File dir, Clipboard clipboard,
                 Slot slot, Location origin, BoundingBox bounds) {
        this.admin = admin;
        this.name = name;
        this.dir = dir;
        this.clipboard = clipboard;
        this.slot = slot;
        this.origin = origin;
        this.bounds = bounds;
    }
}
