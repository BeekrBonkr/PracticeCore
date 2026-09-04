package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Entry point for the admin arena-setup GUI.
 *
 * Deliberately NOT configurable: unlike the player menus, nothing here reads
 * its text from messages.yml — the words are fixed, so a half-edited config
 * can never break the tool used to fix things. The look still follows the
 * UI style guide (R25): controls go through {@link Button}, colors carry the
 * shared meanings, and nav sits where it does in every other menu.
 */
public final class SetupGui {

    private SetupGui() {
    }

    /** The wizard's control panel when a setup is open, else the arena list. */
    public static void open(PracticeCorePlugin plugin, Player admin) {
        if (plugin.setup().isAdmin(admin.getUniqueId())) {
            new SetupActionsMenu(plugin, admin).open();
        } else {
            new ArenaListMenu(plugin, admin).open();
        }
    }

    // ------------------------------------------- fixed-style item shorthands

    static Component title(String text) {
        return Component.text(text, NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
    }

    /**
     * A control with a fixed English label. {@code color} is the name color
     * by control type (R27): white for most, green for go/save, red for
     * exit/cancel/destructive, yellow for navigation. Description lines are
     * gray; an empty string is a blank separator.
     */
    static Button control(PracticeCorePlugin plugin, Material material, String name,
                          NamedTextColor color, String... lore) {
        Button button = Button.of(plugin, material)
                .name(Component.text(name, color, TextDecoration.BOLD))
                .hideAttributes();
        for (String line : lore) {
            button.line(gray(line));
        }
        return button;
    }

    static Component gray(String text) {
        return text.isEmpty() ? Component.empty() : Component.text(text, NamedTextColor.GRAY);
    }

    /** A state line: {@code Label: value}, gray label, white value (R31). */
    static Component state(String label, String value) {
        return state(label, Component.text(value, NamedTextColor.WHITE));
    }

    static Component state(String label, Component value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(value);
    }
}
