package me.beekrbonkr.practicecore.message;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.Backups;
import me.beekrbonkr.practicecore.config.Versions;
import me.beekrbonkr.practicecore.config.YamlMigrator;
import me.beekrbonkr.practicecore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every piece of text the plugin shows a player, loaded from messages.yml.
 *
 * The jar's copy is always indexed first and the admin's file overlaid on top,
 * so a key can never go missing however the file is edited. Placeholder values
 * are inserted with {@link Placeholder#unparsed}: a player name containing
 * MiniMessage syntax is shown literally rather than being interpreted.
 */
public final class Messages {

    private static final String RESOURCE = "messages.yml";

    private final PracticeCorePlugin plugin;
    private final File file;
    private final Map<String, List<String>> values = new HashMap<>();
    private Component prefix = Component.empty();

    public Messages(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), RESOURCE);
    }

    /** @return notes worth showing an admin (migrations, parse failures) */
    public List<String> load() {
        List<String> notes = new ArrayList<>();
        values.clear();

        YamlConfiguration bundled = Backups.jarDefaults(plugin, RESOURCE);
        if (bundled != null) {
            index(bundled);
        }
        if (!file.exists()) {
            plugin.saveResource(RESOURCE, false);
        }
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            notes.add(RESOURCE + " could not be parsed: " + e.getMessage()
                    + " — falling back to the built-in text.");
            refreshPrefix();
            return notes;
        }
        notes.addAll(new YamlMigrator(plugin, "messages", RESOURCE, file,
                Versions.MESSAGES, Messages::steps).run(user));
        index(user);
        refreshPrefix();
        return notes;
    }

    /** Reshapes an older messages.yml. See {@link Versions#MESSAGES}. */
    private static void steps(FileConfiguration cfg, int from) {
        // v0 → v1 is the first versioned layout; nothing moved.
        if (from < 2 && "<red><bold>You died".equals(cfg.getString("pvpbot.title.death"))) {
            // v2 shouts the death title; only rewrite the untouched default.
            cfg.set("pvpbot.title.death", "<red><bold>YOU DIED");
        }
        if (from < 3 && "<red><bold>PvP Bot <dark_gray>| <red><health>❤"
                .equals(cfg.getString("pvpbot.bot-name"))) {
            // v3 splits the bot tag: health moved to its own bot-health line
            // (added by top-up); an untouched combined default slims to the name.
            cfg.set("pvpbot.bot-name", "<red><bold>PvP Bot");
        }
    }

    private void index(FileConfiguration cfg) {
        for (String key : cfg.getKeys(true)) {
            if (cfg.isConfigurationSection(key)) {
                continue;
            }
            if (cfg.isList(key)) {
                values.put(key, List.copyOf(cfg.getStringList(key)));
            } else {
                Object value = cfg.get(key);
                if (value != null) {
                    values.put(key, List.of(String.valueOf(value)));
                }
            }
        }
    }

    private void refreshPrefix() {
        List<String> lines = values.get("prefix");
        prefix = lines == null || lines.isEmpty() ? Component.empty() : Text.parse(lines.get(0));
    }

    // ------------------------------------------------------------- lookups

    private List<String> lines(String key) {
        return values.getOrDefault(key, List.of());
    }

    /** A single-line message set to '' — deliberately silenced by the admin. */
    public boolean silenced(String key) {
        List<String> lines = lines(key);
        return lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty());
    }

    public String raw(String key) {
        List<String> lines = lines(key);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private TagResolver resolver(String... placeholders) {
        return resolver(TagResolver.empty(), placeholders);
    }

    private TagResolver resolver(TagResolver extra, String... placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.component("prefix", prefix));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            builder.resolver(Placeholder.unparsed(placeholders[i], value == null ? "" : value));
        }
        builder.resolver(extra);
        return builder.build();
    }

    /**
     * A placeholder whose value is another message, inserted already formatted.
     * Use for text the admin wrote (a state label, a status line) — never for
     * anything a player supplied, which must stay {@link Placeholder#unparsed}.
     */
    public TagResolver ref(String placeholder, String key, String... keyPlaceholders) {
        return Placeholder.component(placeholder, component(key, keyPlaceholders));
    }

    public TagResolver ref(String placeholder, Component value) {
        return Placeholder.component(placeholder, value);
    }

    // -------------------------------------------------------------- output

    public void send(CommandSender to, String key, String... placeholders) {
        send(to, key, TagResolver.empty(), placeholders);
    }

    public void send(CommandSender to, String key, TagResolver extra, String... placeholders) {
        if (silenced(key)) {
            return;
        }
        TagResolver resolver = resolver(extra, placeholders);
        for (String line : lines(key)) {
            to.sendMessage(Text.parse(line, resolver));
        }
    }

    public void broadcast(String key, String... placeholders) {
        if (silenced(key)) {
            return;
        }
        TagResolver resolver = resolver(placeholders);
        for (String line : lines(key)) {
            Bukkit.broadcast(Text.parse(line, resolver));
        }
    }

    public void actionBar(Player to, String key, String... placeholders) {
        if (silenced(key)) {
            return;
        }
        to.sendActionBar(Text.parse(raw(key), resolver(placeholders)));
        // Anything that isn't the speedometer's own refresh must stay readable
        // instead of being wiped by the next speedometer tick.
        if (!key.startsWith("speedometer.") && plugin.speedometer() != null) {
            plugin.speedometer().yieldActionBar(to.getUniqueId());
        }
    }

    public void title(Player to, String titleKey, String subtitleKey, String... placeholders) {
        TagResolver resolver = resolver(placeholders);
        to.showTitle(Title.title(
                Text.parse(raw(titleKey), resolver),
                Text.parse(raw(subtitleKey), resolver),
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(1200), Duration.ofMillis(400))));
    }

    // ---------------------------------------------------------- components

    /** Rendered as a chat/menu component rather than sent. */
    public Component component(String key, String... placeholders) {
        return Text.parse(raw(key), resolver(placeholders));
    }

    /** An item name: parsed with the vanilla italic default removed. */
    public Component name(String key, String... placeholders) {
        return name(key, TagResolver.empty(), placeholders);
    }

    public Component name(String key, TagResolver extra, String... placeholders) {
        return Text.item(raw(key), resolver(extra, placeholders));
    }

    /** Item lore, one component per configured line. */
    public List<Component> lore(String key, String... placeholders) {
        return lore(key, TagResolver.empty(), placeholders);
    }

    public List<Component> lore(String key, TagResolver extra, String... placeholders) {
        TagResolver resolver = resolver(extra, placeholders);
        return lines(key).stream().map(line -> Text.item(line, resolver)).toList();
    }

    // ------------------------------------------------- free-form admin text

    /** Diagnostics whose body is generated at runtime; the styling is configurable. */
    public void note(CommandSender to, String text) {
        send(to, "admin.note", "note", text);
    }

    public void problem(CommandSender to, String text) {
        send(to, "admin.problem", "note", text);
    }

    public void done(CommandSender to, String text) {
        send(to, "admin.done", "note", text);
    }
}
