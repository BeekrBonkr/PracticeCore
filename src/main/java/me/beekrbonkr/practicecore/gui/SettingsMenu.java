package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.settings.SettingsService;
import me.beekrbonkr.practicecore.util.ItemBuilder;
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
                        plugin.settings().toggleNightVision(viewer);
                        click();
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
        backButton(plugin.guis().slot("settings.back", 18));
        closeButton(plugin.guis().slot("settings.close", 26));
    }

    private ItemStack nightVisionIcon() {
        boolean on = plugin.settings().nightVision(viewer.getUniqueId());
        return ItemBuilder.of(plugin.guis()
                        .buttonMaterial("settings.buttons.night-vision", Material.GOLDEN_CARROT))
                .name(name("gui.settings.night-vision.name"))
                .lore(lore("gui.settings.night-vision.lore", plugin.messages().ref("state",
                        on ? "gui.settings.night-vision.state-on"
                           : "gui.settings.night-vision.state-off")))
                .glow(on)
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
        return ItemBuilder.of(icon)
                .name(name("gui.settings.wool-color.name"))
                .lore(lore("gui.settings.wool-color.lore",
                        "color", color == null
                                ? raw("gui.settings.wool-color.default")
                                : color.name().toLowerCase(Locale.ROOT).replace('_', ' ')))
                .glow(color != null)
                .build();
    }

    private ItemStack timeIcon() {
        SettingsService.TimeOfDay time = plugin.settings().timeOfDay(viewer.getUniqueId());
        return ItemBuilder.of(plugin.guis().buttonMaterial("settings.buttons.time", Material.CLOCK))
                .name(name("gui.settings.time.name"))
                .lore(lore("gui.settings.time.lore", "time", raw(time.messageKey())))
                .glow(time != SettingsService.TimeOfDay.DEFAULT)
                .build();
    }
}
