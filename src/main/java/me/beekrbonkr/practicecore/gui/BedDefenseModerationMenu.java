package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The moderation list for players holding
 * {@code practicecore.beddefense.moderate}: every bed defense on the server,
 * private ones included, reported ones first. Left-click opens a defense's
 * actions (show or hide it, delete it, see its reports); right-click goes
 * straight to the reports. A footer toggle narrows the list to defenses
 * with open reports.
 */
public final class BedDefenseModerationMenu extends PagedMenu<BedDefense> {

    private boolean reportedOnly;

    public BedDefenseModerationMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    public void open() {
        if (!plugin.bedDefenses().isModerator(viewer)) {
            plugin.messages().send(viewer, "permission.beddefense-moderate");
            return;
        }
        super.open();
    }

    @Override
    protected Component title() {
        return text(reportedOnly ? "gui.beddefense.moderation.title-reported"
                : "gui.beddefense.moderation.title");
    }

    @Override
    protected List<BedDefense> entries() {
        if (!plugin.bedDefenses().isModerator(viewer)) {
            return List.of();
        }
        return reportedOnly ? plugin.bedDefenses().store().reported()
                : plugin.bedDefenses().store().forModeration();
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon(reportedOnly ? "gui.beddefense.moderation.empty-reported"
                : "gui.beddefense.moderation.empty");
    }

    @Override
    protected ItemStack icon(BedDefense defense) {
        List<Component> lines = new ArrayList<>(lore("gui.beddefense.moderation.entry-lore",
                TagResolver.resolver(
                        plugin.messages().ref("visibility", defense.published()
                                ? "label.state.public" : "label.state.private"),
                        plugin.messages().ref("cleared", defense.authorCleared()
                                ? "gui.beddefense.actions.cleared-yes"
                                : "gui.beddefense.actions.cleared-no")),
                "name", defense.name(),
                "author", defense.authorName(),
                "reports", String.valueOf(defense.reportCount()),
                "blocks", String.valueOf(defense.blocks().size()),
                "likes", String.valueOf(defense.likeCount()),
                "players", String.valueOf(defense.uniquePlayers())));
        if (defense.reportCount() > 0) {
            BedDefense.Report latest = defense.reports().get(defense.reports().size() - 1);
            lines.add(name("gui.beddefense.moderation.latest-line",
                    "reporter", latest.reporterName(), "reason", latest.reason()));
        }
        if (defense.unseenReports() > 0) {
            lines.add(name("gui.beddefense.moderation.unseen-line",
                    "count", String.valueOf(defense.unseenReports())));
        }
        if (defense.autoHidden()) {
            lines.add(name("gui.beddefense.moderation.auto-hidden-line"));
        }
        Button tile = Button.of(plugin, defense.icon())
                .name("gui.beddefense.moderation.entry-name", "name", defense.name())
                .lore(lines)
                .hint("open");
        if (defense.reportCount() > 0) {
            tile.rightHint("view");
        }
        return tile.build();
    }

    @Override
    protected void onEntryClick(BedDefense defense, InventoryClickEvent event) {
        if (event.isRightClick() && defense.reportCount() > 0) {
            click();
            later(() -> new BedDefenseReportsMenu(plugin, viewer, this, defense).open());
            return;
        }
        sound("menu.select");
        later(() -> new BedDefenseActionsMenu(plugin, viewer, this, defense, null).open());
    }

    @Override
    protected void renderFooter() {
        BedDefenseService service = plugin.bedDefenses();
        int reported = service.store().reportedCount();
        set(plugin.guis().slot("beddefense-moderation.buttons.reported", footerSlot(0)),
                Button.of(plugin, plugin.guis().buttonMaterial("beddefense-moderation.buttons.reported",
                                Material.BELL))
                        .name("gui.beddefense.moderation.reported.name")
                        .lore("gui.beddefense.moderation.reported.lore", plugin.messages().ref("state",
                                reportedOnly ? "label.state.on" : "label.state.off"),
                                "count", String.valueOf(reported))
                        .glow(reportedOnly)
                        .hint("toggle")
                        .build(), event -> {
            reportedOnly = !reportedOnly;
            sound(reportedOnly ? "menu.toggle-on" : "menu.toggle-off");
            refresh();
        });
    }
}
