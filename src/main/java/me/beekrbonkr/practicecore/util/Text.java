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

    /**
     * MiniMessage's standard tags that take arguments. A token like
     * {@code <gradient:zzz:qqq>} whose arguments do not parse is kept as
     * literal text by the lenient parser — indistinguishable at parse time
     * from a placeholder like {@code <arena>}, except that these names are
     * never placeholders.
     */
    private static final java.util.Set<String> ARG_TAGS = java.util.Set.of(
            "color", "colour", "c", "gradient", "transition", "rainbow",
            "click", "hover", "key", "lang", "translate", "tr", "insertion",
            "insert", "font", "selector", "sel", "score", "nbt", "data",
            "shadow", "bold", "b", "italic", "i", "em", "underlined", "u",
            "strikethrough", "st", "obfuscated", "obf");

    private static final java.util.regex.Pattern TAG =
            java.util.regex.Pattern.compile("<([!?#]?[a-zA-Z0-9_-]+)(:[^<>]*)?>");

    private static final net.kyori.adventure.text.serializer.plain
            .PlainTextComponentSerializer PLAIN = net.kyori.adventure.text.serializer.plain
            .PlainTextComponentSerializer.plainText();

    /**
     * The reason a line would show broken to a player, or null when it parses
     * clean. {@link #parse} deliberately swallows failures at play time (raw
     * text beats a broken menu) and the lenient parser keeps a malformed tag
     * as literal text — so the validation sweep looks for exactly that: a
     * token wearing a <em>standard tag's name</em> that still came out
     * literal, which means its arguments are broken. Unknown names are left
     * alone; they are the placeholders each message legitimately carries.
     */
    public static String problem(String mini) {
        if (mini == null || mini.isEmpty()) {
            return null;
        }
        try {
            MM.deserialize(mini);
        } catch (RuntimeException e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        java.util.regex.Matcher matcher = TAG.matcher(mini);
        while (matcher.find()) {
            String token = matcher.group();
            String name = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            // Bare tokens are left alone even when they wear a standard tag's
            // name: several messages legitimately use <color>, <key> or
            // <data> as placeholders. Only a token WITH arguments is
            // unambiguous — placeholders never take any.
            if (matcher.group(2) == null && !name.startsWith("#")) {
                continue;
            }
            if (consumed(token)) {
                continue; // a real tag, parsed fine
            }
            if (name.startsWith("#") || ARG_TAGS.contains(name)) {
                return "'" + token + "' looks like a <" + matcher.group(1)
                        + "> tag but its arguments do not parse — it will show as raw text";
            }
            // An unknown name that stayed literal is a placeholder — fine.
        }
        return null;
    }

    /** Whether the lenient parser accepted this token as a tag (vs literal text). */
    private static boolean consumed(String token) {
        try {
            return !PLAIN.serialize(MM.deserialize(token)).equals(token);
        } catch (RuntimeException e) {
            return false;
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
