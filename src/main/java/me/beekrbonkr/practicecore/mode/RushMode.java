package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.rush.RushObjective;
import me.beekrbonkr.practicecore.rush.RushSelection;
import me.beekrbonkr.practicecore.rush.RushState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rush practice: an empty bedwars map (imported from MBedwars or built by
 * hand) where the player spawns at a team base of their choice and races to
 * an objective — break an enemy bed, or grab an emerald or diamond off its
 * generator. Every objective the map supports is armed on every run;
 * whichever the player completes first ends it, and the time lands on that
 * objective's own board (kept per map+objective, so every pairing has its own
 * personal bests and leaderboard).
 *
 * The timer always starts on the player's first movement off the spawn,
 * regardless of the configured start mode. Runs that used any starting items
 * (starter blocks, starting resources, a pickaxe) still record times but can
 * never set personal bests or rank on a leaderboard.
 *
 * The map layout comes from arena.yml {@code settings.rush} (see
 * {@link RushMapData}); the player's team and difficulty modifiers come from
 * the rush config menu and are persisted as prefs. Enemy beds and their
 * optional generated defenses are re-placed on every reset; dealer NPCs and
 * generator drops are entities, wiped by the arena reset and respawned in
 * {@link #onReady}.
 */
public final class RushMode implements Mode {

    public static final String ID = "rush";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Rush";
    }

    @Override
    public boolean requiresTrigger() {
        return false; // the objective is the trigger
    }

    @Override
    public boolean validatesInventory() {
        return false; // shop purchases and generator pickups are open-ended
    }

    @Override
    public boolean startsTimerOnMove(me.beekrbonkr.practicecore.PCConfig config) {
        return true; // rush always times from the first movement off the spawn
    }

    @Override
    public boolean startsTimerOnFirstBlock(me.beekrbonkr.practicecore.PCConfig config) {
        return false;
    }

    /** Only competitive runs are written to stats and ranked. */
    @Override
    public boolean recordsRun(PracticeCorePlugin plugin, PracticeSession session) {
        return isCompetitive(session);
    }

    @Override
    public boolean pbEligible(PracticeCorePlugin plugin, PracticeSession session) {
        return isCompetitive(session);
    }

    private static boolean isCompetitive(PracticeSession session) {
        RushState state = state(session);
        return state != null && state.selection() != null && state.selection().competitive();
    }

    // ------------------------------------------------------------ join-time

    @Override
    public String validateJoin(PracticeCorePlugin plugin, Player player, ArenaTemplate template) {
        RushMapData data = RushMapData.parse(template);
        RushSelection selection = plugin.rush().selection(player.getUniqueId(), template, data);
        if (!data.playable()
                || plugin.rush().supportedObjectives(data, selection).isEmpty()) {
            return "rush.not-configured";
        }
        return null;
    }

    @Override
    public Location spawnLocation(PracticeCorePlugin plugin, Player player,
                                  ArenaTemplate template, Location origin) {
        RushMapData data = RushMapData.parse(template);
        RushSelection selection = plugin.rush().selection(player.getUniqueId(), template, data);
        RushMapData.TeamBase base = data.team(selection.team());
        if (base == null || !base.playable()) {
            return template.spawnLocation(origin);
        }
        return new Location(origin.getWorld(),
                origin.getX() + base.spawn().getX(),
                origin.getY() + base.spawn().getY(),
                origin.getZ() + base.spawn().getZ(),
                base.yaw(), base.pitch());
    }

    /**
     * The board of whichever objective ended the run. Before one has (join
     * preloads, mid-run reads) it falls back to the bed board — those reads
     * only seed session fields the rush sidebar no longer displays.
     */
    @Override
    public String statsKey(PracticeCorePlugin plugin, PracticeSession session) {
        return objectiveOf(session).statsKey(session.template().name());
    }

    @Override
    public List<String> statsKeys(ArenaTemplate template) {
        List<String> keys = new ArrayList<>();
        for (RushObjective objective : RushObjective.values()) {
            keys.add(objective.statsKey(template.name()));
        }
        return keys;
    }

    @Override
    public String runDisplayName(PracticeCorePlugin plugin, PracticeSession session) {
        return plugin.rush().displayFor(session.template(), objectiveOf(session));
    }

    private static RushObjective objectiveOf(PracticeSession session) {
        RushState state = state(session);
        if (state != null && state.completed() != null) {
            return state.completed();
        }
        return state != null && state.combat() ? RushObjective.TEAM_WIPE : RushObjective.BED;
    }

    // --------------------------------------------------------------- rounds

    public static RushState state(PracticeSession session) {
        return session.modeState() instanceof RushState state ? state : null;
    }

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        // The previous round's defense shell must go before the new state
        // rebuilds — the selection may have changed between runs.
        if (session.modeState() instanceof RushState previous) {
            previous.removeDefenses(session.origin().getWorld());
            plugin.rushBots().cleanup(session);
        }
        // A death hold from the previous round must not blind the fresh one.
        if (player != null) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        }
        RushState state = new RushState();
        session.setModeState(state);
        state.rebuild(plugin, session);
    }

    @Override
    public void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        // The entity wipe removes the bodies; the disguise bookkeeping and
        // tags have to be released deliberately.
        plugin.rushBots().cleanup(session);
    }

    @Override
    public void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        plugin.rushBots().cleanup(session);
    }

    /**
     * Combat runs treat a fall as a bedwars death — back to base, kit reset —
     * because the player's own bed still stands. Race runs keep the standard
     * fail-and-reset.
     */
    @Override
    public boolean onVoidFall(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        RushState state = state(session);
        if (state == null || !state.combat()) {
            return false;
        }
        plugin.rushBots().killPlayer(player, session, state);
        return true;
    }

    /**
     * What an explosion may take out: blocks the player placed and generated
     * defense shells. Never the map, and — unlike a pickaxe — never a bed;
     * bedwars beds are explosion-proof and runs must end by hand.
     */
    public static boolean explosionCanBreak(PracticeSession session, Location loc) {
        if (!(session.mode() instanceof RushMode)) {
            return false;
        }
        if (session.tracker().isTracked(loc)) {
            return true;
        }
        RushState state = state(session);
        return state != null && state.isDefenseBlock(loc);
    }

    @Override
    public boolean canBreak(PracticeSession session, Block block) {
        Location loc = block.getLocation();
        if (session.tracker().isTracked(loc)) {
            return true;
        }
        RushState state = state(session);
        return state != null && (state.isEnemyBedBlock(loc) || state.isDefenseBlock(loc));
    }

    @Override
    public void onBlockBreak(PracticeCorePlugin plugin, Player player, PracticeSession session,
                             BlockBreakEvent event) {
        RushState state = state(session);
        if (state == null) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (state.isDefenseBlock(loc)) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (state.combat()) {
                // Chipping at a bed's shell wakes the defenders guarding it.
                plugin.rushBots().alertNear(state, loc, 12);
            }
            return;
        }
        if (!state.isEnemyBedBlock(loc)) {
            return; // a block the player placed — nothing special
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        // Reaching the bed without ever moving off the spawn block would leave
        // the timer unstarted — count the run from here then.
        if (session.state() == SessionState.READY) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
        if (state.combat()) {
            // Combat runs: the bed is a step, not the finish. Its team stops
            // respawning — the wipe becomes possible — and its defenders come
            // for the intruder.
            String team = state.markBedBroken(loc);
            if (team != null) {
                plugin.messages().send(player, "rush.bots.bed-destroyed",
                        "team", prettyTeam(team));
                plugin.sounds().play(player, "rush.bed-destroyed");
                plugin.rushBots().alertNear(state, loc, 24);
                plugin.rushBots().checkTeamWipe(player, session, state);
            }
            return;
        }
        finishAs(plugin, player, session, state, RushObjective.BED);
    }

    /** Combat runs end here: one enemy team fully out — bed gone, all defenders down. */
    public void completeTeamWipe(PracticeCorePlugin plugin, Player player,
                                 PracticeSession session) {
        RushState state = state(session);
        if (state == null) {
            return;
        }
        if (session.state() == SessionState.READY) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
        finishAs(plugin, player, session, state, RushObjective.TEAM_WIPE);
    }

    /** Objective item picked up (called by RushListener with a matching type). */
    public void completePickup(PracticeCorePlugin plugin, Player player,
                               PracticeSession session, RushObjective objective) {
        RushState state = state(session);
        if (state == null || session.state() != SessionState.ACTIVE) {
            return;
        }
        finishAs(plugin, player, session, state, objective);
    }

    /**
     * Ends the run on the objective that was actually completed. The finish
     * happens a tick later — a bed break's own block removal runs after the
     * event handler and must not eat the bed the reset re-places — but the
     * clock is pinned now, so the recorded time is the completion.
     */
    private void finishAs(PracticeCorePlugin plugin, Player player, PracticeSession session,
                          RushState state, RushObjective objective) {
        if (state.completed() != null) {
            // A second objective in the same tick (bed break + pickup) must
            // not steal the run — the first one ended it.
            return;
        }
        state.setCompleted(objective);
        session.freezeTimer();
        if (!recordsRun(plugin, session)) {
            plugin.messages().actionBar(player, "rush.records-disabled");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                plugin.sessions().finish(player, session);
            }
        });
    }

    // ------------------------------------------------------------------ kit

    @Override
    public Map<Integer, ItemStack> arrangeKit(PracticeCorePlugin plugin, Player player,
                                              ArenaTemplate template) {
        Map<Integer, ItemStack> kit = new LinkedHashMap<>(new TreeMap<>(template.kit()));
        RushSelection selection = plugin.rush().selection(player.getUniqueId(), template,
                RushMapData.parse(template));
        // Every spawn — and every combat respawn — carries at least the
        // starter sword in the first hotbar slot, like a real game. A kit
        // that already puts a sword there keeps its own; anything else in
        // that slot moves to the first free one.
        Material starterSword = plugin.pcConfig().rushStarterSword();
        if (starterSword != null) {
            ItemStack first = kit.get(0);
            if (first == null || !first.getType().name().endsWith("_SWORD")) {
                kit.remove(0);
                if (first != null) {
                    place(kit, first);
                }
                kit.put(0, new ItemStack(starterSword));
            }
        }
        if (selection.pickaxe().item() != null) {
            place(kit, new ItemStack(selection.pickaxe().item()));
        }
        if (selection.blocks().amount() > 0) {
            place(kit, new ItemStack(Material.WHITE_WOOL, selection.blocks().amount()));
        }
        if (selection.currency().iron() > 0) {
            place(kit, new ItemStack(Material.IRON_INGOT, selection.currency().iron()));
        }
        if (selection.currency().gold() > 0) {
            place(kit, new ItemStack(Material.GOLD_INGOT, selection.currency().gold()));
        }
        if (selection.tnt().amount() > 0) {
            place(kit, new ItemStack(Material.TNT, selection.tnt().amount()));
        }
        return kit;
    }

    private static void place(Map<Integer, ItemStack> kit, ItemStack item) {
        for (int slot = 0; slot < 36; slot++) {
            if (!kit.containsKey(slot)) {
                kit.put(slot, item);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- board

    /**
     * The rush sidebar is assembled per session rather than being one fixed
     * layout: the header shows the map, base, casual/competitive mode and
     * timer, then one best-time line per objective <em>this map actually
     * supports</em> — a map with no diamond generator never shows a diamond
     * line — and casual runs get an unranked notice.
     */
    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        RushState state = state(session);
        if (state == null || state.base() == null || state.selection() == null) {
            return null;
        }
        Messages msg = plugin.messages();
        String none = msg.raw("gui.none");
        String arena = session.template().name();
        boolean competitive = state.selection().competitive();
        Component timer = session.state() == SessionState.ACTIVE
                ? msg.component("board.timer-running", "time", TimeFormat.tenths(session.elapsedMs()))
                : msg.component("board.timer-ready");
        Component mode = msg.component(competitive
                ? "board.rush.mode-competitive" : "board.rush.mode-casual");
        List<Component> lines = new ArrayList<>(msg.lore("board.rush.lines",
                TagResolver.resolver(msg.ref("time", timer), msg.ref("mode", mode)),
                "arena", session.template().displayName(),
                "team", prettyTeam(state.base().name())));
        for (RushObjective objective : plugin.rush()
                .supportedObjectives(state.data(), state.selection())) {
            lines.add(msg.component("board.rush.objective-line",
                    "objective", plugin.rush().objectiveName(objective),
                    "best", value(plugin.stats().bestMs(session.playerId(),
                            objective.statsKey(arena)), none)));
        }
        if (state.combat()) {
            lines.add(msg.component("board.rush.combat-line",
                    "beds", String.valueOf(state.bedsStanding()),
                    "defenders", String.valueOf(plugin.rushBots().aliveDefenders(state)),
                    "deaths", String.valueOf(state.playerDeaths())));
        }
        if (!competitive) {
            lines.add(msg.component("board.rush.casual-line"));
        }
        return lines;
    }

    /**
     * Imported maps wear their MBedwars selector icon in the menus, cached in
     * {@link me.beekrbonkr.practicecore.rush.RushService} so the API is asked
     * once per map, not once per redraw. A red bed — the icon every import
     * used to stamp — counts as "no choice made" so existing imports pick up
     * their MBedwars face without a re-import; any other configured icon is
     * the admin's own and stands.
     */
    @Override
    public Material menuIcon(PracticeCorePlugin plugin, ArenaTemplate template) {
        Material configured = template.icon();
        if (configured != null && configured != Material.RED_BED) {
            return configured;
        }
        Material imported = plugin.rush().importedIcon(template);
        return imported != null ? imported : template.effectiveIcon();
    }

    public static String prettyTeam(String name) {
        String lower = name.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static String value(long millis, String none) {
        return millis >= 0 ? TimeFormat.tenths(millis) : none;
    }
}
