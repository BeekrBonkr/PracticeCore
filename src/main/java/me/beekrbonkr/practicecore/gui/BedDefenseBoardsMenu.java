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
 * lists every defense with times on it; opened from a defense's actions it
 * shows just that defense's board. Each tile opens the full ranking.
 */
public final class BedDefenseBoardsMenu extends PagedMenu<BedDefense> {

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
    protected List<BedDefense> entries() {
        List<BedDefense> boards = new ArrayList<>();
        List<BedDefense> defenses = only != null ? List.of(only)
                : plugin.bedDefenses().store().playableBy(viewer.getUniqueId());
        for (BedDefense defense : defenses) {
            if (only != null
                    || plugin.leaderboards().size(BedDefenseService.statsKey(defense.id())) > 0) {
                boards.add(defense);
            }
        }
        return boards;
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.boards.empty");
    }

    @Override
    protected ItemStack icon(BedDefense board) {
        String key = BedDefenseService.statsKey(board.id());
        LeaderboardService.Entry record = plugin.leaderboards().record(key);
        int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
        return Button.of(plugin, board.icon())
                .name("gui.beddefense.boards.entry-name",
                        "board", plugin.bedDefenses().displayFor(board))
                .lore("gui.beddefense.boards.entry-lore",
                        "name", board.name(),
                        "author", board.authorName(),
                        "players", String.valueOf(plugin.leaderboards().size(key)),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none"))
                .hint("view")
                .build();
    }

    @Override
    protected void onEntryClick(BedDefense board, InventoryClickEvent event) {
        click();
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this,
                BedDefenseService.statsKey(board.id()),
                plugin.bedDefenses().displayFor(board), board.icon(), () -> {
            viewer.closeInventory();
            plugin.bedDefenses().play(viewer, board);
        }).open());
    }
}
