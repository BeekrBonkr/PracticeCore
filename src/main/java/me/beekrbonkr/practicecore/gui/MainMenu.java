package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.command.PracticeCommand;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The hub every other menu hangs off. */
public final class MainMenu extends Menu {

    /** Cached per open — computing it loads the snapshot file. */
    private String destination;

    public MainMenu(PracticeCorePlugin plugin, Player viewer) {
        super(plugin, viewer, null);
    }

    @Override
    protected Component title() {
        return text("gui.main.title");
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("main", 4);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("main.buttons." + button, def);
    }

    private boolean shown(String button) {
        return plugin.guis().buttonEnabled("main.buttons." + button);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("main.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        if (shown("play")) {
            set(slot("play", 10), joinIcon(), event -> {
                click();
                later(() -> {
                    if (plugin.guis().categoriesEnabled()) {
                        new CategoryMenu(plugin, viewer, this).open();
                    } else {
                        new ArenaMenu(plugin, viewer, this, null).open();
                    }
                });
            });
        }
        if (shown("random")) {
            set(slot("random", 11), randomIcon(), event -> joinRandom());
        }
        // STYLE-GUIDE: needs logic change (R53) — shown as disabled would
        // reveal the button to players without practicecore.leaderboard.
        if (shown("leaderboards") && viewer.hasPermission("practicecore.leaderboard")) {
            set(slot("leaderboards", 12), leaderboardIcon(), event -> {
                click();
                later(() -> {
                    // Same shape as Play: a category picker first, unless the
                    // admin has flattened the menus.
                    if (plugin.guis().categoriesEnabled()) {
                        new LeaderboardCategoryMenu(plugin, viewer, this).open();
                    } else {
                        new LeaderboardMenu(plugin, viewer, this).open();
                    }
                });
            });
        }
        if (shown("stats")) {
            set(slot("stats", 13), statsIcon(), event -> {
                click();
                later(() -> new StatsMenu(plugin, viewer, this).open());
            });
        }
        // Contextual entries: only meaningful inside a session (R53).
        PracticeSession session = plugin.sessions().get(viewer.getUniqueId());
        if (shown("restart") && session != null) {
            set(slot("restart", 14), restartIcon(session), event -> restart());
        }
        if (shown("bot") && me.beekrbonkr.practicecore.pvpbot.PvpBotService.fightOf(session) != null) {
            set(slot("bot", 20), botIcon(), event -> {
                click();
                later(() -> new PvpBotSettingsMenu(plugin, viewer, this, session).open());
            });
        }
        // STYLE-GUIDE: needs logic change (R53) — same permission reveal as above.
        if (shown("spectate") && plugin.pcConfig().spectateEnabled()
                && viewer.hasPermission("practicecore.spectate")) {
            set(slot("spectate", 19), spectateIcon(), event -> {
                click();
                later(() -> new SpectateMenu(plugin, viewer, this).open());
            });
        }
        if (shown("sidebar")) {
            set(slot("sidebar", 15), scoreboardIcon(), event -> toggleScoreboard());
        }
        if (shown("help")) {
            set(slot("help", 16), helpIcon(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    PracticeCommand.sendHelp(plugin, viewer);
                });
            });
        }
        if (shown("settings")) {
            set(slot("settings", 21), settingsIcon(), event -> {
                click();
                later(() -> new SettingsMenu(plugin, viewer, this).open());
            });
        }
        if (shown("leave") && session != null) {
            set(slot("leave", 23), leaveIcon(), event -> {
                click();
                later(() -> {
                    viewer.closeInventory();
                    plugin.leaveService().leave(viewer);
                });
            });
        }
        nav("main");
    }

    // ---------------------------------------------------------------- icons

    private ItemStack joinIcon() {
        int count = plugin.templates().visibleTo(viewer).size();
        return Button.of(plugin, icon("play", Material.COMPASS))
                .name("gui.main.play.name")
                .lore("gui.main.play.lore", "count", String.valueOf(count))
                .hint("open")
                .build();
    }

    private ItemStack randomIcon() {
        Button button = Button.of(plugin, icon("random", Material.ENDER_EYE))
                .name("gui.main.random.name")
                .lore("gui.main.random.lore");
        if (plugin.templates().availableTo(viewer).isEmpty()) {
            button.disabled("gui.reason.no-arenas");
        } else {
            button.hint("play");
        }
        return button.build();
    }

    private ItemStack leaderboardIcon() {
        return Button.of(plugin, icon("leaderboards", Material.GOLD_INGOT))
                .name("gui.main.leaderboards.name")
                .lore("gui.main.leaderboards.lore")
                .hint("view")
                .build();
    }

    private ItemStack statsIcon() {
        return Button.of(plugin, icon("stats", Material.BOOK))
                .name("gui.main.stats.name")
                .lore("gui.main.stats.lore")
                .hint("view")
                .build();
    }

    private ItemStack restartIcon(PracticeSession session) {
        return Button.of(plugin, icon("restart", Material.CLOCK))
                .name("gui.main.restart.name")
                .lore("gui.main.restart.lore", "arena", session.template().displayName())
                .hint("restart")
                .build();
    }

    private ItemStack scoreboardIcon() {
        boolean on = plugin.stats().scoreboardEnabled(viewer.getUniqueId());
        return Button.of(plugin, icon("sidebar", Material.OAK_SIGN))
                .name("gui.main.sidebar.name")
                .lore("gui.main.sidebar.lore", plugin.messages().ref("state",
                        on ? "label.state.shown" : "label.state.hidden"))
                .glow(on)
                .hint("toggle")
                .build();
    }

    private ItemStack botIcon() {
        return Button.of(plugin, icon("bot", Material.ZOMBIE_HEAD))
                .name("gui.main.bot.name")
                .lore("gui.main.bot.lore")
                .hint("open")
                .build();
    }

    private ItemStack spectateIcon() {
        return Button.of(plugin, icon("spectate", Material.SPYGLASS))
                .name("gui.main.spectate.name")
                .lore("gui.main.spectate.lore")
                .hint("open")
                .build();
    }

    private ItemStack helpIcon() {
        return Button.of(plugin, icon("help", Material.PAPER))
                .name("gui.main.help.name")
                .lore("gui.main.help.lore")
                .hint("view")
                .build();
    }

    private ItemStack settingsIcon() {
        return Button.of(plugin, icon("settings", Material.COMPARATOR))
                .name("gui.main.settings.name")
                .lore("gui.main.settings.lore")
                .hint("open")
                .build();
    }

    private ItemStack leaveIcon() {
        return Button.of(plugin, icon("leave", Material.OAK_DOOR))
                .name("gui.main.leave.name")
                .lore("gui.main.leave.lore", "destination", destination())
                .hint("leave")
                .build();
    }

    /** Where the leave button will actually put them, spelled out up front. */
    private String destination() {
        if (destination != null) {
            return destination;
        }
        String server = plugin.pcConfig().leaveServer();
        destination = !server.isEmpty() ? server
                : plugin.snapshots().load(viewer.getUniqueId())
                        .map(snapshot -> snapshot.worldName())
                        .orElseGet(() -> plugin.leaveService().fallback().getWorld().getName());
        return destination;
    }

    // -------------------------------------------------------------- actions

    private void joinRandom() {
        List<ArenaTemplate> available = plugin.templates().availableTo(viewer);
        if (available.isEmpty()) {
            deny();
            return;
        }
        ArenaTemplate template = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        click();
        later(() -> {
            viewer.closeInventory();
            plugin.sessions().join(viewer, template);
        });
    }

    private void restart() {
        if (plugin.sessions().get(viewer.getUniqueId()) == null) {
            deny();
            return;
        }
        click();
        later(() -> {
            viewer.closeInventory();
            plugin.sessions().restart(viewer);
        });
    }

    private void toggleScoreboard() {
        boolean on = !plugin.stats().scoreboardEnabled(viewer.getUniqueId());
        plugin.stats().setScoreboardEnabled(viewer.getUniqueId(), on);
        plugin.boards().applyPreference(viewer);
        sound(on ? "menu.toggle-on" : "menu.toggle-off");
        refresh();
    }
}
