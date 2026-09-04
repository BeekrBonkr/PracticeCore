package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The map picker for bed defense practice: every rush map the viewer can
 * see, since any of them has a base with a bed. Picking one opens the bed
 * defense setup menu, never the rush one.
 */
public final class BedDefenseArenaMenu extends PagedMenu<ArenaTemplate> {

    public BedDefenseArenaMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.arenas.title");
    }

    @Override
    protected List<ArenaTemplate> entries() {
        return plugin.bedDefenses().maps(viewer);
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.arenas.empty");
    }

    @Override
    protected ItemStack icon(ArenaTemplate template) {
        boolean allowed = plugin.templates().canUse(viewer, template);
        RushMapData data = RushMapData.parse(template);
        String team = plugin.stats().pref(viewer.getUniqueId(), "rush.team." + template.name(), null);
        RushMapData.TeamBase base = data.team(team);
        if (base == null && !data.playableTeams().isEmpty()) {
            base = data.playableTeams().get(0);
        }
        Button button = Button.of(plugin, plugin.modes().of(template).menuIcon(plugin, template))
                .name("gui.beddefense.arenas.entry-name", "arena", template.displayName())
                .lore("gui.beddefense.arenas.entry-lore",
                        "arena", template.displayName(),
                        "team", base == null ? raw("gui.none") : RushMode.prettyTeam(base.name()),
                        "bases", String.valueOf(data.playableTeams().size()));
        if (allowed) {
            button.hint("open");
        } else if (viewer.hasPermission("practicecore.arena")) {
            button.locked("gui.reason.needs-node", "node", plugin.templates().permissionFor(template));
        } else {
            button.locked("gui.reason.no-permission");
        }
        return button.build();
    }

    @Override
    protected void onEntryClick(ArenaTemplate template, InventoryClickEvent event) {
        if (!plugin.templates().canUse(viewer, template)) {
            deny();
            return;
        }
        click();
        later(() -> new BedDefenseConfigMenu(plugin, viewer, this, template).open());
    }
}
