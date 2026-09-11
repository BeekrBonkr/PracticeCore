package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection.Mode;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * The four bed defense start buttons — Practice, Competitive, Obsidian and
 * Bed Repair — shared by the setup menu and the in-arena menu. One click
 * picks the mode and starts (or restarts) the round in it; the mode the
 * player is currently set to glows. A mode with nothing it can run on is
 * disabled and says why.
 */
final class BedDefenseStartButtons {

    private BedDefenseStartButtons() {
    }

    /** The style-guide icon for each mode: unranked go, ranked go, and the two drills' own blocks. */
    static Material defaultIcon(Mode mode) {
        return switch (mode) {
            case PRACTICE -> Material.LIME_DYE;
            case COMPETITIVE -> Material.NETHER_STAR;
            case OBSIDIAN -> Material.OBSIDIAN;
            case REPAIR -> Material.TNT;
        };
    }

    /**
     * Places all four on {@code menu}. {@code section} is the menu's
     * guis.yml section (the buttons live at {@code <section>.buttons.start-<mode>});
     * {@code defaults} the four fallback slots in mode order.
     */
    static void place(Menu menu, String section, int[] defaults, ArenaTemplate template,
                      boolean inSession) {
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            String path = section + ".buttons.start-" + modes[i].key();
            if (!menu.plugin.guis().buttonEnabled(path)) {
                continue;
            }
            place(menu, menu.plugin.guis().slot(path, defaults[i]),
                    menu.plugin.guis().buttonMaterial(path, defaultIcon(modes[i])),
                    modes[i], template, inSession);
        }
    }

    private static void place(Menu menu, int slot, Material icon, Mode mode, ArenaTemplate template,
                              boolean inSession) {
        PracticeCorePlugin plugin = menu.plugin;
        BedDefenseService service = plugin.bedDefenses();
        UUID id = menu.viewer.getUniqueId();
        BedDefenseSelection selection = service.rawSelection(id);
        BedDefenseSelection wanted = selection.withMode(mode);
        BedDefenseSelection effective = wanted.effective();
        List<BedDefense> playable = service.playableBy(id, wanted);
        int all = service.store().playableBy(id).size();
        BedDefense chosen = service.store().get(wanted.defense());
        if (!BedDefenseService.playable(wanted, chosen)) {
            chosen = null;
        }
        String defenseLabel = chosen != null ? chosen.name()
                : effective.shuffle() != BedDefenseSelection.Shuffle.OFF
                        ? menu.raw(effective.shuffle().messageKey())
                        : playable.isEmpty() ? menu.none() : playable.get(0).name();
        String reason = null;
        if (playable.isEmpty()) {
            reason = switch (mode) {
                case OBSIDIAN -> "gui.reason.no-obsidian-defenses";
                case REPAIR -> "gui.reason.no-repair-defenses";
                default -> "gui.reason.no-defenses";
            };
        } else if (mode == Mode.COMPETITIVE && !service.shopAvailable()) {
            reason = "gui.reason.no-shop";
        }
        Button button = Button.of(plugin, icon)
                .name("gui.beddefense.start." + mode.key() + ".name")
                .lore("gui.beddefense.start." + mode.key() + ".lore",
                        "arena", template.displayName(),
                        "defense", defenseLabel,
                        "shuffle", menu.raw(effective.shuffle().messageKey()),
                        "timer", menu.raw(effective.timerStart().messageKey()),
                        "eligible", String.valueOf(playable.size()),
                        "available", String.valueOf(all),
                        "limit", String.valueOf(plugin.pcConfig().bedDefenseRepairExposedStartTicks() / 20),
                        "min", String.valueOf(plugin.pcConfig().bedDefenseRepairExposedMinTicks() / 20));
        boolean enabled = reason == null;
        if (enabled) {
            button.glow(selection.mode() == mode).hint(inSession ? "restart" : "play");
        } else {
            button.disabled(reason);
        }
        menu.set(slot, button.build(), event -> {
            if (!enabled) {
                menu.deny();
                return;
            }
            menu.click();
            menu.later(() -> {
                menu.viewer.closeInventory();
                service.start(menu.viewer, template, wanted);
            });
        });
    }
}
