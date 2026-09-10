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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
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
    /** Players whose chat line is being swallowed right now, until the answer is delivered. */
    private final Set<UUID> capturing = ConcurrentHashMap.newKeySet();

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
    // Cancelling alone is not enough on a server with a chat plugin: some
    // ignore the cancel flag, un-cancel later, or act on the legacy event.
    // So the answer is taken here first, then swallowed at every level it
    // could leak from — cancelled, no viewers, empty text — and swallowed
    // again after every other plugin has had its say.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Consumer<String> callback = pending.remove(id);
        if (callback == null) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        capturing.add(id);
        swallow(event);
        Bukkit.getScheduler().runTask(plugin, () -> {
            capturing.remove(id);
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

    /** Re-asserts the capture once every other plugin's handler has run. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatLate(AsyncChatEvent event) {
        if (capturing.contains(event.getPlayer().getUniqueId())) {
            swallow(event);
        }
    }

    /** Plugins still on the legacy chat event get the same empty, cancelled line. */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!capturing.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
        event.setMessage("");
    }

    private static void swallow(AsyncChatEvent event) {
        event.setCancelled(true);
        event.viewers().clear();
        event.message(Component.empty());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
