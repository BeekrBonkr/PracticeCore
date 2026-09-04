package me.beekrbonkr.practicecore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of config.yml. Replaced wholesale on /practice reload —
 * every reader goes through {@code plugin.pcConfig()} and therefore picks up
 * the new snapshot on its next call, which is what lets almost every setting
 * change under a running session.
 */
public final class PCConfig {

    public enum TimerStartMode { MOVE, FIRST_BLOCK }

    /**
     * What happens to a player with no explicit setting for an arena's node.
     * An explicit grant or denial always wins over either.
     */
    public enum AccessMode { DENY, ALLOW }

    private final PracticeCorePlugin plugin;

    private final String worldName;
    private final Map<String, Boolean> worldGameRuleFlags;
    private final Map<String, Integer> worldGameRuleNumbers;
    private final String worldDifficulty;
    private final long worldTime;

    private final int gridSpacing;
    private final int baseY;
    private final int maxSchematicSize;
    private final int slotReleaseDelayTicks;

    private final TimerStartMode timerStartMode;
    private final int scoreboardTicks;
    private final String serverIp;
    private final boolean spectatorsSeePlayerBoard;

    private final int failYOffset;
    private final boolean allowPearls;
    private final boolean allowBuckets;
    private final boolean validateInventory;
    private final int validateInventoryTicks;
    private final List<Long> kitVerifyTicks;
    private final double triggerReach;
    private final boolean blockPistons;
    private final boolean wipeContainers;
    private final boolean blockEnderChests;
    private final boolean blockCrafting;
    private final boolean blockVehicles;
    private final boolean blockElytra;
    private final boolean freezeHunger;
    private final boolean blockItemDrops;

    private final boolean speedometerEnabled;
    private final int speedometerTicks;
    private final double speedometerSmoothing;
    private final double speedometerTeleportDistance;
    private final long speedometerHoldNanos;

    private final boolean spectateEnabled;
    private final double spectateLeashMargin;
    private final int spectateTicks;
    private final boolean spectateJoinDefaultArena;
    private final Map<String, Material> spectateItemMaterials;
    private final Map<String, Integer> spectateItemSlots;

    private final int rushIronIntervalTicks;
    private final int rushGoldIntervalTicks;
    private final int rushDiamondIntervalTicks;
    private final int rushEmeraldIntervalTicks;
    private final int rushGeneratorItemCap;
    private final int rushGeneratorTickPeriod;
    private final double rushGeneratorCapRadius;
    private final boolean rushBaseGeneratorsDefault;
    private final Map<String, me.beekrbonkr.practicecore.rush.RushDefense> rushDefenses;
    private final String rushCompetitiveDefense;
    private final int rushTntFuseTicks;
    private final double rushFireballPower;
    private final double rushFireballSpeed;
    private final double rushExplosionRadius;
    private final double rushExplosionStrength;
    private final double rushBridgeEggSpeed;
    private final int rushBridgeEggLifetimeTicks;
    private final int rushBridgeEggDropBelow;
    private final Material rushRescuePlatformMaterial;
    private final int rushRescuePlatformRadius;
    private final int rushRescuePlatformDepth;
    private final String rushDealerProfession;
    private final Material rushStarterSword;
    private final boolean rushPunchToDeposit;
    private final List<Material> rushDepositItems;
    private final int rushTeleporterChannelTicks;
    private final int rushTntSheepFuseTicks;
    private final double rushTntSheepPower;
    private final double rushTntSheepSpeed;
    private final int rushBotsMaxPerTeam;
    private final double rushBotsAggroRange;
    private final double rushBotsLeashRange;
    private final int rushBotsRespawnTicks;
    private final int rushBotsPlayerRespawnTicks;
    private final int rushBotsCompetitivePerTeam;
    private final String rushBotsCompetitiveDifficulty;
    private final me.beekrbonkr.practicecore.rush.RushSelection.BotArmor rushBotsCompetitiveArmor;
    private final me.beekrbonkr.practicecore.rush.RushSelection.BotSword rushBotsCompetitiveSword;

    private final List<Material> bedDefenseBlocks;
    private final boolean bedDefenseWaterBuckets;
    private final int bedDefenseEditRadius;
    private final int bedDefenseMaxPerPlayer;
    private final int bedDefenseNameMaxLength;
    private final boolean bedDefenseEmeraldForObsidian;
    private final Material bedDefenseItemMaterial;
    private final int bedDefenseItemSlot;
    private final int bedDefensePreviewStepTicks;
    private final Map<String, Material> bedDefensePreviewMaterials;
    private final Map<String, Integer> bedDefensePreviewSlots;
    private final int bedDefenseGuideBlinkTicks;
    private final int bedDefenseHologramTicks;
    private final double bedDefenseHologramHideDistance;

    private final int mlgPlatformRadius;
    private final Material mlgPlatformMaterial;
    private final int mlgPadRadius;
    private final Material mlgPadMaterial;
    private final int mlgMinDrop;
    private final int mlgMaxDrop;

