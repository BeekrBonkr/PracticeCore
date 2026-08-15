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
        List<PvpKit> kits = plugin.botTuning().kits().all();
        // CONTENT_SLOTS is laid out for a six-row menu; a shorter one has
        // fewer cells inside its border, and writing into the bottom row would
        // land tiles on top of the nav buttons.
        int lastUsable = rows() * 9 - 10;
        int capacity = 0;
        while (capacity < CONTENT_SLOTS.length && CONTENT_SLOTS[capacity] <= lastUsable) {
            capacity++;
        }
        // More kits than the gallery has cells is a config choice, not a bug —
        // say which ones are unreachable rather than dropping them silently.
        if (kits.size() > capacity) {
            plugin.getLogger().warning("pvpbot.yml defines " + kits.size()
                    + " kits but this gallery has room for " + capacity
                    + " — the rest are not shown. Raise pvpbot-kits.rows in guis.yml.");
        }
        for (int i = 0; i < kits.size() && i < capacity; i++) {
            PvpKit kit = kits.get(i);
            set(CONTENT_SLOTS[i], tile(kit, selected != null && kit.id().equals(selected.id())),
                    event -> {
                if (event.isRightClick()) {
                    click();
                    later(() -> new KitPreviewMenu(plugin, viewer, this, kit).open());
                    return;
                }
                PvpKit current = selectedKit();
                if (current != null && kit.id().equals(current.id())) {
                    deny();
                    return;
                }
                click();
                plugin.stats().setPref(viewer.getUniqueId(), "pvpbot.kit", kit.id());
                refresh();
            });
        }
        backButton(plugin.guis().slot("pvpbot-kits.back", 36));
        closeButton(plugin.guis().slot("pvpbot-kits.close", 40));
    }

    private PvpKit selectedKit() {
        PvpKit saved = plugin.botTuning().kits()
                .get(plugin.stats().pref(viewer.getUniqueId(), "pvpbot.kit", null));
        return saved != null ? saved : plugin.botTuning().defaultKit();
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

    static String kitName(PracticeCorePlugin plugin, PvpKit kit) {
        return plugin.botTuning().kits().displayName(kit);
    }

    static String pretty(String materialName) {
        String lower = materialName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
