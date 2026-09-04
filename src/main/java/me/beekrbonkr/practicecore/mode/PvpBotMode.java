package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.pvpbot.BotFight;
import me.beekrbonkr.practicecore.pvpbot.BotSettings;
import me.beekrbonkr.practicecore.pvpbot.PvpBotService;
import me.beekrbonkr.practicecore.session.PracticeSession;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PvP practice against an AI opponent: an endless spar in an admin-built
 * arena against a bot that strafes out of the player's crosshair, spam-clicks
 * at a configured CPS, jump-crits, W-taps, rods, shoots and sword-blocks —
 * every behavior dialed in from the in-game settings item, which freezes the
 * bot while its GUI is open.
 *
 * There is no timer and no leaderboard. The sidebar keeps session stats
 * (kills, deaths, hits, combos); dying — including a ring-out over the edge —
 * costs a stock and starts a 3-second respawn hold (blind and untouchable
 * under a countdown title; the bot's own deaths count down on the action
 * bar), then the spar continues with no death screen. Kits are the built-in presets in
 * {@link me.beekrbonkr.practicecore.pvpbot.PvpKit}, not the arena's admin kit,
 * and their consumables are kept topped up.
 *
 * Combat mechanics themselves (1.8 cooldowns, knockback, sword-block damage
 * halving for the player) belong to the server's combat plugins — this mode
 * only opens the damage path between the player and their own bot.
 */
public final class PvpBotMode implements Mode {

    public static final String ID = "pvpbot";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "PvP Bot";
    }

    @Override
    public boolean requiresTrigger() {
        return false; // nothing to finish — the spar is endless
    }

    @Override
    public boolean usesStandardTimerStart() {
        return false; // no timer at all
    }

    @Override
    public boolean validatesInventory() {
        return false; // pots get drunk, arrows get shot, refills top up
    }

    @Override
    public boolean recordsRun(PracticeCorePlugin plugin, PracticeSession session) {
        return false; // sessions never finish() — nothing to record
    }

    @Override
    public boolean hasLeaderboards() {
        return false; // session stats only — no shared boards to show
    }

    // --------------------------------------------------------------- rounds

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        // onReady also runs after /practice restart: the reset already wiped
        // the old bot entity, so the fight is rebuilt from scratch.
        plugin.pvpBot().beginFight(player, session);
    }

    @Override
    public void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        plugin.pvpBot().captureLayout(player, PvpBotService.fightOf(session));
        plugin.pvpBot().despawn(PvpBotService.fightOf(session));
    }

    @Override
    public void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        plugin.pvpBot().captureLayout(player, PvpBotService.fightOf(session));
        plugin.pvpBot().despawn(PvpBotService.fightOf(session));
    }

    /** Falling off the arena is a ring-out: a lost stock, not a failed run. */
    @Override
    public boolean onVoidFall(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        BotFight fight = PvpBotService.fightOf(session);
        if (fight == null) {
            return false;
        }
        plugin.pvpBot().playerDied(player, session, fight);
        return true;
    }

    // ------------------------------------------------------------------ kit

    /**
     * The kit is the player's chosen preset — the arena's own kit is ignored
     * — rearranged into the layout they last kept it in. Armor (slots 36-39)
     * always stays on the body; the layout memory covers only slots 0-35 and
     * is kept per preset, so a NoDebuff hotbar never scrambles a BuildUHC one.
     */
    @Override
    public Map<Integer, ItemStack> arrangeKit(PracticeCorePlugin plugin, Player player,
                                              me.beekrbonkr.practicecore.template.ArenaTemplate template) {
        BotSettings settings = BotSettings.load(plugin, player.getUniqueId());
        if (settings.kit() == null) {
            return Map.of(); // pvpbot.yml defines no kits — nothing to hand out
        }
        Map<Integer, ItemStack> kit = settings.kit().kit();
        Map<Integer, String> saved = plugin.stats().kitLayout(player.getUniqueId(),
                PvpBotService.layoutKey(settings.kit()));
        if (saved.isEmpty()) {
            return kit;
        }
        Map<Integer, ItemStack> arranged = new java.util.HashMap<>();
        List<Map.Entry<Integer, ItemStack>> unplaced = new java.util.ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : new java.util.TreeMap<>(kit).entrySet()) {
            if (entry.getKey() >= 36) {
                arranged.put(entry.getKey(), entry.getValue()); // armor is fixed
            } else {
                unplaced.add(entry);
            }
        }
        // First pass: every item the player has given a home goes there.
        for (Map.Entry<Integer, String> pref : saved.entrySet()) {
            if (pref.getKey() >= 36) {
                continue;
            }
            unplaced.removeIf(item -> {
                if (!arranged.containsKey(pref.getKey())
                        && item.getValue().getType().name().equals(pref.getValue())) {
                    arranged.put(pref.getKey(), item.getValue());
                    return true;
                }
                return false;
            });
        }
        // Then everything else: its own slot if free, else the first free one.
        for (Map.Entry<Integer, ItemStack> item : unplaced) {
            int slot = item.getKey();
            if (arranged.containsKey(slot)) {
                slot = 0;
                while (arranged.containsKey(slot)) {
                    slot++;
                }
            }
            arranged.put(slot, item.getValue());
        }
        return arranged;
    }

    // ---------------------------------------------------------------- board

    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        BotFight fight = PvpBotService.fightOf(session);
        if (fight == null) {
            return null;
        }
        me.beekrbonkr.practicecore.pvpbot.BotPreset preset = fight.settings.matchingPreset();
        String difficultyKey = preset == null
                ? "label.difficulty.short.custom" : preset.messageKey("short");
        Component difficulty = plugin.messages().raw(difficultyKey).isEmpty()
                ? Component.text(preset == null ? "Custom" : preset.configuredName())
                : plugin.messages().component(difficultyKey);
        return plugin.messages().lore("board.pvpbot-lines",
                // The difficulty label carries its own color, so it goes in as
                // a rendered reference rather than as literal text.
                plugin.messages().ref("difficulty", difficulty),
                "arena", session.template().displayName(),
                "kit", plugin.botTuning().kits().displayName(fight.settings.kit()),
                "kills", String.valueOf(fight.kills),
                "deaths", String.valueOf(fight.deaths),
                "hits", String.valueOf(fight.hitsLanded),
                "taken", String.valueOf(fight.hitsTaken),
                "combo", String.valueOf(fight.combo),
                "best-combo", String.valueOf(fight.longestCombo),
                "accuracy", accuracy(fight),
                "kd", kdRatio(fight),
                "dodged", String.valueOf(Math.max(0, fight.botAttacks - fight.hitsTaken)));
    }

    /** Hits landed over swings thrown, as a percentage. */
    private static String accuracy(BotFight fight) {
        if (fight.playerSwings <= 0) {
            return "0%";
        }
        return Math.round(fight.hitsLanded * 100.0 / fight.playerSwings) + "%";
    }

    /** Kills over deaths, one decimal; a deathless session shows the kills. */
    private static String kdRatio(BotFight fight) {
        if (fight.deaths == 0) {
            return String.valueOf(fight.kills);
        }
        return String.format(Locale.ROOT, "%.1f", fight.kills / (double) fight.deaths);
    }
}
