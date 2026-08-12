package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bed breaking practice: a fixed-composition set of defense blocks stands
 * between the player and a bed; the timer starts on the first broken block and
 * stops on the bed. The blocks are reshuffled every run, but their composition
 * is fixed, so times are comparable between players and across runs.
 *
 * Two orientations:
 * <ul>
 *   <li>{@code VERTICAL} — a column above the bed inside a sealed shaft; the
 *       player spawns on top and digs straight down. One block per step.</li>
 *   <li>{@code HORIZONTAL} — a wall filling a sealed corridor in front of the
 *       bed; the player digs forward. Each step is a two-high column of the
 *       same material.</li>
 * </ul>
 *
 * The mode also measures <b>reaction time</b>: whenever finishing a step means
 * the next step needs a different tool, the clock runs from the moment the
 * step's last block breaks until the player switches to that tool. Steps of
 * the same material (or sharing a best tool) never count. The run's average is
 * persisted per arena as its own stat.
 *
 * The player's tool arrangement is remembered: however they order their
 * hotbar during a run is how the kit is handed to them next time.
 *
 * Template settings (arena.yml {@code settings.bedbreak}):
 * <pre>
 * bedbreak:
 *   orientation: VERTICAL    # or HORIZONTAL
 *   bed-x: 1                 # bed HEAD block, relative to the paste origin
 *   bed-y: 1
 *   bed-z: 1
 *   bed-facing: NORTH        # the direction the foot-to-head axis points;
 *                            # HORIZONTAL blocks extend this way from the head
 *   bed-material: RED_BED
 *   blocks:                  # one entry per step — order here is irrelevant,
 *     WHITE_WOOL: 8          # the sequence is shuffled every run
 *     OAK_PLANKS: 4
 *     END_STONE_BRICKS: 4
 *     OBSIDIAN: 1
 * </pre>
 */
public final class BedBreakMode implements Mode {

    public static final String ID = "bedbreak";

    public enum Orientation { VERTICAL, HORIZONTAL }

    private static final class State {
        Location bedHead;
        Location bedFoot;
        BlockFace facing = BlockFace.NORTH;
        Material bedMaterial = Material.RED_BED;
        Orientation orientation = Orientation.VERTICAL;
        /** One material per step; composition fixed, order shuffled per run. */
        List<Material> composition = List.of();
        /** This run's step materials, first-broken first. */
        List<Material> breakOrder = List.of();
        /** Blocks left standing per step. */
        int[] stepRemaining = new int[0];
        int broken;
        /** material → the kit tool that clears it fastest (null: none helps). */
        final Map<Material, Material> toolCache = new HashMap<>();
        /** Reaction stopwatch: waiting for this tool since that nano instant. */
        Material pendingTool;
        long pendingSince;
        final List<Long> reactionsMs = new ArrayList<>();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Bed Break";
    }

    @Override
    public boolean requiresTrigger() {
        return false; // the bed is the trigger
    }

    @Override
    public boolean usesStandardTimerStart() {
        return false; // the timer starts on the first broken block
    }

    private State state(PracticeSession session) {
        if (session.modeState() instanceof State state) {
            return state;
        }
        State state = new State();
        session.setModeState(state);
        return state;
    }

    private static int blocksPerStep(State state) {
        return state.orientation == Orientation.HORIZONTAL ? 2 : 1;
    }

