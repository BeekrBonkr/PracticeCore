package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.command.PracticeCommand;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The hub every other menu hangs off. */
public final class MainMenu extends Menu {

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
        if (shown("leaderboards") && viewer.hasPermission("practicecore.leaderboard")) {
            set(slot("leaderboards", 12), leaderboardIcon(), event -> {
                click();
                later(() -> new LeaderboardMenu(plugin, viewer, this).open());
            });
        }
        if (shown("stats")) {
            set(slot("stats", 13), statsIcon(), event -> {
                click();
                later(() -> new StatsMenu(plugin, viewer, this).open());
            });
        }
        if (shown("restart")) {
            set(slot("restart", 14), restartIcon(), event -> restart());
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
        if (shown("leave")) {
            set(slot("leave", 23), leaveIcon(), event -> {
                sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
                later(() -> {
                    viewer.closeInventory();
                    plugin.leaveService().leave(viewer);
                });
            });
        }
    }

    // ---------------------------------------------------------------- icons

    private ItemStack joinIcon() {
        int count = plugin.templates().visibleTo(viewer).size();
        return ItemBuilder.of(icon("play", Material.COMPASS))
                .name(name("gui.main.play.name"))
                .lore(lore("gui.main.play.lore", "count", String.valueOf(count)))
                .build();
    }

    private ItemStack randomIcon() {
        return ItemBuilder.of(icon("random", Material.ENDER_EYE))
                .name(name("gui.main.random.name"))
                .lore(lore("gui.main.random.lore"))
                .build();
    }

    private ItemStack leaderboardIcon() {
        return ItemBuilder.of(icon("leaderboards", Material.GOLD_INGOT))
                .name(name("gui.main.leaderboards.name"))
                .lore(lore("gui.main.leaderboards.lore"))
                .build();
    }

    private ItemStack statsIcon() {
        return ItemBuilder.of(icon("stats", Material.WRITABLE_BOOK))
                .name(name("gui.main.stats.name"))
                .lore(lore("gui.main.stats.lore"))
                .build();
    }

    private ItemStack restartIcon() {
        PracticeSession session = plugin.sessions().get(viewer.getUniqueId());
        return ItemBuilder.of(icon("restart", Material.CLOCK))
                .name(name("gui.main.restart.name"))
                .lore(session == null
                        ? lore("gui.main.restart.lore-idle")
                        : lore("gui.main.restart.lore", "arena", session.template().displayName()))
                .build();
    }

    private ItemStack scoreboardIcon() {
        boolean on = plugin.stats().scoreboardEnabled(viewer.getUniqueId());
        return ItemBuilder.of(on
                        ? icon("sidebar", Material.ITEM_FRAME)
                        : plugin.guis().material("main.buttons.sidebar.material-off",
                                Material.GLOW_ITEM_FRAME))
                .name(name("gui.main.sidebar.name"))
                .lore(lore("gui.main.sidebar.lore", plugin.messages().ref("state",
                        on ? "gui.main.sidebar.state-shown" : "gui.main.sidebar.state-hidden")))
                .glow(on)
                .build();
    }

    private ItemStack helpIcon() {
        return ItemBuilder.of(icon("help", Material.PAPER))
                .name(name("gui.main.help.name"))
                .lore(lore("gui.main.help.lore"))
                .build();
    }

    private ItemStack settingsIcon() {
        return ItemBuilder.of(icon("settings", Material.COMPARATOR))
                .name(name("gui.main.settings.name"))
                .lore(lore("gui.main.settings.lore"))
                .build();
    }

    private ItemStack leaveIcon() {
        return ItemBuilder.of(icon("leave", Material.OAK_DOOR))
                .name(name("gui.main.leave.name"))
                .lore(lore("gui.main.leave.lore", "destination", destination()))
                .build();
    }

    /** Where the leave button will actually put them, spelled out up front. */
    private String destination() {
        String server = plugin.pcConfig().leaveServer();
        if (!server.isEmpty()) {
            return server;
        }
        return plugin.snapshots().load(viewer.getUniqueId())
                .map(snapshot -> snapshot.worldName())
                .orElseGet(() -> plugin.leaveService().fallback().getWorld().getName());
    }

    // -------------------------------------------------------------- actions

    private void joinRandom() {
        List<ArenaTemplate> available = plugin.templates().availableTo(viewer);
        if (available.isEmpty()) {
            deny();
            later(() -> {
                viewer.closeInventory();
                plugin.messages().send(viewer, "arena.none-available");
            });
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
        sound(Sound.UI_BUTTON_CLICK, 0.6f, on ? 1.6f : 1.0f);
        refresh();
    }
}
