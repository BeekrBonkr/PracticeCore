package me.beekrbonkr.practicecore.mode;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MLG water bucket practice: the player spawns looking straight down at the
 * landing pad from a glass platform, jumps off whenever they like — the
 * platform breaks away behind them the moment they do — and has to place
 * water on the pad below before impact. Touching the water is an instant
 * success and resets the arena; hitting the pad without it fails the run.
 * Every reset re-rolls how far down the pad sits, so the fall's length can't
 * be learned by muscle memory.
 *
 * Scorekeeping is the <b>streak</b> — consecutive successful clutches, not
 * time. The current streak survives arena resets and even relogs (persisted
 * per arena in playerdata as {@code streak}, with the record under
 * {@code streak-best}); any failed or abandoned mid-air run resets it to zero.
 *
 * The platform and pad are mode-built: the shaft below the spawn is
 * mode-owned space, cleared and rebuilt every reset. The bucket may only be
 * emptied mid-fall, so a pre-placed pool can never count.
 *
 * Template settings (arena.yml {@code settings.mlg}, all optional):
 * <pre>
 * mlg:
 *   platform-radius: 1     # glass start platform half-size (1 → 3×3)
 *   pad-radius: 5          # landing pad half-size (5 → 11×11)
 *   pad-material: GRASS_BLOCK
 *   min-drop: 20           # platform-to-pad distance is rolled from…
 *   max-drop: 100          # …this range every reset
 * </pre>
 */
public final class MlgMode implements Mode {

    public static final String ID = "mlg";

    private static final class State {
        int platformRadius = 1;
        int padRadius = 5;
        Material padMaterial = Material.GRASS_BLOCK;
        int minDrop = 20;
        int maxDrop = 100;
        /** This round's rolled platform-to-pad distance, for the sidebar. */
        int currentDrop;

        /** Persisted streak, read once per session then kept live here. */
        int streak;
        boolean streakLoaded;
        /** Persisted best, read once then kept live — the board refreshes too often for a path walk. */
        int bestStreak = -1;
        /** The player went over the edge this run — abandoning now costs the streak. */
        boolean airborne;
        /** A splash was seen and the reset is scheduled — count nothing twice. */
        boolean finishing;

        final List<Location> platformBlocks = new ArrayList<>();
        final List<Location> padBlocks = new ArrayList<>();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "MLG";
    }

    @Override
    public boolean requiresTrigger() {
        return false; // the clutch water is the finish line
    }

    @Override
    public boolean usesStandardTimerStart() {
        return false; // there is no timer — the score is the streak
    }

    @Override
    public boolean allowsBuckets() {
        return true;
    }

    @Override
    public boolean hasLeaderboards() {
        return false; // streaks are per-player; there is no shared time board
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
        state.airborne = false;
        state.finishing = false;
        parse(session, state);
        if (!state.streakLoaded) {
            state.streak = plugin.stats().streak(session.playerId(), session.template().name());
            state.streakLoaded = true;
        }
        clearBuilt(state);
        buildRound(plugin, player, session, state);
        // Aim the camera at the pad — the whole round happens straight below.
        player.setRotation(player.getLocation().getYaw(), 90f);
    }

    private void parse(PracticeSession session, State state) {
        ConfigurationSection cfg = session.template().settingsSection();
        state.platformRadius = Math.max(0, cfg.getInt("mlg.platform-radius", 1));
        state.padRadius = Math.max(0, cfg.getInt("mlg.pad-radius", 5));
        Material pad = Material.matchMaterial(cfg.getString("mlg.pad-material", "GRASS_BLOCK"));
        state.padMaterial = pad != null && pad.isBlock() && pad.isSolid() ? pad : Material.GRASS_BLOCK;
        state.minDrop = Math.max(2, cfg.getInt("mlg.min-drop", 20));
        state.maxDrop = Math.max(state.minDrop, cfg.getInt("mlg.max-drop", 100));
    }

    /** Rebuilds the glass platform and rolls the pad depth. */
    private void buildRound(PracticeCorePlugin plugin, Player player,
                            PracticeSession session, State state) {
        Location spawn = session.spawn();
        World world = spawn.getWorld();
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();
        int platformY = spawn.getBlockY() - 1;

        // Air-only writes: the platform and pad must never replace (and later
        // clear to air) the template's own blocks — the generated arena's
        // barrier walls sit exactly at the default pad's outermost ring.
        for (int x = -state.platformRadius; x <= state.platformRadius; x++) {
            for (int z = -state.platformRadius; z <= state.platformRadius; z++) {
                Location loc = new Location(world, centerX + x, platformY, centerZ + z);
                if (session.containsBlock(loc) && world.getBlockAt(loc).getType().isAir()) {
                    world.getBlockAt(loc).setType(Material.GLASS, false);
                    state.platformBlocks.add(loc);
                }
            }
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int drop = rnd.nextInt(state.minDrop, state.maxDrop + 1);
        // Never at or below the arena floor — clearing the pad next reset
        // would otherwise punch a hole into it.
        int padY = Math.max((int) session.bounds().getMinY() + 1, platformY - drop);
        state.currentDrop = platformY - padY;
        for (int x = -state.padRadius; x <= state.padRadius; x++) {
            for (int z = -state.padRadius; z <= state.padRadius; z++) {
                Location loc = new Location(world, centerX + x, padY, centerZ + z);
                if (session.containsBlock(loc) && world.getBlockAt(loc).getType().isAir()) {
                    world.getBlockAt(loc).setType(state.padMaterial, false);
                    state.padBlocks.add(loc);
                }
            }
        }

    }

    /** The player jumped off — the platform breaks away behind them. */
    private void breakPlatform(PracticeCorePlugin plugin, Player player, State state) {
        for (Location loc : state.platformBlocks) {
            loc.getWorld().getBlockAt(loc).setType(Material.AIR, false);
        }
        state.platformBlocks.clear();
        if (player.isOnline() && plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f);
        }
    }