    // -------------------------------------------------------------- rounds

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        State state = state(session);
        state.broken = 0;
        state.pendingTool = null;
        state.reactionsMs.clear();
        parse(plugin, session, state);
        if (state.bedHead == null || state.composition.isEmpty()) {
            plugin.getLogger().warning("Bedbreak arena '" + session.template().name()
                    + "' is missing settings.bedbreak bed position or blocks — runs can never finish.");
            return;
        }
        rebuild(state);
    }

    private void parse(PracticeCorePlugin plugin, PracticeSession session, State state) {
        ConfigurationSection cfg =
                session.template().settingsSection().getConfigurationSection("bedbreak");
        if (cfg == null) {
            state.bedHead = null;
            return;
        }
        Location origin = session.origin();
        state.bedHead = new Location(origin.getWorld(),
                origin.getBlockX() + cfg.getInt("bed-x"),
                origin.getBlockY() + cfg.getInt("bed-y"),
                origin.getBlockZ() + cfg.getInt("bed-z"));
        try {
            state.facing = BlockFace.valueOf(cfg.getString("bed-facing", "NORTH")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            state.facing = BlockFace.NORTH;
        }
        try {
            state.orientation = Orientation.valueOf(cfg.getString("orientation", "VERTICAL")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            state.orientation = Orientation.VERTICAL;
        }
        state.bedFoot = state.bedHead.getBlock()
                .getRelative(state.facing.getOppositeFace()).getLocation();
        Material bed = Material.matchMaterial(cfg.getString("bed-material", "RED_BED"));
        state.bedMaterial = bed != null && Tag.BEDS.isTagged(bed) ? bed : Material.RED_BED;

        List<Material> composition = new ArrayList<>();
        ConfigurationSection blocks = cfg.getConfigurationSection("blocks");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                int count = blocks.getInt(key, 0);
                if (material == null || !material.isBlock() || count <= 0) {
                    plugin.getLogger().warning("Bedbreak arena '" + session.template().name()
                            + "': ignoring settings.bedbreak.blocks entry '" + key + "'");
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    composition.add(material);
                }
            }
        }
        state.composition = composition;
    }

    /** Restores the bed and re-rolls the blocks in front of / above it. */
    private void rebuild(State state) {
        World world = state.bedHead.getWorld();
        String facing = state.facing.name().toLowerCase();
        world.getBlockAt(state.bedFoot).setBlockData(
                Bukkit.createBlockData(state.bedMaterial, "[part=foot,facing=" + facing + "]"), false);
        world.getBlockAt(state.bedHead).setBlockData(
                Bukkit.createBlockData(state.bedMaterial, "[part=head,facing=" + facing + "]"), false);

        // Shuffled outward from the bed; the player breaks them bed-wards,
        // so the break order is the reverse.
        List<Material> outward = new ArrayList<>(state.composition);
        Collections.shuffle(outward);
        int n = outward.size();
        for (int j = 0; j < n; j++) {
            for (Location loc : stepBlocks(state, j)) {
                world.getBlockAt(loc).setType(outward.get(j), false);
            }
        }
        List<Material> breakOrder = new ArrayList<>(outward);
        Collections.reverse(breakOrder);
        state.breakOrder = breakOrder;
        state.stepRemaining = new int[n];
        java.util.Arrays.fill(state.stepRemaining, blocksPerStep(state));
    }

    /** The block positions of the step at distance {@code j + 1} from the bed head. */
    private List<Location> stepBlocks(State state, int j) {
        int x = state.bedHead.getBlockX();
        int y = state.bedHead.getBlockY();
        int z = state.bedHead.getBlockZ();
        World world = state.bedHead.getWorld();
        if (state.orientation == Orientation.VERTICAL) {
            return List.of(new Location(world, x, y + 1 + j, z));
        }
        int bx = x + state.facing.getModX() * (j + 1);
        int bz = z + state.facing.getModZ() * (j + 1);
        return List.of(new Location(world, bx, y, bz), new Location(world, bx, y + 1, bz));
    }

    /**
     * The break-order step index of this block, or -1 when it is not one of
     * the defense blocks. Distance d from the bed head maps to step n - d.
     */
    private int stepIndexOf(State state, Location loc) {
        int n = state.breakOrder.size();
        if (n == 0) {
            return -1;
        }
        int dx = loc.getBlockX() - state.bedHead.getBlockX();
        int dy = loc.getBlockY() - state.bedHead.getBlockY();
        int dz = loc.getBlockZ() - state.bedHead.getBlockZ();
        if (state.orientation == Orientation.VERTICAL) {
            if (dx != 0 || dz != 0 || dy < 1 || dy > n) {
                return -1;
            }
            return n - dy;
        }
        if (dy != 0 && dy != 1) {
            return -1;
        }
        int d = dx * state.facing.getModX() + dz * state.facing.getModZ();
        if (d < 1 || d > n
                || dx != state.facing.getModX() * d || dz != state.facing.getModZ() * d) {
            return -1;
        }
        return n - d;
    }

    // ------------------------------------------------------------- breaking

    @Override
    public boolean canBreak(PracticeSession session, Block block) {
        State state = state(session);
        if (state.bedHead == null) {
            return false;
        }
        Location loc = block.getLocation();
        if (loc.equals(state.bedHead) || loc.equals(state.bedFoot)) {
            return true;
        }
        return stepIndexOf(state, loc) >= 0;
    }

    @Override
    public void onBlockBreak(PracticeCorePlugin plugin, Player player, PracticeSession session,
                             BlockBreakEvent event) {
        event.setDropItems(false); // "players will not pick up the blocks they break"
        event.setExpToDrop(0);
        if (session.state() == SessionState.READY) {
            session.setState(SessionState.ACTIVE);
            session.startTimer();
        }
        State state = state(session);
        if (Tag.BEDS.isTagged(event.getBlock().getType())) {
            // Finish a tick later: the event's own block removal runs after
            // this handler, and it must not eat the bed the reset rebuilds.
            // The clock is pinned now, so the recorded time is the break.
            session.freezeTimer();
            long avgReaction = averageReaction(state);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                    if (avgReaction >= 0 && session.state() == SessionState.ACTIVE) {
                        plugin.stats().recordReaction(player.getUniqueId(),
                                session.template().name(), avgReaction);
                    }
                    plugin.sessions().finish(player, session);
                }
            });
            return;
        }
        int step = stepIndexOf(state, event.getBlock().getLocation());
        state.broken++;
        if (step < 0 || step >= state.stepRemaining.length) {
            return;
        }
        if (--state.stepRemaining[step] == 0 && step + 1 < state.breakOrder.size()) {
            armReaction(player, session, state, step);
        }
    }

    // -------------------------------------------------------- reaction time

    /**
     * A step just fully broke; if the next one is a different material needing
     * a different tool, start timing until the player switches to it. Already
     * holding it counts as a zero — pre-switching is the skill being measured.
     *
     * Any stopwatch still armed from an earlier step is abandoned first: a
     * measurement window never outlives the step transition it belongs to, so
     * a switch the player simply skipped can't surface later as one giant
     * sample.
     */
    private void armReaction(Player player, PracticeSession session, State state, int step) {
        state.pendingTool = null;
        Material current = state.breakOrder.get(step);
        Material next = state.breakOrder.get(step + 1);
        if (current == next) {
            return; // consecutive same-material steps never count
        }
        Material tool = bestToolFor(session.template(), state, next);
        if (tool == null || tool == bestToolFor(session.template(), state, current)) {
            return; // no switch needed — nothing to measure
        }
        if (player.getInventory().getItemInMainHand().getType() == tool) {
            state.reactionsMs.add(0L);
            return;
        }
        state.pendingTool = tool;
        state.pendingSince = System.nanoTime();
    }

    @Override
    public void onHeldItemChange(PracticeCorePlugin plugin, Player player,
                                 PracticeSession session, ItemStack held) {
        State state = state(session);
        if (state.pendingTool == null || held == null || held.getType() != state.pendingTool) {
            return;
        }
        state.reactionsMs.add((System.nanoTime() - state.pendingSince) / 1_000_000L);
        state.pendingTool = null;
    }

    /** The kit item that clears this material fastest, or null when none helps. */
    private Material bestToolFor(ArenaTemplate template, State state, Material material) {
        return state.toolCache.computeIfAbsent(material, mat -> {
            var data = mat.createBlockData();
            Material best = null;
            float bestSpeed = 1.0f; // bare-hand baseline: a "tool" must beat it
            for (ItemStack item : template.kit().values()) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                float speed = data.getDestroySpeed(item, true);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = item.getType();
                }
            }
            return best;
        });
    }

    /** Average reaction of the run in ms, or -1 with no samples. */
    private static long averageReaction(State state) {
        if (state.reactionsMs.isEmpty()) {
            return -1;
        }
        long sum = 0;
        for (long ms : state.reactionsMs) {
            sum += ms;
        }
        return sum / state.reactionsMs.size();
    }

    // ----------------------------------------------------------- kit layout

    @Override
    public Map<Integer, ItemStack> arrangeKit(PracticeCorePlugin plugin, Player player,
                                              ArenaTemplate template) {
        Map<Integer, String> saved =
                plugin.stats().kitLayout(player.getUniqueId(), template.name());
        if (saved.isEmpty()) {
            return template.kit();
        }
        Map<Integer, ItemStack> arranged = new HashMap<>();
        List<Map.Entry<Integer, ItemStack>> unplaced =
                new ArrayList<>(new TreeMap<>(template.kit()).entrySet());
        // First pass: every kit item the player has given a home goes there.
        for (Map.Entry<Integer, String> pref : saved.entrySet()) {
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

    /** Wherever the kit's items sit right now is how the player wants them. */
    private void captureLayout(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var kitTypes = session.template().kit().values().stream()
                .map(ItemStack::getType).collect(java.util.stream.Collectors.toSet());
        Map<Integer, String> layout = new LinkedHashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < Math.min(contents.length, 36); slot++) {
            ItemStack item = contents[slot];
            if (item != null && kitTypes.contains(item.getType())) {
                layout.put(slot, item.getType().name());
            }
        }
        if (!layout.isEmpty()) {
            plugin.stats().saveKitLayout(player.getUniqueId(), session.template().name(), layout);
        }
    }

    @Override
    public void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        captureLayout(plugin, player, session);
    }

    @Override
    public void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        captureLayout(plugin, player, session);
    }

    // --------------------------------------------------------------- board

    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        State state = state(session);
        Messages msg = plugin.messages();
        int rank = plugin.leaderboards().rank(session.template().name(), session.playerId());
        Component timer = session.state() == SessionState.ACTIVE
                ? msg.component("board.timer-running", "time", TimeFormat.tenths(session.elapsedMs()))
                : msg.component("board.timer-ready");
        long runReaction = averageReaction(state);
        long reaction = runReaction >= 0 ? runReaction
                : plugin.stats().reactionLastMs(session.playerId(), session.template().name());
        String none = msg.raw("gui.none");
        return msg.lore("board.bedbreak-lines",
                TagResolver.resolver(msg.ref("time", timer)),
                "arena", session.template().displayName(),
                "broken", String.valueOf(state.broken),
                "total", String.valueOf(state.breakOrder.size() * blocksPerStep(state)),
                "reaction", reaction >= 0 ? reaction + "ms" : none,
                "last", value(session.lastTimeMs(), none),
                "best", value(session.bestTimeMs(), none),
                "rank", rank > 0 ? "#" + rank : none);
    }

    private static String value(long millis, String none) {
        return millis >= 0 ? TimeFormat.tenths(millis) : none;
    }
}
