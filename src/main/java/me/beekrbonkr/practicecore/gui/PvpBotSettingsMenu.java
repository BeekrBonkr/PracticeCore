package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.pvpbot.BotPreset;
import me.beekrbonkr.practicecore.pvpbot.BotSettings;
import me.beekrbonkr.practicecore.pvpbot.PvpKit;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The in-arena PvP bot settings, reached from the practice menu's Bot
 * Settings button (shown only during a pvpbot session). While it is open the
 * bot stands still and holds fire (PvpBotListener pauses it); closing applies
 * everything — a changed kit re-deals the loadout, a changed gear tier
 * re-dresses the bot, the AI knobs just take effect.
 *
 * Every click writes its pref immediately (the same pattern as the rush
 * menu), so choices survive relogs and follow the player between arenas.
 */
public final class PvpBotSettingsMenu extends Menu {

    private final PracticeSession session;
    private BotSettings settings;

    public PvpBotSettingsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                              PracticeSession session) {
        super(plugin, viewer, parent);
        this.session = session;
        this.settings = BotSettings.load(plugin, viewer.getUniqueId());
    }

    @Override
    protected Component title() {
        return text("gui.pvpbot.title", "arena", session.template().displayName());
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("pvpbot", 5);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("pvpbot.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        kitButton(slot("kit", 11));
        presetButton(slot("preset", 13));
        gearButton(slot("gear", 15));
        cycle(slot("evasiveness", 19), "evasiveness", Material.FEATHER,
                () -> settings.evasiveness().name(), () -> settings.evasiveness().next().name());
        cpsButton(slot("cps", 20));
        cycle(slot("accuracy", 21), "accuracy", Material.TARGET,
                () -> settings.accuracy().name(), () -> settings.accuracy().next().name());
        cycle(slot("combos", 22), "combos", Material.RABBIT_FOOT,
                () -> settings.combos().name(), () -> settings.combos().next().name());
        cycle(slot("reach", 23), "reach", Material.STICK,
                () -> settings.reach().name(), () -> settings.reach().next().name());
        cycle(slot("aggression", 24), "aggression", Material.BLAZE_POWDER,
                () -> settings.aggression().name(), () -> settings.aggression().next().name());
        toggle(slot("rod", 29), "rod", Material.FISHING_ROD, settings.rod());
        toggle(slot("bow", 30), "bow", Material.BOW, settings.bow());
        toggle(slot("block", 31), "block", Material.SHIELD, settings.block());
        toggle(slot("build", 32), "build", Material.WHITE_WOOL, settings.build());
        backButton(plugin.guis().slot("pvpbot.back", 36));
        closeButton(plugin.guis().slot("pvpbot.close", 40));
    }

    // -------------------------------------------------------------- buttons

    private void kitButton(int slot) {
        PvpKit kit = settings.kit();
        if (kit == null) {
            return; // pvpbot.yml defines no kits; the gallery would be empty
        }
        set(slot, ItemBuilder.of(kit.icon())
                .name(name("gui.pvpbot.kit.name"))
                .lore(lore("gui.pvpbot.kit.lore",
                        "kit", plugin.botTuning().kits().displayName(kit)))
                .glow(true)
                .hideAttributes()
                .build(), event -> {
            click();
            later(() -> new KitsMenu(plugin, viewer, this).open());
        });
    }

    /**
     * The named difficulty presets — one click configures every AI knob at
     * once. A hand-tuned mix that matches no preset reads as Custom, and
     * clicking from Custom starts back at the first one in pvpbot.yml.
     */
    private void presetButton(int slot) {
        BotPreset current = settings.matchingPreset();
        if (plugin.botTuning().presets().isEmpty()) {
            return; // no presets configured — the individual knobs still work
        }
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("pvpbot.buttons.preset", Material.NETHER_STAR))
                .name(name("gui.pvpbot.preset.name"))
                .lore(lore("gui.pvpbot.preset.lore",
                        plugin.messages().ref("level", presetLabel(current, "option"))))
                .glow(current != null)
                .build(), event -> {
            click();
            BotPreset next = plugin.botTuning().nextPreset(current);
            if (next == null) {
                return;
            }
            plugin.stats().setPrefs(viewer.getUniqueId(), next.prefs());
            settings = BotSettings.load(plugin, viewer.getUniqueId());
            refresh();
        });
    }

    /**
     * A preset's label, in the requested style. messages.yml wins so the
     * bundled presets keep their taglines and colors; one an admin invented
     * falls back to its own configured name.
     */
    private Component presetLabel(BotPreset preset, String style) {
        String key = preset == null
                ? "gui.pvpbot.preset." + style + ".custom" : preset.messageKey(style);
        if (!plugin.messages().raw(key).isEmpty()) {
            return plugin.messages().component(key);
        }
        return net.kyori.adventure.text.Component.text(preset == null ? "Custom"
                : preset.configuredName().isEmpty() ? preset.id() : preset.configuredName());
    }

    private void gearButton(int slot) {
        BotSettings.GearTier gear = settings.gear();
        Material icon = switch (gear) {
            case MIRROR -> Material.ARMOR_STAND;
            case NONE -> Material.LEATHER;
            case LEATHER -> Material.LEATHER_CHESTPLATE;
            case IRON -> Material.IRON_CHESTPLATE;
            case DIAMOND -> Material.DIAMOND_CHESTPLATE;
        };
        set(slot, ItemBuilder.of(icon)
                .name(name("gui.pvpbot.gear.name"))
                .lore(lore("gui.pvpbot.gear.lore", plugin.messages().ref("tier",
                        "gui.pvpbot.gear.option." + lower(gear.name()))))
                .hideAttributes()
                .build(), event -> save("gear", gear.next().name()));
    }

    private void cpsButton(int slot) {
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("pvpbot.buttons.cps", Material.CLOCK))
                .name(name("gui.pvpbot.cps.name"))
                .lore(lore("gui.pvpbot.cps.lore", "cps",
                        String.valueOf(plugin.botTuning().clicksPerSecond(settings.cps()))))
                .build(), event -> save("cps", settings.cps().next().name()));
    }

    /** A standard enum-cycling difficulty knob. */
    private void cycle(int slot, String key, Material fallbackIcon,
                       Supplier<String> current, Supplier<String> next) {
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("pvpbot.buttons." + key, fallbackIcon))
                .name(name("gui.pvpbot." + key + ".name"))
                .lore(lore("gui.pvpbot." + key + ".lore", plugin.messages().ref("level",
                        "gui.pvpbot." + key + ".option." + lower(current.get()))))
                .build(), event -> save(key, next.get()));
    }

    /** An arsenal on/off switch. */
    private void toggle(int slot, String key, Material fallbackIcon, boolean on) {
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("pvpbot.buttons." + key, fallbackIcon))
                .name(name("gui.pvpbot." + key + ".name"))
                .lore(lore("gui.pvpbot." + key + ".lore", plugin.messages().ref("state",
                        on ? "gui.pvpbot.state-on" : "gui.pvpbot.state-off")))
                .glow(on)
                .hideAttributes()
                .build(), event -> save(key, !on));
    }

    private void save(String key, Object value) {
        click();
        plugin.stats().setPref(viewer.getUniqueId(), "pvpbot." + key, value);
        settings = BotSettings.load(plugin, viewer.getUniqueId());
        refresh();
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
