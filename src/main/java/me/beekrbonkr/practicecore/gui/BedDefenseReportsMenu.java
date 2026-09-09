package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The reports filed on one bed defense, newest first, for moderators. A
 * tile is one player's report; clicking it twice drops that report alone,
 * the footer button drops them all (style guide R56). The defense itself
 * is untouched either way — hiding or deleting it is the actions menu's job.
 */
public final class BedDefenseReportsMenu extends PagedMenu<BedDefense.Report> {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BedDefense defense;

    public BedDefenseReportsMenu(PracticeCorePlugin plugin, Player viewer, Menu parent, BedDefense defense) {
        super(plugin, viewer, parent);
        this.defense = defense;
    }

    @Override
    public void open() {
        if (!plugin.bedDefenses().isModerator(viewer)) {
            plugin.messages().send(viewer, "permission.beddefense-moderate");
            return;
        }
        BedDefense live = plugin.bedDefenses().store().get(defense.id());
        if (live != null) {
            plugin.bedDefenses().markReportsSeen(live);
        }
        super.open();
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.reports.title", "name", defense.name());
    }

    @Override
    protected List<BedDefense.Report> entries() {
        BedDefense live = plugin.bedDefenses().store().get(defense.id());
        if (live == null) {
            return List.of();
        }
        List<BedDefense.Report> newestFirst = new ArrayList<>(live.reports());
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.beddefense.reports.empty");
    }

    @Override
    protected ItemStack icon(BedDefense.Report report) {
        // Arming is per slot; the slot a report sits in is stable between a
        // click and the redraw that follows it, which is all arming needs.
        int slot = slotOf(report);
        Button tile = Button.of(plugin, Material.PAPER);
        if (slot >= 0 && isArmed(slot)) {
            tile.name("gui.beddefense.reports.entry-name-armed")
                    .lore("gui.beddefense.reports.entry-lore-armed")
                    .glow(true)
                    .hint("confirm")
                    .line(name("gui.beddefense.actions.delete.cancel-line"));
        } else {
            tile.name("gui.beddefense.reports.entry-name", "reporter", report.reporterName())
                    .lore("gui.beddefense.reports.entry-lore",
                            "reporter", report.reporterName(),
                            "reason", report.reason(),
                            "when", WHEN.format(Instant.ofEpochMilli(report.when())
                                    .atZone(ZoneId.systemDefault())))
                    .hint("delete");
        }
        return tile.build();
    }

    /** The content slot a report is drawn in on the current page, or -1. */
    private int slotOf(BedDefense.Report report) {
        List<BedDefense.Report> all = entries();
        int index = all.indexOf(report);
        if (index < 0) {
            return -1;
        }
        int onPage = index - page() * CONTENT_SLOTS.length;
        return onPage >= 0 && onPage < CONTENT_SLOTS.length ? CONTENT_SLOTS[onPage] : -1;
    }

    @Override
    protected void onEntryClick(BedDefense.Report report, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!isArmed(slot)) {
            arm(slot);
            return;
        }
        disarm();
        BedDefense live = plugin.bedDefenses().store().get(defense.id());
        if (live != null) {
            plugin.bedDefenses().dismissReport(viewer, live, report.reporter());
        }
        refresh();
    }

    @Override
    protected void renderFooter() {
        BedDefenseService service = plugin.bedDefenses();
        BedDefense live = service.store().get(defense.id());
        int count = live == null ? 0 : live.reportCount();
        int slot = plugin.guis().slot("beddefense-reports.buttons.dismiss", footerSlot(2));
        Button dismiss = Button.of(plugin, plugin.guis().buttonMaterial("beddefense-reports.buttons.dismiss",
                Material.LAVA_BUCKET));
        if (count == 0) {
            dismiss.name("gui.beddefense.reports.dismiss.name")
                    .lore("gui.beddefense.reports.dismiss.lore", "count", "0")
                    .disabled("gui.reason.no-reports");
        } else if (isArmed(slot)) {
            dismiss.name("gui.beddefense.reports.dismiss.name-armed")
                    .lore("gui.beddefense.reports.dismiss.lore-armed")
                    .glow(true)
                    .hint("confirm")
                    .line(name("gui.beddefense.actions.delete.cancel-line"));
        } else {
            dismiss.name("gui.beddefense.reports.dismiss.name")
                    .lore("gui.beddefense.reports.dismiss.lore", "count", String.valueOf(count))
                    .hint("delete");
        }
        set(slot, dismiss.build(), event -> {
            if (count == 0) {
                deny();
                return;
            }
            if (!isArmed(slot)) {
                arm(slot);
                return;
            }
            disarm();
            BedDefense current = service.store().get(defense.id());
            if (current != null) {
                service.dismissReports(viewer, current);
            }
            refresh();
        });
    }
}
