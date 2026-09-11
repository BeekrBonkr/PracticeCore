package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefense;
import me.beekrbonkr.practicecore.beddefense.BedDefenseSelection;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Phase;
import me.beekrbonkr.practicecore.beddefense.BlockKinds;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bed defense practice: a team base on a bed defense map, the player's own
 * bed, and a saved bed defense to build against the clock. The maps are
 * admin-made templates of this mode — built in the setup wizard or pulled
 * out of MBedwars — carrying the same team/bed/generator/dealer layout a
 * rush map does; the defenses are player-designed, stored server-wide and
 * shared through a gallery.
 *
 * <p>A round is complete when every block of the defense stands with the
 * right kind of material at the right spot, in any order (guided building is a
 * separate variant with its own boards). Times are keyed per defense, not
 * per map. Every finished round keeps a personal best, but only competitive
 * ones — the match-opening loadout with blocks bought from the shop — and
 * obsidian ones — the defense standing, tools and eight obsidian to break
 * in, seal the bed and build back — are ranked against other players.
 * Bed repair ({@link me.beekrbonkr.practicecore.beddefense.BedRepair}) has
 * no finish at all: the defense stands, the sky keeps wrecking it, and the
 * run is scored in volleys survived until the bed stays exposed too long.
 *
 * <p>Beyond building there are three side phases, all in
 * {@link BedDefenseService}: a block-by-block <b>preview</b>, <b>guided</b>
 * building with the next block pointed out, and the <b>editor</b> where
 * defenses are designed and saved.
 */
public final class BedDefenseMode implements Mode {

