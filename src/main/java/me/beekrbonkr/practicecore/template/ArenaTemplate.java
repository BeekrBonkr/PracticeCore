package me.beekrbonkr.practicecore.template;

import me.beekrbonkr.practicecore.config.Versions;
import me.beekrbonkr.practicecore.mode.BridgingMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * One configured arena: a schematic plus arena.yml in its own folder.
 * Spawn and finish-trigger positions are stored as offsets relative to the
 * paste origin, so the arena can be pasted anywhere on the grid.
 */
public final class ArenaTemplate {

    private final String name;
    private final File dir;

    /** Format version this arena.yml was read at; see {@link Versions#ARENA}. */
    private int loadedVersion = Versions.ARENA;
    private String mode = BridgingMode.ID;
    private String displayName;
    private boolean complete;
    private Vector spawnOffset;
    private float spawnYaw;
    private float spawnPitch;
    private Vector triggerOffset;
    private TriggerType triggerType;
    private String triggerBlockData;
    private boolean requireBlocksForPb;
    /** Explicit permission node, or null to fall back to the config policy. */
    private String permission;
    /** Menu icon, or null to derive one from the kit. */
    private Material icon;
    private final Map<Integer, ItemStack> kit = new HashMap<>();

    public ArenaTemplate(String name, File dir) {
        this.name = name;
        this.dir = dir;
        this.displayName = name;
    }