    /** Returns the mode-built blocks to air; the next round rebuilds fresh. */
    private void clearBuilt(State state) {
        for (List<Location> built : List.of(state.platformBlocks, state.padBlocks)) {
            for (Location loc : built) {
                loc.getWorld().getBlockAt(loc).setType(Material.AIR, false);
            }
            built.clear();
        }
    }

    // ------------------------------------------------------------ run logic

    /** True while the player may empty the bucket — i.e. they are mid-fall. */
    public boolean mayPlaceWater(PracticeSession session) {
        return session.state() == SessionState.ACTIVE;
    }

    /** Movement hook (from {@link me.beekrbonkr.practicecore.listener.MlgListener}). */
    public void handleMove(PracticeCorePlugin plugin, Player player,
                           PracticeSession session, Location to) {
        State state = state(session);
        if (session.state() == SessionState.READY) {
            // A block below the spawn can only mean the fall has begun — a
            // normal jump on the platform never dips under its own surface.
            // The platform breaks the instant the player commits, so there is
            // no landing back on it and the clutch is armed from the jump.
            if (session.spawn().getY() - to.getY() > 1.0) {
                session.setState(SessionState.ACTIVE);
                state.airborne = true;
                breakPlatform(plugin, player, state);
            }
            return;
        }
        if (session.state() != SessionState.ACTIVE || state.finishing
                || !isWater(to.getBlock())) {
            return;
        }
        // Touched the clutch water before any fall damage — an instant win.
        state.finishing = true;
        state.streak++;
        String arena = session.template().name();
        int previousBest = state.bestStreak >= 0
                ? state.bestStreak : plugin.stats().streakBest(session.playerId(), arena);
        plugin.stats().recordStreak(session.playerId(), arena, state.streak);
        state.bestStreak = Math.max(previousBest, state.streak);
        plugin.messages().send(player,
                state.streak > previousBest ? "mlg.success-best" : "mlg.success",
                "streak", String.valueOf(state.streak));
        if (plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        // Reset a tick later: teleporting out of a move event mid-unwind is
        // asking for trouble.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                plugin.sessions().completeUntimed(player, session);
            }
        });
    }

    /** The player landed hard (fall damage) — the clutch failed. */
    public void handleGroundImpact(PracticeCorePlugin plugin, Player player,
                                   PracticeSession session) {
        State state = state(session);
        if (session.state() != SessionState.ACTIVE || state.finishing) {
            return;
        }
        dropStreak(plugin, session, state);
        plugin.sessions().fail(player, session);
    }

    private void dropStreak(PracticeCorePlugin plugin, PracticeSession session, State state) {
        state.airborne = false;
        if (state.streak != 0) {
            state.streak = 0;
            plugin.stats().recordStreak(session.playerId(), session.template().name(), 0);
        }
    }

    private static boolean isWater(Block block) {
        return block.getType() == Material.WATER
                || (block.getBlockData() instanceof Waterlogged logged && logged.isWaterlogged());
    }

    // ------------------------------------------------------------- teardown

    @Override
    public void onArenaReset(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        State state = state(session);
        // A run abandoned mid-air (restart, arena switch) is a dodged fail —
        // it costs the streak, or bailing just before impact would keep it.
        if (state.airborne && !state.finishing) {
            dropStreak(plugin, session, state);
        }
    }

    @Override
    public void onSessionEnd(PracticeCorePlugin plugin, Player player, PracticeSession session) {
        State state = state(session);
        if (state.airborne && !state.finishing) {
            dropStreak(plugin, session, state);
        }
    }

    // --------------------------------------------------------------- board

    @Override
    public List<Component> boardLines(PracticeCorePlugin plugin, PracticeSession session) {
        State state = state(session);
        Messages msg = plugin.messages();
        if (state.bestStreak < 0) {
            state.bestStreak = plugin.stats().streakBest(session.playerId(),
                    session.template().name());
        }
        int best = state.bestStreak;
        return msg.lore("board.mlg-lines",
                "arena", session.template().displayName(),
                "drop", String.valueOf(state.currentDrop),
                "streak", String.valueOf(state.streak),
                "best", String.valueOf(Math.max(best, state.streak)));
    }
}
