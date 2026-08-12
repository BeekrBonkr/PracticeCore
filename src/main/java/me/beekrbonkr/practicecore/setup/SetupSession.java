package me.beekrbonkr.practicecore.setup;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.template.ArenaTrigger;
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
    String category;
    String displayName;
    String permission;
    Material icon;
    boolean requireBlocksForPb;

    Vector spawnOffset;
    float spawnYaw;
    float spawnPitch;
    /** Finish triggers so far; every placed button/plate adds one. */
    final java.util.List<ArenaTrigger> triggers = new java.util.ArrayList<>();

    /**
     * Every position an admin has put a button or plate at during this setup,
     * plus the ones stamped in when an existing arena was opened. Triggers
     * live outside the schematic, so none of these may be baked into a
     * captured schematic.
     */
    final Set<Location> triggerCandidates = new LinkedHashSet<>();
    final Map<Integer, ItemStack> kit = new HashMap<>();
    /** Per-mode settings carried through an edit so saving never drops them. */
    final Map<String, Object> settings = new java.util.LinkedHashMap<>();

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
        category = template.category();
        displayName = template.displayName();
        permission = template.permission();
        icon = template.icon();
        requireBlocksForPb = template.requireBlocksForPb();
        if (template.spawnOffset() != null) {
            spawnOffset = template.spawnOffset().clone();
            spawnYaw = template.spawnYaw();
            spawnPitch = template.spawnPitch();
        }
        triggers.addAll(template.triggers());
        kit.putAll(template.kit());
        settings.putAll(template.settings());
    }

    boolean ready(boolean needsTrigger) {
        return spawnOffset != null && (!needsTrigger || !triggers.isEmpty());
    }
}
