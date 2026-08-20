package me.beekrbonkr.practicecore.rush;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-session rush scratch state, rebuilt on every arena reset ({@code
 * onReady}). Owned by RushMode; the generator countdowns are ticked by
 * {@link RushService}'s task.
 */
public final class RushState {

    /** An enemy bed the run may target. Both block positions are world locations. */
    public record TargetBed(String team, Location head, Location foot,
                            Material material, BlockFace facing) {
    }

    /** A live generator: iron/gold at bases, plus diamond/emerald on combat runs. */
    public static final class ActiveGenerator {
        final Location dropSpot;
        final Material drops;
        final String type;
        final int intervalTicks;
        int countdown;

        ActiveGenerator(Location dropSpot, Material drops, String type, int intervalTicks) {
            this.dropSpot = dropSpot;
            this.drops = drops;
            this.type = type;
            this.intervalTicks = Math.max(1, intervalTicks);
            this.countdown = this.intervalTicks;
        }

        public Location dropSpot() {
            return dropSpot;
        }

        public Material drops() {
            return drops;
        }

        /** The generator id ("iron", "gold", "diamond", "emerald"). */
        public String type() {
            return type;
        }
    }

    private RushMapData data;
    private RushSelection selection;
    private RushMapData.TeamBase base;
    /** The objective that actually ended this run; null while it is live. */
    private RushObjective completed;
    private final List<TargetBed> enemyBeds = new ArrayList<>();
    /** Every block position of every enemy bed, for O(1) break checks. */
    private final Set<Location> enemyBedBlocks = new HashSet<>();
    /** Defense blocks this mode generated over enemy beds. */
    private final Set<Location> defenseBlocks = new HashSet<>();
    private final List<ActiveGenerator> generators = new ArrayList<>();
    /** Combat runs: enemy teams whose bed the player has destroyed. */
    private final Set<String> bedsBroken = new HashSet<>();
    /** Combat runs: this session's defender bots, owned by RushBotService. */
    private final List<me.beekrbonkr.practicecore.rushbot.RushBot> bots = new ArrayList<>();
    /** Player deaths to defenders this run — the sidebar's combat line. */
    private int playerDeaths;
    /** Ticks left of the blind post-death hold at the player's own base. */
    private int playerHoldTicks;
    /**
     * The run's own ender chest, like a real game's: opened from any ender
     * chest block on the map, survives combat respawns, and dies with this
     * state on the next reset. Never the player's real ender chest — that is
     * player data no arena may touch.
     */
    private org.bukkit.inventory.Inventory enderChest;

    public RushSelection selection() {
        return selection;
    }

    /** Whether this run fights defender bots (team wipe) instead of racing. */
    public boolean combat() {
        return selection != null && selection.combat() && !enemyBeds.isEmpty();
    }

    public List<TargetBed> enemyBeds() {
        return List.copyOf(enemyBeds);
    }

    /** The enemy bed of one team, or null. */
    public TargetBed bedOf(String team) {
        for (TargetBed bed : enemyBeds) {
            if (bed.team().equals(team)) {
                return bed;
            }
        }
        return null;
    }

    public boolean isBedBroken(String team) {
        return bedsBroken.contains(team);
    }

    /** Marks the team owning this bed block as bed-less; null if not a bed block. */
    public String markBedBroken(Location loc) {
        for (TargetBed bed : enemyBeds) {
            if (bed.head().equals(loc) || bed.foot().equals(loc)) {
                bedsBroken.add(bed.team());
                return bed.team();
            }
        }
        return null;
    }

    public int bedsStanding() {
        return enemyBeds.size() - bedsBroken.size();
    }

    /** The live defender-bot list; RushBotService owns its contents. */
    public List<me.beekrbonkr.practicecore.rushbot.RushBot> bots() {
        return bots;
    }

    public int playerDeaths() {
        return playerDeaths;
    }

    public void countPlayerDeath() {
        playerDeaths++;
    }

    /** Combat runs: mid post-death hold — pinned at base, untouchable. */
    public boolean playerHeld() {
        return playerHoldTicks > 0;
    }

    public int playerHoldTicks() {
        return playerHoldTicks;
    }

    public void setPlayerHoldTicks(int ticks) {
        this.playerHoldTicks = Math.max(0, ticks);
    }

    /** The run's ender chest, created on first open. */
    public org.bukkit.inventory.Inventory enderChest(PracticeCorePlugin plugin) {
        if (enderChest == null) {
            enderChest = Bukkit.createInventory(null, 27,
                    plugin.messages().component("rush.ender-chest-title"));
        }
        return enderChest;
    }

    public RushMapData data() {
        return data;
    }

