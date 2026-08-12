package me.beekrbonkr.practicecore;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/** Immutable snapshot of config.yml. Replaced wholesale on /practice reload. */
public final class PCConfig {

    public enum TimerStartMode { MOVE, FIRST_BLOCK }

    /**
     * What happens to a player with no explicit setting for an arena's node.
     * An explicit grant or denial always wins over either.
     */
    public enum AccessMode { DENY, ALLOW }

    private final String worldName;
    private final int gridSpacing;
    private final int baseY;
    private final int maxSchematicSize;
    private final TimerStartMode timerStartMode;
    private final int scoreboardTicks;
    private final int failYOffset;
    private final boolean allowPearls;
    private final boolean allowBuckets;
    private final boolean validateInventory;
    private final int validateInventoryTicks;

    private final boolean speedometerEnabled;
    private final int speedometerTicks;

    private final int rushIronIntervalTicks;
    private final int rushGoldIntervalTicks;
    private final int rushGeneratorItemCap;
    private final boolean rushBaseGeneratorsDefault;

    private final boolean bundledTemplateEnabled;
    private final String bundledTemplateName;

    private final boolean generatedArenasEnabled;
    private final java.util.Map<String, String> generatedArenaNames;

    private final AccessMode arenaAccessMode;
    private final String arenaPermissionPrefix;
    private final boolean hideLockedArenas;

    private final String defaultArenaName;
    private final boolean defaultArenaOnServerJoin;
    private final boolean defaultArenaOnWorldEnter;
    private final boolean defaultArenaOnBareJoin;

    private final String leaveServer;
    private final String leaveFallbackWorld;

    private final boolean menuItemEnabled;
    private final Material menuItemMaterial;
    private final String menuItemName;
    private final List<String> menuItemLore;
    private final int menuItemSlot;
    private final boolean menuItemForceInKit;

    private final int leaderboardSize;
    private final boolean leaderboardHeads;

    private final boolean finishTitle;
    private final boolean sounds;
    private final boolean broadcastRecords;
    private final boolean broadcastPbs;