    public static final String ID = "beddefense";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Bed Defense";
    }

    @Override
    public boolean requiresTrigger() {
        return false; // the finished defense is the trigger
    }

    @Override
    public boolean validatesInventory() {
        return false; // the shop and generators are open-ended
    }

    @Override
    public boolean allowsBuckets() {
        return true; // water is a bed defense block
    }

    public static BedDefenseState state(PracticeSession session) {
        return session.modeState() instanceof BedDefenseState state ? state : null;
    }

    // ------------------------------------------------------------------ timer

    /** Bed repair starts its own clock with the first volley; nothing else does. */
    @Override
    public boolean startsTimerOnMove(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        return state != null && state.phase() == Phase.PLAY && !state.repair()
                && state.selection().timerStart() == BedDefenseSelection.TimerStart.MOVE;
    }

    @Override
    public boolean startsTimerOnFirstBlock(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        return state != null && state.phase() == Phase.PLAY && !state.repair()
                && state.selection().timerStart() == BedDefenseSelection.TimerStart.FIRST_BLOCK;
    }

    @Override
    public boolean startsTimerOnMove(me.beekrbonkr.practicecore.PCConfig config) {
        return true;
    }

    @Override
    public boolean startsTimerOnFirstBlock(me.beekrbonkr.practicecore.PCConfig config) {
        return false;
    }

    /** Every finished round is timed; preview and editor detours are not. */
    @Override
    public boolean recordsRun(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        return state != null && state.phase() == Phase.PLAY;
    }

    @Override
    public boolean pbEligible(PracticeCorePlugin plugin, PracticeSession session) {
        return recordsRun(plugin, session);
    }

    /**
     * Competitive and obsidian rounds are ranked. Practice still keeps a
     * personal best, on its own key, for the player to measure themselves
     * against; it is never submitted to a leaderboard or broadcast.
     */
    @Override
    public boolean ranked(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        return state != null && state.selection().ranked();
    }

    // -------------------------------------------------------------- join-time

    @Override
    public String validateJoin(PracticeCorePlugin plugin, Player player, ArenaTemplate template) {
        if (!plugin.bedDefenses().supports(template)) {
            return "beddefense.not-a-map";
        }
        return null;
    }

    /** Boards are per defense, never per map — a map tile has no time board of its own. */
    @Override
    public boolean hasLeaderboards() {
        return false;
    }

    @Override
    public Location spawnLocation(PracticeCorePlugin plugin, Player player,
                                  ArenaTemplate template, Location origin) {
        RushMapData data = RushMapData.parse(template);
        String team = plugin.stats().pref(player.getUniqueId(),
                "rush.team." + template.name(), null);
        RushMapData.TeamBase base = data.team(team);
        if ((base == null || !base.playable()) && !data.playableTeams().isEmpty()) {
            base = data.playableTeams().get(0);
        }
        if (base == null) {
            return template.spawnLocation(origin);
        }
        return new Location(origin.getWorld(),
                origin.getX() + base.spawn().getX(),
                origin.getY() + base.spawn().getY(),
                origin.getZ() + base.spawn().getZ(),
                base.yaw(), base.pitch());
    }

    /**
     * Boards are kept per defense, not per map: {@code beddefense#<id>} for
     * competitive, {@code beddefense#<id>#obsidian} for obsidian practice,
     * {@code beddefense#<id>#repair} for bed repair's rounds survived,
     * {@code beddefense#<id>#practice} for the practice bests only the
     * player sees. Before a round has a defense (join preloads) the chosen
     * one stands in.
     */
    @Override
    public String statsKey(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        BedDefense defense = state != null && state.defense() != null
                ? state.defense() : plugin.bedDefenses().roundDefense(session.playerId());
        String id = defense == null ? "none" : defense.id();
        BedDefenseSelection selection = state != null ? state.selection()
                : plugin.bedDefenses().selection(session.playerId());
        if (selection.repair()) {
            return BedDefenseService.repairStatsKey(id);
        }
        if (selection.obsidian()) {
            return BedDefenseService.obsidianStatsKey(id);
        }
        return selection.competitive() ? BedDefenseService.statsKey(id)
                : BedDefenseService.practiceStatsKey(id);
    }

    /** Nothing per arena: a defense's boards are purged when the defense is deleted. */
    @Override
    public List<String> statsKeys(ArenaTemplate template) {
        return List.of();
    }

    @Override
    public String runDisplayName(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state == null || state.defense() == null) {
            return session.template().displayName();
        }
        return plugin.bedDefenses().displayForKey(statsKey(plugin, session), state.defense());
    }

    // ----------------------------------------------------------------- rounds

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state == null) {
            state = new BedDefenseState();
            session.setModeState(state);
        }
        plugin.bedDefenses().rebuild(player, session, state);
    }

    @Override
    public void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state == null) {
            return;
        }
        plugin.bedDefenses().removeEntities(state);
        if (state.repair()) {
            // Any reset ends the run — a restart from the menu included —
            // and the rounds survived so far are its score.
            plugin.bedDefenses().repair().record(player, session, state);
            plugin.bedDefenses().repair().clearVolley(state);
        }
        if (state.phase() == Phase.EDIT) {
            plugin.bedDefenses().snapshotEdit(state);
        } else {
            plugin.bedDefenses().captureLayout(player, state);
        }
        if (state.phase() == Phase.PREVIEW) {
            plugin.bedDefenses().exitPreview(player, session, state, false);
        }
        // The next round draws afresh (shuffle) — the kit dealt next needs it.
        plugin.bedDefenses().clearRound(session.playerId());
    }

    @Override
    public void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state != null) {
            plugin.bedDefenses().cleanup(player, session, state);
        }
    }

    /**
     * A preview flies; a fall while flying is a teleport home, not a failed
     * run. In bed repair a blast can throw the player off the island, and
     * the run does not end for that either — they land back at the spawn
     * with the clock still running.
     */
    @Override
    public boolean onVoidFall(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state == null) {
            return false;
        }
        if (state.phase() == Phase.PLAY && state.repair()) {
            plugin.sessions().teleportInternal(player, session.spawn());
            player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            return true;
        }
        if (state.phase() != Phase.PREVIEW) {
            return false;
        }
        plugin.sessions().teleportInternal(player, session.spawn());
        player.setFlying(true);
        return true;
    }

    /** Bed repair's TNT and fireballs take out the standing defense and whatever the player added — never the bed or the map. */
    @Override
    public boolean explosionCanBreak(PracticeSession session, Location loc) {
        BedDefenseState state = state(session);
        if (state == null || !state.repair() || state.phase() != Phase.PLAY) {
            return false;
        }
        if (state.frame() != null && state.frame().isBed(loc)) {
            return false;
        }
        return session.tracker().isTracked(loc);
    }

    // ---------------------------------------------------------------- blocks

    @Override
    public boolean canBreak(PracticeSession session, Block block) {
        BedDefenseState state = state(session);
        if (state == null) {
            return false;
        }
        Location loc = block.getLocation();
        if (state.frame() != null && state.frame().isBed(loc)) {
            return false; // your own bed — never
        }
        if (state.phase() == Phase.EDIT) {
            return session.tracker().isTracked(loc) || state.isEditBlock(loc);
        }
        if (state.phase() == Phase.PREVIEW) {
            return false;
        }
        return session.tracker().isTracked(loc);
    }

    @Override
    public void onBlockBreak(PracticeCorePlugin plugin, Player player, PracticeSession session,
                             BlockBreakEvent event) {
        BedDefenseState state = state(session);
        if (state == null) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (state.phase() == Phase.EDIT) {
            plugin.bedDefenses().editBroken(state, event.getBlock().getLocation());
            return;
        }
        // Item entities never spawn in the practice world, so a misplaced
        // block would be gone for good — and a practice kit has exactly as
        // many as the defense needs. Hand it straight back instead.
        Material type = event.getBlock().getType();
        if (type.isItem() && !type.isAir()) {
            player.getInventory().addItem(new ItemStack(type));
        }
        plugin.bedDefenses().afterBreak(player, session, state);
    }

    // ------------------------------------------------------------------- kit

    @Override
    public Map<Integer, ItemStack> arrangeKit(PracticeCorePlugin plugin, Player player,
                                              ArenaTemplate template) {
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        BedDefenseState state = session != null && session.mode() == this ? state(session) : null;
        return plugin.bedDefenses().kit(player, template, state);
    }

    // ----------------------------------------------------------------- board

    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        BedDefenseState state = state(session);
        if (state == null || state.base() == null) {
            return null;
        }
        Messages msg = plugin.messages();
        String none = msg.none();
        Component timer = session.state() == SessionState.ACTIVE
                ? msg.component("board.timer-running", "time", TimeFormat.tenths(session.elapsedMs()))
                : msg.component("board.timer-ready");
        String modeKey = switch (state.phase()) {
            case PLAY -> state.repair() ? "board.beddefense.mode-repair"
                    : state.obsidian() ? "board.beddefense.mode-obsidian"
                    : state.selection().competitive()
                    ? "board.beddefense.mode-competitive" : "board.beddefense.mode-practice";
            case PREVIEW -> "board.beddefense.mode-preview";
            case GUIDED -> "board.beddefense.mode-guided";
            case EDIT -> "board.beddefense.mode-edit";
        };
        String defenseName = state.phase() == Phase.EDIT
                ? (state.editName() == null ? msg.raw("board.beddefense.unnamed") : state.editName())
                : state.defense() != null ? state.defense().name() : none;
        List<Component> lines = new ArrayList<>(msg.lore("board.beddefense.lines",
                TagResolver.resolver(msg.ref("time", timer), msg.ref("mode", modeKey)),
                "arena", session.template().displayName(),
                "team", RushMode.prettyTeam(state.base().name()),
                "defense", defenseName));
        switch (state.phase()) {
            case PLAY, GUIDED -> {
                int total = state.targets().size();
                if (state.repair() && state.phase() == Phase.PLAY) {
                    // Rounds, not times: the run's score so far, the best
                    // score, the defense's state and what the sky is up to.
                    long best = plugin.stats().bestMs(session.playerId(), statsKey(plugin, session));
                    lines.add(msg.component("board.beddefense.repair-rounds-line",
                            "rounds", String.valueOf(state.rounds())));
                    lines.add(msg.component("board.beddefense.repair-best-line",
                            "best", best >= 0 ? String.valueOf(best) : none));
                    lines.add(msg.component("board.beddefense.progress-line",
                            "placed", String.valueOf(state.satisfied()),
                            "total", String.valueOf(total)));
                    lines.add(plugin.bedDefenses().repair().statusLine(state));
                    break;
                }
                if (state.obsidian()) {
                    lines.add(msg.component("board.beddefense.obsidian-line",
                            "placed", String.valueOf(state.obsidianSatisfied()),
                            "total", String.valueOf(state.obsidianTargets().size())));
                }
                lines.add(msg.component("board.beddefense.progress-line",
                        "placed", String.valueOf(state.satisfied()),
                        "total", String.valueOf(total)));
                if (state.phase() == Phase.GUIDED) {
                    var next = state.nextTarget();
                    lines.add(msg.component("board.beddefense.next-line",
                            "material", next == null ? none : BlockKinds.pretty(next.block().kind())));
                } else {
                    // Both modes keep a best now; the practice one is on its
                    // own key and goes no further than the player.
                    long best = plugin.stats().bestMs(session.playerId(),
                            statsKey(plugin, session));
                    lines.add(msg.component("board.beddefense.best-line",
                            "best", best >= 0 ? TimeFormat.tenths(best) : none));
                    if (!state.selection().ranked()) {
                        lines.add(msg.component("board.beddefense.casual-line"));
                    }
                }
            }
            case PREVIEW -> lines.add(msg.component("board.beddefense.preview-line",
                    "step", String.valueOf(state.previewIndex()),
                    "total", String.valueOf(state.previewTargets().size())));
            case EDIT -> lines.add(msg.component("board.beddefense.edit-line",
                    "blocks", String.valueOf(state.editSequence().size()),
                    "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius())));
        }
        return lines;
    }

    @Override
    public Material menuIcon(PracticeCorePlugin plugin, ArenaTemplate template) {
        return plugin.modes().get(RushMode.ID).map(mode -> mode.menuIcon(plugin, template))
                .orElse(template.effectiveIcon());
    }
}
