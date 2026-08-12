package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "Are you sure?" for arena deletion — the GUI twin of the command's
 * {@code confirm} argument. Layout and text are fixed — see {@link SetupGui}.
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
        set(13, SetupGui.button(Material.RED_BED, arena, NamedTextColor.WHITE,
                "Deleting removes the arena folder,",
                "its schematic, its leaderboard and",
                "every recorded time on it.",
                "This cannot be undone."));
        set(11, SetupGui.button(Material.LIME_CONCRETE, "Delete Forever", NamedTextColor.GREEN),
                event -> {
                    click();
                    later(this::delete);
                });
        set(15, SetupGui.button(Material.RED_CONCRETE, "Keep It", NamedTextColor.RED),
                event -> {
                    click();
                    later(() -> parent().open());
                });
    }

    private void delete() {
        viewer.closeInventory();
        boolean deleted = plugin.templates().deleteCompletely(arena, wiped ->
                viewer.sendMessage(Component.text("Leaderboard for '" + arena + "' cleared ("
                        + wiped + " player record(s)).", NamedTextColor.GREEN)));
        viewer.sendMessage(deleted
                ? Component.text("Deleted arena '" + arena + "'. Clearing its recorded times…",
                        NamedTextColor.GREEN)
                : Component.text("Could not delete '" + arena + "'.", NamedTextColor.RED));
        new ArenaListMenu(plugin, viewer).open();
    }
}
