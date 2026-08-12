package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One player's personal bests, ranked against everyone else's. */
public final class StatsMenu extends PagedMenu<Map.Entry<String, Long>> {

    private final UUID subject;
    private final String subjectName;

    public StatsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        this(plugin, viewer, parent, viewer.getUniqueId(), viewer.getName());
    }

    public StatsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, UUID subject, String subjectName) {
        super(plugin, viewer, parent);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    private boolean self() {
        return subject.equals(viewer.getUniqueId());
    }

    @Override
    protected Component title() {
        return self()
                ? text("gui.stats.title-self")
                : text("gui.stats.title-other", "player", subjectName);
    }

    @Override
    protected List<Map.Entry<String, Long>> entries() {
        return List.copyOf(arenas().entrySet());
    }

    /**
     * Every arena the player has data on: their bests, fastest first, then
     * arenas they finished without ever setting a best (value -1) — a run can
     * count without being PB-eligible, and those must not disappear here.
     */
    private Map<String, Long> arenas() {
        Map<String, Long> found = new LinkedHashMap<>(plugin.stats().bests(subject));
        for (String key : allStatsKeys()) {
            if (!found.containsKey(key) && plugin.stats().finishes(subject, key) > 0) {
                found.put(key, -1L);
            }
        }
        return found;
    }

    /** Every key that can hold times — plain arena names and rush boards. */
    private List<String> allStatsKeys() {
        List<String> keys = new ArrayList<>();
        for (ArenaTemplate template : plugin.templates().all()) {
            if (template.mode().equals(RushMode.ID)) {
                for (RushObjective objective : RushObjective.values()) {
                    keys.add(objective.statsKey(template.name()));
                }
            } else {
                keys.add(template.name());
            }
        }
        return keys;
    }

    @Override
    protected ItemStack emptyIcon() {
        String key = self() ? "gui.stats.empty-self" : "gui.stats.empty-other";
        return ItemBuilder.of(emptyMaterial())
                .name(name(key + ".name"))
                .lore(lore(key + ".lore"))
                .build();
    }

    @Override
    protected ItemStack icon(Map.Entry<String, Long> entry) {
        String arena = entry.getKey();
        long best = entry.getValue();
        String display = displayFor(arena);
        ArenaTemplate template = templateFor(arena);
        int rank = plugin.leaderboards().rank(arena, subject);
        LeaderboardService.Entry record = plugin.leaderboards().record(arena);
        long last = plugin.stats().lastMs(subject, arena);

        List<Component> lines = new ArrayList<>(lore("gui.stats.entry-lore",
                "arena", display,
                "best", best >= 0 ? TimeFormat.precise(best) : raw("gui.none"),
                "last", last >= 0 ? TimeFormat.precise(last) : raw("gui.none"),
                "finishes", String.valueOf(plugin.stats().finishes(subject, arena)),
                "rank", rank > 0 ? "#" + rank : raw("gui.none"),
                "players", String.valueOf(plugin.leaderboards().size(arena)),
                "behind", record != null && rank > 1
                        ? "+" + TimeFormat.precise(best - record.millis())
                        : raw("gui.none")));
        if (template == null) {
            lines.addAll(lore("gui.stats.entry-lore-missing", "arena", arena));
        }
        return ItemBuilder.of(template != null ? template.effectiveIcon() : Material.PAPER)
                .name(name("gui.stats.entry-name", "arena", display))
                .lore(lines)
                .glow(rank == 1)
                .build();
    }

    /** The arena behind a stats key — plain name or a rush composite key. */
    private ArenaTemplate templateFor(String key) {
        ArenaTemplate template = plugin.templates().get(key);
        if (template != null) {
            return template;
        }
        var rush = plugin.rush().resolveStatsKey(key);
        return rush != null ? rush.getKey() : null;
    }

    private String displayFor(String key) {
        ArenaTemplate template = plugin.templates().get(key);
        if (template != null) {
            return template.displayName();
        }
        var rush = plugin.rush().resolveStatsKey(key);
        return rush != null ? plugin.rush().displayFor(rush.getKey(), rush.getValue()) : key;
    }

    @Override
    protected void onEntryClick(Map.Entry<String, Long> entry, InventoryClickEvent event) {
        ArenaTemplate template = templateFor(entry.getKey());
        if (template == null) {
            deny();
            plugin.messages().send(viewer, "stats.arena-gone");
            return;
        }
        if (!viewer.hasPermission("practicecore.leaderboard")) {
            deny();
            plugin.messages().send(viewer, "permission.leaderboard");
            return;
        }
        click();
        var rush = plugin.rush().resolveStatsKey(entry.getKey());
        if (rush != null) {
            later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template, entry.getKey(),
                    plugin.rush().displayFor(rush.getKey(), rush.getValue())).open());
            return;
        }
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template).open());
    }

    @Override
    protected void renderFooter() {
        Map<String, Long> arenas = arenas();
        int records = (int) arenas.keySet().stream()
                .filter(arena -> plugin.leaderboards().rank(arena, subject) == 1)
                .count();
        int finishes = arenas.keySet().stream()
                .mapToInt(arena -> plugin.stats().finishes(subject, arena))
                .sum();
        setFooter(47, ItemBuilder.of(Material.PLAYER_HEAD)
                .edit(meta -> {
                    if (meta instanceof SkullMeta skull) {
                        skull.setOwningPlayer(Bukkit.getOfflinePlayer(subject));
                    }
                })
                .name(name("gui.stats.summary.name", "player", subjectName))
                .lore(lore("gui.stats.summary.lore",
                        "player", subjectName,
                        "arenas", String.valueOf(arenas.size()),
                        "finishes", String.valueOf(finishes),
                        "records", String.valueOf(records)))
                .build());
    }
}
