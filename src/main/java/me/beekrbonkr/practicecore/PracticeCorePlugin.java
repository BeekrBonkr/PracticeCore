package me.beekrbonkr.practicecore;

import me.beekrbonkr.practicecore.board.BoardService;
import me.beekrbonkr.practicecore.command.PracticeCommand;
import me.beekrbonkr.practicecore.config.GuiConfig;
import me.beekrbonkr.practicecore.config.ReloadResult;
import me.beekrbonkr.practicecore.config.Versions;
import me.beekrbonkr.practicecore.config.YamlMigrator;
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
    private SpectateService spectate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig().forEach(note -> getLogger().info(note));
        pcConfig = new PCConfig(getConfig());
        messages = new Messages(this);
        messages.load().forEach(note -> getLogger().info(note));
        guis = new GuiConfig(this);
        guis.load().forEach(note -> getLogger().info(note));

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

        boards.startTask();
        speedometer.startTask();
        inventoryValidator.startTask();
        rush.startTask();
        pvpBot.startTask();
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
        if (stats != null) {
            stats.flushSync();
        }
    }

    /**
     * Re-reads config.yml and every arena folder.
     *
     * The whole point of the ceremony below is that a reload either fully
     * succeeds or changes nothing:
     * <ol>
     *   <li>config.yml is parsed on a throwaway object first — Bukkit's own
     *       {@code reloadConfig()} swallows a syntax error and hands back an
     *       empty config, which would quietly reset every setting to its
     *       default on a live server.</li>
     *   <li>Live sessions are ended and restored before anything changes,
     *       because grid spacing, base Y and arena definitions all describe
     *       arenas that are already pasted. That needs {@code force}.</li>
     *   <li>The previous {@link PCConfig} is kept until the new one has been
     *       built without throwing.</li>
     * </ol>
     *
     * @param force proceed even though it will end live sessions
     */
    private List<String> migrateConfig() {
        return new YamlMigrator(this, "config", "config.yml",
                new File(getDataFolder(), "config.yml"), Versions.CONFIG,
                PracticeCorePlugin::configSteps).run(getConfig());
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
        // v3 added scoreboard.server-ip — purely additive, the migrator's
        // top-up writes it (with its comments) into older files by itself.
    }

    public ReloadResult reload(boolean force) {
        List<String> notes = new ArrayList<>();
        File file = new File(getDataFolder(), "config.yml");
        if (!file.isFile()) {
            saveDefaultConfig();
            notes.add("config.yml was missing — a fresh one was written.");
        }
        try {
            new YamlConfiguration().load(file);
        } catch (IOException | InvalidConfigurationException e) {
            notes.add("config.yml could not be parsed: " + e.getMessage());
            notes.add("Nothing was changed; the running settings are untouched.");
            return ReloadResult.failed(notes);
        }

        String wizard = setup.activeName();
        int watching = spectate.spectators().size();
        int running = sessions.all().size();
        if ((running > 0 || wizard != null) && !force) {
            if (running > 0) {
                notes.add(running + " player(s) are practicing.");
            }
            if (wizard != null) {
                notes.add("The setup wizard is open on '" + wizard + "' (unsaved changes would be lost).");
            }
            notes.add("Reloading ends those first. Run /practice reload confirm to go ahead.");
            return ReloadResult.confirm(notes);
        }
        // Config and arenas are rebuilt BEFORE any session is ended: if either
        // step fails, the reload reports failure with the old settings still
        // running — and nobody's run was terminated for nothing.
        PCConfig previous = pcConfig;
        try {
            reloadConfig();
            notes.addAll(migrateConfig());
            pcConfig = new PCConfig(getConfig());
            notes.addAll(messages.load());
            notes.addAll(guis.load());
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
        boards.restartTask();
        speedometer.restartTask();
        inventoryValidator.restartTask();
        rush.restartTask();
        pvpBot.restartTask();
        spectate.restartTask();

        if (!pcConfig.worldName().equals(previous.worldName())) {
            notes.add("world.name changed '" + previous.worldName() + "' → '" + pcConfig.worldName()
                    + "'. Run /practice world regen to build it — the old world is still loaded.");
        }
        notes.add("Loaded " + templates.all().size() + " arena(s), "
                + templates.completeTemplates().size() + " playable.");
        return ReloadResult.ok(notes);
    }

    /**
     * Writes a single config.yml value and reapplies it, so admin commands can
     * change settings without anyone hand-editing the file and reloading.
     */
    public void setConfigValue(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        pcConfig = new PCConfig(getConfig());
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

    public SpectateService spectate() {
        return spectate;
    }
}
