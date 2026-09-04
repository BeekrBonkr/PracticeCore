package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.DyeColors;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        strictButton(slot("strict", 23));
        shuffleButton(slot("shuffle", 24));
        timerButton(slot("timer", 25));
        newButton(slot("new", 30));
        editButton(slot("edit", 32));
        startButton(slot("start", 40));
        backButton(plugin.guis().slot("beddefense.back", 45));
        closeButton(plugin.guis().slot("beddefense.close", 53));
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
        set(slot, ItemBuilder.of(wool)
                .name(name("gui.rush.team.name"))
                .lore(lore("gui.rush.team.lore",
                        "team", teamName, "count", String.valueOf(teams.size())))
                .build(), event -> {
            if (teams.size() <= 1) {
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
        ItemBuilder builder = ItemBuilder.of(defense != null ? defense.icon() : icon("defense", Material.RED_BED))
                .name(name("gui.beddefense.defense.name"));
        if (defense != null) {
            builder.lore(lore("gui.beddefense.defense.lore",
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "blocks", String.valueOf(defense.blocks().size()),
                    "available", String.valueOf(playable.size())));
        } else {
            builder.lore(lore(playable.isEmpty()
                    ? "gui.beddefense.defense.lore-none-exist" : "gui.beddefense.defense.lore-none",
                    "available", String.valueOf(playable.size())));
        }
        set(slot, builder.glow(defense != null).build(), event -> {
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
        set(slot, ItemBuilder.of(competitive
                        ? icon("mode", Material.NETHER_STAR)
                        : plugin.guis().material("beddefense.buttons.mode.material-practice",
                                Material.WHITE_WOOL))
                .name(name("gui.beddefense.mode.name"))
                .lore(lore("gui.beddefense.mode.lore", plugin.messages().ref("mode", competitive
                        ? "gui.beddefense.mode.option.competitive"
                        : "gui.beddefense.mode.option.practice")))
                .glow(competitive)
                .build(), event -> {
            click();
            selection = selection.withCompetitive(!competitive);
            save();
            refresh();
        });
    }

    private void strictButton(int slot) {
        boolean strict = selection.strictOrder();
        set(slot, ItemBuilder.of(icon("strict", Material.COMPARATOR))
                .name(name("gui.beddefense.strict.name"))
                .lore(lore("gui.beddefense.strict.lore", plugin.messages().ref("state",
                        strict ? "gui.beddefense.state-on" : "gui.beddefense.state-off")))
                .glow(strict)
                .build(), event -> {
            click();
            selection = selection.withStrictOrder(!strict);
            save();
            refresh();
        });
    }

    private void shuffleButton(int slot) {
        BedDefenseSelection.Shuffle shuffle = selection.shuffle();
        boolean pinned = selection.competitive();
        set(slot, ItemBuilder.of(icon("shuffle", Material.ENDER_EYE))
                .name(name("gui.beddefense.shuffle.name"))
                .lore(lore(pinned ? "gui.beddefense.shuffle.lore-competitive"
                                : "gui.beddefense.shuffle.lore",
                        plugin.messages().ref("pool", shuffle.messageKey())))
                .glow(!pinned && shuffle != BedDefenseSelection.Shuffle.OFF)
                .build(), event -> {
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
        set(slot, ItemBuilder.of(icon("timer", Material.CLOCK))
                .name(name("gui.beddefense.timer.name"))
                .lore(lore(pinned ? "gui.beddefense.timer.lore-competitive"
                                : "gui.beddefense.timer.lore",
                        plugin.messages().ref("start", (pinned
                                ? BedDefenseSelection.TimerStart.MOVE : start).messageKey())))
                .build(), event -> {
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
        set(slot, ItemBuilder.of(icon("new", Material.CRAFTING_TABLE))
                .name(name("gui.beddefense.new.name"))
                .lore(lore("gui.beddefense.new.lore",
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius())))
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
        set(slot, ItemBuilder.of(icon("edit", Material.WRITABLE_BOOK))
                .name(name("gui.beddefense.edit.name"))
                .lore(lore(mine.isEmpty() ? "gui.beddefense.edit.lore-none" : "gui.beddefense.edit.lore",
                        "count", String.valueOf(mine.size())))
                .build(), event -> {
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
            set(slot, ItemBuilder.of(icon("start", Material.LIME_DYE))
                    .name(name("gui.beddefense.start.name-design"))
                    .lore(lore("gui.beddefense.start.lore-design"))
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
        set(slot, ItemBuilder.of(icon("start", competitive ? Material.NETHER_STAR : Material.LIME_DYE))
                .name(name(inSession() ? "gui.beddefense.start.name-apply"
                        : competitive ? "gui.beddefense.start.name-competitive"
                        : "gui.beddefense.start.name"))
                .lore(lore("gui.beddefense.start.lore", TagResolver.resolver(
                        plugin.messages().ref("mode", competitive
                                ? "gui.beddefense.mode.option.competitive"
                                : "gui.beddefense.mode.option.practice"),
                        plugin.messages().ref("strict", selection.strictOrder()
                                ? "gui.beddefense.state-on" : "gui.beddefense.state-off"),
                        plugin.messages().ref("shuffle", competitive
                                ? BedDefenseSelection.Shuffle.OFF.messageKey()
                                : selection.shuffle().messageKey()),
                        plugin.messages().ref("timer", (competitive
                                ? BedDefenseSelection.TimerStart.MOVE
                                : selection.timerStart()).messageKey()),
                        plugin.messages().ref("ranked", competitive
                                ? "gui.beddefense.start.ranked" : "gui.beddefense.start.unranked")),
                        "arena", template.displayName(),
                        "defense", defenseLabel))
                .glow(competitive)
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
