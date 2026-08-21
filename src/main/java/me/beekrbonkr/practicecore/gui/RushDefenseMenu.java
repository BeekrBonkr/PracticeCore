package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushDefense;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The bed-defense gallery: every preset config.yml defines as a tile, the
 * chosen one glowing. Cycling through a dozen materials on a single button
 * would be miserable, so the rush config menu hands the choice to this
 * picker instead; clicking a tile saves it and goes straight back.
 *
 * <p>Each tile spells its pyramid out layer by layer, outermost first —
 * which is the order a rusher meets them.
 */
public final class RushDefenseMenu extends Menu {

    private final String selected;
    private final Consumer<RushDefense> onPick;

    public RushDefenseMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                           String selected, Consumer<RushDefense> onPick) {
        super(plugin, viewer, parent);
        this.selected = selected;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return text("gui.rush.defenses.title");
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("rush-defenses", 5);
    }

    @Override
    protected void render() {
        border();
        List<RushDefense> presets = new ArrayList<>(plugin.pcConfig().rushDefenses().values());
        // CONTENT_SLOTS is laid out for six rows; a shorter menu has fewer
        // cells inside its border, and writing into the bottom row would put
        // tiles on top of the nav buttons.
        int lastUsable = rows() * 9 - 10;
        int capacity = 0;
        while (capacity < CONTENT_SLOTS.length && CONTENT_SLOTS[capacity] <= lastUsable) {
            capacity++;
        }
        if (presets.size() > capacity) {
            plugin.getLogger().warning("config.yml defines " + presets.size()
                    + " rush.defense-presets but this gallery has room for " + capacity
                    + " — the rest are not shown. Raise rush-defenses.rows in guis.yml.");
        }
        for (int i = 0; i < presets.size() && i < capacity; i++) {
            RushDefense preset = presets.get(i);
            boolean current = preset.id().equalsIgnoreCase(selected);
            set(CONTENT_SLOTS[i], tile(preset, current), event -> {
                if (current) {
                    deny();
                    return;
                }
                click();
                onPick.accept(preset);
                later(() -> {
                    if (parent() != null) {
                        parent().open();
                    } else {
                        viewer.closeInventory();
                    }
                });
            });
        }
        backButton(plugin.guis().slot("rush-defenses.back", 36));
        closeButton(plugin.guis().slot("rush-defenses.close", 44));
    }

    private ItemStack tile(RushDefense preset, boolean current) {
        List<Component> lore = new ArrayList<>(lore("gui.rush.defenses.tile-lore",
                "layers", String.valueOf(preset.reach())));
        // Outermost first: the order a rusher digs through them.
        List<Material> layers = preset.layers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            lore.add(plugin.messages().name("gui.rush.defenses.layer-line",
                    "depth", String.valueOf(layers.size() - i),
                    "material", pretty(layers.get(i))));
        }
        lore.addAll(lore(current ? "gui.rush.defenses.selected" : "gui.rush.defenses.select"));
        return ItemBuilder.of(preset.menuIcon())
                .name(name("gui.rush.defenses.tile-name", "preset", presetName(preset)))
                .lore(lore)
                .glow(current)
                .hideAttributes()
                .build();
    }

    /**
     * A preset's label: messages.yml wins so the shipped presets keep their
     * translations, and one an admin invented falls back to its own
     * configured name.
     */
    private String presetName(RushDefense preset) {
        String configured = plugin.messages().raw("gui.rush.defense.option." + preset.id());
        return configured.isEmpty() ? preset.displayName() : configured;
    }

    /** END_STONE → "End stone". */
    static String pretty(Material material) {
        String lower = material.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
