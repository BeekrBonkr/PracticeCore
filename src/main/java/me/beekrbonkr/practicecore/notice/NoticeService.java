package me.beekrbonkr.practicecore.notice;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player something whether or not they are online. Someone here
 * gets the line at once; someone away gets it queued in their playerdata
 * and delivered a moment after their next login, so a moderator hiding a
 * defense at 3am never goes unexplained.
 *
 * <p>A notice is a messages.yml key plus its placeholders, never rendered
 * text: the admin's wording at delivery time wins, and player-supplied
 * values stay unparsed exactly as they would in a live message. Formatted
 * fragments (a reason line that is itself a messages.yml key) travel as
 * {@code refs} and are resolved with {@code Messages.ref} on delivery.
 */
public final class NoticeService {

    /** Queued notices per player are capped so a flood never grows a file without bound. */
    private static final int MAX_QUEUED = 50;

    private final PracticeCorePlugin plugin;

    public NoticeService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Sends now, or queues for the next login. Placeholders are name, value pairs. */
    public void notify(UUID target, String key, String... placeholders) {
        notify(target, key, Map.of(), placeholders);
    }

    /**
     * Sends now, or queues for the next login.
     *
     * @param refs placeholder name → messages.yml key rendered as formatted text
     */
    public void notify(UUID target, String key, Map<String, String> refs, String... placeholders) {
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            send(online, key, refs, placeholders);
            return;
        }
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("key", key);
        notice.put("when", System.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            values.put(placeholders[i], placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        notice.put("placeholders", values);
        if (!refs.isEmpty()) {
            notice.put("refs", new LinkedHashMap<>(refs));
        }
        plugin.stats().addNotice(target, notice, MAX_QUEUED);
        plugin.stats().unloadIfOffline(target);
    }

    /**
     * Delivers everything queued for a player who just joined. Deferred a
     * tick so it lands after every other plugin's join chatter, and after
     * the snapshot restore this plugin may do itself.
     */
    public void deliverOnJoin(Player player) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            List<Map<?, ?>> queued = plugin.stats().drainNotices(id);
            for (Map<?, ?> raw : queued) {
                Object key = raw.get("key");
                if (key == null) {
                    continue;
                }
                List<String> placeholders = new ArrayList<>();
                if (raw.get("placeholders") instanceof Map<?, ?> values) {
                    values.forEach((name, value) -> {
                        placeholders.add(String.valueOf(name));
                        placeholders.add(String.valueOf(value));
                    });
                }
                Map<String, String> refs = new LinkedHashMap<>();
                if (raw.get("refs") instanceof Map<?, ?> stored) {
                    stored.forEach((name, refKey) -> refs.put(String.valueOf(name), String.valueOf(refKey)));
                }
                send(player, String.valueOf(key), refs, placeholders.toArray(new String[0]));
            }
        }, 20L);
    }

    private void send(Player player, String key, Map<String, String> refs, String... placeholders) {
        if (refs.isEmpty()) {
            plugin.messages().send(player, key, placeholders);
            return;
        }
        List<TagResolver> resolvers = new ArrayList<>();
        refs.forEach((name, refKey) -> resolvers.add(plugin.messages().ref(name, refKey)));
        plugin.messages().send(player, key, TagResolver.resolver(resolvers), placeholders);
    }
}