    private final String bedbreakOrientation;
    private final Material bedbreakBedMaterial;
    private final boolean bedbreakMeasureReaction;

    private final boolean bundledTemplateEnabled;
    private final String bundledTemplateName;

    private final boolean generatedArenasEnabled;
    private final Map<String, String> generatedArenaNames;

    private final AccessMode arenaAccessMode;
    private final String arenaPermissionPrefix;
    private final boolean hideLockedArenas;

    private final String defaultArenaName;
    private final boolean defaultArenaOnServerJoin;
    private final boolean defaultArenaOnWorldEnter;
    private final boolean defaultArenaOnBareJoin;

    private final String leaveServer;
    private final String leaveFallbackWorld;
    private final int leaveTransferDelayTicks;

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
    private final int titleFadeInMs;
    private final int titleStayMs;
    private final int titleFadeOutMs;
    private final int titleHoldMs;
    private final int titleDelayTicks;

    public PCConfig(PracticeCorePlugin plugin, FileConfiguration cfg) {
        this.plugin = plugin;

        this.worldName = cfg.getString("world.name", "practice_world");
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Map<String, Integer> numbers = new LinkedHashMap<>();
        ConfigurationSection rules = cfg.getConfigurationSection("world.gamerules");
        if (rules != null) {
            for (String key : rules.getKeys(false)) {
                Object value = rules.get(key);
                if (value instanceof Boolean flag) {
                    flags.put(key, flag);
                } else if (value instanceof Number number) {
                    numbers.put(key, number.intValue());
                }
            }
        }
        this.worldGameRuleFlags = Map.copyOf(flags);
        this.worldGameRuleNumbers = Map.copyOf(numbers);
        this.worldDifficulty = cfg.getString("world.difficulty", "NORMAL");
        this.worldTime = cfg.getLong("world.time", 6000L);

        this.gridSpacing = Math.max(16, cfg.getInt("grid.spacing", 1000));
        this.baseY = cfg.getInt("grid.base-y", 100);
        this.maxSchematicSize = cfg.getInt("grid.max-schematic-size", 800);
        this.slotReleaseDelayTicks = Math.max(0, cfg.getInt("grid.slot-release-delay-ticks", 60));

        TimerStartMode mode;
        try {
            mode = TimerStartMode.valueOf(cfg.getString("timer.start-mode", "MOVE")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = TimerStartMode.MOVE;
        }
        this.timerStartMode = mode;
        this.scoreboardTicks = Math.max(1, cfg.getInt("scoreboard.update-ticks", 2));
        this.serverIp = cfg.getString("scoreboard.server-ip", "").trim();
        this.spectatorsSeePlayerBoard =
                cfg.getBoolean("scoreboard.spectators-see-player-board", true);

        this.failYOffset = cfg.getInt("session.fail-y-offset", 0);
        this.allowPearls = cfg.getBoolean("session.allow-pearls", false);
        this.allowBuckets = cfg.getBoolean("session.allow-buckets", false);
        this.validateInventory = cfg.getBoolean("session.validate-inventory", true);
        this.validateInventoryTicks = Math.max(1, cfg.getInt("session.validate-inventory-ticks", 20));
        List<Integer> verify = cfg.getIntegerList("session.kit-verify-ticks");
        List<Long> verifyTicks = new ArrayList<>();
        for (int tick : verify) {
            verifyTicks.add((long) Math.max(0, tick));
        }
        this.kitVerifyTicks = verifyTicks.isEmpty() ? List.of(5L, 20L, 40L) : List.copyOf(verifyTicks);
        this.triggerReach = Math.max(0.5, cfg.getDouble("session.trigger-reach", 6.0));
        this.blockPistons = cfg.getBoolean("session.block-pistons", true);
        this.wipeContainers = cfg.getBoolean("session.wipe-containers", true);
        this.blockEnderChests = cfg.getBoolean("session.block-ender-chests", true);
        this.blockCrafting = cfg.getBoolean("session.block-crafting", true);
        this.blockVehicles = cfg.getBoolean("session.block-vehicles", true);
        this.blockElytra = cfg.getBoolean("session.block-elytra", true);
        this.freezeHunger = cfg.getBoolean("session.freeze-hunger", true);
        this.blockItemDrops = cfg.getBoolean("session.block-item-drops", true);

        this.speedometerEnabled = cfg.getBoolean("speedometer.enabled", true);
        this.speedometerTicks = Math.max(1, cfg.getInt("speedometer.update-ticks", 5));
        this.speedometerSmoothing = Math.clamp(cfg.getDouble("speedometer.smoothing", 0.55), 0.0, 0.99);
        this.speedometerTeleportDistance =
                Math.max(0.5, cfg.getDouble("speedometer.teleport-distance", 10));
        this.speedometerHoldNanos = (long) (Math.max(0,
                cfg.getDouble("speedometer.message-hold-seconds", 2.5)) * 1_000_000_000L);

        this.spectateEnabled = cfg.getBoolean("spectate.enabled", true);
        this.spectateLeashMargin = Math.max(0, cfg.getDouble("spectate.leash-margin", 12));
        this.spectateTicks = Math.max(1, cfg.getInt("spectate.update-ticks", 20));
        this.spectateJoinDefaultArena = cfg.getBoolean("spectate.join-default-arena-on-stop", true);
        Map<String, Material> spectateMaterials = new LinkedHashMap<>();
        Map<String, Integer> spectateSlots = new LinkedHashMap<>();
        readSpectateTool(cfg, spectateMaterials, spectateSlots, "teleport", Material.COMPASS, 0);
        readSpectateTool(cfg, spectateMaterials, spectateSlots, "menu", Material.SPYGLASS, 4);
        readSpectateTool(cfg, spectateMaterials, spectateSlots, "leave", Material.RED_BED, 8);
        this.spectateItemMaterials = Map.copyOf(spectateMaterials);
        this.spectateItemSlots = Map.copyOf(spectateSlots);

        this.rushIronIntervalTicks = Math.max(1, cfg.getInt("rush.iron-interval-ticks", 25));
        // 4× the iron interval: one gold for every four iron.
        this.rushGoldIntervalTicks = Math.max(1, cfg.getInt("rush.gold-interval-ticks", 100));
        this.rushDiamondIntervalTicks = Math.max(1, cfg.getInt("rush.diamond-interval-ticks", 600));
        this.rushEmeraldIntervalTicks = Math.max(1, cfg.getInt("rush.emerald-interval-ticks", 1300));
        this.rushGeneratorItemCap = Math.max(1, cfg.getInt("rush.generator-item-cap", 48));
        this.rushGeneratorTickPeriod = Math.max(1, cfg.getInt("rush.generator-tick-period", 5));
        this.rushGeneratorCapRadius = Math.max(0.5, cfg.getDouble("rush.generator-cap-radius", 2.0));
        this.rushBaseGeneratorsDefault = cfg.getBoolean("rush.base-generators-default", true);
        this.rushDefenses = readDefensePresets(cfg);
        String competitiveDefense = cfg.getString("rush.competitive-defense", "endstone")
                .trim().toLowerCase(Locale.ROOT);
        this.rushCompetitiveDefense = this.rushDefenses.containsKey(competitiveDefense)
                ? competitiveDefense
                : me.beekrbonkr.practicecore.rush.RushDefense.NONE;
        this.rushTntFuseTicks = Math.max(1, cfg.getInt("rush.tnt-fuse-ticks", 60));
        this.rushFireballPower = Math.max(0, cfg.getDouble("rush.fireball-power", 3.0));
        this.rushFireballSpeed = Math.max(0.1, cfg.getDouble("rush.fireball-speed", 1.5));
        this.rushExplosionRadius = Math.max(0, cfg.getDouble("rush.explosion-knockback-radius", 5.0));
        this.rushExplosionStrength = Math.max(0, cfg.getDouble("rush.explosion-knockback-strength", 1.6));
        this.rushBridgeEggSpeed = Math.max(0.1, cfg.getDouble("rush.bridge-egg.speed", 1.4));
        this.rushBridgeEggLifetimeTicks = Math.max(1, cfg.getInt("rush.bridge-egg.lifetime-ticks", 60));
        this.rushBridgeEggDropBelow = Math.max(0, cfg.getInt("rush.bridge-egg.drop-below", 2));
        this.rushRescuePlatformMaterial =
                material(cfg.getString("rush.rescue-platform.material"), Material.SLIME_BLOCK);
        this.rushRescuePlatformRadius = Math.clamp(cfg.getInt("rush.rescue-platform.radius", 2), 0, 8);
        this.rushRescuePlatformDepth = Math.clamp(cfg.getInt("rush.rescue-platform.depth", 2), 1, 8);
        this.rushDealerProfession = cfg.getString("rush.dealer-profession", "LIBRARIAN");
        // Blank is a deliberate off-switch here, so it must not fall back.
        String starterSword = cfg.getString("rush.starter-sword", "WOODEN_SWORD");
        this.rushStarterSword = starterSword == null || starterSword.isBlank()
                ? null : material(starterSword, Material.WOODEN_SWORD);
        this.rushPunchToDeposit = cfg.getBoolean("rush.punch-to-deposit", true);
        List<Material> deposit = new ArrayList<>();
        for (String name : cfg.getStringList("rush.deposit-items")) {
            Material parsed = Material.matchMaterial(name);
            if (parsed != null && parsed.isItem()) {
                deposit.add(parsed);
            } else if (plugin != null) {
                plugin.getLogger().warning("config.yml: '" + name
                        + "' under rush.deposit-items is not an item this server knows — skipped.");
            }
        }
        this.rushDepositItems = deposit.isEmpty()
                ? List.of(Material.IRON_INGOT, Material.GOLD_INGOT,
                        Material.DIAMOND, Material.EMERALD)
                : List.copyOf(deposit);
        this.rushTeleporterChannelTicks =
                Math.max(1, cfg.getInt("rush.teleporter-channel-ticks", 60));
        this.rushTntSheepFuseTicks = Math.max(1, cfg.getInt("rush.tnt-sheep.fuse-ticks", 80));
        this.rushTntSheepPower = Math.max(0, cfg.getDouble("rush.tnt-sheep.power", 2.5));
        this.rushTntSheepSpeed = Math.max(0.1, cfg.getDouble("rush.tnt-sheep.speed", 1.2));
        this.rushBotsMaxPerTeam = Math.clamp(cfg.getInt("rush.bots.max-per-team", 4), 0, 8);
        this.rushBotsAggroRange = Math.max(1, cfg.getDouble("rush.bots.aggro-range", 10.0));
        this.rushBotsLeashRange = Math.max(4, cfg.getDouble("rush.bots.leash-range", 26.0));
        this.rushBotsRespawnTicks = Math.max(1, cfg.getInt("rush.bots.respawn-ticks", 100));
        this.rushBotsPlayerRespawnTicks =
                Math.max(1, cfg.getInt("rush.bots.player-respawn-ticks", 60));
        this.rushBotsCompetitivePerTeam =
                Math.clamp(cfg.getInt("rush.bots.competitive.per-team", 0), 0, 8);
        this.rushBotsCompetitiveDifficulty =
                cfg.getString("rush.bots.competitive.difficulty", "veteran")
                        .trim().toLowerCase(Locale.ROOT);
        me.beekrbonkr.practicecore.rush.RushSelection.BotArmor competitiveArmor;
        try {
            competitiveArmor = me.beekrbonkr.practicecore.rush.RushSelection.BotArmor.valueOf(
                    cfg.getString("rush.bots.competitive.armor", "IRON").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            competitiveArmor = me.beekrbonkr.practicecore.rush.RushSelection.BotArmor.IRON;
        }
        this.rushBotsCompetitiveArmor = competitiveArmor;
        me.beekrbonkr.practicecore.rush.RushSelection.BotSword competitiveSword;
        try {
            competitiveSword = me.beekrbonkr.practicecore.rush.RushSelection.BotSword.valueOf(
                    cfg.getString("rush.bots.competitive.sword", "IRON").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            competitiveSword = me.beekrbonkr.practicecore.rush.RushSelection.BotSword.IRON;
        }
        this.rushBotsCompetitiveSword = competitiveSword;

        List<Material> defenseBlocks = new ArrayList<>();
        for (String name : cfg.getStringList("beddefense.blocks")) {
            Material parsed = Material.matchMaterial(name);
            if (parsed != null && parsed.isBlock()) {
                Material kind = me.beekrbonkr.practicecore.beddefense.BlockKinds.normalize(parsed);
                if (!defenseBlocks.contains(kind)) {
                    defenseBlocks.add(kind);
                }
            } else if (plugin != null) {
                plugin.getLogger().warning("config.yml: '" + name
                        + "' under beddefense.blocks is not a block this server knows — skipped.");
            }
        }
        this.bedDefenseBlocks = defenseBlocks.isEmpty()
                ? List.of(Material.WHITE_WOOL, Material.OAK_PLANKS, Material.END_STONE,
                        Material.GLASS, Material.OBSIDIAN, Material.TERRACOTTA, Material.LADDER)
                : List.copyOf(defenseBlocks);
        this.bedDefenseWaterBuckets = cfg.getBoolean("beddefense.water-buckets", true);
        this.bedDefenseEditRadius = Math.clamp(cfg.getInt("beddefense.edit-radius", 10), 1, 32);
        this.bedDefenseMaxPerPlayer = Math.clamp(cfg.getInt("beddefense.max-per-player", 25), 1, 1000);
        this.bedDefenseNameMaxLength = Math.clamp(cfg.getInt("beddefense.name-max-length", 24), 1, 64);
        this.bedDefenseEmeraldForObsidian = cfg.getBoolean("beddefense.emerald-for-obsidian", true);
        this.bedDefenseItemMaterial = material(cfg.getString("beddefense.item.material"), Material.RED_BED);
        this.bedDefenseItemSlot = Math.clamp(cfg.getInt("beddefense.item.slot", 7), 0, 8);
        this.bedDefensePreviewStepTicks = Math.clamp(cfg.getInt("beddefense.preview.step-ticks", 8), 1, 200);
        Map<String, Material> previewMaterials = new LinkedHashMap<>();
        Map<String, Integer> previewSlots = new LinkedHashMap<>();
        readPreviewItem(cfg, previewMaterials, previewSlots, "previous", Material.ARROW, 2);
        readPreviewItem(cfg, previewMaterials, previewSlots, "play", Material.LIME_DYE, 3);
        readPreviewItem(cfg, previewMaterials, previewSlots, "pause", Material.ORANGE_DYE, 3);
        readPreviewItem(cfg, previewMaterials, previewSlots, "next", Material.SPECTRAL_ARROW, 4);
        readPreviewItem(cfg, previewMaterials, previewSlots, "guided", Material.COMPASS, 6);
        readPreviewItem(cfg, previewMaterials, previewSlots, "exit", Material.BARRIER, 8);
        this.bedDefensePreviewMaterials = Map.copyOf(previewMaterials);
        this.bedDefensePreviewSlots = Map.copyOf(previewSlots);
        this.bedDefenseGuideBlinkTicks = Math.clamp(cfg.getInt("beddefense.guided.blink-ticks", 8), 2, 200);
        this.bedDefenseHologramTicks = (int) Math.round(
                Math.clamp(cfg.getDouble("beddefense.hologram.seconds", 8.0), 0.0, 600.0) * 20);
        this.bedDefenseHologramHideDistance =
                Math.max(0, cfg.getDouble("beddefense.hologram.hide-distance", 4.0));

        this.mlgPlatformRadius = Math.max(0, cfg.getInt("mlg.platform-radius", 1));
        this.mlgPlatformMaterial = material(cfg.getString("mlg.platform-material"), Material.GLASS);
        this.mlgPadRadius = Math.max(0, cfg.getInt("mlg.pad-radius", 5));
        this.mlgPadMaterial = material(cfg.getString("mlg.pad-material"), Material.GRASS_BLOCK);
        this.mlgMinDrop = Math.max(2, cfg.getInt("mlg.min-drop", 20));
        this.mlgMaxDrop = Math.max(this.mlgMinDrop, cfg.getInt("mlg.max-drop", 100));

        this.bedbreakOrientation = cfg.getString("bedbreak.orientation", "VERTICAL");
        this.bedbreakBedMaterial = material(cfg.getString("bedbreak.bed-material"), Material.RED_BED);
        this.bedbreakMeasureReaction = cfg.getBoolean("bedbreak.measure-reaction", true);

        this.bundledTemplateEnabled = cfg.getBoolean("bundled-template.enabled", true);
        this.bundledTemplateName = cfg.getString("bundled-template.name", "turtle")
                .toLowerCase(Locale.ROOT);

        this.generatedArenasEnabled = cfg.getBoolean("generated-arenas.enabled", true);
        this.generatedArenaNames = Map.of(
                "bedbreak", cfg.getString("generated-arenas.bedbreak", "bedbreak")
                        .trim().toLowerCase(Locale.ROOT),
                "bedbreak-horizontal",
                cfg.getString("generated-arenas.bedbreak-horizontal", "bedbreak-horizontal")
                        .trim().toLowerCase(Locale.ROOT),
                "mlg", cfg.getString("generated-arenas.mlg", "mlg")
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
        this.leaveTransferDelayTicks =
                Math.max(0, cfg.getInt("leave.transfer-delay-ticks", 2));

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
        this.titleFadeInMs = Math.max(0, cfg.getInt("effects.title-fade-in-ms", 50));
        this.titleStayMs = Math.max(0, cfg.getInt("effects.title-stay-ms", 1200));
        this.titleFadeOutMs = Math.max(0, cfg.getInt("effects.title-fade-out-ms", 400));
        this.titleHoldMs = Math.max(0, cfg.getInt("effects.title-hold-ms", 15000));
        this.titleDelayTicks = Math.max(0, cfg.getInt("effects.title-delay-ticks", 10));
    }

    private void readPreviewItem(FileConfiguration cfg, Map<String, Material> materials,
                                 Map<String, Integer> slots, String item,
                                 Material defMaterial, int defSlot) {
        materials.put(item, material(cfg.getString("beddefense.preview.items." + item + ".material"),
                defMaterial));
        slots.put(item, Math.clamp(cfg.getInt("beddefense.preview.items." + item + ".slot", defSlot), 0, 8));
    }

    private void readSpectateTool(FileConfiguration cfg, Map<String, Material> materials,
                                  Map<String, Integer> slots, String tool,
                                  Material defMaterial, int defSlot) {
        materials.put(tool, material(cfg.getString("spectate.items." + tool + ".material"), defMaterial));
        slots.put(tool, Math.clamp(cfg.getInt("spectate.items." + tool + ".slot", defSlot), 0, 8));
    }

    /**
     * The bed-defense gallery from {@code rush.defense-presets}, in file order
     * — which is the order the picker menu lists them in. A "none" entry is
     * always present, whether or not the admin kept theirs, because it is what
     * every unknown id falls back to.
     */
    private Map<String, me.beekrbonkr.practicecore.rush.RushDefense> readDefensePresets(
            FileConfiguration cfg) {
        Map<String, me.beekrbonkr.practicecore.rush.RushDefense> presets = new LinkedHashMap<>();
        presets.put(me.beekrbonkr.practicecore.rush.RushDefense.NONE,
                me.beekrbonkr.practicecore.rush.RushDefense.none());
        ConfigurationSection section = cfg.getConfigurationSection("rush.defense-presets");
        if (section == null) {
            return java.util.Collections.unmodifiableMap(presets);
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.trim().toLowerCase(Locale.ROOT);
            List<Material> layers = new ArrayList<>();
            for (String name : entry.getStringList("layers")) {
                Material layer = material(name, null);
                if (layer != null && layer.isBlock()) {
                    layers.add(layer);
                }
            }
            presets.put(id, new me.beekrbonkr.practicecore.rush.RushDefense(id,
                    entry.getString("name", id), material(entry.getString("icon"), null), layers));
        }
        // LinkedHashMap, not Map.copyOf: the file's order is the menu's order.
        return java.util.Collections.unmodifiableMap(presets);
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(name);
        if (parsed == null || !parsed.isItem()) {
            if (plugin != null) {
                plugin.getLogger().warning("config.yml: '" + name
                        + "' is not an item this server knows — using "
                        + (fallback == null ? "nothing" : fallback.name()) + ".");
            }
            return fallback;
        }
        return parsed;
    }

    // ----------------------------------------------------------------- world

    public String worldName() {
        return worldName;
    }

    /** Boolean gamerules to apply to a freshly built practice world. */
    public Map<String, Boolean> worldGameRuleFlags() {
        return worldGameRuleFlags;
    }

    /** Numeric gamerules to apply to a freshly built practice world. */
    public Map<String, Integer> worldGameRuleNumbers() {
        return worldGameRuleNumbers;
    }

    /**
     * The practice world's difficulty. NORMAL by default and for a reason:
     * PEACEFUL removes hostile mobs on the spot and zeroes their damage, and
     * the PvP bot is a husk that needs both.
     */
    public String worldDifficulty() {
        return worldDifficulty;
    }

    /** Fixed time of day, with the daylight cycle off. */
    public long worldTime() {
        return worldTime;
    }

    // ------------------------------------------------------------------ grid

    public int gridSpacing() {
        return gridSpacing;
    }

    public int baseY() {
        return baseY;
    }

    public int maxSchematicSize() {
        return maxSchematicSize;
    }

    /**
     * Ticks before a freed grid slot may be handed out again. The erase that
     * empties it may still be flushing off-thread, and a new paste must never
     * race the wipe.
     */
    public int slotReleaseDelayTicks() {
        return slotReleaseDelayTicks;
    }

    public TimerStartMode timerStartMode() {
        return timerStartMode;
    }

    /** Server address for the sidebar footer; empty hides the footer. */
    public String serverIp() {
        return serverIp;
    }

    /** Spectators mirror the watched player's exact board instead of a summary. */
    public boolean spectatorsSeePlayerBoard() {
        return spectatorsSeePlayerBoard;
    }

    public int scoreboardTicks() {
        return scoreboardTicks;
    }

    // --------------------------------------------------------------- session

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

    /** Ticks after a spawn at which a missing kit is repaired. */
    public List<Long> kitVerifyTicks() {
        return kitVerifyTicks;
    }

    /** How close a player must stand to a finish trigger for it to count. */
    public double triggerReach() {
        return triggerReach;
    }

    public boolean blockPistons() {
        return blockPistons;
    }

    public boolean wipeContainers() {
        return wipeContainers;
    }

    public boolean blockEnderChests() {
        return blockEnderChests;
    }

    public boolean blockCrafting() {
        return blockCrafting;
    }

    public boolean blockVehicles() {
        return blockVehicles;
    }

    public boolean blockElytra() {
        return blockElytra;
    }

    public boolean freezeHunger() {
        return freezeHunger;
    }

    public boolean blockItemDrops() {
        return blockItemDrops;
    }

    // ----------------------------------------------------------- speedometer

    public boolean speedometerEnabled() {
        return speedometerEnabled;
    }

    public int speedometerTicks() {
        return speedometerTicks;
    }

    /** Weight of the previous reading; higher is steadier and slower to react. */
    public double speedometerSmoothing() {
        return speedometerSmoothing;
    }

    /** A per-sample jump this large is a teleport, not running. */
    public double speedometerTeleportDistance() {
        return speedometerTeleportDistance;
    }

    /** How long other action-bar text keeps the speedometer quiet, in nanos. */
    public long speedometerHoldNanos() {
        return speedometerHoldNanos;
    }

    // ------------------------------------------------------------- spectate

    public boolean spectateEnabled() {
        return spectateEnabled;
    }

    /** Blocks past the target's arena walls a spectator may drift. */
    public double spectateLeashMargin() {
        return spectateLeashMargin;
    }

    public int spectateTicks() {
        return spectateTicks;
    }

    /** Whether leaving spectate drops the player into the default arena. */
    public boolean spectateJoinDefaultArena() {
        return spectateJoinDefaultArena;
    }

    public Material spectateItemMaterial(String tool) {
        return spectateItemMaterials.getOrDefault(tool, Material.PAPER);
    }

    public int spectateItemSlot(String tool) {
        return spectateItemSlots.getOrDefault(tool, 0);
    }

    // ------------------------------------------------------------------ rush

    public int rushIronIntervalTicks() {
        return rushIronIntervalTicks;
    }

    public int rushGoldIntervalTicks() {
        return rushGoldIntervalTicks;
    }

    /** Combat runs only: ticks between diamond generator drops. */
    public int rushDiamondIntervalTicks() {
        return rushDiamondIntervalTicks;
    }

    /** Combat runs only: ticks between emerald generator drops. */
    public int rushEmeraldIntervalTicks() {
        return rushEmeraldIntervalTicks;
    }

    /** Items lying near a generator before it pauses dropping more. */
    public int rushGeneratorItemCap() {
        return rushGeneratorItemCap;
    }

    /** Ticks between generator passes; drop countdowns run on this beat. */
    public int rushGeneratorTickPeriod() {
        return rushGeneratorTickPeriod;
    }

    /** How close an item must lie to a generator to count toward its cap. */
    public double rushGeneratorCapRadius() {
        return rushGeneratorCapRadius;
    }

    /** The bed defense preset every competitive run is pinned to. */
    /** The bed-defense preset id every competitive run races under. */
    public String rushCompetitiveDefense() {
        return rushCompetitiveDefense;
    }

    /** Every bed-defense preset, in the order config.yml lists them. */
    public Map<String, me.beekrbonkr.practicecore.rush.RushDefense> rushDefenses() {
        return rushDefenses;
    }

    /**
     * One preset by id, case-insensitively. Never null: an id an admin
     * removed (or a pref written by an older build) resolves to the "none"
     * preset rather than leaving a run without an answer.
     */
    public me.beekrbonkr.practicecore.rush.RushDefense rushDefense(String id) {
        me.beekrbonkr.practicecore.rush.RushDefense preset = id == null ? null
                : rushDefenses.get(id.trim().toLowerCase(Locale.ROOT));
        if (preset != null) {
            return preset;
        }
        preset = rushDefenses.get(me.beekrbonkr.practicecore.rush.RushDefense.NONE);
        return preset != null ? preset : me.beekrbonkr.practicecore.rush.RushDefense.none();
    }

    /** Whether base generators run for players with no saved preference. */
    public boolean rushBaseGeneratorsDefault() {
        return rushBaseGeneratorsDefault;
    }

    public int rushTntFuseTicks() {
        return rushTntFuseTicks;
    }

    public double rushFireballPower() {
        return rushFireballPower;
    }

    public double rushFireballSpeed() {
        return rushFireballSpeed;
    }

    /** How far an explosion's manual knockback reaches, falling off to nothing. */
    public double rushExplosionRadius() {
        return rushExplosionRadius;
    }

    public double rushExplosionStrength() {
        return rushExplosionStrength;
    }

    public double rushBridgeEggSpeed() {
        return rushBridgeEggSpeed;
    }

    public int rushBridgeEggLifetimeTicks() {
        return rushBridgeEggLifetimeTicks;
    }

    /** How far under the egg's flight path the wool trail is laid. */
    public int rushBridgeEggDropBelow() {
        return rushBridgeEggDropBelow;
    }

    public Material rushRescuePlatformMaterial() {
        return rushRescuePlatformMaterial;
    }

    public int rushRescuePlatformRadius() {
        return rushRescuePlatformRadius;
    }

    /** Blocks below the player's feet the rescue platform is built. */
    public int rushRescuePlatformDepth() {
        return rushRescuePlatformDepth;
    }

    public String rushDealerProfession() {
        return rushDealerProfession;
    }

    /**
     * The sword every rush spawn carries in the first hotbar slot, like a
     * real game. Null when disabled ('' in config.yml).
     */
    public Material rushStarterSword() {
        return rushStarterSword;
    }

    /** Whether punching a chest deposits resources into it in one hit. */
    public boolean rushPunchToDeposit() {
        return rushPunchToDeposit;
    }

    /** The resources punch-to-deposit sweeps into the chest. */
    public List<Material> rushDepositItems() {
        return rushDepositItems;
    }

    /** Ticks the MBedwars-style teleporter channels before firing. */
    public int rushTeleporterChannelTicks() {
        return rushTeleporterChannelTicks;
    }

    public int rushTntSheepFuseTicks() {
        return rushTntSheepFuseTicks;
    }

    public double rushTntSheepPower() {
        return rushTntSheepPower;
    }

    /** Pathfinder speed multiplier of a walking TNT sheep. */
    public double rushTntSheepSpeed() {
        return rushTntSheepSpeed;
    }

    // ------------------------------------------------------------- rush bots

    /** The ceiling the bots-per-team button cycles up to. */
    public int rushBotsMaxPerTeam() {
        return rushBotsMaxPerTeam;
    }

    /** Blocks from a defender bot at which it engages the player. */
    public double rushBotsAggroRange() {
        return rushBotsAggroRange;
    }

    /** Blocks from its post past which a defender breaks off and walks home. */
    public double rushBotsLeashRange() {
        return rushBotsLeashRange;
    }

    /** Ticks a killed defender stays down while its bed still stands. */
    public int rushBotsRespawnTicks() {
        return rushBotsRespawnTicks;
    }

    /** Ticks the player is held at their base after a defender kills them. */
    public int rushBotsPlayerRespawnTicks() {
        return rushBotsPlayerRespawnTicks;
    }

    /** Defender lineup competitive runs are pinned to; 0 = classic race. */
    public int rushBotsCompetitivePerTeam() {
        return rushBotsCompetitivePerTeam;
    }

    public String rushBotsCompetitiveDifficulty() {
        return rushBotsCompetitiveDifficulty;
    }

    public me.beekrbonkr.practicecore.rush.RushSelection.BotArmor rushBotsCompetitiveArmor() {
        return rushBotsCompetitiveArmor;
    }

    public me.beekrbonkr.practicecore.rush.RushSelection.BotSword rushBotsCompetitiveSword() {
        return rushBotsCompetitiveSword;
    }

    // ----------------------------------------------------------- bed defense

    /** The block kinds a bed defense may be made of (normalized, see BlockKinds). */
    public List<Material> bedDefenseBlocks() {
        return bedDefenseBlocks;
    }

    public boolean bedDefenseWaterBuckets() {
        return bedDefenseWaterBuckets;
    }

    public int bedDefenseEditRadius() {
        return bedDefenseEditRadius;
    }

    public int bedDefenseMaxPerPlayer() {
        return bedDefenseMaxPerPlayer;
    }

    public int bedDefenseNameMaxLength() {
        return bedDefenseNameMaxLength;
    }

    public boolean bedDefenseEmeraldForObsidian() {
        return bedDefenseEmeraldForObsidian;
    }

    public Material bedDefenseItemMaterial() {
        return bedDefenseItemMaterial;
    }

    public int bedDefenseItemSlot() {
        return bedDefenseItemSlot;
    }

    public int bedDefensePreviewStepTicks() {
        return bedDefensePreviewStepTicks;
    }

    public Material bedDefensePreviewItemMaterial(String item) {
        return bedDefensePreviewMaterials.getOrDefault(item, Material.PAPER);
    }

    public int bedDefensePreviewItemSlot(String item) {
        return bedDefensePreviewSlots.getOrDefault(item, 4);
    }

    public int bedDefenseGuideBlinkTicks() {
        return bedDefenseGuideBlinkTicks;
    }

    /** Ticks the bed hologram lingers; 0 disables it. */
    public int bedDefenseHologramTicks() {
        return bedDefenseHologramTicks;
    }

    public double bedDefenseHologramHideDistance() {
        return bedDefenseHologramHideDistance;
    }

    // ------------------------------------------------------------------- mlg

    /** Default MLG round shape; an arena's own settings.mlg overrides each key. */
    public int mlgPlatformRadius() {
        return mlgPlatformRadius;
    }

    public Material mlgPlatformMaterial() {
        return mlgPlatformMaterial;
    }

    public int mlgPadRadius() {
        return mlgPadRadius;
    }

    public Material mlgPadMaterial() {
        return mlgPadMaterial;
    }

    public int mlgMinDrop() {
        return mlgMinDrop;
    }

    public int mlgMaxDrop() {
        return mlgMaxDrop;
    }

    // -------------------------------------------------------------- bedbreak

    /** Default bedbreak shape; an arena's own settings.bedbreak overrides it. */
    public String bedbreakOrientation() {
        return bedbreakOrientation;
    }

    public Material bedbreakBedMaterial() {
        return bedbreakBedMaterial;
    }

    /** Whether tool-switch reaction times are measured and recorded. */
    public boolean bedbreakMeasureReaction() {
        return bedbreakMeasureReaction;
    }

    // -------------------------------------------------------------- arenas

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

    // ----------------------------------------------------------------- leave

    /** Empty when leaving should not hand the player to a proxy server. */
    public String leaveServer() {
        return leaveServer;
    }

    public String leaveFallbackWorld() {
        return leaveFallbackWorld;
    }

    /**
     * Ticks between restoring a player and handing them to the proxy. The gap
     * is what makes the restored inventory — rather than the arena kit — the
     * state the hand-off persists.
     */
    public int leaveTransferDelayTicks() {
        return leaveTransferDelayTicks;
    }

    // ------------------------------------------------------------- menu item

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

    // ----------------------------------------------------------- leaderboard

    public int leaderboardSize() {
        return leaderboardSize;
    }

    public boolean leaderboardHeads() {
        return leaderboardHeads;
    }

    // --------------------------------------------------------------- effects

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

    public int titleFadeInMs() {
        return titleFadeInMs;
    }

    public int titleStayMs() {
        return titleStayMs;
    }

    public int titleFadeOutMs() {
        return titleFadeOutMs;
    }

    /**
     * How long a "loading" title stays up on its own. It is normally taken
     * down the moment the work finishes; this is only the backstop for a
     * queued action that never reports back, so it must outlast a slow paste.
     */
    public int titleHoldMs() {
        return titleHoldMs;
    }

    /**
     * How long a queued action has to still be running before its "loading"
     * title appears at all. Most joins land in a tick or two, and flashing a
     * card at the player for those would be worse than saying nothing.
     */
    public int titleDelayTicks() {
        return titleDelayTicks;
    }
}
