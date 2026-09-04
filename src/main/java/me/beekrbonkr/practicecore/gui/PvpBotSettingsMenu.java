package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.pvpbot.BotPreset;
import me.beekrbonkr.practicecore.pvpbot.BotSettings;
import me.beekrbonkr.practicecore.pvpbot.PvpKit;
import me.beekrbonkr.practicecore.session.PracticeSession;
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
        nav("pvpbot");
    }

    // -------------------------------------------------------------- buttons

    private void kitButton(int slot) {
        PvpKit kit = settings.kit();
        if (kit == null) {
            return; // pvpbot.yml defines no kits at all — the feature is off
        }
        set(slot, Button.of(plugin, plugin.guis().buttonMaterial("pvpbot.buttons.kit", Material.CHEST))
                .name("gui.pvpbot.kit.name")
                .lore("gui.pvpbot.kit.lore",
                        "kit", plugin.botTuning().kits().displayName(kit))
                .hint("open")
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
        Button button = Button.of(plugin,
                        plugin.guis().buttonMaterial("pvpbot.buttons.preset", Material.EXPERIENCE_BOTTLE))
                .name("gui.pvpbot.preset.name")
                .lore("gui.pvpbot.preset.lore",
                        plugin.messages().ref("level", presetLabel(current, "long")));
        if (plugin.botTuning().presets().isEmpty()) {
            set(slot, button.disabled("gui.reason.no-presets").build(), event -> deny());
            return;
        }
        set(slot, button.hint("cycle").build(), event -> {
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
                ? "label.difficulty." + style + ".custom" : preset.messageKey(style);
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
        set(slot, Button.of(plugin, icon)
                .name("gui.pvpbot.gear.name")
                .lore("gui.pvpbot.gear.lore", plugin.messages().ref("tier",
                        "gui.pvpbot.gear.option." + lower(gear.name())))
                .hideAttributes()
                .hint("cycle")
                .build(), event -> save("gear", gear.next().name()));
    }

    private void cpsButton(int slot) {
        set(slot, Button.of(plugin,
                        plugin.guis().buttonMaterial("pvpbot.buttons.cps", Material.SUGAR))
                .name("gui.pvpbot.cps.name")
                .lore("gui.pvpbot.cps.lore", "cps",
                        String.valueOf(plugin.botTuning().clicksPerSecond(settings.cps())))
                .hint("cycle")
                .build(), event -> save("cps", settings.cps().next().name()));
    }

    /** A standard enum-cycling difficulty knob. */
    private void cycle(int slot, String key, Material fallbackIcon,
                       Supplier<String> current, Supplier<String> next) {
        set(slot, Button.of(plugin,
                        plugin.guis().buttonMaterial("pvpbot.buttons." + key, fallbackIcon))
                .name("gui.pvpbot." + key + ".name")
                .lore("gui.pvpbot." + key + ".lore", plugin.messages().ref("level",
                        "gui.pvpbot." + key + ".option." + lower(current.get())))
                .hint("cycle")
                .build(), event -> save(key, next.get()));
    }

    /** An arsenal on/off switch. */
    private void toggle(int slot, String key, Material fallbackIcon, boolean on) {
        set(slot, Button.of(plugin,
                        plugin.guis().buttonMaterial("pvpbot.buttons." + key, fallbackIcon))
                .name("gui.pvpbot." + key + ".name")
                .lore("gui.pvpbot." + key + ".lore", plugin.messages().ref("state",
                        on ? "label.state.on" : "label.state.off"))
                .glow(on)
                .hideAttributes()
                .hint("toggle")
                .build(), event -> {
            sound(on ? "menu.toggle-off" : "menu.toggle-on");
            plugin.stats().setPref(viewer.getUniqueId(), "pvpbot." + key, !on);
            settings = BotSettings.load(plugin, viewer.getUniqueId());
            refresh();
        });
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