    public RushMapData.TeamBase base() {
        return base;
    }

    public RushObjective completed() {
        return completed;
    }

    public void setCompleted(RushObjective completed) {
        this.completed = completed;
    }

    public List<ActiveGenerator> generators() {
        return generators;
    }

    public boolean isEnemyBedBlock(Location loc) {
        return enemyBedBlocks.contains(loc);
    }

    public boolean isDefenseBlock(Location loc) {
        return defenseBlocks.contains(loc);
    }

    /**
     * Airs out every shell block this state generated. Runs before the next
     * round's state takes over: shells are never block-tracked, so a changed
     * defense preset would otherwise leave the old shell standing — and, no
     * longer in the new state's set, unbreakable. Only ever built on air, so
     * clearing to air restores exactly the schematic's state.
     */
    public void removeDefenses(World world) {
        for (Location loc : defenseBlocks) {
            world.getBlockAt(loc).setType(Material.AIR, false);
        }
        defenseBlocks.clear();
    }

    // ------------------------------------------------------------- building

    /**
     * (Re)derives everything from the template and the player's selection,
     * then puts the arena instance in playing shape: enemy beds re-placed,
     * defenses generated, dealers spawned, objective items waiting, base
     * generators armed. Runs at READY — after the join teleport and after
     * every reset (which has already wiped entities and reverted blocks).
     */
    public void rebuild(PracticeCorePlugin plugin, PracticeSession session) {
        data = RushMapData.parse(session.template());
        selection = plugin.rush().selection(session.playerId(), session.template(), data);
        List<RushMapData.TeamBase> playable = data.playableTeams();
        base = data.team(selection.team());
        if ((base == null || !base.playable()) && !playable.isEmpty()) {
            base = playable.get(0);
        }
        enemyBeds.clear();
        enemyBedBlocks.clear();
        defenseBlocks.clear();
        generators.clear();
        bedsBroken.clear();
        playerDeaths = 0;
        if (base == null) {
            plugin.getLogger().warning("Rush arena '" + session.template().name()
                    + "' has no playable team — runs can never finish.");
            return;
        }

        Location origin = session.origin();
        World world = origin.getWorld();
        for (RushMapData.TeamBase team : playable) {
            if (team.name().equals(base.name())) {
                continue;
            }
            Location head = block(origin, team.bedHead());
            Location foot = head.getBlock().getRelative(team.bedFacing().getOppositeFace())
                    .getLocation();
            enemyBeds.add(new TargetBed(team.name(), head, foot, team.bedMaterial(), team.bedFacing()));
            enemyBedBlocks.add(head);
            enemyBedBlocks.add(foot);
        }

        replaceBeds(world);
        if (selection.defense() != RushSelection.DefensePreset.NONE) {
            generateDefenses(session, world);
        }
        for (RushMapData.Dealer dealer : data.dealers()) {
            Location loc = new Location(world,
                    origin.getX() + dealer.offset().getX(),
                    origin.getY() + dealer.offset().getY(),
                    origin.getZ() + dealer.offset().getZ(),
                    dealer.yaw(), 0);
            plugin.rush().spawnDealer(loc);
        }
        if (combat()) {
            // A combat run plays like a real match: the shared diamond and
            // emerald generators produce on a cycle instead of holding one
            // objective item, and enemy bases are defended.
            armObjectiveGenerators(plugin, origin);
            plugin.rushBots().spawnAll(session, this);
        } else {
            placeObjectiveItems(plugin, origin);
        }
        if (selection.baseGenerators()) {
            armBaseGenerators(plugin, origin);
        }
    }

    private static Location block(Location origin, Vector offset) {
        return new Location(origin.getWorld(),
                origin.getBlockX() + offset.getBlockX(),
                origin.getBlockY() + offset.getBlockY(),
                origin.getBlockZ() + offset.getBlockZ());
    }

    /** Restores every enemy bed — the previous run may have broken one. */
    private void replaceBeds(World world) {
        for (TargetBed bed : enemyBeds) {
            String facing = bed.facing().name().toLowerCase(java.util.Locale.ROOT);
            world.getBlockAt(bed.foot()).setBlockData(
                    Bukkit.createBlockData(bed.material(), "[part=foot,facing=" + facing + "]"), false);
            world.getBlockAt(bed.head()).setBlockData(
                    Bukkit.createBlockData(bed.material(), "[part=head,facing=" + facing + "]"), false);
        }
    }

