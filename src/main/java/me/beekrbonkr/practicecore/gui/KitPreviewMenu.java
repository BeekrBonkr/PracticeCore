package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.pvpbot.PvpKit;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * The full kit preview: the loadout laid out the way it lands in a real
 * inventory — armor across the top, the three storage rows in the middle,
 * the hotbar along the bottom — so what you see is literally what you get.
 */
public final class KitPreviewMenu extends Menu {

    /** Where the armor pieces display: helmet, chestplate, leggings, boots. */
    private static final int[] ARMOR_SLOTS = {10, 11, 12, 13};

    private final PvpKit kit;

    public KitPreviewMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, PvpKit kit) {
        super(plugin, viewer, parent);
        this.kit = kit;
    }

    @Override
    protected Component title() {
        return text("gui.pvpbot.preview.title", "kit", KitsMenu.kitName(plugin, kit));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        border();
        Map<Integer, ItemStack> contents = kit.kit();

        // The banner is information, not a button: no bold, no hint (R32).
        set(4, Button.of(plugin, kit.icon())
                .name("gui.pvpbot.preview.banner-name", "kit", KitsMenu.kitName(plugin, kit))
                .lore("gui.pvpbot.preview.icon-lore")
                .hideAttributes()
                .build());

        // Armor row, helmet to boots, with placeholders for bare slots.
        int[] armorContents = {PvpKit.HELMET, PvpKit.CHESTPLATE, PvpKit.LEGGINGS, PvpKit.BOOTS};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack piece = contents.get(armorContents[i]);
            set(ARMOR_SLOTS[i], piece != null ? piece.clone()
                    : ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name(name("gui.pvpbot.preview.no-armor"))
                            .build());
        }

        // Storage rows: inventory slots 9-35 land on menu rows 2-4 unchanged
        // (menu slot = inventory slot + 9), so relative positions are exact.
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack item = contents.get(slot);
            if (item != null) {
                set(slot + 9, item.clone());
            } else {
                set(slot + 9, null);
            }
        }
        // Hotbar along the bottom row, exactly as slots 0-8 will sit.
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = contents.get(slot);
            if (item != null) {
                set(45 + slot, item.clone());
            } else {
                set(45 + slot, null);
            }
        }

        // The one permitted exception to bottom-row nav (R49): the whole grid
        // mirrors a real inventory, so Back and Close take the top corners.
        backButton(plugin.guis().slot("pvpbot-preview.back", 0));
        closeButton(plugin.guis().slot("pvpbot-preview.close", 8));
    }
}
