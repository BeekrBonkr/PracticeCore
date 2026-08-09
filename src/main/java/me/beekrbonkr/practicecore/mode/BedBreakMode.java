package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Bed breaking practice: a tower of defense blocks stands on the bed, the
 * player spawns on top of it inside a barrier shaft, and digs down to the bed
 * as fast as possible. The tower is reshuffled every run, but its composition
 * is fixed, so times are comparable between players and across runs.
 *
 * The player's tool arrangement is remembered: however they order their
 * hotbar during a run is how the kit is handed to them next time.
 *
 * Template settings (arena.yml {@code settings.bedbreak}):
 * <pre>
 * bedbreak:
 *   bed-x: 1                 # bed HEAD block, relative to the paste origin
 *   bed-y: 1
 *   bed-z: 1
 *   bed-facing: NORTH        # the direction the foot-to-head axis points
 *   bed-material: RED_BED
 *   blocks:                  # the tower, bottom of this list included as much
 *     WHITE_WOOL: 8          # as every other entry — order here is irrelevant,
 *     OAK_PLANKS: 4          # the column is shuffled every run
 *     END_STONE_BRICKS: 4
 *     OBSIDIAN: 1
 * </pre>
 * The tower rises from one block above the bed head; its height is the sum of
 * the configured counts.
 */
public final class BedBreakMode implements Mode {

    public static final String ID = "bedbreak";

    private static final class State {
        Location bedHead;
        Location bedFoot;
        BlockFace facing = BlockFace.NORTH;
        Material bedMaterial = Material.RED_BED;
        List<Material> composition = List.of();
        int broken;
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

    // -------------------------------------------------------------- rounds

    @Override
    public void onReady(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        State state = state(session);
        state.broken = 0;
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
            state.facing = BlockFace.valueOf(cfg.getString("bed-facing", "NORTH").toUpperCase());
        } catch (IllegalArgumentException e) {
            state.facing = BlockFace.NORTH;
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

    /** Restores the bed and re-rolls the tower above it. */
    private void rebuild(State state) {
        World world = state.bedHead.getWorld();
        String facing = state.facing.name().toLowerCase();
        world.getBlockAt(state.bedFoot).setBlockData(
                Bukkit.createBlockData(state.bedMaterial, "[part=foot,facing=" + facing + "]"), false);
        world.getBlockAt(state.bedHead).setBlockData(
                Bukkit.createBlockData(state.bedMaterial, "[part=head,facing=" + facing + "]"), false);

        List<Material> column = new ArrayList<>(state.composition);
        Collections.shuffle(column);
        int x = state.bedHead.getBlockX();
        int y = state.bedHead.getBlockY();
        int z = state.bedHead.getBlockZ();
        for (int i = 0; i < column.size(); i++) {
            world.getBlockAt(x, y + 1 + i, z).setType(column.get(i), false);
        }
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
        return loc.getBlockX() == state.bedHead.getBlockX()
                && loc.getBlockZ() == state.bedHead.getBlockZ()
                && loc.getBlockY() > state.bedHead.getBlockY()
                && loc.getBlockY() <= state.bedHead.getBlockY() + state.composition.size();
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
        if (Tag.BEDS.isTagged(event.getBlock().getType())) {
            // Finish a tick later: the event's own block removal runs after
            // this handler, and it must not eat the bed the reset rebuilds.
            // The clock is pinned now, so the recorded time is the break.
            session.freezeTimer();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                    plugin.sessions().finish(player, session);
                }
            });
            return;
        }
        state(session).broken++;
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
        int rank = plugin.leaderboards().rank(session.template().name(), session.playerId());
        Component timer = session.state() == SessionState.ACTIVE
                ? Component.text(TimeFormat.tenths(session.elapsedMs()), NamedTextColor.YELLOW)
                : Component.text("ready", NamedTextColor.DARK_GRAY);
        return List.of(
                Component.empty(),
                Component.text("Arena: ", NamedTextColor.GRAY)
                        .append(Component.text(session.template().displayName(), NamedTextColor.WHITE)),
                Component.empty(),
                Component.text("Time: ", NamedTextColor.GRAY).append(timer),
                Component.text("Broken: ", NamedTextColor.GRAY)
                        .append(Component.text(state.broken + "/" + state.composition.size(),
                                NamedTextColor.WHITE)),
                Component.text("Last: ", NamedTextColor.GRAY).append(value(session.lastTimeMs())),
                Component.text("Best: ", NamedTextColor.GRAY).append(value(session.bestTimeMs())),
                Component.text("Rank: ", NamedTextColor.GRAY)
                        .append(rank > 0
                                ? Component.text("#" + rank, NamedTextColor.WHITE)
                                : Component.text("—", NamedTextColor.DARK_GRAY)));
    }

    private static Component value(long millis) {
        return millis >= 0
                ? Component.text(TimeFormat.tenths(millis), NamedTextColor.WHITE)
                : Component.text("—", NamedTextColor.DARK_GRAY);
    }
}