    public static ArenaTemplate load(File dir) {
        ArenaTemplate template = new ArenaTemplate(dir.getName(), dir);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(dir, "arena.yml"));
        template.loadedVersion = yml.getInt(Versions.KEY, 0);
        migrate(yml, template.loadedVersion);
        template.mode = yml.getString("mode", BridgingMode.ID);
        template.displayName = yml.getString("display-name", template.name);
        template.complete = yml.getBoolean("complete", false);
        template.requireBlocksForPb = yml.getBoolean("require-blocks-for-pb", false);
        String node = yml.getString("permission");
        template.permission = node == null || node.isBlank() ? null : node.trim();
        String iconName = yml.getString("icon");
        if (iconName != null && !iconName.isBlank()) {
            Material parsed = Material.matchMaterial(iconName);
            template.icon = parsed != null && parsed.isItem() ? parsed : null;
        }
        if (yml.isConfigurationSection("spawn")) {
            ConfigurationSection s = yml.getConfigurationSection("spawn");
            template.spawnOffset = new Vector(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            template.spawnYaw = (float) s.getDouble("yaw");
            template.spawnPitch = (float) s.getDouble("pitch");
        }
        if (yml.isConfigurationSection("trigger")) {
            ConfigurationSection t = yml.getConfigurationSection("trigger");
            template.triggerOffset = new Vector(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            try {
                template.triggerType = TriggerType.valueOf(t.getString("type", "BUTTON"));
            } catch (IllegalArgumentException e) {
                template.triggerType = TriggerType.BUTTON;
            }
            template.triggerBlockData = t.getString("block-data", "minecraft:stone_button");
        }
        if (yml.isConfigurationSection("kit")) {
            ConfigurationSection k = yml.getConfigurationSection("kit");
            for (String key : k.getKeys(false)) {
                ItemStack item = k.getItemStack(key);
                if (item != null) {
                    try {
                        template.kit.put(Integer.parseInt(key), item);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return template;
    }

    /**
     * Reshapes an older arena.yml in memory before its fields are read. Each
     * step must be safe to apply to a file several versions behind.
     */
    private static void migrate(YamlConfiguration yml, int from) {
        // v0 → v1 is the first versioned layout; nothing moved. Later steps:
        //   if (from < 2) { yml.set("trigger.type", …); yml.set("old", null); }
    }

    public void save() throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set(Versions.KEY, Versions.ARENA);
        loadedVersion = Versions.ARENA;
        yml.set("mode", mode);
        yml.set("display-name", displayName);
        yml.set("complete", complete);
        yml.set("require-blocks-for-pb", requireBlocksForPb);
        if (permission != null) {
            yml.set("permission", permission);
        }
        if (icon != null) {
            yml.set("icon", icon.name());
        }
        if (spawnOffset != null) {
            yml.set("spawn.x", spawnOffset.getX());
            yml.set("spawn.y", spawnOffset.getY());
            yml.set("spawn.z", spawnOffset.getZ());
            yml.set("spawn.yaw", spawnYaw);
            yml.set("spawn.pitch", spawnPitch);
        }
        if (triggerOffset != null) {
            yml.set("trigger.x", triggerOffset.getBlockX());
            yml.set("trigger.y", triggerOffset.getBlockY());
            yml.set("trigger.z", triggerOffset.getBlockZ());
            yml.set("trigger.type", triggerType.name());
            yml.set("trigger.block-data", triggerBlockData);
        }
        // Sorted so a hand-edited arena.yml keeps a stable, readable order.
        for (Map.Entry<Integer, ItemStack> entry : new TreeMap<>(kit).entrySet()) {
            yml.set("kit." + entry.getKey(), entry.getValue());
        }
        yml.save(new File(dir, "arena.yml"));
    }

    public Location spawnLocation(Location origin) {
        return new Location(origin.getWorld(),
                origin.getX() + spawnOffset.getX(),
                origin.getY() + spawnOffset.getY(),
                origin.getZ() + spawnOffset.getZ(),
                spawnYaw, spawnPitch);
    }

    public Location triggerLocation(Location origin) {
        return new Location(origin.getWorld(),
                origin.getBlockX() + triggerOffset.getBlockX(),
                origin.getBlockY() + triggerOffset.getBlockY(),
                origin.getBlockZ() + triggerOffset.getBlockZ());
    }

    public File schematicFile() {
        return new File(dir, "arena.schem");
    }

    public String name() {
        return name;
    }

    public File dir() {
        return dir;
    }

    public int loadedVersion() {
        return loadedVersion;
    }

    /** True when this arena.yml predates {@link Versions#ARENA}. */
    public boolean needsUpgrade() {
        return loadedVersion < Versions.ARENA;
    }

    /** True when it came from a newer build than this one. */
    public boolean fromFuture() {
        return loadedVersion > Versions.ARENA;
    }

    public String mode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public void setSpawn(Vector offset, float yaw, float pitch) {
        this.spawnOffset = offset;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
    }

    public Vector spawnOffset() {
        return spawnOffset;
    }

    public float spawnYaw() {
        return spawnYaw;
    }

    public float spawnPitch() {
        return spawnPitch;
    }

    public void setTrigger(Vector offset, TriggerType type, String blockData) {
        this.triggerOffset = offset;
        this.triggerType = type;
        this.triggerBlockData = blockData;
    }

    public Vector triggerOffset() {
        return triggerOffset;
    }

    public TriggerType triggerType() {
        return triggerType;
    }

    public String triggerBlockData() {
        return triggerBlockData;
    }

    public boolean requireBlocksForPb() {
        return requireBlocksForPb;
    }

    public void setRequireBlocksForPb(boolean requireBlocksForPb) {
        this.requireBlocksForPb = requireBlocksForPb;
    }

    public String permission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null || permission.isBlank() ? null : permission.trim();
    }

    public Material icon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    /** Configured icon, else the largest kit stack's material, else a fallback. */
    public Material effectiveIcon() {
        if (icon != null) {
            return icon;
        }
        Material best = null;
        int bestAmount = -1;
        for (ItemStack item : kit.values()) {
            if (item != null && item.getType().isItem() && item.getAmount() > bestAmount) {
                best = item.getType();
                bestAmount = item.getAmount();
            }
        }
        return best != null ? best : Material.GRASS_BLOCK;
    }

    public Map<Integer, ItemStack> kit() {
        return kit;
    }
}
