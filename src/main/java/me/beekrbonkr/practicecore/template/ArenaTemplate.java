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
 *
 * The menu grouping is not stored in arena.yml: it is the folder the arena
 * folder sits in ({@code templates/<category>/<arena>/}), so re-categorising
 * an arena is a drag-and-drop away. See {@link TemplateRegistry}.
 */
public final class ArenaTemplate {

    private final String name;
    private File dir;

    /** Format version this arena.yml was read at; see {@link Versions#ARENA}. */
    private int loadedVersion = Versions.ARENA;
    private String mode = BridgingMode.ID;
    /** Folder-derived menu grouping, or null when it sits outside one. */
    private String category;
    /**
     * The {@code category:} key of an arena.yml written before v3, kept only
     * so the registry can move the folder into place once and then drop it.
     */
    private String legacyCategory;
    private String displayName;
    private boolean complete;
    private Vector spawnOffset;
    private float spawnYaw;
    private float spawnPitch;
    /** Finish triggers; empty for modes that do not use one. */
    private final java.util.List<ArenaTrigger> triggers = new java.util.ArrayList<>();
    private boolean requireBlocksForPb;
    /** Explicit permission node, or null to fall back to the config policy. */
    private String permission;
    /** Menu icon, or null to derive one from the kit. */
    private Material icon;
    private final Map<Integer, ItemStack> kit = new HashMap<>();
    /**
     * Free-form per-mode configuration (the arena.yml {@code settings:}
     * section). The template only carries it; each mode parses its own keys
     * via {@link #settingsSection()}.
     */
    private final Map<String, Object> settings = new java.util.LinkedHashMap<>();

    /** An arena outside any category folder. */
    public ArenaTemplate(String name, File dir) {
        this(name, dir, null);
    }

    public ArenaTemplate(String name, File dir, String category) {
        this.name = name;
        this.dir = dir;
        this.category = normalizeCategory(category);
        this.displayName = name;
    }

    /**
     * @param category the folder the arena folder sits in, or null when it
     *                 sits directly in {@code templates/}
     */
    public static ArenaTemplate load(File dir, String category) {
        ArenaTemplate template = new ArenaTemplate(dir.getName(), dir, category);
        // Explicit load, not loadConfiguration: the latter swallows a YAML
        // syntax error into an empty config, which would then be "upgraded"
        // over the admin's recoverable file with near-empty defaults.
        YamlConfiguration yml = new YamlConfiguration();
        File file = new File(dir, "arena.yml");
        if (file.exists()) {
            try {
                yml.load(file);
            } catch (java.io.IOException
                     | org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IllegalStateException("arena.yml is unreadable: " + e.getMessage(), e);
            }
        }
        template.loadedVersion = yml.getInt(Versions.KEY, 0);
        // Read before migrate() strips it: the registry needs the old value to
        // move the folder into the matching category folder exactly once.
        template.legacyCategory = normalizeCategory(yml.getString("category"));
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
        for (Map<?, ?> entry : yml.getMapList("triggers")) {
            ArenaTrigger trigger = readTrigger(entry);
            if (trigger != null) {
                template.triggers.add(trigger);
            }
        }
        if (yml.isConfigurationSection("settings")) {
            template.settings.putAll(deepMap(yml.getConfigurationSection("settings")));
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
        // v0 → v1 was the first versioned layout; nothing moved.
        if (from < 2 && yml.isConfigurationSection("trigger")) {
            // v2: the single `trigger:` section became the `triggers:` list.
            ConfigurationSection t = yml.getConfigurationSection("trigger");
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("x", t.getInt("x"));
            entry.put("y", t.getInt("y"));
            entry.put("z", t.getInt("z"));
            entry.put("type", t.getString("type", "BUTTON"));
            entry.put("block-data", t.getString("block-data", "minecraft:stone_button"));
            yml.set("triggers", java.util.List.of(entry));
            yml.set("trigger", null);
        }
        if (from < 3) {
            // v3: the menu grouping became the arena's parent folder. The key
            // is dropped here; TemplateRegistry has already read it and moves
            // the folder to match.
            yml.set("category", null);
        }
    }

    private static ArenaTrigger readTrigger(Map<?, ?> entry) {
        Object x = entry.get("x");
        Object y = entry.get("y");
        Object z = entry.get("z");
        if (!(x instanceof Number nx) || !(y instanceof Number ny) || !(z instanceof Number nz)) {
            return null;
        }
        TriggerType type;
        try {
            type = TriggerType.valueOf(String.valueOf(entry.get("type")));
        } catch (IllegalArgumentException e) {
            type = TriggerType.BUTTON;
        }
        Object data = entry.get("block-data");
        return new ArenaTrigger(new Vector(nx.intValue(), ny.intValue(), nz.intValue()), type,
                data != null ? String.valueOf(data) : "minecraft:stone_button");
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
        if (!triggers.isEmpty()) {
            java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
            for (ArenaTrigger trigger : triggers) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("x", trigger.offset().getBlockX());
                entry.put("y", trigger.offset().getBlockY());
                entry.put("z", trigger.offset().getBlockZ());
                entry.put("type", trigger.type().name());
                entry.put("block-data", trigger.blockData());
                list.add(entry);
            }
            yml.set("triggers", list);
        }
        if (!settings.isEmpty()) {
            yml.createSection("settings", settings);
        }
        // Sorted so a hand-edited arena.yml keeps a stable, readable order.
        for (Map.Entry<Integer, ItemStack> entry : new TreeMap<>(kit).entrySet()) {
            yml.set("kit." + entry.getKey(), entry.getValue());
        }
        yml.save(new File(dir, "arena.yml"));
    }