    /**
     * Covers every enemy bed in concentric shells, innermost layer first —
     * Chebyshev distance 1 is the first material, distance 2 the second.
     * Only air (or a previous run's identical shell) is written, never the
     * map's own blocks, and nothing below the bed's own Y so floors survive.
     */
    private void generateDefenses(PracticeSession session, World world) {
        Material[] layers = selection.defense().layers();
        if (layers.length == 0) {
            return;
        }
        for (TargetBed bed : enemyBeds) {
            List<Location> cells = List.of(bed.head(), bed.foot());
            int bedY = bed.head().getBlockY();
            int reach = layers.length;
            int minX = cells.stream().mapToInt(Location::getBlockX).min().orElse(0) - reach;
            int maxX = cells.stream().mapToInt(Location::getBlockX).max().orElse(0) + reach;
            int minZ = cells.stream().mapToInt(Location::getBlockZ).min().orElse(0) - reach;
            int maxZ = cells.stream().mapToInt(Location::getBlockZ).max().orElse(0) + reach;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = bedY; y <= bedY + reach; y++) {
                        int distance = Integer.MAX_VALUE;
                        for (Location cell : cells) {
                            int d = Math.max(Math.abs(x - cell.getBlockX()),
                                    Math.max(y - cell.getBlockY(), Math.abs(z - cell.getBlockZ())));
                            distance = Math.min(distance, d);
                        }
                        if (distance < 1 || distance > layers.length) {
                            continue;
                        }
                        Material material = layers[distance - 1];
                        Location loc = new Location(world, x, y, z);
                        if (!session.containsBlock(loc)) {
                            // A bed against the map's edge must not shell
                            // past the bounds — nothing out there is erased.
                            continue;
                        }
                        Material current = world.getBlockAt(loc).getType();
                        if (current.isAir() || current == material) {
                            world.getBlockAt(loc).setType(material, false);
                            defenseBlocks.add(loc);
                        }
                    }
                }
            }
        }
    }

    /**
     * Every objective is armed at once: an emerald and a diamond sit waiting
     * on each of their generators (and the enemy beds are already standing) —
     * whichever the player reaches first ends the run.
     */
    private void placeObjectiveItems(PracticeCorePlugin plugin, Location origin) {
        for (RushObjective objective : List.of(RushObjective.EMERALD, RushObjective.DIAMOND)) {
            Material material = objective == RushObjective.EMERALD
                    ? Material.EMERALD : Material.DIAMOND;
            for (RushMapData.Generator generator : data.generatorsOf(objective.id())) {
                plugin.rush().dropTracked(dropSpot(origin, generator),
                        new ItemStack(material), objective.id(), true);
            }
        }
    }

    /**
     * Arms every iron/gold generator on the map — all bases produce, exactly
     * as they would in a real game, so rushing an enemy base finds resources
     * waiting there. The per-generator item cap keeps distant bases from
     * flooding while the player is elsewhere.
     */
    private void armBaseGenerators(PracticeCorePlugin plugin, Location origin) {
        for (RushMapData.Generator generator : data.generators()) {
            Material drops = switch (generator.type()) {
                case "iron" -> Material.IRON_INGOT;
                case "gold" -> Material.GOLD_INGOT;
                default -> null;
            };
            if (drops == null) {
                continue;
            }
            int interval = drops == Material.IRON_INGOT
                    ? plugin.pcConfig().rushIronIntervalTicks()
                    : plugin.pcConfig().rushGoldIntervalTicks();
            generators.add(new ActiveGenerator(dropSpot(origin, generator), drops,
                    generator.type(), interval));
        }
    }

    /**
     * Combat runs: the map's diamond and emerald generators run on their own
     * cycle, the way a real game's shared generators do. Pickups are just
     * resources — the run ends on the team wipe, nothing else.
     */
    private void armObjectiveGenerators(PracticeCorePlugin plugin, Location origin) {
        for (RushMapData.Generator generator : data.generatorsOf("diamond")) {
            generators.add(new ActiveGenerator(dropSpot(origin, generator),
                    Material.DIAMOND, "diamond", plugin.pcConfig().rushDiamondIntervalTicks()));
        }
        for (RushMapData.Generator generator : data.generatorsOf("emerald")) {
            generators.add(new ActiveGenerator(dropSpot(origin, generator),
                    Material.EMERALD, "emerald", plugin.pcConfig().rushEmeraldIntervalTicks()));
        }
    }

    /** The middle of the generator block, one block up, where items appear. */
    private static Location dropSpot(Location origin, RushMapData.Generator generator) {
        return new Location(origin.getWorld(),
                origin.getBlockX() + generator.offset().getBlockX() + 0.5,
                origin.getBlockY() + generator.offset().getBlockY() + 1.0,
                origin.getBlockZ() + generator.offset().getBlockZ() + 0.5);
    }
}
