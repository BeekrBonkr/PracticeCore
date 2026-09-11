package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.DyeColors;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Pre-join setup for bed defense practice on one map, and the same menu
 * mid-session as the round settings. Reads top to bottom as the order the
 * choices are made: <em>where</em> (the team base, only on maps with more
 * than one), <em>what</em> (the defense and the timer start),
 * <em>or design your own</em> (the editor), then <em>go</em>: one start
 * button per mode — practice, competitive, obsidian, bed repair. Every
 * change is persisted immediately.
 */
public final class BedDefenseConfigMenu extends Menu {

    private final ArenaTemplate template;
    private final RushMapData data;
    private BedDefenseSelection selection;

    public BedDefenseConfigMenu(PracticeCorePlugin plugin, Player viewer, Menu parent,
                                ArenaTemplate template) {
        super(plugin, viewer, parent);
        this.template = template;
        this.data = RushMapData.parse(template);
        this.selection = plugin.bedDefenses().rawSelection(viewer.getUniqueId());
    }

    @Override
    protected Component title() {
        return text("gui.beddefense.title", "arena", template.displayName());
    }

    @Override
    protected int rows() {
        return plugin.guis().rows("beddefense", 5);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("beddefense.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("beddefense.buttons." + button, def);
    }

    private UUID id() {
        return viewer.getUniqueId();
    }

    /** True while the viewer is already practicing on this map in this mode. */
    private boolean inSession() {
        var session = plugin.sessions().get(id());
        return session != null && session.mode().id().equals(me.beekrbonkr.practicecore.mode.BedDefenseMode.ID)
                && session.template().name().equals(template.name());
    }

    @Override
    protected void render() {
        border();
        teamButton(slot("team", 4));
        defenseButton(slot("defense", 12));
        timerButton(slot("timer", 14));
        newButton(slot("new", 21));
        editButton(slot("edit", 23));
        BedDefenseStartButtons.place(this, "beddefense", new int[] {28, 30, 32, 34},
                template, inSession());
        nav("beddefense");
    }

    private void save() {
        plugin.bedDefenses().saveSelection(id(), selection);
    }

    // ----------------------------------------------------------------- team

    private void teamButton(int slot) {
        List<RushMapData.TeamBase> teams = data.playableTeams();
        if (teams.size() <= 1) {
            // Nothing to choose: the button only appears once a map has more
            // than one base to start from.
            return;
        }
        String stored = plugin.stats().pref(id(), "rush.team." + template.name(), null);
        RushMapData.TeamBase current = data.team(stored);
        if (current == null && !teams.isEmpty()) {
            current = teams.get(0);
        }
        String teamName = current == null ? none() : RushMode.prettyTeam(current.name());
        Material wool = current == null ? Material.WHITE_WOOL
                : DyeColors.wool(DyeColors.parse(current.name(), DyeColor.WHITE));
        RushMapData.TeamBase chosen = current;
        Button button = Button.of(plugin, wool)
                .name("gui.beddefense.team.name")
                .lore("gui.beddefense.team.lore",
                        "team", teamName, "count", String.valueOf(teams.size()))
                .hint("cycle").rightHint("cycle-back");
        set(slot, button.build(), event -> {
            click();
            int index = 0;
            for (int i = 0; i < teams.size(); i++) {
                if (chosen != null && teams.get(i).name().equals(chosen.name())) {
                    index = i;
                    break;
                }
            }
            index = Math.floorMod(index + (event.isRightClick() ? -1 : 1), teams.size());
            plugin.rush().saveTeam(id(), template, teams.get(index).name());
            refresh();
        });
    }

    // -------------------------------------------------------------- defense

    /** The defenses the current choices can run on: all of them, or the variant's eligible ones. */
    private List<BedDefense> playable() {
        return plugin.bedDefenses().playableBy(id(), selection);
    }

    /** The chosen defense, unless the current choices cannot run on it. */
    private BedDefense chosen() {
        BedDefense defense = plugin.bedDefenses().store().get(selection.defense());
        return BedDefenseService.playable(selection, defense) ? defense : null;
    }

    private void defenseButton(int slot) {
        BedDefense defense = chosen();
        List<BedDefense> playable = playable();
        Button button = Button.of(plugin, defense != null ? defense.icon() : icon("defense", Material.RED_BED))
                .name("gui.beddefense.defense.name");
        if (defense != null) {
            button.lore("gui.beddefense.defense.lore",
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "blocks", String.valueOf(defense.blocks().size()),
                    "available", String.valueOf(playable.size()));
        } else {
            button.lore(playable.isEmpty()
                    ? "gui.beddefense.defense.lore-none-exist" : "gui.beddefense.defense.lore-none",
                    "available", String.valueOf(playable.size()));
        }
        if (playable.isEmpty()) {
            button.disabled("gui.reason.no-defenses");
        } else {
            button.glow(defense != null).hint("open");
        }
        set(slot, button.build(), event -> {
            if (playable.isEmpty()) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseGalleryMenu(plugin, viewer, this,
                    BedDefenseGalleryMenu.Purpose.SELECT, picked -> {
                selection = selection.withDefense(picked.id());
                save();
            }).open());
        });
    }

