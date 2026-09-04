package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.settings.SettingsService;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Per-player settings: permanent night vision, kit wool color and the arena's
 * (client-side) time of day. Choices persist in playerdata and apply
 * immediately when the player is mid-session.
 */
public final class SettingsMenu extends Menu {

    public SettingsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.settings.title");
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("settings", 3);
    }

    @Override
    protected void render() {
        border();
        if (plugin.guis().buttonEnabled("settings.buttons.night-vision")) {
            set(plugin.guis().slot("settings.buttons.night-vision", 11), nightVisionIcon(),
                    event -> {
                        boolean on = !plugin.settings().nightVision(viewer.getUniqueId());
                        plugin.settings().toggleNightVision(viewer);
                        sound(on ? "menu.toggle-on" : "menu.toggle-off");
                        refresh();
                    });
        }
        if (plugin.guis().buttonEnabled("settings.buttons.wool-color")) {
            set(plugin.guis().slot("settings.buttons.wool-color", 13), woolIcon(), event -> {
                plugin.settings().cycleWoolColor(viewer, !event.isRightClick());
                click();
                refresh();
            });
        }
        if (plugin.guis().buttonEnabled("settings.buttons.time")) {
            set(plugin.guis().slot("settings.buttons.time", 15), timeIcon(), event -> {
                plugin.settings().cycleTimeOfDay(viewer);
                click();
                refresh();
            });
        }
        nav("settings");
    }

    private ItemStack nightVisionIcon() {
        boolean on = plugin.settings().nightVision(viewer.getUniqueId());
        return Button.of(plugin, plugin.guis()
                        .buttonMaterial("settings.buttons.night-vision", Material.GOLDEN_CARROT))
                .name("gui.settings.night-vision.name")
                .lore("gui.settings.night-vision.lore", plugin.messages().ref("state",
                        on ? "label.state.on" : "label.state.off"))
                .glow(on)
                .hint("toggle")
                .build();
    }

    private ItemStack woolIcon() {
        DyeColor color = plugin.settings().woolColor(viewer.getUniqueId());
        Material icon = color == null
                ? plugin.guis().buttonMaterial("settings.buttons.wool-color", Material.WHITE_WOOL)
                : SettingsService.woolOf(color);
        if (icon == null) {
            icon = Material.WHITE_WOOL;
        }
        return Button.of(plugin, icon)
                .name("gui.settings.wool-color.name")
                .lore("gui.settings.wool-color.lore",
                        "color", color == null
                                ? raw("gui.settings.wool-color.default")
                                : color.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .hint("cycle")
                .rightHint("cycle-back")
                .build();
    }

    private ItemStack timeIcon() {
        SettingsService.TimeOfDay time = plugin.settings().timeOfDay(viewer.getUniqueId());
        return Button.of(plugin, plugin.guis()
                        .buttonMaterial("settings.buttons.time", Material.DAYLIGHT_DETECTOR))
                .name("gui.settings.time.name")
                .lore("gui.settings.time.lore", "time", raw(time.messageKey()))
                .hint("cycle")
                .build();
    }
}
