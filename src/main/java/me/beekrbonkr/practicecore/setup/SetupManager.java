package me.beekrbonkr.practicecore.setup;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.template.TriggerType;
import me.beekrbonkr.practicecore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The admin arena-configuration wizard. One wizard may run at a time.
 * Flow: //copy an arena build anywhere → /practice setup start <name>
 * (clipboard is read directly — no fragile "wait for //paste") → set spawn →
 * place the finish button/plate → optional kit → save.
 */
public final class SetupManager {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<Material> KIT_WARN = Set.of(
            Material.ENDER_PEARL, Material.CHORUS_FRUIT, Material.ELYTRA, Material.TNT,
            Material.LAVA_BUCKET, Material.WATER_BUCKET, Material.TRIDENT,
            Material.WIND_CHARGE, Material.MACE, Material.FIREWORK_ROCKET);

    private final PracticeCorePlugin plugin;
    private SetupSession active;

    public SetupManager(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAdmin(UUID player) {
        return active != null && active.admin.equals(player);
    }

    public void start(Player admin, String name) {
        if (active != null) {
            String holder = Bukkit.getOfflinePlayer(active.admin).getName();
            Msg.error(admin, "Setup already in progress by " + holder + ".");
            return;
        }
        if (plugin.sessions().get(admin.getUniqueId()) != null) {
            Msg.error(admin, "Leave practice before configuring arenas.");
            return;
        }
        if (!NAME.matcher(name).matches()) {
            Msg.error(admin, "Template names must match [a-z0-9_-], max 32 chars.");
            return;
        }
        if (plugin.templates().get(name) != null) {
            Msg.error(admin, "Template '" + name + "' already exists. Delete its folder first to redo it.");
            return;
        }
        Clipboard clipboard = plugin.schematics().playerClipboard(admin);
        if (clipboard == null) {
            Msg.error(admin, "Your WorldEdit clipboard is empty. Select your arena build and run //copy first.");
            return;
        }
        BlockVector3 dims = clipboard.getDimensions();
        int max = plugin.pcConfig().maxSchematicSize();
        if (dims.x() > max || dims.z() > max) {
            Msg.error(admin, "Schematic is " + dims.x() + "×" + dims.z()
                    + " blocks; the limit is " + max + " so neighboring arenas can never overlap.");
            return;
        }

        File dir = plugin.templates().dirFor(name);
        if (!dir.exists() && !dir.mkdirs()) {
            Msg.error(admin, "Could not create the template folder.");
            return;
        }
        try {
            plugin.schematics().save(clipboard, new File(dir, "arena.schem"));
        } catch (IOException e) {
            Msg.error(admin, "Could not save the schematic: " + e.getMessage());
            return;
        }

        Slot slot = plugin.allocator().acquire(admin.getUniqueId());
        World world = plugin.worldService().world();
        int spacing = plugin.pcConfig().gridSpacing();
        Location origin = new Location(world,
                (long) slot.gridX() * spacing, plugin.pcConfig().baseY(), (long) slot.gridZ() * spacing);
        org.bukkit.util.BoundingBox bounds;
        try {
            bounds = plugin.schematics().paste(clipboard, origin);
        } catch (WorldEditException e) {
            plugin.allocator().release(slot);
            Msg.error(admin, "Failed to paste the arena: " + e.getMessage());
            return;
        }
        slot.occupy();

        SetupSession session = new SetupSession(admin.getUniqueId(), name, dir, clipboard, slot, origin, bounds);
        this.active = session;

        Location tp = new Location(world, bounds.getCenterX(), bounds.getMaxY() + 1, bounds.getCenterZ());
        admin.teleportAsync(tp).whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok) || !admin.isOnline() || active != session) {
                abort(session, admin.isOnline() ? admin : null);
                if (admin.isOnline()) {
                    Msg.error(admin, "Could not teleport you to the setup area.");
                }
                return;
            }
            plugin.snapshots().save(admin.getUniqueId(), PlayerSnapshot.capture(admin));
            admin.setGameMode(GameMode.CREATIVE);
            admin.setAllowFlight(true);
            admin.setFlying(true);
            admin.getInventory().addItem(
                    new ItemStack(Material.STONE_BUTTON), new ItemStack(Material.STONE_PRESSURE_PLATE));
            Msg.success(admin, "Configuring '" + name + "'. Steps:");
            Msg.info(admin, "1. Stand at the player spawn and run /practice setup spawn");
            Msg.info(admin, "2. Place the given button or pressure plate at the finish");
            Msg.info(admin, "3. Optional: arrange your inventory, then /practice setup kit");
            Msg.info(admin, "4. /practice setup save  (or /practice setup cancel)");
        });
    }

    public void setSpawn(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        Location loc = admin.getLocation();
        if (!session.bounds.contains(loc.getX(), loc.getY(), loc.getZ())) {
            Msg.error(admin, "Stand inside the arena to set its spawn.");
            return;
        }
        session.spawnOffset = new Vector(
                loc.getX() - session.origin.getX(),
                loc.getY() - session.origin.getY(),
                loc.getZ() - session.origin.getZ());
        session.spawnYaw = loc.getYaw();
        session.spawnPitch = loc.getPitch();
        Msg.success(admin, "Spawn point set (facing preserved).");
    }

    /** Routed from BlockListener for the active setup admin. */
    public void handlePlace(BlockPlaceEvent event) {
        SetupSession session = active;
        Player admin = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!session.bounds.contains(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5)) {
            event.setCancelled(true);
            Msg.error(admin, "Keep setup changes inside the arena bounds.");
            return;
        }
        Material type = block.getType();
        if (Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type)) {
            session.triggerOffset = new Vector(
                    block.getX() - session.origin.getBlockX(),
                    block.getY() - session.origin.getBlockY(),
                    block.getZ() - session.origin.getBlockZ());
            session.triggerType = Tag.PRESSURE_PLATES.isTagged(type) ? TriggerType.PLATE : TriggerType.BUTTON;
            session.triggerBlockData = block.getBlockData().getAsString();
            Msg.success(admin, "Finish trigger set to the " +
                    (session.triggerType == TriggerType.PLATE ? "pressure plate" : "button")
                    + " you just placed. Place another to move it.");
        } else {
            admin.sendActionBar(Component.text(
                    "Note: blocks placed during setup are not saved into the schematic",
                    NamedTextColor.YELLOW));
        }
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
                Msg.error(admin, "Warning: " + item.getType() + " in the kit can skip or break runs "
                        + "(kept anyway — remove it and rerun /practice setup kit if unintended).");
            }
            session.kit.put(slot, item.clone());
        }
        Msg.success(admin, "Kit saved (" + session.kit.size() + " stack(s)).");
    }

    public void save(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        if (session.spawnOffset == null) {
            Msg.error(admin, "Set the spawn first: /practice setup spawn");
            return;
        }
        if (session.triggerOffset == null) {
            Msg.error(admin, "Place the finish button or pressure plate first.");
            return;
        }
        ArenaTemplate template = new ArenaTemplate(session.name, session.dir);
        template.setSpawn(session.spawnOffset, session.spawnYaw, session.spawnPitch);
        template.setTrigger(session.triggerOffset, session.triggerType, session.triggerBlockData);
        template.kit().putAll(session.kit);
        template.setComplete(true);
        try {
            template.save();
        } catch (IOException e) {
            Msg.error(admin, "Could not write arena.yml: " + e.getMessage());
            return;
        }
        plugin.templates().register(template);
        cleanup(session, admin, false);
        Msg.success(admin, "Template '" + session.name + "' saved and ready — /practice join " + session.name);
    }

    public void cancel(Player admin) {
        SetupSession session = requireActive(admin);
        if (session == null) {
            return;
        }
        cleanup(session, admin, true);
        Msg.info(admin, "Setup cancelled; nothing was saved.");
    }

    public void handleQuit(Player admin) {
        SetupSession session = active;
        if (session == null || !session.admin.equals(admin.getUniqueId())) {
            return;
        }
        cleanup(session, admin, true);
    }

    public void cancelAll() {
        SetupSession session = active;
        if (session == null) {
            return;
        }
        Player admin = Bukkit.getPlayer(session.admin);
        cleanup(session, admin, true);
    }

    private SetupSession requireActive(Player admin) {
        if (active == null || !active.admin.equals(admin.getUniqueId())) {
            Msg.error(admin, "No setup in progress. Start with /practice setup start <name>.");
            return null;
        }
        return active;
    }

    private void abort(SetupSession session, Player admin) {
        cleanup(session, admin, true);
    }

    private void cleanup(SetupSession session, Player admin, boolean discardTemplate) {
        if (active == session) {
            active = null;
        }
        World world = plugin.worldService().world();
        session.slot.markDirty();
        plugin.schematics().erase(world, session.bounds);
        for (Entity entity : world.getNearbyEntities(session.bounds.clone().expand(4))) {
            if (!(entity instanceof Player)) {
                entity.remove();
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
}
