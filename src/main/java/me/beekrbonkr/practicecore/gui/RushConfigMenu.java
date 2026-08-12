package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.rush.RushSelection;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Locale;

/**
 * Pre-join setup for one rush map: pick the team base, dial the difficulty
 * modifiers, then start — casually with those modifiers, or <b>competitive</b>,
 * the fixed ranked loadout (no starting items, defenses on, generators on)
 * that is the only way onto the leaderboards. There is no objective to
 * choose — every one the map supports is armed, and whichever is completed
 * first ends the run. Every change is persisted immediately, so a plain
 * /practice join later replays the same choices.
 */
public final class RushConfigMenu extends Menu {

    private final ArenaTemplate template;
    private final RushMapData data;
    /** The raw stored selection — competitive pins are applied at join, not here. */
    private RushSelection selection;

    public RushConfigMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                          ArenaTemplate template) {
        super(plugin, viewer, parent);
        this.template = template;
        this.data = RushMapData.parse(template);
        this.selection = plugin.rush().rawSelection(viewer.getUniqueId(), template, data);
    }

    @Override
    protected Component title() {
        return text("gui.rush.title", "arena", template.displayName());
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("rush", 4);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("rush.buttons." + button, def);
    }

    @Override
    protected void render() {
        border();
        teamButton(slot("team", 11));
        startButton(slot("start", 13));
        competitiveButton(slot("competitive", 15));
        blocksButton(slot("blocks", 19));
        currencyButton(slot("currency", 20));
        pickaxeButton(slot("pickaxe", 21));
        defenseButton(slot("defense", 22));
        generatorsButton(slot("generators", 23));
        backButton(plugin.guis().slot("rush.back", 27));
        closeButton(plugin.guis().slot("rush.close", 35));
    }

    // ----------------------------------------------------------------- team

    private void teamButton(int slot) {
        List<RushMapData.TeamBase> teams = data.playableTeams();
        RushMapData.TeamBase current = data.team(selection.team());
        if (current == null && !teams.isEmpty()) {
            current = teams.get(0);
        }
        String teamName = current == null ? raw("gui.none") : RushMode.prettyTeam(current.name());
        set(slot, ItemBuilder.of(teamWool(current))
                .name(name("gui.rush.team.name"))
                .lore(lore("gui.rush.team.lore",
                        "team", teamName,
                        "count", String.valueOf(teams.size())))
                .build(), event -> {
            if (teams.size() <= 1) {
                deny();
                return;
            }
            click();
            int index = 0;
            for (int i = 0; i < teams.size(); i++) {
                if (selection.team() != null && teams.get(i).name().equals(selection.team())) {
                    index = i;
                    break;
                }
            }
            index = Math.floorMod(index + (event.isRightClick() ? -1 : 1), teams.size());
            selection = selection.withTeam(teams.get(index).name());
            plugin.rush().saveTeam(viewer.getUniqueId(), template, selection.team());
            refresh();
        });
    }

    /** The wool matching an MBedwars team name, or white for exotic ones. */
    private static Material teamWool(RushMapData.TeamBase team) {
        if (team == null) {
            return Material.WHITE_WOOL;
        }
        String name = team.name().toUpperCase(Locale.ROOT);
        DyeColor color;
        try {
            color = DyeColor.valueOf(name.equals("AQUA") ? "LIGHT_BLUE" : name);
        } catch (IllegalArgumentException e) {
            color = DyeColor.WHITE;
        }
        Material wool = Material.matchMaterial(color.name() + "_WOOL");
        return wool != null ? wool : Material.WHITE_WOOL;
    }

    // ------------------------------------------------------------ modifiers

    private void blocksButton(int slot) {
        RushSelection.BlockTier tier = selection.blocks();
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.blocks", Material.WHITE_WOOL),
                        Math.max(1, tier.amount()))
                .name(name("gui.rush.blocks.name"))
                .lore(lore("gui.rush.blocks.lore", "amount",
                        tier.amount() == 0 ? raw("gui.none") : String.valueOf(tier.amount())))
                .glow(tier.amount() > 0)
                .build(), event -> {
            click();
            selection = selection.withBlocks(tier.next());
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            refresh();
        });
    }

    private void currencyButton(int slot) {
        RushSelection.CurrencyTier tier = selection.currency();
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.currency", Material.IRON_INGOT),
                        Math.max(1, tier.iron()))
                .name(name("gui.rush.currency.name"))
                .lore(lore("gui.rush.currency.lore",
                        "iron", tier.iron() == 0 ? raw("gui.none") : String.valueOf(tier.iron()),
                        "gold", tier.gold() == 0 ? raw("gui.none") : String.valueOf(tier.gold())))
                .glow(tier.iron() > 0)
                .build(), event -> {
            click();
            selection = selection.withCurrency(tier.next());
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            refresh();
        });
    }

    private void pickaxeButton(int slot) {
        RushSelection.PickaxeTier tier = selection.pickaxe();
        Material icon = tier.item() != null ? tier.item()
                : plugin.guis().buttonMaterial("rush.buttons.pickaxe", Material.IRON_PICKAXE);
        set(slot, ItemBuilder.of(icon)
                .name(name("gui.rush.pickaxe.name"))
                .lore(lore("gui.rush.pickaxe.lore", plugin.messages().ref("tier",
                        "gui.rush.pickaxe.option." + tier.name().toLowerCase(Locale.ROOT))))
                .glow(tier.item() != null)
                .hideAttributes()
                .build(), event -> {
            click();
            selection = selection.withPickaxe(tier.next());
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            refresh();
        });
    }

    private void defenseButton(int slot) {
        RushSelection.DefensePreset preset = selection.defense();
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.defense", Material.END_STONE))
                .name(name("gui.rush.defense.name"))
                .lore(lore("gui.rush.defense.lore", plugin.messages().ref("preset",
                        "gui.rush.defense.option." + preset.name().toLowerCase(Locale.ROOT))))
                .glow(preset != RushSelection.DefensePreset.NONE)
                .build(), event -> {
            click();
            selection = selection.withDefense(preset.next());
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            refresh();
        });
    }

    private void generatorsButton(int slot) {
        boolean on = selection.baseGenerators();
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.generators", Material.FURNACE))
                .name(name("gui.rush.generators.name"))
                .lore(lore("gui.rush.generators.lore", plugin.messages().ref("state",
                        on ? "gui.rush.generators.state-on" : "gui.rush.generators.state-off")))
                .glow(on)
                .build(), event -> {
            click();
            selection = selection.withBaseGenerators(!on);
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            refresh();
        });
    }

    // ----------------------------------------------------------------- start

    private void startButton(int slot) {
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.start", Material.LIME_DYE))
                .name(name("gui.rush.start.name"))
                .lore(lore("gui.rush.start.lore", "arena", template.displayName()))
                .build(), event -> {
            click();
            plugin.rush().setCompetitive(viewer.getUniqueId(), false);
            plugin.rush().saveSelection(viewer.getUniqueId(), selection);
            if (selection.team() != null) {
                plugin.rush().saveTeam(viewer.getUniqueId(), template, selection.team());
            }
            later(() -> {
                viewer.closeInventory();
                plugin.sessions().join(viewer, template);
            });
        });
    }

    /**
     * Instant start under the fixed ranked loadout: generators on, no
     * starting items, defenses on. The stored casual modifiers are left
     * untouched underneath — competitive pins them only for the run.
     */
    private void competitiveButton(int slot) {
        set(slot, ItemBuilder.of(
                        plugin.guis().buttonMaterial("rush.buttons.competitive", Material.NETHER_STAR))
                .name(name("gui.rush.competitive.name"))
                .lore(lore("gui.rush.competitive.lore", "arena", template.displayName()))
                .glow(true)
                .build(), event -> {
            click();
            plugin.rush().setCompetitive(viewer.getUniqueId(), true);
            if (selection.team() != null) {
                plugin.rush().saveTeam(viewer.getUniqueId(), template, selection.team());
            }
            later(() -> {
                viewer.closeInventory();
                plugin.sessions().join(viewer, template);
            });
        });
    }
}
