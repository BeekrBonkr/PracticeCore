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
 * hand) where the player spawns at a team base of their choice and races one
 * objective — break an enemy bed, or grab an emerald or diamond off its
 * generator. Times are kept per map+objective, so every pairing has its own
 * personal bests and leaderboard.
 *
 * The map layout comes from arena.yml {@code settings.rush} (see
 * {@link RushMapData}); the player's team, objective and difficulty modifiers
 * come from the rush config menu and are persisted as prefs. Enemy beds and
 * their optional generated defenses are re-placed on every reset; dealer NPCs
 * and generator drops are entities, wiped by the arena reset and respawned in
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

    // ------------------------------------------------------------ join-time

    @Override
    public String validateJoin(PracticeCorePlugin plugin, Player player, ArenaTemplate template) {
        RushMapData data = RushMapData.parse(template);
        if (!data.playable() || plugin.rush().supportedObjectives(data).isEmpty()) {
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

    @Override
    public String statsKey(PracticeCorePlugin plugin, PracticeSession session) {
        return objectiveOf(plugin, session).statsKey(session.template().name());
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
        return plugin.rush().displayFor(session.template(), objectiveOf(plugin, session));
    }

    private RushObjective objectiveOf(PracticeCorePlugin plugin, PracticeSession session) {
        if (session.modeState() instanceof RushState state && state.selection() != null) {
            return state.selection().objective();
        }
        ArenaTemplate template = session.template();
        return plugin.rush().selection(session.playerId(), template,
                RushMapData.parse(template)).objective();
    }

    // --------------------------------------------------------------- rounds

    public static RushState state(PracticeSession session) {
        return session.modeState() instanceof RushState state ? state : null;
    }

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        RushState state = new RushState();
        session.setModeState(state);
        state.rebuild(plugin, session);
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
            return;
        }
        if (!state.isEnemyBedBlock(loc)) {
            return; // a block the player placed — nothing special
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (state.selection().objective() != RushObjective.BED) {
            return; // beds are breakable regardless, but only the bed run ends on one
        }
        // Reaching the bed without tripping the timer start is possible under
        // FIRST_BLOCK with no placed blocks — count the run from here then.
        if (session.state() == SessionState.READY) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
        // Finish a tick later: the event's own block removal runs after this
        // handler and must not eat the bed the reset re-places. The clock is
        // pinned now, so the recorded time is the break.
        session.freezeTimer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                plugin.sessions().finish(player, session);
            }
        });
    }

    /** Objective item picked up (called by RushListener with a matching type). */
    public void completePickup(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        if (session.state() != SessionState.ACTIVE) {
            return;
        }
        session.freezeTimer();
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

    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        RushState state = state(session);
        if (state == null) {
            return null;
        }
        Messages msg = plugin.messages();
        String none = msg.raw("gui.none");
        String statsKey = statsKey(plugin, session);
        int rank = plugin.leaderboards().rank(statsKey, session.playerId());
        Component timer = session.state() == SessionState.ACTIVE
                ? msg.component("board.timer-running", "time", TimeFormat.tenths(session.elapsedMs()))
                : msg.component("board.timer-ready");
        return msg.lore("board.rush-lines",
                TagResolver.resolver(msg.ref("time", timer)),
                "arena", session.template().displayName(),
                "objective", plugin.rush().objectiveName(state.selection().objective()),
                "team", prettyTeam(state.base().name()),
                "last", value(session.lastTimeMs(), none),
                "best", value(session.bestTimeMs(), none),
                "rank", rank > 0 ? "#" + rank : none);
    }

    public static String prettyTeam(String name) {
        String lower = name.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static String value(long millis, String none) {
        return millis >= 0 ? TimeFormat.tenths(millis) : none;
    }
}
