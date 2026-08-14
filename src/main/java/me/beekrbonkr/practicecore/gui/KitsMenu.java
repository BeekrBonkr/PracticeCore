package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.pvpbot.PvpKit;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The PvP kit gallery: every built-in preset as a tile, the selected one
 * glowing. Left-click picks a kit (persisted immediately — the bot fight
 * re-reads it when the menus close); right-click opens the full preview
 * window laid out like a real inventory.
 */
public final class KitsMenu extends Menu {

    public KitsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.pvpbot.kits.title");
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("pvpbot-kits", 5);
    }

    @Override
    protected void render() {
        border();
        PvpKit selected = selectedKit();
        PvpKit[] kits = PvpKit.values();
        for (int i = 0; i < kits.length && i < CONTENT_SLOTS.length; i++) {
            PvpKit kit = kits[i];
            set(CONTENT_SLOTS[i], tile(kit, kit == selected), event -> {
                if (event.isRightClick()) {
                    click();
                    later(() -> new KitPreviewMenu(plugin, viewer, this, kit).open());
                    return;
                }
                if (kit == selectedKit()) {
                    deny();
                    return;
                }
                click();
                plugin.stats().setPref(viewer.getUniqueId(), "pvpbot.kit", kit.name());
                refresh();
            });
        }
        backButton(plugin.guis().slot("pvpbot-kits.back", 36));
        closeButton(plugin.guis().slot("pvpbot-kits.close", 40));
    }

    private PvpKit selectedKit() {
        String saved = plugin.stats().pref(viewer.getUniqueId(), "pvpbot.kit", null);
        try {
            return saved == null ? PvpKit.SWORD : PvpKit.valueOf(saved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PvpKit.SWORD;
        }
    }

    private ItemStack tile(PvpKit kit, boolean selected) {
        List<Component> lines = new ArrayList<>(contentsSummary(kit));
        if (kit.hasBlocks()) {
            lines.addAll(lore("gui.pvpbot.kits.blocks-note"));
        }
        lines.add(Component.empty());
        lines.addAll(lore(selected
                ? "gui.pvpbot.kits.selected" : "gui.pvpbot.kits.click-hint"));
        return ItemBuilder.of(kit.icon())
                .name(name("gui.pvpbot.kits.entry-name", "kit", kitName(plugin, kit)))
                .lore(lines)
                .glow(selected)
                .hideAttributes()
                .build();
    }

    /** "8× Golden Apple"-style lines, armor first, capped so tiles stay tidy. */
    private List<Component> contentsSummary(PvpKit kit) {
        Map<Integer, ItemStack> contents = kit.kit();
        List<Component> lines = new ArrayList<>();
        ItemStack helmet = contents.get(PvpKit.HELMET);
        lines.add(plugin.messages().name("gui.pvpbot.kits.armor-line", "armor",
                helmet == null
                        ? plugin.messages().raw("gui.none")
                        : pretty(helmet.getType().name().replace("_HELMET", ""))));
        Map<Material, Integer> counted = new LinkedHashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : contents.entrySet()) {
            if (entry.getKey() < 36) {
                counted.merge(entry.getValue().getType(), entry.getValue().getAmount(),
                        Integer::sum);
            }
        }
        int shown = 0;
        for (Map.Entry<Material, Integer> entry : counted.entrySet()) {
            if (shown++ >= 6) {
                lines.add(plugin.messages().name("gui.pvpbot.kits.more-line",
                        "count", String.valueOf(counted.size() - 6)));
                break;
            }
            lines.add(plugin.messages().name("gui.pvpbot.kits.item-line",
                    "amount", String.valueOf(entry.getValue()),
                    "item", pretty(entry.getKey().name())));
        }
        return lines;
    }

    /** The kit's display name from messages.yml — plain text, no tags. */
    static String kitName(PracticeCorePlugin plugin, PvpKit kit) {
        return plugin.messages().raw("gui.pvpbot.kit.option."
                + kit.name().toLowerCase(Locale.ROOT));
    }

    static String pretty(String materialName) {
        String lower = materialName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
