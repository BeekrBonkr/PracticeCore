package me.beekrbonkr.practicecore;

import me.beekrbonkr.practicecore.board.BoardService;
import me.beekrbonkr.practicecore.command.PracticeCommand;
import me.beekrbonkr.practicecore.config.GuiConfig;
import me.beekrbonkr.practicecore.config.ReloadResult;
import me.beekrbonkr.practicecore.config.SoundConfig;
import me.beekrbonkr.practicecore.config.Versions;
import me.beekrbonkr.practicecore.config.YamlMigrator;
import me.beekrbonkr.practicecore.pvpbot.BotTuning;
import me.beekrbonkr.practicecore.grid.SlotAllocator;
import me.beekrbonkr.practicecore.gui.MenuListener;
import me.beekrbonkr.practicecore.item.MenuItemListener;
import me.beekrbonkr.practicecore.item.MenuItemService;
import me.beekrbonkr.practicecore.leave.LeaveService;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.listener.BlockListener;
import me.beekrbonkr.practicecore.listener.ConnectionListener;
import me.beekrbonkr.practicecore.listener.InteractListener;
import me.beekrbonkr.practicecore.listener.MovementListener;
import me.beekrbonkr.practicecore.listener.ProtectionListener;
import me.beekrbonkr.practicecore.listener.TeleportListener;
import me.beekrbonkr.practicecore.listener.MlgListener;
import me.beekrbonkr.practicecore.listener.PvpBotListener;
import me.beekrbonkr.practicecore.listener.RushListener;
import me.beekrbonkr.practicecore.mode.BedBreakMode;
import me.beekrbonkr.practicecore.mode.BridgingMode;
import me.beekrbonkr.practicecore.mode.MlgMode;
import me.beekrbonkr.practicecore.mode.PvpBotMode;
import me.beekrbonkr.practicecore.mode.ModeRegistry;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.pvpbot.PvpBotService;
import me.beekrbonkr.practicecore.rush.RushService;
import me.beekrbonkr.practicecore.schematic.SchematicService;
import me.beekrbonkr.practicecore.session.InventoryValidator;
import me.beekrbonkr.practicecore.session.SessionManager;
import me.beekrbonkr.practicecore.session.SpeedometerService;
import me.beekrbonkr.practicecore.settings.SettingsService;
import me.beekrbonkr.practicecore.setup.ChatPrompts;
import me.beekrbonkr.practicecore.setup.SetupManager;
import me.beekrbonkr.practicecore.snapshot.SnapshotStore;
import me.beekrbonkr.practicecore.spectate.SpectateListener;
import me.beekrbonkr.practicecore.spectate.SpectateService;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.stats.StatsStore;
import me.beekrbonkr.practicecore.template.TemplateRegistry;
import me.beekrbonkr.practicecore.world.PracticeWorldService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PracticeCorePlugin extends JavaPlugin {

    private PCConfig pcConfig;
    private ModeRegistry modes;
    private PracticeWorldService worldService;
    private SlotAllocator allocator;
    private SchematicService schematics;
    private TemplateRegistry templates;
    private SnapshotStore snapshots;
    private LeaderboardService leaderboards;
    private StatsStore stats;
    private BoardService boards;
    private SessionManager sessions;
    private SetupManager setup;
    private MenuItemService menuItems;
    private LeaveService leaveService;
    private Messages messages;
    private GuiConfig guis;
    private SettingsService settings;
    private SpeedometerService speedometer;
    private InventoryValidator inventoryValidator;
    private ChatPrompts prompts;
    private RushService rush;
    private PvpBotService pvpBot;
    private me.beekrbonkr.practicecore.rushbot.RushBotService rushBots;
    private SpectateService spectate;
    private SoundConfig sounds;
    private BotTuning botTuning;

    @Override
    public void onEnable() {
        // A config.yml that does not parse is invisible through getConfig() —
        // Bukkit quietly hands back an empty one — so the syntax check runs
        // first, loudly. (The ConfigFile-backed files report their own.)
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.isFile()) {
            try {
                new YamlConfiguration().load(configFile);
            } catch (IOException | InvalidConfigurationException e) {
                getLogger().severe("config.yml could not be parsed — running on the"
                        + " built-in defaults until it is fixed: " + e.getMessage());
            }
        }
        saveDefaultConfig();
        migrateConfig().forEach(note -> getLogger().info(note));
        pcConfig = new PCConfig(this, getConfig());
        messages = new Messages(this);
        messages.load().forEach(note -> getLogger().info(note));
        guis = new GuiConfig(this);
        guis.load().forEach(note -> getLogger().info(note));
        sounds = new SoundConfig(this);
        sounds.load().forEach(note -> getLogger().info(note));
        botTuning = new BotTuning(this);
        botTuning.load().forEach(note -> getLogger().info(note));
        reportValidation();

        modes = new ModeRegistry();
        modes.register(new BridgingMode());
        modes.register(new BedBreakMode());
        modes.register(new RushMode());
        modes.register(new MlgMode());
        modes.register(new PvpBotMode());

        worldService = new PracticeWorldService(this);
        worldService.recreate();

        allocator = new SlotAllocator();
        schematics = new SchematicService();
        templates = new TemplateRegistry(this);
        templates.loadAll();
        arenaFingerprint = templates.fingerprint();
        snapshots = new SnapshotStore(this);
        leaderboards = new LeaderboardService();
        stats = new StatsStore(this);
        boards = new BoardService(this);
        sessions = new SessionManager(this);
        setup = new SetupManager(this);
        menuItems = new MenuItemService(this);
        leaveService = new LeaveService(this);
        settings = new SettingsService(this);
        speedometer = new SpeedometerService(this);
        inventoryValidator = new InventoryValidator(this);
        prompts = new ChatPrompts(this);
        rush = new RushService(this);
        pvpBot = new PvpBotService(this);
        rushBots = new me.beekrbonkr.practicecore.rushbot.RushBotService(this);
        spectate = new SpectateService(this);

        // Builds the name index and every leaderboard from disk, off-thread.
        stats.scanAsync();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new MovementListener(this), this);
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new InteractListener(this), this);
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new MenuItemListener(this), this);
        pm.registerEvents(new RushListener(this), this);
        pm.registerEvents(new MlgListener(this), this);
        pm.registerEvents(new PvpBotListener(this), this);
        pm.registerEvents(new me.beekrbonkr.practicecore.rushbot.RushBotListener(this), this);
        pm.registerEvents(new SpectateListener(this), this);
        pm.registerEvents(prompts, this);

        // Used by the leave button when leave.server points at a proxy backend.
        getServer().getMessenger().registerOutgoingPluginChannel(this, LeaveService.channel());

        PracticeCommand command = new PracticeCommand(this);
        PluginCommand pluginCommand = getCommand("practice");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        // /spectate <player>: the /practice spectate flow, one word shorter.
        var spectateCommand = new me.beekrbonkr.practicecore.command.SpectateCommand(this);
        PluginCommand spectatePluginCommand = getCommand("spectate");
        if (spectatePluginCommand != null) {
            spectatePluginCommand.setExecutor(spectateCommand);
            spectatePluginCommand.setTabCompleter(spectateCommand);
        }

        boards.startTask();
        speedometer.startTask();
        inventoryValidator.startTask();
        rush.startTask();
        pvpBot.startTask();
        rushBots.startTask();
        spectate.startTask();
        getLogger().info("PracticeCore enabled — practice world '" + pcConfig.worldName() + "' ready.");
    }

    @Override
    public void onDisable() {
        // Order matters: wizard first (it holds a slot), then sessions
        // (restores every player synchronously), then UI and pending writes.
        if (setup != null) {
            setup.cancelAll();
        }
        if (spectate != null) {
            // Before sessions: a spectator restore must not race the world unload.
            spectate.shutdown();
            spectate.endAllSync();
        }
        if (sessions != null) {
            sessions.endAllSync();
        }
        if (boards != null) {
            boards.shutdown();
        }
        if (speedometer != null) {
            speedometer.shutdown();
        }
        if (inventoryValidator != null) {
            inventoryValidator.shutdown();
        }
        if (rush != null) {
            rush.shutdown();
        }
        if (pvpBot != null) {
            pvpBot.shutdown();
        }
        if (rushBots != null) {
            rushBots.shutdown();
        }
        if (stats != null) {
            stats.flushSync();
        }
    }

    /**
     * The value sweep behind start-time and reload-time config validation:
     * every loaded file checked for values that will not resolve in game.
     * Findings are warnings, never refusals — each one falls back safely.
     */
    public List<String> validateConfigs() {
        return new me.beekrbonkr.practicecore.config.ConfigValidator(this).validate();
    }

    private void reportValidation() {
        List<String> problems = validateConfigs();
        if (problems.isEmpty()) {
            getLogger().info("Configuration validated — no problems found.");
            return;
        }
        getLogger().warning(problems.size() + " configuration problem(s) found —"
                + " each value falls back to its default until fixed:");
        problems.forEach(problem -> getLogger().warning("  - " + problem));
    }

    private List<String> migrateConfig() {
        return new YamlMigrator(this, "config", "config.yml",
                new File(getDataFolder(), "config.yml"), Versions.CONFIG,
                PracticeCorePlugin::configSteps,
                // A gamerule — or a bed-defense preset — the admin removed
                // must stay removed.
                java.util.Set.of("world.gamerules", "rush.defense-presets")).run(getConfig());
    }

    /** Reshapes an older config.yml. See {@link Versions#CONFIG}. */
    private static void configSteps(FileConfiguration cfg, int from) {
        if (from < 2) {
            // v1 gated arenas with a boolean; v2 states the same thing as a
            // mode, because "everyone unless denied" needed a name of its own.
            boolean required = cfg.getBoolean("arenas.require-permission", false);
            cfg.set("arenas.require-permission", null);
            if (!cfg.contains("arenas.access-mode", true)) {
                cfg.set("arenas.access-mode",
                        required ? PCConfig.AccessMode.ALLOW.name() : PCConfig.AccessMode.DENY.name());
            }
        }
        // v3 added scoreboard.server-ip and v4 a great many new sections —
        // both purely additive, so the migrator's top-up writes them (with
        // their comments) into older files by itself.
        if (from < 5) {
            // v5 sets the gold generator to the 4:1 forge ratio (one gold per
            // four iron). Only a value still at the old shipped default moves;
            // an admin's own interval stands.
            if (cfg.isSet("rush.gold-interval-ticks")
                    && cfg.getInt("rush.gold-interval-ticks", 120) == 120) {
                cfg.set("rush.gold-interval-ticks", 100);
            }
        }
        if (from < 6) {
            // v6: competitive follows the bots toggle — off is the classic
            // race, on is the pinned team wipe — so per-team stops meaning
            // "competitive never has bots" and becomes the wipe's lineup
            // size. The old shipped default (0) would leave the team-wipe
            // preset with nothing to pin; only that untouched 0 moves.
            if (cfg.isSet("rush.bots.competitive.per-team")
                    && cfg.getInt("rush.bots.competitive.per-team", 0) == 0) {
                cfg.set("rush.bots.competitive.per-team", 4);
            }
        }
    }

    /**
     * Re-reads every admin-editable file and every arena folder, without
     * needing a server restart.
     *
     * Two properties make that safe on a live server:
     *
     * <p><b>All or nothing.</b> Every file is parsed on a throwaway object
     * before anything live is replaced — Bukkit's own {@code reloadConfig()}
     * swallows a syntax error and hands back an empty config, which would
     * quietly reset every setting to its default. A reload that cannot be
     * completed changes nothing at all.
     *
     * <p><b>Runs are only interrupted when they have to be.</b> Almost
     * everything here — messages, menus, sounds, bot tuning, kits, mode
     * settings, effects — describes what happens <em>next</em>, and is simply
     * picked up by whatever asks for it after the swap. Only a handful of keys
     * describe arenas that are <em>already pasted into the world</em>: the
     * world name, the grid geometry, and the arena definitions themselves.
     * Changing one of those while someone is standing in the arena it
     * describes is what needs a confirmation, and nothing else does.
     *
     * @param force go ahead even though live sessions must end first
     */
    public ReloadResult reload(boolean force) {
        List<String> notes = new ArrayList<>();
        File file = new File(getDataFolder(), "config.yml");
        if (!file.isFile()) {
            saveDefaultConfig();
            notes.add("config.yml was missing — a fresh one was written.");
        }
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            notes.add("config.yml could not be parsed: " + e.getMessage());
            notes.add("Nothing was changed; the running settings are untouched.");
            return ReloadResult.failed(notes);
        }
        // The other files get the same treatment before anything is swapped —
        // a typo in sounds.yml must not leave messages.yml half-reloaded.
        for (String problem : new String[]{
                messages.probe(), guis.probe(), sounds.probe(), botTuning.probe()}) {
            if (problem != null) {
                notes.add(problem);
                notes.add("Nothing was changed; the running settings are untouched.");
                return ReloadResult.failed(notes);
            }
        }

        String wizard = setup.activeName();
        int watching = spectate.spectators().size();
        int running = sessions.all().size();
        // What in this reload describes arenas that are already in the ground?
        List<String> structural = structuralChanges(candidate);
        boolean interrupts = !structural.isEmpty() && (running > 0 || wizard != null);
        if (interrupts && !force) {
            notes.add("This reload changes something about arenas that are already built:");
            structural.forEach(change -> notes.add("  • " + change));
            if (running > 0) {
                notes.add(running + " player(s) are practicing and would be restored first.");
            }
            if (wizard != null) {
                notes.add("The setup wizard is open on '" + wizard
                        + "' (unsaved changes would be lost).");
            }
            notes.add("Run /practice reload confirm to go ahead.");
            return ReloadResult.confirm(notes);
        }

        // Config and arenas are rebuilt BEFORE any session is ended: if either
        // step fails, the reload reports failure with the old settings still
        // running — and nobody's run was terminated for nothing.
        PCConfig previous = pcConfig;
        try {
            reloadConfig();
            notes.addAll(migrateConfig());
            pcConfig = new PCConfig(this, getConfig());
            notes.addAll(messages.load());
            notes.addAll(guis.load());
            notes.addAll(sounds.load());
            notes.addAll(botTuning.load());
        } catch (RuntimeException e) {
            pcConfig = previous;
            notes.add("Config could not be applied (" + e + ").");
            notes.add("The previous settings are still in effect.");
            return ReloadResult.failed(notes);
        }
        try {
            notes.addAll(templates.loadAll());
        } catch (RuntimeException e) {
            notes.add("Arenas could not be reloaded: " + e);
            return ReloadResult.failed(notes);
        }
        // The value sweep: names and numbers that parsed as YAML but will not
        // resolve in game. Warnings, not refusals — every one falls back.
        List<String> problems = validateConfigs();
        if (!problems.isEmpty()) {
            notes.add(problems.size() + " value problem(s) found — each falls back"
                    + " to its default:");
            problems.forEach(problem -> notes.add("  • " + problem));
        }
        // loadAll may have written arenas of its own (bundled/generated
        // installs, arena.yml upgrades), so the fingerprint is taken again
        // rather than reusing the one the comparison saw.
        arenaFingerprint = templates.fingerprint();
        pendingArenaFingerprint = arenaFingerprint;

        if (interrupts) {
            if (wizard != null) {
                setup.cancelAll();
                notes.add("Canceled the setup wizard on '" + wizard + "'.");
            }
            if (watching > 0) {
                spectate.endAllSync();
                notes.add("Ended and restored " + watching + " spectator(s).");
            }
            if (running > 0) {
                sessions.endAllSync();
                notes.add("Ended and restored " + running + " session(s).");
            }
        } else if (running > 0 || watching > 0) {
            notes.add("Applied live — " + running + " run(s) and " + watching
                    + " spectator(s) carried on uninterrupted.");
        }

        boards.restartTask();
        speedometer.restartTask();
        inventoryValidator.restartTask();
        rush.restartTask();
        pvpBot.restartTask();
        rushBots.restartTask();
        spectate.restartTask();
        // Sidebars are rebuilt from the freshly loaded text rather than left
        // showing the old title until their next natural teardown.
        boards.refreshAll();
        // Gamerules, difficulty and time go onto the world that is already
        // loaded, so changing one of those never needs a regeneration.
        worldService.applyWorldSettings();

        if (!pcConfig.worldName().equals(previous.worldName())) {
            notes.add("world.name changed '" + previous.worldName() + "' → '" + pcConfig.worldName()
                    + "'. Run /practice world regen confirm to build it — the old world is"
                    + " still the one in use until you do.");
        }
        notes.add("Loaded " + templates.all().size() + " arena(s), "
                + templates.completeTemplates().size() + " playable.");
        return ReloadResult.ok(notes);
    }

    /**
     * The changes in a candidate config.yml that describe arenas already
     * standing in the world, plus whether the arena files themselves moved.
     * Everything else can be swapped under a running session safely.
     *
     * @return one human-readable line per change, empty when the reload is
     *         purely a matter of settings
     */
    private List<String> structuralChanges(YamlConfiguration candidate) {
        List<String> changes = new ArrayList<>();
        compare(changes, "world.name",
                pcConfig.worldName(), candidate.getString("world.name", pcConfig.worldName()));
        compare(changes, "grid.spacing",
                pcConfig.gridSpacing(), candidate.getInt("grid.spacing", pcConfig.gridSpacing()));
        compare(changes, "grid.base-y",
                pcConfig.baseY(), candidate.getInt("grid.base-y", pcConfig.baseY()));
        compare(changes, "grid.max-schematic-size", pcConfig.maxSchematicSize(),
                candidate.getInt("grid.max-schematic-size", pcConfig.maxSchematicSize()));
        // Deliberately not committed here: a reload that goes on to fail must
        // still see the change next time, or the second attempt would apply
        // edited arenas under running sessions without asking.
        pendingArenaFingerprint = templates.fingerprint();
        if (!pendingArenaFingerprint.equals(arenaFingerprint)) {
            changes.add("the arena files on disk changed");
        }
        return changes;
    }

    private static void compare(List<String> changes, String key, Object was, Object now) {
        if (!was.equals(now)) {
            changes.add(key + ": " + was + " → " + now);
        }
    }

    /** What the arena folder looked like at the last successful load. */
    private String arenaFingerprint = "";
    private String pendingArenaFingerprint = "";

    /**
     * Writes a single config.yml value and reapplies it, so admin commands can
     * change settings without anyone hand-editing the file and reloading.
     */
    public void setConfigValue(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        pcConfig = new PCConfig(this, getConfig());
    }

    public SoundConfig sounds() {
        return sounds;
    }

    public BotTuning botTuning() {
        return botTuning;
    }

    public PCConfig pcConfig() {
        return pcConfig;
    }

    public Messages messages() {
        return messages;
    }

    public ModeRegistry modes() {
        return modes;
    }

    public PracticeWorldService worldService() {
        return worldService;
    }

    public SlotAllocator allocator() {
        return allocator;
    }

    public SchematicService schematics() {
        return schematics;
    }

    public TemplateRegistry templates() {
        return templates;
    }

    public SnapshotStore snapshots() {
        return snapshots;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public StatsStore stats() {
        return stats;
    }

    public BoardService boards() {
        return boards;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public SetupManager setup() {
        return setup;
    }

    public MenuItemService menuItems() {
        return menuItems;
    }

    public LeaveService leaveService() {
        return leaveService;
    }

    public GuiConfig guis() {
        return guis;
    }

    public SpeedometerService speedometer() {
        return speedometer;
    }

    public ChatPrompts prompts() {
        return prompts;
    }

    public SettingsService settings() {
        return settings;
    }

    public RushService rush() {
        return rush;
    }

    public InventoryValidator validator() {
        return inventoryValidator;
    }

    public PvpBotService pvpBot() {
        return pvpBot;
    }

    public me.beekrbonkr.practicecore.rushbot.RushBotService rushBots() {
        return rushBots;
    }

    public SpectateService spectate() {
        return spectate;
    }
}
