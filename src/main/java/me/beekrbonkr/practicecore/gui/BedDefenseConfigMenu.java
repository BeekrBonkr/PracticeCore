package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.DyeColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Pre-join setup for bed defense practice on one map, and the same menu
 * mid-session (where Start becomes Apply). Reads top to bottom as the order
 * the choices are made: <em>where</em> (the team base), <em>what</em> (the
 * defense and the round rules), <em>or design your own</em> (the editor),
 * then go. Every change is persisted immediately.
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
        return plugin.guis().rows("beddefense", 6);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("beddefense.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("beddefense.buttons." + button, def);
    }

    private Material competitiveIcon(String button, Material def) {
        return plugin.guis().material("beddefense.buttons." + button + ".material-competitive", def);
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
        teamButton(slot("team", 13));
        defenseButton(slot("defense", 20));
        modeButton(slot("mode", 22));
        shuffleButton(slot("shuffle", 23));
        timerButton(slot("timer", 24));
        newButton(slot("new", 30));
        editButton(slot("edit", 32));
        startButton(slot("start", 40));
        nav("beddefense");
    }

    private void save() {
        plugin.bedDefenses().saveSelection(id(), selection);
    }

    // ----------------------------------------------------------------- team

    private void teamButton(int slot) {
        List<RushMapData.TeamBase> teams = data.playableTeams();
        String stored = plugin.stats().pref(id(), "rush.team." + template.name(), null);
        RushMapData.TeamBase current = data.team(stored);
        if (current == null && !teams.isEmpty()) {
            current = teams.get(0);
        }
        String teamName = current == null ? raw("gui.none") : RushMode.prettyTeam(current.name());
        Material wool = current == null ? Material.WHITE_WOOL
                : DyeColors.wool(DyeColors.parse(current.name(), DyeColor.WHITE));
        RushMapData.TeamBase chosen = current;
        boolean single = teams.size() <= 1;
        Button button = Button.of(plugin, wool)
                .name("gui.beddefense.team.name")
                .lore("gui.beddefense.team.lore",
                        "team", teamName, "count", String.valueOf(teams.size()));
        if (single) {
            button.disabledKeepIcon("gui.reason.only-one-team");
        } else {
            button.hint("cycle").rightHint("cycle-back");
        }
        set(slot, button.build(), event -> {
            if (single) {
                deny();
                return;
            }
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

    private void defenseButton(int slot) {
        BedDefense defense = plugin.bedDefenses().store().get(selection.defense());
        List<BedDefense> playable = plugin.bedDefenses().store().playableBy(id());
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

    private void modeButton(int slot) {
        boolean competitive = selection.competitive();
        set(slot, Button.of(plugin, competitive
                        ? competitiveIcon("mode", Material.NETHER_STAR)
                        : icon("mode", Material.LEVER))
                .name("gui.beddefense.mode.name")
                .lore("gui.beddefense.mode.lore", plugin.messages().ref("mode", competitive
                        ? "gui.beddefense.mode.option.competitive"
                        : "gui.beddefense.mode.option.practice"))
                .glow(competitive)
                .hint("toggle")
                .build(), event -> {
            sound(competitive ? "menu.toggle-off" : "menu.toggle-on");
            selection = selection.withCompetitive(!competitive);
            save();
            refresh();
        });
    }

    private void shuffleButton(int slot) {
        BedDefenseSelection.Shuffle shuffle = selection.shuffle();
        boolean pinned = selection.competitive();
        Button button = Button.of(plugin, icon("shuffle", Material.ENDER_EYE))
                .name("gui.beddefense.shuffle.name")
                .lore(pinned ? "gui.beddefense.shuffle.lore-competitive" : "gui.beddefense.shuffle.lore",
                        plugin.messages().ref("pool", shuffle.messageKey()));
        if (pinned) {
            button.disabled("gui.reason.pinned-competitive");
        } else {
            button.glow(shuffle != BedDefenseSelection.Shuffle.OFF).hint("cycle");
        }
        set(slot, button.build(), event -> {
            if (pinned) {
                deny();
                return;
            }
            click();
            selection = selection.withShuffle(shuffle.next());
            save();
            refresh();
        });
    }

    private void timerButton(int slot) {
        BedDefenseSelection.TimerStart start = selection.timerStart();
        boolean pinned = selection.competitive();
        Button button = Button.of(plugin, icon("timer", Material.REPEATER))
                .name("gui.beddefense.timer.name")
                .lore(pinned ? "gui.beddefense.timer.lore-competitive" : "gui.beddefense.timer.lore",
                        plugin.messages().ref("start", (pinned
                                ? BedDefenseSelection.TimerStart.MOVE : start).messageKey()));
        if (pinned) {
            button.disabled("gui.reason.pinned-competitive");
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

    // ---------------------------------------------------------------- start

    private void startButton(int slot) {
        List<BedDefense> playable = plugin.bedDefenses().store().playableBy(id());
        BedDefense chosen = plugin.bedDefenses().store().get(selection.defense());
        if (playable.isEmpty()) {
            set(slot, Button.of(plugin, icon("new", Material.CRAFTING_TABLE))
                    .name("gui.beddefense.start.name-design")
                    .lore("gui.beddefense.start.lore-design")
                    .hint("open")
                    .build(), event -> {
                click();
                plugin.bedDefenses().requestEdit(id(), null);
                later(() -> {
                    viewer.closeInventory();
                    plugin.bedDefenses().join(viewer, template);
                });
            });
            return;
        }
        boolean competitive = selection.competitive();
        String defenseLabel = chosen != null ? chosen.name()
                : selection.shuffle() != BedDefenseSelection.Shuffle.OFF && !competitive
                        ? raw(selection.shuffle().messageKey()) : playable.get(0).name();
        set(slot, Button.of(plugin, competitive
                        ? competitiveIcon("start", Material.NETHER_STAR)
                        : icon("start", Material.LIME_DYE))
                .name(inSession() ? "gui.beddefense.start.name-apply"
                        : competitive ? "gui.beddefense.start.name-competitive"
                        : "gui.beddefense.start.name")
                .lore("gui.beddefense.start.lore", TagResolver.resolver(
                        plugin.messages().ref("mode", competitive
                                ? "gui.beddefense.mode.option.competitive"
                                : "gui.beddefense.mode.option.practice"),
                        plugin.messages().ref("shuffle", competitive
                                ? BedDefenseSelection.Shuffle.OFF.messageKey()
                                : selection.shuffle().messageKey()),
                        plugin.messages().ref("timer", (competitive
                                ? BedDefenseSelection.TimerStart.MOVE
                                : selection.timerStart()).messageKey()),
                        plugin.messages().ref("ranked", competitive
                                ? "gui.beddefense.start.ranked" : "gui.beddefense.start.unranked")),
                        "arena", template.displayName(),
                        "defense", defenseLabel)
                .hint("play")
                .build(), event -> {
            click();
            save();
            plugin.bedDefenses().requestPlay(id());
            plugin.bedDefenses().clearRound(id());
            later(() -> {
                viewer.closeInventory();
                plugin.bedDefenses().join(viewer, template);
            });
        });
    }
}
