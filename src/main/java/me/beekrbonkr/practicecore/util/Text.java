package me.beekrbonkr.practicecore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

/** MiniMessage helpers for config-driven display text. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String mini) {
        return parse(mini, TagResolver.empty());
    }

    public static Component parse(String mini, TagResolver resolver) {
        if (mini == null || mini.isEmpty()) {
            return Component.empty();
        }
        try {
            return MM.deserialize(mini, resolver);
        } catch (RuntimeException e) {
            // Malformed tags in a config file must never break a menu or a
            // message — show the raw text instead.
            return Component.text(mini);
        }
    }

    /** Item names and lore: parsed, with the vanilla italic default removed. */
    public static Component item(String mini) {
        return item(mini, TagResolver.empty());
    }

    public static Component item(String mini, TagResolver resolver) {
        return parse(mini, resolver).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> itemLore(List<String> lines) {
        return lines.stream().map(Text::item).toList();
    }
}
