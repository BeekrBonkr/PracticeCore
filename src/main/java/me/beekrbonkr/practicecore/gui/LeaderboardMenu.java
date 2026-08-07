package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Arena picker for leaderboards. */
public final class LeaderboardMenu extends PagedMenu<ArenaTemplate> {

    public LeaderboardMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.leaderboards.title");
    }

    @Override
    protected List<ArenaTemplate> entries() {
        // Leaderboards are public even for arenas you cannot play — knowing
        // the times is half of why anyone wants access in the first place.
        return plugin.templates().completeTemplates();
    }

    @Override
    protected ItemStack emptyIcon() {
        return ItemBuilder.of(Material.COBWEB)
                .name(name("gui.leaderboards.empty.name"))
                .lore(lore("gui.leaderboards.empty.lore"))
                .build();
    }

    @Override
    protected ItemStack icon(ArenaTemplate template) {
        LeaderboardService.Entry record = plugin.leaderboards().record(template.name());
        int rank = plugin.leaderboards().rank(template.name(), viewer.getUniqueId());
        return ItemBuilder.of(template.effectiveIcon())
                .name(name("gui.leaderboards.entry-name", "arena", template.displayName()))
                .lore(lore("gui.leaderboards.entry-lore",
                        "arena", template.displayName(),
                        "players", String.valueOf(plugin.leaderboards().size(template.name())),
                        "record", record != null ? TimeFormat.precise(record.millis()) : raw("gui.none"),
                        "record-holder", record != null ? record.displayName() : raw("gui.none"),
                        "rank", rank > 0 ? "#" + rank : raw("gui.none")))
                .glow(rank == 1)
                .build();
    }

    @Override
    protected void onEntryClick(ArenaTemplate template, InventoryClickEvent event) {
        click();
        later(() -> new ArenaLeaderboardMenu(plugin, viewer, this, template).open());
    }
}