    public PCConfig(FileConfiguration cfg) {
        this.worldName = cfg.getString("world.name", "practice_world");
        this.gridSpacing = cfg.getInt("grid.spacing", 1000);
        this.baseY = cfg.getInt("grid.base-y", 100);
        this.maxSchematicSize = cfg.getInt("grid.max-schematic-size", 800);
        TimerStartMode mode;
        try {
            mode = TimerStartMode.valueOf(cfg.getString("timer.start-mode", "MOVE").toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = TimerStartMode.MOVE;
        }
        this.timerStartMode = mode;
        this.scoreboardTicks = Math.max(1, cfg.getInt("scoreboard.update-ticks", 2));
        this.failYOffset = cfg.getInt("session.fail-y-offset", 0);
        this.allowPearls = cfg.getBoolean("session.allow-pearls", false);
        this.allowBuckets = cfg.getBoolean("session.allow-buckets", false);
        this.validateInventory = cfg.getBoolean("session.validate-inventory", true);
        this.validateInventoryTicks = Math.max(1, cfg.getInt("session.validate-inventory-ticks", 20));

        this.speedometerEnabled = cfg.getBoolean("speedometer.enabled", true);
        this.speedometerTicks = Math.max(1, cfg.getInt("speedometer.update-ticks", 5));

        this.rushIronIntervalTicks = Math.max(1, cfg.getInt("rush.iron-interval-ticks", 25));
        this.rushGoldIntervalTicks = Math.max(1, cfg.getInt("rush.gold-interval-ticks", 120));
        this.rushGeneratorItemCap = Math.max(1, cfg.getInt("rush.generator-item-cap", 48));
        this.rushBaseGeneratorsDefault = cfg.getBoolean("rush.base-generators-default", true);

        this.bundledTemplateEnabled = cfg.getBoolean("bundled-template.enabled", true);
        this.bundledTemplateName = cfg.getString("bundled-template.name", "turtle")
                .toLowerCase(Locale.ROOT);

        this.generatedArenasEnabled = cfg.getBoolean("generated-arenas.enabled", true);
        this.generatedArenaNames = java.util.Map.of(
                "bedbreak", cfg.getString("generated-arenas.bedbreak", "bedbreak")
                        .trim().toLowerCase(Locale.ROOT),
                "bedbreak-horizontal",
                cfg.getString("generated-arenas.bedbreak-horizontal", "bedbreak-horizontal")
                        .trim().toLowerCase(Locale.ROOT));

        AccessMode access;
        try {
            access = AccessMode.valueOf(cfg.getString("arenas.access-mode", "DENY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            access = AccessMode.DENY;
        }
        this.arenaAccessMode = access;
        this.arenaPermissionPrefix = cfg.getString("arenas.permission-prefix", "practicecore.arena.");
        this.hideLockedArenas = cfg.getBoolean("arenas.hide-locked", false);

        this.defaultArenaName = cfg.getString("default-arena.name", "").trim().toLowerCase(Locale.ROOT);
        this.defaultArenaOnServerJoin = cfg.getBoolean("default-arena.on-server-join", false);
        this.defaultArenaOnWorldEnter = cfg.getBoolean("default-arena.on-world-enter", true);
        this.defaultArenaOnBareJoin = cfg.getBoolean("default-arena.on-bare-join", true);

        this.leaveServer = cfg.getString("leave.server", "").trim();
        this.leaveFallbackWorld = cfg.getString("leave.fallback-world", "").trim();

        this.menuItemEnabled = cfg.getBoolean("menu-item.enabled", true);
        this.menuItemMaterial = material(cfg.getString("menu-item.material"), Material.NETHER_STAR);
        this.menuItemName = cfg.getString("menu-item.name", "<gold><bold>Practice Menu</bold></gold>");
        List<String> lore = cfg.getStringList("menu-item.lore");
        this.menuItemLore = lore.isEmpty() ? List.of("<gray>Right-click to open.") : List.copyOf(lore);
        this.menuItemSlot = Math.clamp(cfg.getInt("menu-item.slot", 8), 0, 8);
        this.menuItemForceInKit = cfg.getBoolean("menu-item.force-in-kit", false);

        this.leaderboardSize = Math.clamp(cfg.getInt("leaderboard.size", 10), 1, 45);
        this.leaderboardHeads = cfg.getBoolean("leaderboard.player-heads", true);

        this.finishTitle = cfg.getBoolean("effects.finish-title", true);
        this.sounds = cfg.getBoolean("effects.sounds", true);
        this.broadcastRecords = cfg.getBoolean("effects.broadcast-records", true);
        this.broadcastPbs = cfg.getBoolean("effects.broadcast-pbs", true);
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(name);
        return parsed != null && parsed.isItem() ? parsed : fallback;
    }

    public String worldName() {
        return worldName;
    }

    public int gridSpacing() {
        return gridSpacing;
    }

    public int baseY() {
        return baseY;
    }

    public int maxSchematicSize() {
        return maxSchematicSize;
    }

    public TimerStartMode timerStartMode() {
        return timerStartMode;
    }

    public int scoreboardTicks() {
        return scoreboardTicks;
    }

    public int failYOffset() {
        return failYOffset;
    }

    public boolean allowPearls() {
        return allowPearls;
    }

    public boolean allowBuckets() {
        return allowBuckets;
    }

    public boolean validateInventory() {
        return validateInventory;
    }

    public int validateInventoryTicks() {
        return validateInventoryTicks;
    }

    public boolean speedometerEnabled() {
        return speedometerEnabled;
    }

    public int speedometerTicks() {
        return speedometerTicks;
    }

    public int rushIronIntervalTicks() {
        return rushIronIntervalTicks;
    }

    public int rushGoldIntervalTicks() {
        return rushGoldIntervalTicks;
    }

    /** Items lying near a generator before it pauses dropping more. */
    public int rushGeneratorItemCap() {
        return rushGeneratorItemCap;
    }

    /** Whether base generators run for players with no saved preference. */
    public boolean rushBaseGeneratorsDefault() {
        return rushBaseGeneratorsDefault;
    }

    public boolean bundledTemplateEnabled() {
        return bundledTemplateEnabled;
    }

    public String bundledTemplateName() {
        return bundledTemplateName;
    }

    public boolean generatedArenasEnabled() {
        return generatedArenasEnabled;
    }

    /** Arena name to generate for a mode id, or empty to skip that one. */
    public String generatedArenaName(String kind) {
        return generatedArenaNames.getOrDefault(kind, "");
    }

    public AccessMode arenaAccessMode() {
        return arenaAccessMode;
    }

    /** Empty when no default arena is configured. */
    public String defaultArenaName() {
        return defaultArenaName;
    }

    public boolean defaultArenaOnServerJoin() {
        return defaultArenaOnServerJoin;
    }

    public boolean defaultArenaOnWorldEnter() {
        return defaultArenaOnWorldEnter;
    }

    public boolean defaultArenaOnBareJoin() {
        return defaultArenaOnBareJoin;
    }

    public String arenaPermissionPrefix() {
        return arenaPermissionPrefix;
    }

    public boolean hideLockedArenas() {
        return hideLockedArenas;
    }

    /** Empty when leaving should not hand the player to a proxy server. */
    public String leaveServer() {
        return leaveServer;
    }

    public String leaveFallbackWorld() {
        return leaveFallbackWorld;
    }

    public boolean menuItemEnabled() {
        return menuItemEnabled;
    }

    public Material menuItemMaterial() {
        return menuItemMaterial;
    }

    public String menuItemName() {
        return menuItemName;
    }

    public List<String> menuItemLore() {
        return menuItemLore;
    }

    public int menuItemSlot() {
        return menuItemSlot;
    }

    public boolean menuItemForceInKit() {
        return menuItemForceInKit;
    }

    public int leaderboardSize() {
        return leaderboardSize;
    }

    public boolean leaderboardHeads() {
        return leaderboardHeads;
    }

    public boolean finishTitle() {
        return finishTitle;
    }

    public boolean sounds() {
        return sounds;
    }

    public boolean broadcastRecords() {
        return broadcastRecords;
    }

    public boolean broadcastPbs() {
        return broadcastPbs;
    }
}
