package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A named difficulty: one click that sets every AI knob at once. Defined in
 * pvpbot.yml, so a server can retune the shipped six, rename them, drop some
 * or add its own — the knobs stay individually editable afterwards, and a mix
 * matching no preset reads as "Custom" in the GUI.
 */
public record BotPreset(String id, String configuredName,
                        BotSettings.Evasiveness evasiveness, BotSettings.Cps cps,
                        BotSettings.Accuracy accuracy, BotSettings.Combos combos,
                        BotSettings.Reach reach, BotSettings.Aggression aggression,
                        Boolean rod, Boolean bow, Boolean block, Boolean build) {

    /**
     * Builds a preset from its config block. A block naming a tier this build
     * does not have is reported and skipped rather than silently collapsing to
     * an easier bot.
     *
     * @return the preset, or null when it could not be read
     */
    static BotPreset parse(PracticeCorePlugin plugin, String id, String name,
                           Map<String, String> knobs) {
        try {
            return new BotPreset(id, name,
                    tier(id, knobs, "evasiveness", BotSettings.Evasiveness.class),
                    tier(id, knobs, "cps", BotSettings.Cps.class),
                    tier(id, knobs, "accuracy", BotSettings.Accuracy.class),
                    tier(id, knobs, "combos", BotSettings.Combos.class),
                    tier(id, knobs, "reach", BotSettings.Reach.class),
                    tier(id, knobs, "aggression", BotSettings.Aggression.class),
                    flag(knobs, "rod"), flag(knobs, "bow"), flag(knobs, "block"),
                    flag(knobs, "build"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("pvpbot.yml: preset '" + id + "' — " + e.getMessage()
                    + ". That preset was skipped.");
            return null;
        }
    }

    private static <E extends Enum<E>> E tier(String id, Map<String, String> knobs,
                                              String knob, Class<E> type) {
        String value = knobs.get(knob);
        if (value == null || value.isBlank()) {
            return null; // a preset may deliberately leave a knob alone
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + value + "' is not a valid " + knob);
        }
    }

    private static Boolean flag(Map<String, String> knobs, String knob) {
        String value = knobs.get(knob);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /** The prefs this preset writes, ready for one batched save. */
    public Map<String, Object> prefs() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        put(prefs, "pvpbot.evasiveness", evasiveness);
        put(prefs, "pvpbot.cps", cps);
        put(prefs, "pvpbot.accuracy", accuracy);
        put(prefs, "pvpbot.combos", combos);
        put(prefs, "pvpbot.reach", reach);
        put(prefs, "pvpbot.aggression", aggression);
        if (rod != null) {
            prefs.put("pvpbot.rod", rod);
        }
        if (bow != null) {
            prefs.put("pvpbot.bow", bow);
        }
        if (block != null) {
            prefs.put("pvpbot.block", block);
        }
        if (build != null) {
            prefs.put("pvpbot.build", build);
        }
        return prefs;
    }

    private static void put(Map<String, Object> prefs, String key, Enum<?> value) {
        if (value != null) {
            prefs.put(key, value.name());
        }
    }

    /** Whether these settings are exactly what this preset would write. */
    public boolean matches(BotSettings settings) {
        return same(evasiveness, settings.evasiveness())
                && same(cps, settings.cps())
                && same(accuracy, settings.accuracy())
                && same(combos, settings.combos())
                && same(reach, settings.reach())
                && same(aggression, settings.aggression())
                && same(rod, settings.rod())
                && same(bow, settings.bow())
                && same(block, settings.block())
                && same(build, settings.build());
    }

    /** A knob the preset does not set cannot disagree with anything. */
    private static boolean same(Object expected, Object actual) {
        return expected == null || expected.equals(actual);
    }

    /**
     * The messages.yml key this preset's label lives under. Presets the admin
     * invented have no such key; {@link #configuredName()} covers those.
     */
    public String messageKey(String style) {
        return "gui.pvpbot.preset." + style + "." + id;
    }
}
