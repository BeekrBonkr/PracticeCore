package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Bed defense leaderboards. Every defense keeps three public boards — its
 * competitive times, its obsidian practice times and its bed repair rounds
 * — and each is a tile of its own. Opened from the leaderboards category
 * picker it lists every board with results on it; opened from a defense's
 * actions it shows just that defense's three. Each tile opens the full
 * ranking.
 */
public final class BedDefenseBoardsMenu extends PagedMenu<BedDefenseBoardsMenu.Board> {

    /** One board: a defense and the key it is ranked under. */
    public record Board(BedDefense defense, String key) {

        public BedDefenseSelection.Variant variant() {
            return BedDefenseService.variantOfKey(key);
        }
    }

    private final BedDefense only;

    public BedDefenseBoardsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        this(plugin, viewer, parent, null);
    }

    public BedDefenseBoardsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, BedDefense only) {
        super(plugin, viewer, parent);
        this.only = only;
    }

    @Override
    protected Component title() {
        return only == null ? text("gui.beddefense.boards.title")
                : text("gui.beddefense.boards.title-one", "name", only.name());
    }

    @Override
    protected List<Board> entries() {
        List<Board> boards = new ArrayList<>();
        List<BedDefense> defenses = only != null ? List.of(only)
                : plugin.bedDefenses().store().playableBy(viewer.getUniqueId());
        for (BedDefense defense : defenses) {
            for (String key : plugin.bedDefenses().rankedStatsKeys(defense)) {
                if (only != null || plugin.leaderboards().size(key) > 0) {
                    boards.add(new Board(defense, key));
                }
            }
        }
        return boards;
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.boards.empty");
    }

    private Material material(Board board) {
        return switch (board.variant()) {
            case OBSIDIAN -> plugin.guis().material("beddefense-boards.obsidian-material", Material.OBSIDIAN);
            case REPAIR -> plugin.guis().material("beddefense-boards.repair-material", Material.TNT);
            case NORMAL -> board.defense().icon();
        };
    }

    @Override
    protected ItemStack icon(Board board) {
        String key = board.key();
        BedDefense defense = board.defense();
        LeaderboardService.Entry record = plugin.leaderboards().record(key);
        int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
        return Button.of(plugin, material(board))
                .name("gui.beddefense.boards.entry-name",
                        "board", plugin.bedDefenses().displayForKey(key, defense))
                .lore("gui.beddefense.boards.entry-lore",
                        "name", defense.name(),
                        "author", defense.authorName(),
                        "players", String.valueOf(plugin.leaderboards().size(key)),
                        "record", record != null
                                ? plugin.leaderboards().format(key, record.millis()) : none(),
                        "record-holder", record != null ? record.displayName() : none(),
                        "rank", rank > 0 ? "#" + rank : none())
                .hint("view")
                .build();
    }

    @Override
    protected void onEntryClick(Board board, InventoryClickEvent event) {
        click();
        BedDefenseSelection.Variant variant = board.variant();
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, board.key(),
                plugin.bedDefenses().displayForKey(board.key(), board.defense()), material(board), () -> {
            viewer.closeInventory();
            // Playing from a board means playing in that board's mode.
            plugin.bedDefenses().play(viewer, board.defense(), variant);
        }).open());
    }
}
