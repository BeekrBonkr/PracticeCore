package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "Are you sure?" for arena deletion — the GUI twin of the command's
 * {@code confirm} argument, in the confirm-menu shape (style guide R57):
 * the subject in the middle, the destructive choice in red on the left,
 * the safe one in yellow on the right, and Close also keeping the arena.
 * Text is fixed — see {@link SetupGui}.
 */
final class ConfirmDeleteMenu extends Menu {

    private final String arena;

    ConfirmDeleteMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, String arena) {
        super(plugin, viewer, parent);
        this.arena = arena;
    }

    @Override
    protected Component title() {
        return SetupGui.title("Delete " + arena + "?");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        border();
        set(13, Button.of(plugin, Material.RED_BED)
                .name(Component.text(arena, NamedTextColor.WHITE, TextDecoration.BOLD))
                .line(SetupGui.gray("Deleting removes the arena folder,"))
                .line(SetupGui.gray("its schematic, its leaderboard and"))
                .line(SetupGui.gray("every recorded time on it."))
                .line(Component.text("This cannot be undone.", NamedTextColor.RED))
                .build());
        set(11, SetupGui.control(plugin, Material.LAVA_BUCKET, "Delete Forever", NamedTextColor.RED)
                .hint("confirm")
                .build(), event -> {
            sound("menu.select");
            later(this::delete);
        });
        set(15, SetupGui.control(plugin, Material.BARRIER, "Keep It", NamedTextColor.YELLOW,
                        "Nothing changes.")
                .hint("cancel")
                .build(), event -> {
            click();
            later(() -> parent().open());
        });
        closeButton(navSlot("admin.close", 8));
    }

    private void delete() {
        viewer.closeInventory();
        boolean deleted = plugin.templates().deleteCompletely(arena, wiped ->
                plugin.messages().done(viewer, "Leaderboard for " + arena + " cleared ("
                        + wiped + " player record(s))."));
        if (deleted) {
            plugin.messages().done(viewer, "Deleted arena " + arena + ". Clearing its recorded times.");
        } else {
            plugin.messages().problem(viewer, "Could not delete " + arena + ".");
        }
        new ArenaListMenu(plugin, viewer).open();
    }
}
