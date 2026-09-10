package me.beekrbonkr.practicecore.beddefense;

import me.beekrbonkr.practicecore.PCConfig;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Target;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bed repair: the defense stands, and as long as it stands complete the
 * sky attacks it — a volley is either lit TNT dropping from above or a
 * single fireball from the side, never both. Once a volley is spent the
 * kit holds exactly the blocks the damage took, the exposure clock may
 * start, and the round is rebuilding before the bed has been exposed for
 * too long — a limit that starts generous and shrinks with every round
 * survived. There is no finish: the run goes on, volley after volley, until
 * the bed stays exposed past the limit; then the score — volleys survived
 * — is recorded and the arena resets straight into the next run.
 *
 * <p>Owned and ticked by {@link BedDefenseService}; all state lives on the
 * session's {@link BedDefenseState}.
 */
public final class BedRepair {

    private final PracticeCorePlugin plugin;
    private final BedDefenseService service;

    BedRepair(PracticeCorePlugin plugin, BedDefenseService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private PCConfig cfg() {
        return plugin.pcConfig();
    }

    // ------------------------------------------------------------- lifecycle

    /** The round is built and standing: a fresh run, the first volley on its way. */
    void begin(Player player, PracticeSession session, BedDefenseState state) {
        state.resetRepair();
        state.setVolleyDelay(cfg().bedDefenseRepairFirstDelayTicks());
        int seconds = (cfg().bedDefenseRepairFirstDelayTicks() + 19) / 20;
        if (state.introduceRepair()) {
            msg().send(player, "beddefense.repair.entered",
                    "name", state.defense().name(),
                    "seconds", String.valueOf(seconds),
                    "limit", String.valueOf(cfg().bedDefenseRepairExposedStartTicks() / 20),
                    "min", String.valueOf(cfg().bedDefenseRepairExposedMinTicks() / 20));
        } else {
            msg().actionBar(player, "beddefense.repair.run-start", "seconds", String.valueOf(seconds));
        }
    }

    /** Whatever the volley left in the air. Safe to call twice. */
    public void clearVolley(BedDefenseState state) {
        for (Entity entity : state.volley()) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        state.volley().clear();
    }

    /**
     * The run's score, written once: on the fail that ends it, and on any
     * reset or session end that cuts it short — rounds survived count
     * either way. Nothing is written for a run no volley was survived in.
     */
    public void record(Player player, PracticeSession session, BedDefenseState state) {
        if (state.repairRecorded() || state.rounds() <= 0 || state.defense() == null) {
            return;
        }
        state.setRepairRecorded(true);
        UUID id = session.playerId();
        BedDefense defense = state.defense();
        String key = BedDefenseService.repairStatsKey(defense.id());
        String display = service.displayForKey(key, defense);
        long rounds = state.rounds();
        long previousBest = plugin.stats().bestMs(id, key);
        LeaderboardService.Entry previousRecord = plugin.leaderboards().record(key);
        boolean pb = plugin.stats().recordScore(id, key, rounds, true);
        if (player == null) {
            return;
        }
        String rounded = String.valueOf(rounds);
        if (pb) {
            msg().send(player, "beddefense.repair.ended-pb", "rounds", rounded, "name", defense.name());
        } else if (previousBest >= 0) {
            msg().send(player, "beddefense.repair.ended", "rounds", rounded,
                    "best", String.valueOf(previousBest), "name", defense.name());
        } else {
            msg().send(player, "beddefense.repair.ended-first", "rounds", rounded, "name", defense.name());
        }
        if (!pb) {
            return;
        }
        boolean recordAnnounced = false;
        if (cfg().broadcastRecords() && plugin.leaderboards().rank(key, id) == 1
                && (previousRecord == null || !previousRecord.uuid().equals(id))) {
            if (previousRecord == null) {
                msg().broadcast("beddefense.repair.record-broadcast",
                        "player", player.getName(), "arena", display, "rounds", rounded);
            } else {
                msg().broadcast("beddefense.repair.record-broadcast-beaten",
                        "player", player.getName(), "arena", display, "rounds", rounded,
                        "previous-holder", previousRecord.displayName(),
                        "previous", String.valueOf(previousRecord.millis()));
            }
            plugin.sounds().broadcast("run.record-broadcast");
            recordAnnounced = true;
        }
        if (!recordAnnounced && previousBest >= 0 && cfg().broadcastPbs()) {
            msg().broadcast("beddefense.repair.pb-broadcast",
                    "player", player.getName(), "arena", display, "rounds", rounded,
                    "improvement", String.valueOf(rounds - previousBest));
        }
    }

    // ------------------------------------------------------------------ tick

    /** Every {@code period} ticks while the round plays. */
    void tick(Player player, PracticeSession session, BedDefenseState state, int period) {
        if (state.defense() == null || state.frame() == null) {
            return;
        }
        tickVolley(player, session, state, period);
        if (tickExposure(player, session, state, period)) {
            return; // the run just ended; the reset rebuilds everything
        }
        tickCountdown(player, session, state, period);
    }

    /** Volley entities going off (or getting stuck), and the round after them. */
    private void tickVolley(Player player, PracticeSession session, BedDefenseState state, int period) {
        List<Entity> volley = state.volley();
        if (volley.isEmpty()) {
            if (state.awaitingRepair() && state.complete()) {
                roundSurvived(player, state);
            }
            return;
        }
        volley.removeIf(entity -> !entity.isValid() || entity.isDead());
        state.setVolleyAge(state.volleyAge() + period);
        if (!volley.isEmpty() && state.volleyAge() >= cfg().bedDefenseRepairVolleyTimeoutTicks()) {
            // A fireball off into the void, a TNT lodged somewhere odd: it
            // must not hold the next volley up forever.
            clearVolley(state);
        }
        if (volley.isEmpty()) {
            // Blocks are handed over only now, with nothing left in the
            // air: a second TNT knocked away by the first would otherwise
            // go off in the middle of the repair.
            syncKit(player, state);
            if (state.complete()) {
                roundSurvived(player, state);
            } else {
                state.setAwaitingRepair(true);
                msg().actionBar(player, "beddefense.repair.rebuild",
                        "missing", String.valueOf(missing(state)));
            }
        }
    }

    /**
     * The exposure clock: starts the moment a cover spot is not a solid
     * block once the volley is spent, is announced every second, stops when
     * the spot is filled, and ends the run when it runs out. It never runs
     * with a volley still in the air — the blocks to cover the bed with
     * only arrive once it is. @return true when the run ended
     */
    private boolean tickExposure(Player player, PracticeSession session, BedDefenseState state,
                                 int period) {
        if (!state.volley().isEmpty()) {
            return false;
        }
        boolean exposed = exposed(session, state);
        int ticks = state.exposedTicks();
        if (!exposed) {
            if (ticks >= 0) {
                state.setExposedTicks(-1);
                msg().actionBar(player, "beddefense.repair.covered");
                plugin.sounds().play(player, "beddefense.repair-covered");
            }
            return false;
        }
        int limit = exposedLimit(state);
        if (ticks < 0) {
            ticks = 0;
            state.setExposedAnnounced(-1);
            msg().title(player, "beddefense.repair.title-exposed", "beddefense.repair.subtitle-exposed",
                    "seconds", String.valueOf(limit / 20));
            plugin.sounds().play(player, "beddefense.repair-exposed");
        } else {
            ticks += period;
        }
        state.setExposedTicks(ticks);
        int secondsLeft = Math.max(0, (limit - ticks + 19) / 20);
        if (secondsLeft != state.exposedAnnounced()) {
            state.setExposedAnnounced(secondsLeft);
            msg().actionBar(player, "beddefense.repair.exposed-bar", "seconds", String.valueOf(secondsLeft));
            if (ticks > 0) {
                plugin.sounds().play(player, "beddefense.repair-exposed-tick");
            }
        }
        if (ticks >= limit) {
            fail(player, session, state);
            return true;
        }
        return false;
    }

    /** The pause before the next volley — running only while the defense stands complete. */
    private void tickCountdown(Player player, PracticeSession session, BedDefenseState state, int period) {
        if (state.volleyDelay() < 0 || !state.volley().isEmpty() || state.awaitingRepair()
                || !state.complete()) {
            return;
        }
        int before = state.volleyDelay();
        int after = before - period;
        if (after > 0) {
            state.setVolleyDelay(after);
            int secondsBefore = (before + 19) / 20;
            int secondsAfter = (after + 19) / 20;
            if (secondsAfter != secondsBefore && secondsAfter <= 3) {
                msg().actionBar(player, "beddefense.repair.countdown",
                        "seconds", String.valueOf(secondsAfter));
                plugin.sounds().play(player, "beddefense.repair-countdown");
            }
            return;
        }
        state.setVolleyDelay(-1);
        fireVolley(player, session, state);
    }

    // ---------------------------------------------------------------- volleys

    /** TNT from the sky over random defense blocks, and maybe fireballs from the side. */
    private void fireVolley(Player player, PracticeSession session, BedDefenseState state) {
        List<Target> targets = state.targets();
        if (targets.isEmpty()) {
            return;
        }
        if (!state.runStarted()) {
            state.setRunStarted(true);
            if (session.state() == SessionState.READY) {
                session.setState(SessionState.ACTIVE);
                session.startTimer();
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // A volley is one thing or the other: a set of TNT from above, or
        // a single fireball from the side.
        boolean fireball = random.nextDouble() < cfg().bedDefenseRepairFireballChance();
        int tnt = fireball ? 0
                : Math.max(1, random.nextInt(cfg().bedDefenseRepairTntMin(), cfg().bedDefenseRepairTntMax() + 1));
        int top = Integer.MIN_VALUE;
        for (Target target : targets) {
            top = Math.max(top, target.loc().getBlockY());
        }
        state.setVolleyAge(0);
        for (int i = 0; i < tnt; i++) {
            Target target = targets.get(random.nextInt(targets.size()));
            Location at = target.loc().clone().add(
                    0.5 + random.nextDouble(-0.3, 0.3), 0, 0.5 + random.nextDouble(-0.3, 0.3));
            at.setY(top + 1 + cfg().bedDefenseRepairTntDropHeight());
            TNTPrimed primed = at.getWorld().spawn(at, TNTPrimed.class, entity -> {
                entity.setFuseTicks(cfg().bedDefenseRepairTntFuseTicks());
                entity.setVelocity(new Vector(0, 0, 0));
            });
            state.volley().add(primed);
            plugin.sounds().play(player, "beddefense.repair-tnt", at);
        }
        Location bed = state.frame().head().add(0.5, 0.5, 0.5)
                .add(state.frame().foot().subtract(state.frame().head()).multiply(0.5));
        if (fireball) {
            double angle = random.nextDouble(0, Math.PI * 2);
            Location from = bed.clone().add(
                    Math.cos(angle) * cfg().bedDefenseRepairFireballDistance(),
                    cfg().bedDefenseRepairFireballHeight(),
                    Math.sin(angle) * cfg().bedDefenseRepairFireballDistance());
            Vector direction = bed.toVector().subtract(from.toVector()).normalize();
            double speed = cfg().bedDefenseRepairFireballSpeed();
            LargeFireball shot = from.getWorld().spawn(from, LargeFireball.class, entity -> {
                entity.setIsIncendiary(false);
                entity.setYield((float) cfg().bedDefenseRepairFireballPower());
                entity.setVelocity(direction.clone().multiply(speed));
                entity.setAcceleration(direction.clone().multiply(speed * 0.05));
            });
            state.volley().add(shot);
            plugin.sounds().play(player, "beddefense.repair-fireball", from);
            msg().actionBar(player, "beddefense.repair.incoming-fireball");
        } else {
            msg().actionBar(player, "beddefense.repair.incoming-tnt", "count", String.valueOf(tnt));
        }
        plugin.sounds().play(player, "beddefense.repair-incoming");
    }

    /** The defense stands again after a volley: one more round on the score, the next volley scheduled. */
    private void roundSurvived(Player player, BedDefenseState state) {
        state.setAwaitingRepair(false);
        state.countRound();
        msg().actionBar(player, "beddefense.repair.survived", "round", String.valueOf(state.rounds()));
        plugin.sounds().play(player, "beddefense.repair-survived");
        int min = cfg().bedDefenseRepairDelayMinTicks();
        int max = cfg().bedDefenseRepairDelayMaxTicks();
        state.setVolleyDelay(max > min ? ThreadLocalRandom.current().nextInt(min, max + 1) : min);
    }

    /** The bed stayed exposed too long: the run is over, and the arena resets into the next. */
    private void fail(Player player, PracticeSession session, BedDefenseState state) {
        int rounds = state.rounds();
        state.setExposedTicks(-1);
        record(player, session, state);
        msg().send(player, "beddefense.repair.failed",
                "rounds", String.valueOf(rounds), "name", state.defense().name());
        msg().title(player, "beddefense.repair.title-failed", "beddefense.repair.subtitle-failed",
                "rounds", String.valueOf(rounds));
        plugin.sounds().play(player, "beddefense.repair-failed");
        clearVolley(state);
        plugin.sessions().resetQuietly(player, session);
    }

    // ------------------------------------------------------------ the world

    /**
     * True while any of the eight cover spots holds nothing solid: air,
     * water, a ladder. Spots past the map's edge could never be built and
     * do not count. A spot the map itself fills (a wall) counts as covered.
     */
    public boolean exposed(PracticeSession session, BedDefenseState state) {
        for (Location spot : state.frame().coverSpots()) {
            if (!session.containsBlock(spot)) {
                continue;
            }
            Block block = spot.getBlock();
            if (block.getType().isAir() || !block.getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    /** Defense blocks not standing right now. */
    public int missing(BedDefenseState state) {
        return state.targets().size() - state.satisfied();
    }

    /**
     * Exactly the blocks the defense is short, and no more: each kind is
     * topped up to what is missing of it, and anything over that is taken
     * away. Run after every explosion. A block placed in the wrong spot
     * still counts against the player — it can be broken and comes back.
     */
    void syncKit(Player player, BedDefenseState state) {
        if (state.defense() == null) {
            return;
        }
        Map<Material, Integer> needed = new EnumMap<>(Material.class);
        for (Target target : state.targets()) {
            if (!state.isSatisfied(target)) {
                needed.merge(target.block().kind(), 1, Integer::sum);
            }
        }
        boolean water = cfg().bedDefenseWaterBuckets();
        for (Material kind : state.defense().kindCounts().keySet()) {
            if (kind == Material.WATER && !water) {
                continue;
            }
            int need = needed.getOrDefault(kind, 0);
            int have = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && !stack.getType().isAir()
                        && service.itemRole(stack) == null && !plugin.menuItems().isMenuItem(stack)
                        && BlockKinds.normalize(stack.getType()) == kind) {
                    have += stack.getAmount();
                }
            }
            if (have < need) {
                Material item = kind == Material.WATER ? Material.WATER_BUCKET
                        : kind == Material.WHITE_WOOL ? service.kitWool(player) : kind;
                int give = need - have;
                while (give > 0) {
                    int amount = Math.min(give, item.getMaxStackSize());
                    player.getInventory().addItem(new ItemStack(item, amount));
                    give -= amount;
                }
            } else if (have > need) {
                int take = have - need;
                ItemStack[] contents = player.getInventory().getStorageContents();
                for (int slot = contents.length - 1; slot >= 0 && take > 0; slot--) {
                    ItemStack stack = contents[slot];
                    if (stack == null || stack.getType().isAir()
                            || service.itemRole(stack) != null || plugin.menuItems().isMenuItem(stack)
                            || BlockKinds.normalize(stack.getType()) != kind) {
                        continue;
                    }
                    int remove = Math.min(take, stack.getAmount());
                    stack.setAmount(stack.getAmount() - remove);
                    contents[slot] = stack.getAmount() <= 0 ? null : stack;
                    take -= remove;
                }
                player.getInventory().setStorageContents(contents);
            }
        }
        player.updateInventory();
    }

    /**
     * How long the bed may stay exposed right now: the starting limit less
     * a second for every {@code shrink-every-rounds} rounds survived, never
     * below the minimum — the run gets harder the longer it goes.
     */
    public int exposedLimit(BedDefenseState state) {
        int shrink = 20 * (state.rounds() / cfg().bedDefenseRepairExposedShrinkEveryRounds());
        return Math.max(cfg().bedDefenseRepairExposedMinTicks(),
                cfg().bedDefenseRepairExposedStartTicks() - shrink);
    }

    // --------------------------------------------------------------- sidebar

    /** The run's status for the sidebar: exposed, incoming, rebuilding, or the next drop. */
    public Component statusLine(BedDefenseState state) {
        if (state.exposedTicks() >= 0) {
            int left = Math.max(0, (exposedLimit(state) - state.exposedTicks() + 19) / 20);
            return msg().component("board.beddefense.repair-exposed-line", "seconds", String.valueOf(left));
        }
        if (!state.volley().isEmpty()) {
            return msg().component("board.beddefense.repair-incoming-line");
        }
        if (state.awaitingRepair() || !state.complete()) {
            return msg().component("board.beddefense.repair-rebuild-line",
                    "missing", String.valueOf(missing(state)));
        }
        if (state.volleyDelay() >= 0) {
            return msg().component("board.beddefense.repair-next-line",
                    "seconds", String.valueOf((state.volleyDelay() + 19) / 20));
        }
        return msg().component("board.beddefense.repair-waiting-line");
    }
}
