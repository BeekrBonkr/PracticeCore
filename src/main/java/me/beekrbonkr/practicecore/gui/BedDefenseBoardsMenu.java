package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Bed defense leaderboards. Opened from the leaderboards category picker it
 * lists every defense with times on it (any-order and strict boards as
 * separate tiles); opened from a defense's actions it shows just that
 * defense's two boards. Each tile opens the full ranking.
 */
public final class BedDefenseBoardsMenu extends PagedMenu<BedDefenseBoardsMenu.Board> {

    /** One board: a defense and whether it is the strict-order variant. */
    public record Board(BedDefense defense, boolean strict) {
        String key() {
            return BedDefenseService.statsKey(defense.id(), strict);
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
            for (boolean strict : new boolean[]{false, true}) {
                Board board = new Board(defense, strict);
                if (only != null || plugin.leaderboards().size(board.key()) > 0) {
                    boards.add(board);
                }
            }
        }
        return boards;
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.boards.empty");
    }

    @Override
    protected ItemStack icon(Board board) {
        String key = board.key();
        LeaderboardService.Entry record = plugin.leaderboards().record(key);
        int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
        Material icon = board.strict()
                ? plugin.guis().material("beddefense-gallery.strict-board-material", Material.CHAIN)
                : board.defense().icon();
        return Button.of(plugin, icon)
                .name("gui.beddefense.boards.entry-name",
                        "board", plugin.bedDefenses().displayFor(board.defense(), board.strict()))
                .lore("gui.beddefense.boards.entry-lore",
                        "name", board.defense().name(),
                        "author", board.defense().authorName(),
                        "players", String.valueOf(plugin.leaderboards().size(key)),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none"))
                .hint("view")
                .build();
    }

    @Override
    protected void onEntryClick(Board board, InventoryClickEvent event) {
        click();
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, board.key(),
                plugin.bedDefenses().displayFor(board.defense(), board.strict()),
                board.defense().icon(), () -> {
            viewer.closeInventory();
            plugin.bedDefenses().play(viewer, board.defense());
        }).open());
    }
}
