package me.beekrbonkr.practicecore.setup;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
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
 * text is fixed rather than read from messages.yml.
 */
public final class ChatPrompts implements Listener {

    private final PracticeCorePlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompts(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Asks in chat and captures the next line the player types. */
    public void prompt(Player player, String question, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        player.sendMessage(Component.text(question, NamedTextColor.GOLD));
        player.sendMessage(Component.text("Type your answer in chat, or 'cancel' to abort.",
                NamedTextColor.GRAY));
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
                player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
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