    private void timerButton(int slot) {
        BedDefenseSelection.TimerStart start = selection.timerStart();
        boolean pinned = selection.mode() != BedDefenseSelection.Mode.PRACTICE;
        Button button = Button.of(plugin, icon("timer", Material.REPEATER))
                .name("gui.beddefense.timer.name")
                .lore(selection.repair() ? "gui.beddefense.timer.lore-repair"
                                : selection.obsidian() ? "gui.beddefense.timer.lore-obsidian"
                                : pinned ? "gui.beddefense.timer.lore-competitive"
                                : "gui.beddefense.timer.lore",
                        plugin.messages().ref("start", (pinned
                                ? BedDefenseSelection.TimerStart.MOVE : start).messageKey()));
        if (pinned) {
            button.disabled(selection.repair() ? "gui.reason.pinned-repair"
                    : selection.obsidian() ? "gui.reason.pinned-obsidian" : "gui.reason.pinned-competitive");
        } else {
            button.hint("cycle");
        }
        set(slot, button.build(), event -> {
            if (pinned) {
                deny();
                return;
            }
            click();
            selection = selection.withTimerStart(start.next());
            save();
            refresh();
        });
    }

    // --------------------------------------------------------------- editor

    private void newButton(int slot) {
        set(slot, Button.of(plugin, icon("new", Material.CRAFTING_TABLE))
                .name("gui.beddefense.new.name")
                .lore("gui.beddefense.new.lore",
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius()))
                .hint("open")
                .build(), event -> {
            click();
            plugin.bedDefenses().requestEdit(id(), null);
            later(() -> {
                viewer.closeInventory();
                plugin.bedDefenses().join(viewer, template);
            });
        });
    }

    private void editButton(int slot) {
        List<BedDefense> mine = plugin.bedDefenses().store().ownedBy(id());
        Button button = Button.of(plugin, icon("edit", Material.WRITABLE_BOOK))
                .name("gui.beddefense.edit.name")
                .lore(mine.isEmpty() ? "gui.beddefense.edit.lore-none" : "gui.beddefense.edit.lore",
                        "count", String.valueOf(mine.size()));
        if (mine.isEmpty()) {
            button.disabled("gui.reason.nothing-saved");
        } else {
            button.hint("open");
        }
        set(slot, button.build(), event -> {
            if (mine.isEmpty()) {
                deny();
                return;
            }
            click();
            later(() -> new BedDefenseGalleryMenu(plugin, viewer, this,
                    BedDefenseGalleryMenu.Purpose.EDIT, picked -> {
                plugin.bedDefenses().requestEdit(id(), picked.id());
                viewer.closeInventory();
                plugin.bedDefenses().join(viewer, template);
            }).open());
        });
    }
}
