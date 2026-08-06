package me.beekrbonkr.practicecore;

import me.beekrbonkr.practicecore.board.BoardService;
import me.beekrbonkr.practicecore.command.PracticeCommand;
import me.beekrbonkr.practicecore.grid.SlotAllocator;
import me.beekrbonkr.practicecore.listener.BlockListener;
import me.beekrbonkr.practicecore.listener.ConnectionListener;
import me.beekrbonkr.practicecore.listener.InteractListener;
import me.beekrbonkr.practicecore.listener.MovementListener;
import me.beekrbonkr.practicecore.listener.ProtectionListener;
import me.beekrbonkr.practicecore.listener.TeleportListener;
import me.beekrbonkr.practicecore.mode.BridgingMode;
import me.beekrbonkr.practicecore.mode.ModeRegistry;
import me.beekrbonkr.practicecore.schematic.SchematicService;
import me.beekrbonkr.practicecore.session.SessionManager;
import me.beekrbonkr.practicecore.setup.SetupManager;
import me.beekrbonkr.practicecore.snapshot.SnapshotStore;
import me.beekrbonkr.practicecore.stats.StatsStore;
import me.beekrbonkr.practicecore.template.TemplateRegistry;
import me.beekrbonkr.practicecore.world.PracticeWorldService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PracticeCorePlugin extends JavaPlugin {

    private PCConfig pcConfig;
    private ModeRegistry modes;
    private PracticeWorldService worldService;
    private SlotAllocator allocator;
    private SchematicService schematics;
    private TemplateRegistry templates;
    private SnapshotStore snapshots;
    private StatsStore stats;
    private BoardService boards;
    private SessionManager sessions;
    private SetupManager setup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pcConfig = new PCConfig(getConfig());

        modes = new ModeRegistry();
        modes.register(new BridgingMode());

        worldService = new PracticeWorldService(this);
        worldService.recreate();

        allocator = new SlotAllocator();
        schematics = new SchematicService();
        templates = new TemplateRegistry(this);
        templates.loadAll();
        snapshots = new SnapshotStore(this);
        stats = new StatsStore(this);
        boards = new BoardService(this);
        sessions = new SessionManager(this);
        setup = new SetupManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new MovementListener(this), this);
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new InteractListener(this), this);
        pm.registerEvents(new TeleportListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);

        PracticeCommand command = new PracticeCommand(this);
        PluginCommand pluginCommand = getCommand("practice");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        boards.startTask();
        getLogger().info("PracticeCore enabled — practice world '" + pcConfig.worldName() + "' ready.");
    }

    @Override
    public void onDisable() {
        // Order matters: wizard first (it holds a slot), then sessions
        // (restores every player synchronously), then UI and pending writes.
        if (setup != null) {
            setup.cancelAll();
        }
        if (sessions != null) {
            sessions.endAllSync();
        }
        if (boards != null) {
            boards.shutdown();
        }
        if (stats != null) {
            stats.flushSync();
        }
    }

    public PCConfig pcConfig() {
        return pcConfig;
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
}
