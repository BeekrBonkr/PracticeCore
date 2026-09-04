package me.beekrbonkr.practicecore.setup;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One-shot chat prompts for the setup GUI: the admin's next chat line is
 * captured (never broadcast) and handed to a callback on the main thread.
 * Typing "cancel" aborts. Part of the non-configurable admin UI, so its own
 * text is fixed rather than read from messages.yml — but it wears the same
 * prefix and colors as every other chat line (style guide R6, R7): the
 * question in yellow, the hints in gray.
 */
public final class ChatPrompts implements Listener {

    private final PracticeCorePlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompts(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Asks in chat and captures the next line the player types. */
    public void prompt(Player player, String question, Consumer<String> callback) {
        prompt(player, line(question, NamedTextColor.YELLOW), callback);
    }

    /** The same, with a question already styled (player-facing prompts read messages.yml). */
    public void prompt(Player player, Component question, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        player.sendMessage(question);
        player.sendMessage(line("Type your answer in chat, or cancel to stop.", NamedTextColor.GRAY));
    }

    /** A fixed-text chat line wearing the configured prefix. */
    private Component line(String text, NamedTextColor color) {
        return Text.parse(plugin.messages().raw("prefix"))
                .append(Component.text(text, color));
    }

    // AsyncChatEvent fires off the main thread; the callback must not.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase("cancel")) {
                player.sendMessage(line("Canceled.", NamedTextColor.GRAY));
                return;
            }
            callback.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
