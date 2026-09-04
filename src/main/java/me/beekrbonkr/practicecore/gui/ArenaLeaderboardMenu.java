package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
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
import java.util.List;

/**
 * The ranked times for one board, fastest first. Usually an arena's; rush
 * arenas hand in a composite {@code boardKey} ("map#bed") with a matching
 * display name, one board per objective.
 */
public final class ArenaLeaderboardMenu extends PagedMenu<LeaderboardService.Entry> {

    private static final Material[] MEDALS = {
            Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};

    /** Null for boards that belong to no arena (bed defenses). */
    private final ArenaTemplate template;
    private final String boardKey;
    private final String boardName;
    /** For arena-less boards: the play button's icon and what it does. */
    private final Material playIcon;
    private final Runnable onPlay;

    public ArenaLeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, ArenaTemplate template) {
        this(plugin, viewer, parent, template, template.name(), template.displayName());
    }

    public ArenaLeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                                ArenaTemplate template, String boardKey, String boardName) {
        super(plugin, viewer, parent);
        this.template = template;
        this.boardKey = boardKey;
        this.boardName = boardName;
        this.playIcon = null;
        this.onPlay = null;
    }

    /** A board with no arena behind it: bed defenses are keyed per defense, not per map. */
    public ArenaLeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                                String boardKey, String boardName, Material playIcon,
                                Runnable onPlay) {
        super(plugin, viewer, parent);
        this.template = null;
        this.boardKey = boardKey;
        this.boardName = boardName;
        this.playIcon = playIcon;
        this.onPlay = onPlay;
    }

    @Override
    protected Component title() {
        return text("gui.board.title", "arena", boardName);
    }

    @Override
    protected List<LeaderboardService.Entry> entries() {
        return plugin.leaderboards().top(boardKey, plugin.pcConfig().leaderboardSize());
    }

    @Override
    protected ItemStack emptyIcon() {
        return ItemBuilder.of(emptyMaterial())
                .name(name("gui.board.empty.name"))
                .lore(lore("gui.board.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(LeaderboardService.Entry entry) {
        int rank = plugin.leaderboards().rank(boardKey, entry.uuid());
        boolean self = entry.uuid().equals(viewer.getUniqueId());
        LeaderboardService.Entry leader = plugin.leaderboards().record(boardKey);

        String nameKey = rank == 1 ? "gui.board.entry-name-first"
                : self ? "gui.board.entry-name-self" : "gui.board.entry-name";
        List<Component> lines = new ArrayList<>(lore("gui.board.entry-lore",
                "rank", String.valueOf(rank),
                "player", entry.displayName(),
                "time", TimeFormat.precise(entry.millis()),
                "behind", leader != null && rank > 1
                        ? "+" + TimeFormat.precise(entry.millis() - leader.millis())
                        : raw("gui.none")));
        if (self) {
            lines.addAll(lore("gui.board.entry-lore-self-suffix"));
        }
        return base(entry, rank)
                .name(name(nameKey, "rank", String.valueOf(rank), "player", entry.displayName()))
                .lore(lines)
                .glow(self)
                .build();
    }

    private ItemBuilder base(LeaderboardService.Entry entry, int rank) {
        int amount = Math.clamp(rank, 1, 64);
        if (plugin.pcConfig().leaderboardHeads()) {
            return ItemBuilder.of(Material.PLAYER_HEAD, amount).edit(meta -> {
                if (meta instanceof SkullMeta skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
                }
            });
        }
        return ItemBuilder.of(rank <= MEDALS.length ? MEDALS[rank - 1] : Material.PAPER, amount);
    }

    @Override
    protected void onEntryClick(LeaderboardService.Entry entry, InventoryClickEvent event) {
        // Entry rows do nothing — a click sound would promise otherwise.
    }

    @Override
    protected void renderFooter() {
        int rank = plugin.leaderboards().rank(boardKey, viewer.getUniqueId());
        long best = plugin.stats().bestMs(viewer.getUniqueId(), boardKey);
        LeaderboardService.Entry ahead = ahead(rank);
        ItemBuilder standing = ItemBuilder.of(Material.NAME_TAG).name(name("gui.board.standing.name"));
        if (rank > 0) {
            standing.lore(lore("gui.board.standing.lore",
                    "rank", "#" + rank,
                    "players", String.valueOf(plugin.leaderboards().size(boardKey)),
                    "best", TimeFormat.precise(best),
                    "next", ahead != null ? ahead.displayName() : raw("gui.none"),
                    "gap", ahead != null ? TimeFormat.precise(best - ahead.millis()) : raw("gui.none")));
        } else {
            standing.lore(lore("gui.board.standing.lore-none"));
        }
        setFooter(47, standing.build());

        if (template == null) {
            if (onPlay != null) {
                set(51, ItemBuilder.of(playIcon != null ? playIcon : Material.RED_BED)
                        .name(name("gui.board.play-name", "arena", boardName))
                        .lore(lore("gui.board.play-lore", "arena", boardName))
                        .build(), event -> {
                    click();
                    later(onPlay);
                });
            }
            return;
        }
        if (plugin.templates().canUse(viewer, template)) {
            set(51, ItemBuilder.of(plugin.modes().of(template).menuIcon(plugin, template))
                    .name(name("gui.board.play-name", "arena", boardName))
                    .lore(lore("gui.board.play-lore", "arena", boardName))
                    .build(), event -> {
                click();
                // Rush runs arm every objective, so the play button of any of
                // an arena's boards starts the same run.
                later(() -> {
                    viewer.closeInventory();
                    plugin.sessions().join(viewer, template);
                });
            });
        }
    }

    private LeaderboardService.Entry ahead(int rank) {
        if (rank <= 1) {
            return null;
        }
        List<LeaderboardService.Entry> top = plugin.leaderboards().top(boardKey, rank);
        return top.size() >= rank - 1 ? top.get(rank - 2) : null;
    }
}