    /** Nested sections flattened to plain maps, so they round-trip through save(). */
    private static Map<String, Object> deepMap(ConfigurationSection section) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, value instanceof ConfigurationSection nested ? deepMap(nested) : value);
        }
        return map;
    }

    public Location spawnLocation(Location origin) {
        return new Location(origin.getWorld(),
                origin.getX() + spawnOffset.getX(),
                origin.getY() + spawnOffset.getY(),
                origin.getZ() + spawnOffset.getZ(),
                spawnYaw, spawnPitch);
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

    /**
     * The folder this arena is filed under, or null when its folder sits
     * directly in {@code templates/} and it groups under its mode instead.
     */
    public String category() {
        return category;
    }

    /**
     * The {@code category:} of a pre-v3 arena.yml, or null. Only the registry
     * uses it, to move the folder into place on the first load after upgrade.
     */
    public String legacyCategory() {
        return legacyCategory;
    }

    /** Points the template at the folder it was just moved to. */
    void relocate(File newDir, String newCategory) {
        this.dir = newDir;
        this.category = normalizeCategory(newCategory);
        this.legacyCategory = null;
    }

    /** The category this arena is listed under — never null. */
    public String effectiveCategory() {
        return category != null ? category : mode;
    }

    /** Category ids are lowercase, like folder lookups; blank means none. */
    static String normalizeCategory(String category) {
        return category == null || category.isBlank()
                ? null : category.trim().toLowerCase(java.util.Locale.ROOT);
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

    public void setTriggers(java.util.List<ArenaTrigger> newTriggers) {
        triggers.clear();
        triggers.addAll(newTriggers);
    }

    /** All finish triggers; touching any of them ends a run. */
    public java.util.List<ArenaTrigger> triggers() {
        return java.util.List.copyOf(triggers);
    }

    public boolean hasTriggers() {
        return !triggers.isEmpty();
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

    /** The raw per-mode settings; mutate then {@link #save()} to persist. */
    public Map<String, Object> settings() {
        return settings;
    }

    /**
     * The settings as a readable ConfigurationSection, so modes can parse
     * their keys with the familiar typed getters and defaults.
     */
    public ConfigurationSection settingsSection() {
        org.bukkit.configuration.MemoryConfiguration holder =
                new org.bukkit.configuration.MemoryConfiguration();
        return holder.createSection("settings", settings);
    }
}
