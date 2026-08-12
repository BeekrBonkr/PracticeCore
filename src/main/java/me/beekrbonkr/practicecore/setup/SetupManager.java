package me.beekrbonkr.practicecore.setup;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.template.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The admin arena-configuration wizard. One wizard may run at a time.
 *
 * Creating: //copy an arena build anywhere → /practice setup start &lt;name&gt;
 * (the clipboard is read directly — no fragile "wait for //paste") → set
 * spawn → place the finish button/plate → optional kit → save.
 *
 * Editing: /practice edit &lt;name&gt; pastes the saved arena and pre-loads every
 * setting, so any single part of it can be changed and saved back. Blocks
 * placed while editing become part of the arena once /practice setup capture
 * writes the region back over the schematic.
 */
public final class SetupManager {

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<Material> KIT_WARN = Set.of(
            Material.ENDER_PEARL, Material.CHORUS_FRUIT, Material.ELYTRA, Material.TNT,
            Material.LAVA_BUCKET, Material.WATER_BUCKET, Material.TRIDENT,
            Material.WIND_CHARGE, Material.MACE, Material.FIREWORK_ROCKET);

    private final PracticeCorePlugin plugin;
    private SetupSession active;

    public SetupManager(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    public boolean isAdmin(UUID player) {
        return active != null && active.admin.equals(player);
    }

    /**
     * True when the block lies inside the open wizard's pasted arena — the
     * world listeners exempt this region the way they exempt the admin, so
     * gravel falls, liquids flow and drops land while an arena is being built.
     */
    public boolean containsBlock(Location loc) {
        return active != null && active.bounds != null
                && active.bounds.contains(loc.getBlockX() + 0.5, loc.getBlockY() + 0.5,
                        loc.getBlockZ() + 0.5);
    }

    /** Name of the arena being configured, or null. */
    public String activeName() {
        return active == null ? null : active.name;
    }

    // ------------------------------------------- read-only state for the GUI

    public boolean activeEditing() {
        return active != null && active.editing;
    }

    public String activeMode() {
        return active == null || active.mode == null
                ? me.beekrbonkr.practicecore.mode.BridgingMode.ID : active.mode;
    }

    /** The explicit category, or null when the arena groups under its mode. */
    public String activeCategory() {
        return active == null ? null : active.category;
    }

    public String activeDisplayName() {
        return active == null ? null
                : (active.displayName != null ? active.displayName : active.name);
    }

    /** The explicit permission node, or null for the arena's default. */
    public String activePermission() {
        return active == null ? null : active.permission;
    }

    public boolean activeRequireBlocks() {
        return active != null && active.requireBlocksForPb;
    }

    public boolean activeHasSpawn() {
        return active != null && active.spawnOffset != null;
    }

    public int activeTriggerCount() {
        return active == null ? 0 : active.triggers.size();
    }

    public int activeKitSize() {
        return active == null ? 0 : active.kit.size();
    }

    public Material activeIcon() {
        return active == null ? null : active.icon;
    }

    /** Whether the active setup could be saved right now. */
    public boolean activeReady() {
        return active != null && active.ready(needsTrigger(active));
    }

    // ----------------------------------------------------------------- open

    public void start(Player admin, String name) {
        if (!canOpen(admin)) {
            return;
        }
        if (!NAME.matcher(name).matches()) {
            msg().send(admin, "setup.bad-name");
            return;
        }
        if (plugin.templates().get(name) != null) {
            msg().send(admin, "setup.exists", "arena", name);
            return;
        }
        Clipboard clipboard = plugin.schematics().playerClipboard(admin);
        if (clipboard == null) {
            msg().send(admin, "setup.clipboard-empty");
            return;
        }
        if (!withinSizeLimit(admin, clipboard)) {
            return;
        }

        File dir = plugin.templates().dirFor(name);
        if (!dir.exists() && !dir.mkdirs()) {
            msg().send(admin, "setup.folder-failed");
            return;
        }
        try {
            plugin.schematics().save(clipboard, new File(dir, "arena.schem"));
        } catch (IOException e) {
            msg().send(admin, "setup.schematic-save-failed", "error", String.valueOf(e.getMessage()));
            return;
        }
        open(admin, name, dir, clipboard, false, null);
    }

    public void edit(Player admin, String name) {
        if (!canOpen(admin)) {
            return;
        }
        ArenaTemplate template = plugin.templates().get(name);
        if (template == null) {
            msg().send(admin, "arena.unknown", "arena", name);
            return;
        }
        Clipboard clipboard;
        try {
            clipboard = plugin.schematics().load(template.schematicFile());
        } catch (IOException e) {
            msg().send(admin, "arena.schematic-failed");
            plugin.getLogger().warning("Could not load schematic for '" + name + "': " + e.getMessage());
            return;
        }
        open(admin, template.name(), template.dir(), clipboard, true, template);
    }

    /**
     * An admin who is mid-run when they open the editor is taken out of their
     * arena in place — no bounce back to the lobby and no second teleport.
     * Their original snapshot is kept, so closing the wizard still returns
     * them to where they were before any of it started.
     */
    private boolean canOpen(Player admin) {
        if (active != null) {
            String holder = Bukkit.getOfflinePlayer(active.admin).getName();
            msg().send(admin, "setup.busy", "holder", String.valueOf(holder), "arena", active.name);
            return false;
        }
        String left = plugin.sessions().handOffToSetup(admin);
        if (left != null) {
            msg().send(admin, "setup.left-session", "arena", left);
        }
        return true;
    }

    private boolean withinSizeLimit(Player admin, Clipboard clipboard) {
        BlockVector3 dims = clipboard.getDimensions();
        int max = plugin.pcConfig().maxSchematicSize();
        if (dims.x() > max || dims.z() > max) {
            msg().send(admin, "setup.too-big",
                    "width", String.valueOf(dims.x()),
                    "length", String.valueOf(dims.z()),
                    "max", String.valueOf(max));
            return false;
        }
        return true;
    }

    private void open(Player admin, String name, File dir, Clipboard clipboard,
                      boolean editing, ArenaTemplate existing) {
        World world = plugin.worldService().world();
        if (world == null) {
            msg().send(admin, "setup.world-unavailable");
            return;
        }
        Slot slot = plugin.allocator().acquire(admin.getUniqueId());
        int spacing = plugin.pcConfig().gridSpacing();
        Location origin = new Location(world,
                (long) slot.gridX() * spacing, plugin.pcConfig().baseY(), (long) slot.gridZ() * spacing);
        // Big maps take seconds to paste — with FAWE that runs off-thread, and
        // either way the admin is told something is happening right now.
        msg().actionBar(admin, "setup.pasting", "arena", name);
        if (plugin.schematics().supportsAsyncEdits()) {
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return plugin.schematics().paste(clipboard, origin);
                        } catch (WorldEditException e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    })
                    .whenComplete((bounds, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                            finishOpen(admin, name, dir, clipboard, editing, existing,
                                    slot, origin, bounds, error)));
        } else {
            BoundingBox bounds = null;
            Throwable error = null;
            try {
                bounds = plugin.schematics().paste(clipboard, origin);
            } catch (WorldEditException e) {
                error = e;
            }
            finishOpen(admin, name, dir, clipboard, editing, existing, slot, origin, bounds, error);
        }
    }

    /** Main-thread tail of {@link #open} — runs once the paste finished. */
    private void finishOpen(Player admin, String name, File dir, Clipboard clipboard,
                            boolean editing, ArenaTemplate existing, Slot slot, Location origin,
                            BoundingBox bounds, Throwable error) {
        World world = plugin.worldService().world();
        if (error != null || bounds == null || world == null) {
            plugin.allocator().release(slot);
            if (admin.isOnline()) {
                msg().send(admin, "setup.paste-failed",
                        "error", String.valueOf(error != null ? error.getMessage() : "no result"));
            }
            return;
        }
        if (!admin.isOnline() || active != null) {
            // The admin left, or someone else opened the wizard while the
            // paste ran — tear the orphaned paste back down.
            slot.markDirty();
            eraseAsync(world, bounds);
            Slot orphaned = slot;
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> plugin.allocator().release(orphaned), 60L);
            if (admin.isOnline()) {
                msg().send(admin, "setup.busy", "holder",
                        String.valueOf(Bukkit.getOfflinePlayer(active.admin).getName()),
                        "arena", active.name);
            }
            return;
        }
        slot.occupy();

        SetupSession session = new SetupSession(
                admin.getUniqueId(), name, dir, clipboard, slot, origin, bounds, editing);
        if (existing != null) {
            session.copyFrom(existing);
            stampTrigger(admin, session);
        }
        this.active = session;

        // An admin who came straight out of a session already has one, and
        // it holds their real pre-practice state — never overwrite it. For
        // everyone else, capture BEFORE the teleport: captured after it, the
        // "restore" location would be the wizard's own soon-erased arena.
        if (!plugin.snapshots().has(admin.getUniqueId())) {
            plugin.snapshots().save(admin.getUniqueId(), PlayerSnapshot.capture(admin));
        }
        Location tp = new Location(world, bounds.getCenterX(), bounds.getMaxY() + 1, bounds.getCenterZ());
        admin.teleportAsync(tp).whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok) || !admin.isOnline() || active != session) {
                cleanup(session, admin.isOnline() ? admin : null, !editing);
                if (admin.isOnline()) {
                    msg().send(admin, "setup.teleport-failed");
                }
                return;
            }
            admin.setGameMode(GameMode.CREATIVE);
            admin.setAllowFlight(true);
            admin.setFlying(true);
            admin.getInventory().addItem(
                    new ItemStack(Material.STONE_BUTTON), new ItemStack(Material.STONE_PRESSURE_PLATE));
            if (editing) {
                msg().send(admin, "setup.editing", "arena", name);
                msg().send(admin, "setup.edit-steps", "arena", name);
            } else {
                msg().send(admin, "setup.started", "arena", name);
                msg().send(admin, "setup.start-steps", "arena", name);
            }
        });
    }

    /**
     * Triggers live outside the schematic — stamp them back in so the admin
     * can see (and change) where runs currently finish.
     */
    private void stampTrigger(Player admin, SetupSession session) {
        for (java.util.Iterator<me.beekrbonkr.practicecore.template.ArenaTrigger> it =
                session.triggers.iterator(); it.hasNext(); ) {
            var trigger = it.next();
            Location loc = trigger.location(session.origin);
            try {
                loc.getBlock().setBlockData(Bukkit.createBlockData(trigger.blockData()), false);
                session.triggerCandidates.add(loc);
            } catch (IllegalArgumentException e) {
                msg().send(admin, "setup.trigger-invalid", "data", trigger.blockData());
                it.remove();
            }
        }
    }

    // -------------------------------------------------------------- editing

    public void setSpawn(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Location loc = admin.getLocation();
        if (!session.bounds.contains(loc.getX(), loc.getY(), loc.getZ())) {
            msg().send(admin, "setup.spawn-outside");
            return;
        }
        session.spawnOffset = new Vector(
                loc.getX() - session.origin.getX(),
                loc.getY() - session.origin.getY(),
                loc.getZ() - session.origin.getZ());
        session.spawnYaw = loc.getYaw();
        session.spawnPitch = loc.getPitch();
        msg().send(admin, "setup.spawn-set");
    }

    /** Routed from BlockListener for the active setup admin. */
    public void handlePlace(BlockPlaceEvent event) {
        SetupSession session = active;
        Player admin = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!session.bounds.contains(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5)) {
            event.setCancelled(true);
            msg().send(admin, "setup.out-of-bounds");
            return;
        }
        Material type = block.getType();
        if (Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type)) {
            TriggerType triggerType = Tag.PRESSURE_PLATES.isTagged(type)
                    ? TriggerType.PLATE : TriggerType.BUTTON;
            session.triggers.add(new me.beekrbonkr.practicecore.template.ArenaTrigger(
                    new Vector(
                            block.getX() - session.origin.getBlockX(),
                            block.getY() - session.origin.getBlockY(),
                            block.getZ() - session.origin.getBlockZ()),
                    triggerType,
                    block.getBlockData().getAsString()));
            session.triggerCandidates.add(block.getLocation());
            msg().send(admin, "setup.trigger-added",
                    msg().ref("type", triggerType == TriggerType.PLATE
                            ? "setup.trigger-plate" : "setup.trigger-button"),
                    "count", String.valueOf(session.triggers.size()));
        } else if (!session.editing) {
            msg().actionBar(admin, "setup.not-captured-hint");
        }
    }

    /** Removes every placed finish trigger so the admin can start over. */
    public void clearTriggers(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        for (Location candidate : session.triggerCandidates) {
            candidate.getBlock().setType(Material.AIR, false);
        }
        session.triggerCandidates.clear();
        session.triggers.clear();
        msg().send(admin, "setup.triggers-cleared");
    }

    public void saveKit(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        session.kit.clear();
        ItemStack[] contents = admin.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (KIT_WARN.contains(item.getType())) {
                msg().send(admin, "setup.kit-warning", "item", item.getType().name());
            }
            session.kit.put(slot, item.clone());
        }
        msg().send(admin, "setup.kit-saved", "count", String.valueOf(session.kit.size()));
        if (plugin.menuItems().kitContainsMenuItem(session.kit)) {
            msg().send(admin, "setup.kit-saved-with-item");
        }
    }

    /** Loads the stored kit into the admin's inventory so it can be tweaked. */
    public void loadKit(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (session.kit.isEmpty()) {
            msg().send(admin, "setup.kit-empty");
            return;
        }
        admin.getInventory().clear();
        session.kit.forEach((slot, item) -> admin.getInventory().setItem(slot, item.clone()));
        msg().send(admin, "setup.kit-loaded");
    }

    public void setDisplayName(Player admin, String displayName) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        session.displayName = displayName;
        msg().send(admin, "setup.display-set", "name", displayName);
    }

    public void setIcon(Player admin, Material material) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (material == null) {
            ItemStack held = admin.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                msg().send(admin, "setup.icon-need-item");
                return;
            }
            material = held.getType();
        }
        session.icon = material;
        msg().send(admin, "setup.icon-set", "material", material.name());
    }

    public void setPermission(Player admin, String node) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (node == null || node.equalsIgnoreCase("none") || node.equalsIgnoreCase("default")) {
            session.permission = null;
            msg().send(admin, "setup.permission-cleared");
            return;
        }
        session.permission = node;
        msg().send(admin, "setup.permission-set", "node", node);
    }

    public void setRequireBlocks(Player admin, boolean require) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        session.requireBlocksForPb = require;
        msg().send(admin, require ? "setup.blocks-required" : "setup.blocks-not-required");
    }

    public void setMode(Player admin, String mode) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (plugin.modes().get(mode).isEmpty()) {
            msg().send(admin, "setup.mode-unknown",
                    "mode", mode, "modes", String.join(", ", plugin.modes().ids()));
            return;
        }
        session.mode = mode;
        msg().send(admin, "setup.mode-set", "mode", mode);
    }

    /**
     * The category is the folder the arena's folder sits in, so this is really
     * a "move it there when we save" — null or "default" moves it back out to
     * templates/, where it groups under its mode.
     */
    public void setCategory(Player admin, String category) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (category == null || category.equalsIgnoreCase("default")) {
            session.category = null;
            msg().send(admin, "setup.category-cleared");
            return;
        }
        String slug = category.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(slug).matches()) {
            msg().send(admin, "setup.category-bad-name");
            return;
        }
        session.category = slug;
        msg().send(admin, "setup.category-set", "category", session.category);
    }

    // ------------------------------------------------------------- rush maps

    private static final Set<String> RUSH_GENERATOR_TYPES =
            Set.of("iron", "gold", "diamond", "emerald");

    /** Records a rush team spawn where the admin stands. */
    public void rushTeamSpawn(Player admin, String team) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Location loc = admin.getLocation();
        if (!session.bounds.contains(loc.getX(), loc.getY(), loc.getZ())) {
            msg().send(admin, "setup.out-of-bounds");
            return;
        }
        String name = team.toUpperCase(Locale.ROOT);
        me.beekrbonkr.practicecore.rush.RushMapData.writeTeamSpawn(session.settings, name,
                new Vector(loc.getX() - session.origin.getX(),
                        loc.getY() - session.origin.getY(),
                        loc.getZ() - session.origin.getZ()),
                loc.getYaw(), loc.getPitch());
        msg().send(admin, "setup.rush-spawn-set", "team", name);
    }

    /** Records the bed the admin is looking at as a rush team's target bed. */
    public void rushBed(Player admin, String team) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Block target = admin.getTargetBlockExact(6);
        if (target == null || !(target.getBlockData() instanceof org.bukkit.block.data.type.Bed bed)) {
            msg().send(admin, "setup.rush-need-bed");
            return;
        }
        if (!session.bounds.contains(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5)) {
            msg().send(admin, "setup.out-of-bounds");
            return;
        }
        Block head = bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD
                ? target : target.getRelative(bed.getFacing());
        String name = team.toUpperCase(Locale.ROOT);
        me.beekrbonkr.practicecore.rush.RushMapData.writeTeamBed(session.settings, name,
                new Vector(head.getX() - session.origin.getBlockX(),
                        head.getY() - session.origin.getBlockY(),
                        head.getZ() - session.origin.getBlockZ()),
                bed.getFacing(), target.getType());
        msg().send(admin, "setup.rush-bed-set", "team", name);
    }

    /** Records a generator on the block the admin stands on. */
    public void rushGenerator(Player admin, String type) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (!RUSH_GENERATOR_TYPES.contains(normalized)) {
            msg().send(admin, "setup.rush-bad-generator", "type", type);
            return;
        }
        Location loc = admin.getLocation();
        if (!session.bounds.contains(loc.getX(), loc.getY(), loc.getZ())) {
            msg().send(admin, "setup.out-of-bounds");
            return;
        }
        me.beekrbonkr.practicecore.rush.RushMapData.addGenerator(session.settings, normalized,
                new Vector(loc.getBlockX() - session.origin.getBlockX(),
                        loc.getBlockY() - session.origin.getBlockY() - 1,
                        loc.getBlockZ() - session.origin.getBlockZ()));
        msg().send(admin, "setup.rush-generator-set", "type", normalized);
    }

    /** Records a shop dealer NPC spot where the admin stands. */
    public void rushDealer(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Location loc = admin.getLocation();
        if (!session.bounds.contains(loc.getX(), loc.getY(), loc.getZ())) {
            msg().send(admin, "setup.out-of-bounds");
            return;
        }
        me.beekrbonkr.practicecore.rush.RushMapData.addDealer(session.settings,
                new Vector(loc.getX() - session.origin.getX(),
                        loc.getY() - session.origin.getY(),
                        loc.getZ() - session.origin.getZ()),
                loc.getYaw());
        msg().send(admin, "setup.rush-dealer-set");
    }

    /** Wipes the whole rush layout so the admin can start over. */
    public void rushClear(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        session.settings.remove("rush");
        msg().send(admin, "setup.rush-cleared");
    }

    /**
     * Writes the arena region as it currently stands in the world back over
     * the template's schematic — this is what makes in-world editing stick.
     */
    public void capture(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        World world = plugin.worldService().world();
        // Buttons/plates are stamped in after every paste, so they must not be
        // part of the schematic — including any the admin moved away from.
        Map<Location, BlockData> stashed = new LinkedHashMap<>();
        for (Location candidate : session.triggerCandidates) {
            Block block = candidate.getBlock();
            stashed.put(candidate, block.getBlockData());
            block.setType(Material.AIR, false);
        }
        Clipboard captured;
        try {
            captured = plugin.schematics().copyRegion(world, session.bounds, session.origin);
            plugin.schematics().save(captured, new File(session.dir, "arena.schem"));
        } catch (WorldEditException | IOException e) {
            msg().send(admin, "setup.capture-failed", "error", String.valueOf(e.getMessage()));
            return;
        } finally {
            stashed.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
        }
        session.clipboard = captured;
        msg().send(admin, "setup.captured");
    }

    /** Replaces the arena with the admin's current WorldEdit clipboard. */
    public void replaceSchematic(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Clipboard clipboard = plugin.schematics().playerClipboard(admin);
        if (clipboard == null) {
            msg().send(admin, "setup.clipboard-empty");
            return;
        }
        if (!withinSizeLimit(admin, clipboard)) {
            return;
        }
        World world = plugin.worldService().world();
        msg().actionBar(admin, "setup.pasting", "arena", session.name);
        if (plugin.schematics().supportsAsyncEdits()) {
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            plugin.schematics().erase(world, session.bounds);
                            BoundingBox pasted = plugin.schematics().paste(clipboard, session.origin);
                            plugin.schematics().save(clipboard, new File(session.dir, "arena.schem"));
                            return pasted;
                        } catch (WorldEditException | IOException e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    })
                    .whenComplete((bounds, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                            finishReplace(admin, session, clipboard, bounds, error)));
        } else {
            BoundingBox bounds = null;
            Throwable error = null;
            try {
                plugin.schematics().erase(world, session.bounds);
                bounds = plugin.schematics().paste(clipboard, session.origin);
                plugin.schematics().save(clipboard, new File(session.dir, "arena.schem"));
            } catch (WorldEditException | IOException e) {
                error = e;
            }
            finishReplace(admin, session, clipboard, bounds, error);
        }
    }

    /** Main-thread tail of {@link #replaceSchematic}. */
    private void finishReplace(Player admin, SetupSession session, Clipboard clipboard,
                               BoundingBox bounds, Throwable error) {
        if (error != null || bounds == null) {
            if (admin.isOnline()) {
                msg().send(admin, "setup.schematic-replace-failed",
                        "error", String.valueOf(error != null ? error.getMessage() : "no result"));
            }
            return;
        }
        if (active != session) {
            // Wizard closed while the paste ran — cleanup erased the old
            // bounds, so the re-paste is orphaned and must go too.
            World world = plugin.worldService().world();
            if (world != null) {
                eraseAsync(world, bounds);
            }
            return;
        }
        session.clipboard = clipboard;
        session.bounds = bounds;
        session.triggerCandidates.clear();
        msg().send(admin, "setup.schematic-replaced");
        msg().send(admin, "setup.schematic-replaced-hint");
    }

    public void info(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        msg().send(admin, "setup.info-header",
                "arena", session.name, "editing", session.editing ? "Editing" : "Creating");
        line(admin, "display-name", session.displayName != null ? session.displayName : session.name);
        line(admin, "mode", session.mode != null ? session.mode : "bridging");
        line(admin, "category", session.category != null ? session.category
                : (session.mode != null ? session.mode : "bridging") + " (mode default)");
        line(admin, "spawn", describe(session.spawnOffset));
        line(admin, "triggers", session.triggers.isEmpty() ? "not set"
                : session.triggers.size() + " placed");
        line(admin, "kit", session.kit.size() + " stack(s)");
        line(admin, "icon", session.icon != null ? session.icon.name() : "auto");
        line(admin, "permission", session.permission != null ? session.permission : "arena default");
        line(admin, "pb requires blocks", String.valueOf(session.requireBlocksForPb));
        if (me.beekrbonkr.practicecore.mode.RushMode.ID.equals(session.mode)) {
            var rush = me.beekrbonkr.practicecore.rush.RushMapData.parseSettings(session.settings);
            line(admin, "rush teams", rush.playableTeams().size() + " playable of "
                    + rush.teams().size() + " set");
            line(admin, "rush generators", String.valueOf(rush.generators().size()));
            line(admin, "rush dealers", String.valueOf(rush.dealers().size()));
        }
        msg().send(admin, session.ready(needsTrigger(session))
                ? "setup.info-ready" : "setup.info-not-ready");
    }

    /** Whether this arena's mode finishes runs on a placed button/plate. */
    private boolean needsTrigger(SetupSession session) {
        String mode = session.mode != null ? session.mode
                : me.beekrbonkr.practicecore.mode.BridgingMode.ID;
        return plugin.modes().get(mode)
                .map(me.beekrbonkr.practicecore.mode.Mode::requiresTrigger).orElse(true);
    }

    private void line(Player admin, String key, String value) {
        msg().send(admin, "setup.info-line", "key", key, "value", value);
    }

    private static String describe(Vector vector) {
        return vector == null ? "not set"
                : "%.1f, %.1f, %.1f".formatted(vector.getX(), vector.getY(), vector.getZ());
    }

    // ------------------------------------------------------------ finishing

    public void save(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (session.spawnOffset == null) {
            msg().send(admin, "setup.need-spawn");
            return;
        }
        if (needsTrigger(session) && session.triggers.isEmpty()) {
            msg().send(admin, "setup.need-trigger");
            return;
        }
        if (me.beekrbonkr.practicecore.mode.RushMode.ID.equals(session.mode)
                && !me.beekrbonkr.practicecore.rush.RushMapData
                        .parseSettings(session.settings).playable()) {
            msg().send(admin, "setup.rush-need-base");
            return;
        }
        ArenaTemplate template = new ArenaTemplate(session.name, session.dir, session.folderCategory);
        template.setSpawn(session.spawnOffset, session.spawnYaw, session.spawnPitch);
        template.setTriggers(session.triggers);
        template.kit().putAll(session.kit);
        template.settings().putAll(session.settings);
        if (session.mode != null) {
            template.setMode(session.mode);
        }
        if (session.displayName != null) {
            template.setDisplayName(session.displayName);
        }
        template.setPermission(session.permission);
        template.setIcon(session.icon);
        template.setRequireBlocksForPb(session.requireBlocksForPb);
        template.setComplete(true);
        try {
            template.save();
        } catch (IOException e) {
            msg().send(admin, "setup.save-failed", "error", String.valueOf(e.getMessage()));
            return;
        }
        // The category is the folder the arena lives in, so choosing one is a
        // move. It comes after the write — a move that cannot happen then
        // leaves a saved arena in its old category rather than losing it.
        try {
            plugin.templates().moveToCategory(template, session.category);
        } catch (IOException e) {
            msg().send(admin, "setup.category-move-failed",
                    "category", session.category != null ? session.category : "default",
                    "error", String.valueOf(e.getMessage()));
        }
        plugin.templates().register(template);
        boolean editing = session.editing;
        String name = session.name;
        cleanup(session, admin, false);
        msg().send(admin, editing ? "setup.updated" : "setup.saved", "arena", name);
    }

    public void cancel(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        boolean editing = session.editing;
        cleanup(session, admin, !editing);
        msg().send(admin, editing ? "setup.edit-cancelled" : "setup.cancelled");
    }

    public void handleQuit(Player admin) {
        SetupSession session = active;
        if (session == null || !session.admin.equals(admin.getUniqueId())) {
            return;
        }
        cleanup(session, admin, !session.editing);
    }

    public void cancelAll() {
        SetupSession session = active;
        if (session == null) {
            return;
        }
        Player admin = Bukkit.getPlayer(session.admin);
        cleanup(session, admin, !session.editing);
    }

    private SetupSession requireActive(Player admin) {
        if (active == null || !active.admin.equals(admin.getUniqueId())) {
            msg().send(admin, "setup.none-active");
            return null;
        }
        return active;
    }

    private void cleanup(SetupSession session, Player admin, boolean discardTemplate) {
        if (active == session) {
            active = null;
        }
        if (session.cleaned) {
            // Quit during the open teleport runs cleanup from both the quit
            // handler and the teleport callback — the second pass must not
            // release the slot again under whoever holds it by then.
            return;
        }
        session.cleaned = true;
        World world = plugin.worldService().world();
        session.slot.markDirty();
        if (world != null) {
            eraseAsync(world, session.bounds);
            for (Entity entity : world.getNearbyEntities(session.bounds.clone().expand(4))) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }
        if (plugin.isEnabled()) {
            Slot slot = session.slot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.allocator().release(slot), 60L);
        } else {
            plugin.allocator().release(session.slot);
        }
        if (admin != null && admin.isOnline()) {
            plugin.snapshots().load(session.admin).ifPresent(snapshot -> snapshot.apply(admin, true));
        }
        plugin.snapshots().delete(session.admin);
        if (discardTemplate) {
            deleteRecursively(session.dir.toPath());
        }
    }

    /**
     * Erases a region off-thread when the WorldEdit implementation allows it
     * (FAWE) — a big rush map erase stalls the whole tick otherwise. The slot
     * is only ever released 60 ticks later, so a new paste can't race it.
     */
    private void eraseAsync(World world, BoundingBox bounds) {
        if (plugin.isEnabled() && plugin.schematics().supportsAsyncEdits()) {
            java.util.concurrent.CompletableFuture
                    .runAsync(() -> plugin.schematics().erase(world, bounds))
                    .exceptionally(e -> {
                        plugin.getLogger().severe("Async setup erase failed: " + e.getMessage());
                        return null;
                    });
        } else {
            plugin.schematics().erase(world, bounds);
        }
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

    /** Normalizes a user-supplied arena name. */
    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
