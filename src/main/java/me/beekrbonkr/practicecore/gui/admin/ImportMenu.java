package me.beekrbonkr.practicecore.gui.admin;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Button;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.gui.PagedMenu;
import me.beekrbonkr.practicecore.rush.MBedwarsHook;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Every MBedwars arena as a tile: left-click imports it as a rush arena,
 * right-click as a bed defense map, shift for either overwrites a template
 * that already exists. The tiles dispatch the import commands rather than
 * re-implementing them, so there is exactly one import code path and the
 * feedback in chat is the command's own. Text is fixed — see
 * {@link SetupGui}.
 */
final class ImportMenu extends PagedMenu<MBedwarsHook.ArenaSummary> {

    ImportMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return SetupGui.title("Import Maps");
    }

    @Override
    protected List<MBedwarsHook.ArenaSummary> entries() {
        return MBedwarsHook.available() ? MBedwarsHook.arenaSummaries() : List.of();
    }

    @Override
    protected ItemStack icon(MBedwarsHook.ArenaSummary summary) {
        String name = sanitize(summary.name());
        ArenaTemplate existing = name.isEmpty() ? null : plugin.templates().get(name);
        Button tile = Button.of(plugin, iconFor(summary.name()))
                .name(Component.text(summary.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .line(SetupGui.state("Teams", summary.teams() + " × " + summary.playersPerTeam()))
                .line(SetupGui.state("Saves as", name.isEmpty()
                        ? Component.text("—", NamedTextColor.DARK_GRAY)
                        : Component.text(name, NamedTextColor.WHITE)))
                .line(SetupGui.state("Imported", Component.text(existing != null ? "yes" : "no",
                        existing != null ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        if (existing != null) {
            tile.line(SetupGui.state("Mode", existing.mode()))
                    .line(SetupGui.gray("Importing again needs overwrite."));
        }
        // The click vocabulary has no import verbs, so the hints are spelled
        // out here in the same shape (R32): left, right, then shift in gray.
        return tile.line(Component.empty())
                .line(Component.text("Left-click to import as Rush", NamedTextColor.YELLOW))
                .line(Component.text("Right-click to import as Bed Defense", NamedTextColor.YELLOW))
                .line(Component.text("Shift-click to overwrite", NamedTextColor.DARK_GRAY))
                .hideAttributes()
                .build();
    }

    @Override
    protected void onEntryClick(MBedwarsHook.ArenaSummary summary, InventoryClickEvent event) {
        if (!MBedwarsHook.available()) {
            deny();
            return;
        }
        if (sanitize(summary.name()).isEmpty()) {
            deny();
            plugin.messages().problem(viewer, summary.name()
                    + " has no usable characters for an arena name.");
            plugin.messages().note(viewer, "Import it with a name: /practice rush import "
                    + summary.name() + " <name>.");
            return;
        }
        String branch = event.isRightClick() ? "beddefense" : "rush";
        String command = "practice " + branch + " import " + summary.name()
                + (event.isShiftClick() ? " overwrite" : "");
        click();
        later(() -> {
            viewer.closeInventory();
            Bukkit.dispatchCommand(viewer, command);
        });
    }

    @Override
    protected ItemStack emptyIcon() {
        Button empty = Button.of(plugin, emptyMaterial())
                .name(Component.text("No maps yet", NamedTextColor.GRAY, TextDecoration.BOLD));
        if (MBedwarsHook.available()) {
            empty.line(SetupGui.gray("MBedwars has no arenas to import."))
                    .line(SetupGui.gray("Create one in MBedwars first."));
        } else {
            empty.line(SetupGui.gray("MBedwars is not installed."))
                    .line(SetupGui.gray("Install it, then reopen this menu."));
        }
        return empty.build();
    }

    // -------------------------------------------------------------- helpers

    /** The map keeps the face players know from the MBedwars selector. */
    private static Material iconFor(String arenaName) {
        try {
            Material icon = MBedwarsHook.arenaIconMaterial(arenaName);
            return icon != null ? icon : Material.GRASS_BLOCK;
        } catch (LinkageError e) {
            // available() proves MBedwars is enabled, not API-compatible.
            return Material.GRASS_BLOCK;
        }
    }

    /** The template name an import lands on — the importer's own cleaning. */
    static String sanitize(String name) {
        return me.beekrbonkr.practicecore.template.TemplateRegistry.sanitizeName(name);
    }
}
