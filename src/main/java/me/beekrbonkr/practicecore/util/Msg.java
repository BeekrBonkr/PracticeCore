package me.beekrbonkr.practicecore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class Msg {

    private static final Component PREFIX = Component.text("Practice", NamedTextColor.GOLD)
            .append(Component.text(" » ", NamedTextColor.DARK_GRAY));

    private Msg() {
    }

    public static void info(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void success(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
    }

    public static void error(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }

    public static Component prefix() {
        return PREFIX;
    }
}
