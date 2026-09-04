package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * A rush arena keeps one leaderboard per objective — this small menu picks
 * which of them to look at. Laid out from guis.yml's {@code rushboards}
 * section like every other menu.
 */
public final class RushBoardPickerMenu extends Menu {

    private static final int[] DEFAULT_SLOTS = {10, 12, 14, 16};

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
        return plugin.guis().rows("rushboards", 3);
    }

    @Override
    protected void render() {
        border();
        RushObjective[] objectives = RushObjective.values();
        for (int i = 0; i < objectives.length; i++) {
            RushObjective objective = objectives[i];
            String button = "rushboards.buttons." + objective.id().toLowerCase(Locale.ROOT);
            if (!plugin.guis().buttonEnabled(button)) {
                continue;
            }
            String key = objective.statsKey(template.name());
            var record = plugin.leaderboards().record(key);
            int rank = plugin.leaderboards().rank(key, viewer.getUniqueId());
            Button tile = Button.of(plugin, plugin.guis().buttonMaterial(button, objective.icon()))
                    .name("gui.rushboards.entry-name",
                            "objective", plugin.rush().objectiveName(objective))
                    .lore("gui.rushboards.entry-lore",
                            "players", String.valueOf(plugin.leaderboards().size(key)),
                            "record", record != null
                                    ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                            "record-holder", record != null ? record.displayName() : raw("gui.none"),
                            "rank", rank > 0 ? "#" + rank : raw("gui.none"));
            if (rank == 1) {
                tile.line(name("gui.rushboards.record-line"));
            }
            int slot = plugin.guis().slot(button,
                    i < DEFAULT_SLOTS.length ? DEFAULT_SLOTS[i] : CONTENT_SLOTS[i]);
            set(slot, tile.hint("view").build(), event -> {
                click();
                later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template, key,
                        plugin.rush().displayFor(template, objective)).open());
            });
        }
        nav("rushboards");
    }
}
