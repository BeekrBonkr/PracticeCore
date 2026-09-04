package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.pvpbot.BotPreset;
import me.beekrbonkr.practicecore.rush.RushDefense;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.rush.RushPreset;
import me.beekrbonkr.practicecore.rush.RushSelection;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.DyeColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Pre-join setup for one rush map: pick the team base, dial the difficulty
 * modifiers, then start — a practice run with those modifiers, or
 * <b>competitive</b>, the fixed ranked loadout (no starting items, defenses
 * on, generators on) that is the only way onto the leaderboards. There is no
 * objective to choose — every one the map supports is armed, and whichever
 * is completed first ends the run. Every change is persisted immediately, so
 * a plain /practice join later replays the same choices.
 *
 * <p>The layout reads top to bottom as the order the choices are actually
 * made: <em>where you start</em> (the base) on the first row, <em>what you
 * start with and what stands in your way</em> (the six match modifiers) on
 * the second, <em>who is waiting for you</em> (the defender lineup) on the
 * third, and <em>go</em> (practice / competitive) on the fourth. The practice
 * start carries a summary of everything set above it, so nobody has to hover
 * a row of buttons to find out what they are about to play. The bottom row
 * holds the one-click presets ({@link RushPreset}): each writes a known-good
 * set of dials and starts the run immediately.
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
        return plugin.guis().rows("rush", 6);
    }

    private int slot(String button, int def) {
        return plugin.guis().slot("rush.buttons." + button, def);
    }

    private Material icon(String button, Material def) {
        return plugin.guis().buttonMaterial("rush.buttons." + button, def);
    }

    private void save() {
        plugin.rush().saveSelection(viewer.getUniqueId(), selection);
    }

    @Override
    protected void render() {
        border();
        teamButton(slot("team", 13));
        blocksButton(slot("blocks", 20));
        currencyButton(slot("currency", 21));
        pickaxeButton(slot("pickaxe", 22));
        defenseButton(slot("defense", 23));
        generatorsButton(slot("generators", 24));
        tntButton(slot("tnt", 25));
        botsButton(slot("bots", 29));
        // The lineup knobs only appear once there is a lineup to shape.
        if (selection.bots() > 0) {
            botDifficultyButton(slot("bot-difficulty", 30));
            botArmorButton(slot("bot-armor", 31));
            botSwordButton(slot("bot-sword", 32));
        }
        startButton(slot("start", 39));
        competitiveButton(slot("competitive", 41));
        presetButtons();
        nav("rush");
    }

    // ----------------------------------------------------------------- team

    private void teamButton(int slot) {
        List<RushMapData.TeamBase> teams = data.playableTeams();
        RushMapData.TeamBase current = data.team(selection.team());
        if (current == null && !teams.isEmpty()) {
            current = teams.get(0);
        }
        String teamName = current == null ? raw("gui.none") : RushMode.prettyTeam(current.name());
        Button button = Button.of(plugin, teamWool(current))
                .name("gui.rush.team.name")
                .lore("gui.rush.team.lore",
                        "team", teamName,
                        "count", String.valueOf(teams.size()));
        if (teams.size() <= 1) {
            button.disabled("gui.reason.only-one-team");
        } else {
            button.hint("cycle").rightHint("cycle-back");
        }
        set(slot, button.build(), event -> {
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
        return DyeColors.wool(DyeColors.parse(team.name(), DyeColor.WHITE));
    }

    // ------------------------------------------------------------ modifiers
    // Cyclers carry their value on a state line and never glow (R37): glow
    // is for selected/on, and "some blocks" is a value, not a state.

    private void blocksButton(int slot) {
        RushSelection.BlockTier tier = selection.blocks();
        set(slot, Button.of(plugin, icon("blocks", Material.WHITE_WOOL), Math.max(1, tier.amount()))
                .name("gui.rush.blocks.name")
                .lore("gui.rush.blocks.lore", "amount",
                        tier.amount() == 0 ? raw("gui.none") : String.valueOf(tier.amount()))
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withBlocks(tier.next());
            save();
            refresh();
        });
    }

    private void currencyButton(int slot) {
        RushSelection.CurrencyTier tier = selection.currency();
        set(slot, Button.of(plugin, icon("currency", Material.IRON_INGOT), Math.max(1, tier.iron()))
                .name("gui.rush.currency.name")
                .lore("gui.rush.currency.lore",
                        "iron", tier.iron() == 0 ? raw("gui.none") : String.valueOf(tier.iron()),
                        "gold", tier.gold() == 0 ? raw("gui.none") : String.valueOf(tier.gold()))
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withCurrency(tier.next());
            save();
            refresh();
        });
    }

    private void pickaxeButton(int slot) {
        RushSelection.PickaxeTier tier = selection.pickaxe();
        Material icon = tier.item() != null ? tier.item() : icon("pickaxe", Material.IRON_PICKAXE);
        set(slot, Button.of(plugin, icon)
                .name("gui.rush.pickaxe.name")
                .lore("gui.rush.pickaxe.lore", plugin.messages().ref("tier",
                        "gui.rush.pickaxe.option." + tier.name().toLowerCase(Locale.ROOT)))
                .hideAttributes()
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withPickaxe(tier.next());
            save();
            refresh();
        });
    }

    /**
     * Opens the preset gallery rather than cycling: there are far more
     * pyramids to choose from than anyone wants to click through, and the
     * gallery can show what each one is actually made of.
     */
    private void defenseButton(int slot) {
        RushDefense preset = plugin.pcConfig().rushDefense(selection.defense());
        set(slot, Button.of(plugin, icon("defense", preset.menuIcon()))
                .name("gui.rush.defense.name")
                .lore("gui.rush.defense.lore",
                        "preset", defenseName(preset),
                        "layers", String.valueOf(preset.reach()))
                .hideAttributes()
                .hint("open")
                .build(), event -> {
            click();
            later(() -> new RushDefenseMenu(plugin, viewer, this, template, selection.defense(),
                    picked -> {
                        selection = selection.withDefense(picked.id());
                        save();
                    }).open());
        });
    }

    /** A preset's label: messages.yml first, then its own configured name. */
    private String defenseName(RushDefense preset) {
        String configured = raw("gui.rush.defense.option." + preset.id());
        return configured.isEmpty() ? preset.displayName() : configured;
    }

    private void generatorsButton(int slot) {
        boolean on = selection.baseGenerators();
        set(slot, Button.of(plugin, icon("generators", Material.FURNACE))
                .name("gui.rush.generators.name")
                .lore("gui.rush.generators.lore", plugin.messages().ref("state",
                        on ? "label.state.on" : "label.state.off"))
                .glow(on)
                .hint("toggle")
                .build(), event -> {
            sound(on ? "menu.toggle-off" : "menu.toggle-on");
            selection = selection.withBaseGenerators(!on);
            save();
            refresh();
        });
    }

    /** Starter TNT — it auto-ignites on place, so it is demolition fuel. */
    private void tntButton(int slot) {
        RushSelection.TntTier tier = selection.tnt();
        set(slot, Button.of(plugin, icon("tnt", Material.TNT), Math.max(1, tier.amount()))
                .name("gui.rush.tnt.name")
                .lore("gui.rush.tnt.lore", "amount",
                        tier.amount() == 0 ? raw("gui.none") : String.valueOf(tier.amount()))
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withTnt(tier.next());
            save();
            refresh();
        });
    }

    // ------------------------------------------------------------- defenders

    /** Bots per enemy team, 0 (classic race) up to the configured ceiling. */
    private void botsButton(int slot) {
        int bots = selection.bots();
        int max = plugin.pcConfig().rushBotsMaxPerTeam();
        set(slot, Button.of(plugin, icon("bots", Material.ZOMBIE_HEAD), Math.max(1, bots))
                .name("gui.rush.bots.name")
                .lore("gui.rush.bots.lore", "count",
                        bots == 0 ? raw("gui.none") : String.valueOf(bots))
                .hint("cycle")
                .rightHint("cycle-back")
                .build(), event -> {
            click();
            int next = event.isRightClick()
                    ? Math.floorMod(bots - 1, max + 1) : (bots + 1) % (max + 1);
            selection = selection.withBots(next);
            save();
            refresh();
        });
    }

    /** The pvpbot.yml preset the defenders fight at, cycled in file order. */
    private void botDifficultyButton(int slot) {
        var presets = plugin.botTuning().presets();
        var current = plugin.rushBots().presetOf(selection.botDifficulty());
        Component label = current == null
                ? name("gui.rush.bot-difficulty.default")
                : presetLabel(current);
        Button button = Button.of(plugin, icon("bot-difficulty", Material.EXPERIENCE_BOTTLE))
                .name("gui.rush.bot-difficulty.name")
                .lore("gui.rush.bot-difficulty.lore", plugin.messages().ref("level", label));
        if (presets.isEmpty()) {
            button.disabled("gui.reason.no-presets");
        } else {
            button.hint("cycle");
        }
        set(slot, button.build(), event -> {
            if (presets.isEmpty()) {
                deny();
                return;
            }
            click();
            var next = plugin.botTuning().nextPreset(current);
            selection = selection.withBotDifficulty(next == null ? "" : next.id());
            save();
            refresh();
        });
    }

    /** A preset's short label, reusing the PvP bot's own difficulty names. */
    private Component presetLabel(BotPreset preset) {
        String key = preset.messageKey("short");
        if (!plugin.messages().raw(key).isEmpty()) {
            return plugin.messages().component(key);
        }
        // STYLE-GUIDE: needs logic change (F8) — a custom preset with no
        // label.difficulty.* key renders uncolored.
        return Component.text(preset.configuredName().isEmpty()
                ? preset.id() : preset.configuredName());
    }

    private void botArmorButton(int slot) {
        RushSelection.BotArmor armor = selection.botArmor();
        Material icon = armor.piece("CHESTPLATE");
        set(slot, Button.of(plugin, icon != null ? icon : Material.LEATHER_CHESTPLATE)
                .name("gui.rush.bot-armor.name")
                .lore("gui.rush.bot-armor.lore", plugin.messages().ref("tier",
                        "gui.rush.bot-armor.option." + armor.name().toLowerCase(Locale.ROOT)))
                .hideAttributes()
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withBotArmor(armor.next());
            save();
            refresh();
        });
    }

    private void botSwordButton(int slot) {
        RushSelection.BotSword sword = selection.botSword();
        set(slot, Button.of(plugin, sword.item())
                .name("gui.rush.bot-sword.name")
                .lore("gui.rush.bot-sword.lore", plugin.messages().ref("tier",
                        "gui.rush.bot-sword.option." + sword.name().toLowerCase(Locale.ROOT)))
                .hideAttributes()
                .hint("cycle")
                .build(), event -> {
            click();
            selection = selection.withBotSword(sword.next());
            save();
            refresh();
        });
    }

    // ----------------------------------------------------------------- start

    private void startButton(int slot) {
        set(slot, Button.of(plugin, icon("start", Material.LIME_DYE))
                .name("gui.rush.start.name")
                .lore("gui.rush.start.lore", summary(),
                        "arena", template.displayName(),
                        "team", teamLabel())
                .hint("play")
                .build(), event -> {
            click();
            plugin.rush().setCompetitive(viewer.getUniqueId(), false);
            save();
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
     * Every modifier this run would use, as one set of placeholders for the
     * start button's lore — so the summary lives where the decision is made
     * instead of five hovers away. These are the <em>stored</em> choices:
     * competitive pins its own loadout and says so in its own lore.
     */
    private TagResolver summary() {
        var msg = plugin.messages();
        RushSelection.BlockTier blocks = selection.blocks();
        RushSelection.CurrencyTier currency = selection.currency();
        RushDefense defense = plugin.pcConfig().rushDefense(selection.defense());
        return TagResolver.resolver(
                msg.ref("blocks", blocks.amount() == 0 ? msg.component("gui.none")
                        : msg.component("gui.rush.summary.blocks",
                                "amount", String.valueOf(blocks.amount()))),
                msg.ref("currency", currency.iron() == 0 && currency.gold() == 0
                        ? msg.component("gui.none")
                        : msg.component("gui.rush.summary.currency",
                                "iron", String.valueOf(currency.iron()),
                                "gold", String.valueOf(currency.gold()))),
                msg.ref("pickaxe", "gui.rush.pickaxe.option."
                        + selection.pickaxe().name().toLowerCase(Locale.ROOT)),
                msg.ref("tnt", selection.tnt().amount() == 0 ? msg.component("gui.none")
                        : msg.component("gui.rush.summary.tnt",
                                "amount", String.valueOf(selection.tnt().amount()))),
                msg.ref("defense", defense.builds()
                        ? msg.component("gui.rush.summary.defense",
                                "preset", defenseName(defense),
                                "layers", String.valueOf(defense.reach()))
                        : msg.component("gui.none")),
                msg.ref("generators", selection.baseGenerators()
                        ? "label.state.on" : "label.state.off"),
                msg.ref("defenders", selection.combat()
                        ? msg.component("gui.rush.summary.defenders",
                                msg.ref("difficulty", defenderDifficulty()),
                                "count", String.valueOf(selection.bots()),
                                "armor", raw("gui.rush.bot-armor.option."
                                        + selection.botArmor().name().toLowerCase(Locale.ROOT)),
                                "sword", raw("gui.rush.bot-sword.option."
                                        + selection.botSword().name().toLowerCase(Locale.ROOT)))
                        : msg.component("gui.none")));
    }

    /** The defender lineup's difficulty label, or the configured default. */
    private Component defenderDifficulty() {
        var preset = plugin.rushBots().presetOf(selection.botDifficulty());
        return preset == null ? name("gui.rush.bot-difficulty.default") : presetLabel(preset);
    }

    /** The chosen base's name, or "none" on a map with no playable team. */
    private String teamLabel() {
        RushMapData.TeamBase team = data.team(selection.team());
        if (team == null) {
            List<RushMapData.TeamBase> teams = data.playableTeams();
            team = teams.isEmpty() ? null : teams.get(0);
        }
        return team == null ? raw("gui.none") : RushMode.prettyTeam(team.name());
    }

    /**
     * Instant start under the fixed ranked loadout: generators on, no
     * starting items, defenses on. The stored practice modifiers are left
     * untouched underneath — competitive pins them only for the run.
     */
    private void competitiveButton(int slot) {
        set(slot, Button.of(plugin, icon("competitive", Material.NETHER_STAR))
                .name("gui.rush.competitive.name")
                .lore("gui.rush.competitive.lore", "arena", template.displayName())
                .hint("play")
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

    /**
     * The one-click presets along the bottom border row: each writes its
     * known-good dial positions over the stored selection and starts the run
     * — the fastest route into a specific kind of practice. Presets the
     * config has switched off (a zero-defender competitive lineup, bots
     * capped at none) are simply not offered. The preset whose dials match
     * the current selection glows — it is the one "selected" (R37).
     */
    private void presetButtons() {
        for (RushPreset preset : RushPreset.values()) {
            if (!preset.available(plugin.pcConfig())
                    || !plugin.guis().buttonEnabled(preset.buttonKey())) {
                continue;
            }
            boolean applied = preset.apply(selection, plugin.pcConfig()).equals(selection);
            set(plugin.guis().slot(preset.buttonKey(), preset.defaultSlot()),
                    Button.of(plugin, plugin.guis().buttonMaterial(preset.buttonKey(), preset.icon()))
                            .name(preset.messageKey("name"))
                            .lore(preset.messageKey("lore"), "arena", template.displayName())
                            .glow(applied)
                            .hideAttributes()
                            .hint("play")
                            .build(), event -> {
                sound("menu.select");
                selection = preset.apply(selection, plugin.pcConfig());
                save();
                plugin.rush().setCompetitive(viewer.getUniqueId(), preset.competitive());
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
}
