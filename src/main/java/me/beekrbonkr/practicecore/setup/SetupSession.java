package me.beekrbonkr.practicecore.setup;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.template.TriggerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mutable state of one in-progress arena configuration (new or edit). */
final class SetupSession {

    final UUID admin;
    final String name;
    final File dir;
    final Slot slot;
    final Location origin;
    /** False for a brand-new arena, true when reopening a saved one. */
    final boolean editing;

    /** Replaced by /practice setup capture and /practice setup schematic. */
    Clipboard clipboard;
    BoundingBox bounds;

    String mode;
    String displayName;
    String permission;
    Material icon;
    boolean requireBlocksForPb;

    Vector spawnOffset;
    float spawnYaw;
    float spawnPitch;
    Vector triggerOffset;
    TriggerType triggerType;
    String triggerBlockData;

    /**
     * Every position an admin has put a button or plate at during this setup,
     * plus the one stamped in when an existing arena was opened. Only one is
     * the live trigger; the rest are leftovers that must not be baked into a
     * captured schematic.
     */
    final Set<Location> triggerCandidates = new LinkedHashSet<>();
    final Map<Integer, ItemStack> kit = new HashMap<>();

    SetupSession(UUID admin, String name, File dir, Clipboard clipboard,
                 Slot slot, Location origin, BoundingBox bounds, boolean editing) {
        this.admin = admin;
        this.name = name;
        this.dir = dir;
        this.clipboard = clipboard;
        this.slot = slot;
        this.origin = origin;
        this.bounds = bounds;
        this.editing = editing;
    }

    /** Pre-fills the wizard from a saved arena so nothing has to be redone. */
    void copyFrom(ArenaTemplate template) {
        mode = template.mode();
        displayName = template.displayName();
        permission = template.permission();
        icon = template.icon();
        requireBlocksForPb = template.requireBlocksForPb();
        if (template.spawnOffset() != null) {
            spawnOffset = template.spawnOffset().clone();
            spawnYaw = template.spawnYaw();
            spawnPitch = template.spawnPitch();
        }
        if (template.triggerOffset() != null) {
            triggerOffset = template.triggerOffset().clone();
            triggerType = template.triggerType();
            triggerBlockData = template.triggerBlockData();
        }
        kit.putAll(template.kit());
    }

    Location triggerLocation() {
        if (triggerOffset == null) {
            return null;
        }
        return new Location(origin.getWorld(),
                origin.getBlockX() + triggerOffset.getBlockX(),
                origin.getBlockY() + triggerOffset.getBlockY(),
                origin.getBlockZ() + triggerOffset.getBlockZ());
    }

    boolean ready() {
        return spawnOffset != null && triggerOffset != null;
    }
}
