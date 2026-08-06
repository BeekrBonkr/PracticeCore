package me.beekrbonkr.practicecore.template;

import me.beekrbonkr.practicecore.mode.BridgingMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * One configured arena: a schematic plus arena.yml in its own folder.
 * Spawn and finish-trigger positions are stored as offsets relative to the
 * paste origin, so the arena can be pasted anywhere on the grid.
 */
public final class ArenaTemplate {

    private final String name;
    private final File dir;

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
    private final Map<Integer, ItemStack> kit = new HashMap<>();

    public ArenaTemplate(String name, File dir) {
        this.name = name;
        this.dir = dir;
        this.displayName = name;
    }

    public static ArenaTemplate load(File dir) {
        ArenaTemplate template = new ArenaTemplate(dir.getName(), dir);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(dir, "arena.yml"));
        template.mode = yml.getString("mode", BridgingMode.ID);
        template.displayName = yml.getString("display-name", template.name);
        template.complete = yml.getBoolean("complete", false);
        template.requireBlocksForPb = yml.getBoolean("require-blocks-for-pb", false);
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

    public void save() throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("mode", mode);
        yml.set("display-name", displayName);
        yml.set("complete", complete);
        yml.set("require-blocks-for-pb", requireBlocksForPb);
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
        for (Map.Entry<Integer, ItemStack> entry : kit.entrySet()) {
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

    public String mode() {
        return mode;
    }

    public String displayName() {
        return displayName;
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

    public Map<Integer, ItemStack> kit() {
        return kit;
    }
}
