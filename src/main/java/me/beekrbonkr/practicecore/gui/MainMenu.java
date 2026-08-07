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
        return 4;
    }

    @Override
    protected void render() {
        border();
        set(10, joinIcon(), event -> {
            click();
            later(() -> new ArenaMenu(plugin, viewer, this).open());
        });
        set(11, randomIcon(), event -> joinRandom());
        if (viewer.hasPermission("practicecore.leaderboard")) {
            set(12, leaderboardIcon(), event -> {
                click();
                later(() -> new LeaderboardMenu(plugin, viewer, this).open());
            });
        }
        set(13, statsIcon(), event -> {
            click();
            later(() -> new StatsMenu(plugin, viewer, this).open());
        });
        set(14, restartIcon(), event -> restart());
        set(15, scoreboardIcon(), event -> toggleScoreboard());
        set(16, helpIcon(), event -> {
            click();
            later(() -> {
                viewer.closeInventory();
                PracticeCommand.sendHelp(plugin, viewer);
            });
        });
        set(22, leaveIcon(), event -> {
            sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
            later(() -> {
                viewer.closeInventory();
                plugin.leaveService().leave(viewer);
            });
        });
    }

    // ---------------------------------------------------------------- icons

    private ItemStack joinIcon() {
        int count = plugin.templates().visibleTo(viewer).size();
        return ItemBuilder.of(Material.COMPASS)
                .name(name("gui.main.play.name"))
                .lore(lore("gui.main.play.lore", "count", String.valueOf(count)))
                .build();
    }

    private ItemStack randomIcon() {
        return ItemBuilder.of(Material.ENDER_EYE)
                .name(name("gui.main.random.name"))
                .lore(lore("gui.main.random.lore"))
                .build();
    }

    private ItemStack leaderboardIcon() {
        return ItemBuilder.of(Material.GOLD_INGOT)
                .name(name("gui.main.leaderboards.name"))
                .lore(lore("gui.main.leaderboards.lore"))
                .build();
    }

    private ItemStack statsIcon() {
        return ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(name("gui.main.stats.name"))
                .lore(lore("gui.main.stats.lore"))
                .build();
    }

    private ItemStack restartIcon() {
        PracticeSession session = plugin.sessions().get(viewer.getUniqueId());
        return ItemBuilder.of(Material.CLOCK)
                .name(name("gui.main.restart.name"))
                .lore(session == null
                        ? lore("gui.main.restart.lore-idle")
                        : lore("gui.main.restart.lore", "arena", session.template().displayName()))
                .build();
    }

    private ItemStack scoreboardIcon() {
        boolean on = plugin.stats().scoreboardEnabled(viewer.getUniqueId());
        return ItemBuilder.of(on ? Material.ITEM_FRAME : Material.GLOW_ITEM_FRAME)
                .name(name("gui.main.sidebar.name"))
                .lore(lore("gui.main.sidebar.lore", plugin.messages().ref("state",
                        on ? "gui.main.sidebar.state-shown" : "gui.main.sidebar.state-hidden")))
                .glow(on)
                .build();
    }

    private ItemStack helpIcon() {
        return ItemBuilder.of(Material.PAPER)
                .name(name("gui.main.help.name"))
                .lore(lore("gui.main.help.lore"))
                .build();
    }

    private ItemStack leaveIcon() {
        return ItemBuilder.of(Material.OAK_DOOR)
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
