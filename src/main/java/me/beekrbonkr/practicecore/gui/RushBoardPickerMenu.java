package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A rush arena keeps one leaderboard per objective — this small menu picks
 * which of the three to look at.
 */
public final class RushBoardPickerMenu extends Menu {

    private static final int[] SLOTS = {11, 13, 15};

    private final ArenaTemplate template;

    public RushBoardPickerMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                               ArenaTemplate template) {
        super(plugin, viewer, parent);
        this.template = template;
    }

    @Override
    protected Component title() {
        return text("gui.rushboards.title", "arena", template.displayName());
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        border();
        RushObjective[] objectives = RushObjective.values();
        for (int i = 0; i < objectives.length; i++) {
            RushObjective objective = objectives[i];
            String key = objective.statsKey(template.name());
            var record = plugin.leaderboards().record(key);
            int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
            set(SLOTS[i], ItemBuilder.of(objective.icon())
                    .name(name("gui.rushboards.entry-name",
                            "objective", plugin.rush().objectiveName(objective)))
                    .lore(lore("gui.rushboards.entry-lore",
                            "players", String.valueOf(plugin.leaderboards().size(key)),
                            "record", record != null
                                    ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                            "record-holder", record != null ? record.displayName() : raw("gui.none"),
                            "rank", rank > 0 ? "#" + rank : raw("gui.none")))
                    .glow(rank == 1)
                    .build(), event -> {
                click();
                later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template, key,
                        plugin.rush().displayFor(template, objective)).open());
            });
        }
        backButton(18);
        closeButton(26);
    }
}
