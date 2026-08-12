package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Entry point for the admin arena-setup GUI.
 *
 * Deliberately NOT configurable: unlike the player menus, nothing here reads
 * messages.yml or guis.yml — layout and text are fixed, so a half-edited
 * config can never break the tool used to fix things.
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

    static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
        ItemBuilder builder = ItemBuilder.of(material)
                .name(Component.text(name, color, TextDecoration.BOLD));
        for (String line : lore) {
            builder.lore(line.isEmpty()
                    ? Component.empty()
                    : Component.text(line, NamedTextColor.GRAY));
        }
        return builder.hideAttributes().build();
    }
}
