package me.beekrbonkr.practicecore.rush;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared services for rush practice: per-player selections (persisted as
 * playerdata prefs), the iron/gold generator tick, tagged item drops that the
 * practice world's blanket item-spawn ban lets through, dealer NPCs and the
 * mirrored MBedwars shop.
 */
public final class RushService {

    /** Ticks between generator task passes; drop countdowns run on this beat. */
    private static final int TICK_PERIOD = 5;
    /** How close (blocks, squared) an item must be to a generator to count toward its cap. */
    private static final double CAP_RADIUS_SQ = 4;

    private final PracticeCorePlugin plugin;
    /** Item entities allowed to spawn; value = generator id that dropped them. */
    private final NamespacedKey dropKey;
    /** Villagers acting as shop dealers. */
    private final NamespacedKey dealerKey;
    private BukkitTask task;

    public RushService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dropKey = new NamespacedKey(plugin, "rush-drop");
        this.dealerKey = new NamespacedKey(plugin, "rush-dealer");
    }

    // ---------------------------------------------------------- selections

    /**
     * The player's remembered rush choices, clamped to what this map actually
     * supports (an objective without its generators or enemy beds is swapped
     * for the first possible one).
     */
    public RushSelection selection(UUID player, ArenaTemplate template, RushMapData data) {
        var stats = plugin.stats();
        RushSelection defaults = RushSelection.defaults();
        RushSelection selection = new RushSelection(
                stats.pref(player, "rush.team." + template.name(), null),
                RushObjective.byId(stats.pref(player, "rush.objective", null), defaults.objective()),
                RushSelection.enumOr(RushSelection.BlockTier.class,
                        stats.pref(player, "rush.blocks", null), defaults.blocks()),
                RushSelection.enumOr(RushSelection.CurrencyTier.class,
                        stats.pref(player, "rush.currency", null), defaults.currency()),
                RushSelection.enumOr(RushSelection.PickaxeTier.class,
                        stats.pref(player, "rush.pickaxe", null), defaults.pickaxe()),
                RushSelection.enumOr(RushSelection.DefensePreset.class,
                        stats.pref(player, "rush.defense", null), defaults.defense()),
                stats.prefBool(player, "rush.base-generators",
                        plugin.pcConfig().rushBaseGeneratorsDefault()));

        List<RushObjective> supported = supportedObjectives(data);
        if (!supported.isEmpty() && !supported.contains(selection.objective())) {
            selection = selection.withObjective(supported.get(0));
        }
        if (data.team(selection.team()) == null || !data.team(selection.team()).playable()) {
            List<RushMapData.TeamBase> playable = data.playableTeams();
            selection = selection.withTeam(playable.isEmpty() ? null : playable.get(0).name());
        }
        return selection;
    }

    /** Persists everything but the team, which is remembered per arena. */
    public void saveSelection(UUID player, RushSelection selection) {
        var stats = plugin.stats();
        stats.setPref(player, "rush.objective", selection.objective().name());
        stats.setPref(player, "rush.blocks", selection.blocks().name());
        stats.setPref(player, "rush.currency", selection.currency().name());
        stats.setPref(player, "rush.pickaxe", selection.pickaxe().name());
        stats.setPref(player, "rush.defense", selection.defense().name());
        stats.setPref(player, "rush.base-generators", selection.baseGenerators());
    }

    public void saveTeam(UUID player, ArenaTemplate template, String team) {
        plugin.stats().setPref(player, "rush.team." + template.name(), team);
    }

    /** The objectives this map can actually offer, in menu order. */
    public List<RushObjective> supportedObjectives(RushMapData data) {
        List<RushObjective> supported = new ArrayList<>();
        if (data.playableTeams().size() >= 2) {
            supported.add(RushObjective.BED);
        }
        if (!data.generatorsOf("emerald").isEmpty()) {
            supported.add(RushObjective.EMERALD);
        }
        if (!data.generatorsOf("diamond").isEmpty()) {
            supported.add(RushObjective.DIAMOND);
        }
        return supported;
    }

    // ---------------------------------------------------------- stats keys

    /**
     * Resolves a rush composite stats key ("map#bed") to its arena and
     * objective, or null when it is a plain arena key or the arena is gone.
     */
    public java.util.Map.Entry<ArenaTemplate, RushObjective> resolveStatsKey(String key) {
        var parsed = RushObjective.parseStatsKey(key);
        if (parsed == null) {
            return null;
        }
        ArenaTemplate template = plugin.templates().get(parsed.getKey());
        return template == null ? null : java.util.Map.entry(template, parsed.getValue());
    }

    /** "<arena display> (<objective>)" for boards, stats and broadcasts. */
    public String displayFor(ArenaTemplate template, RushObjective objective) {
        return template.displayName() + " (" + objectiveName(objective) + ")";
    }

    public String objectiveName(RushObjective objective) {
        String name = plugin.messages().raw(objective.messageKey());
        return name.isEmpty() ? objective.id() : name;
    }

    // ------------------------------------------------------------ dropping

    /**
     * Drops an item the practice world's item-spawn ban will allow through.
     * The tag also tells the pickup listener what was picked up.
     */
    public Item dropTracked(Location loc, ItemStack stack, String type, boolean objective) {
        return loc.getWorld().dropItem(loc, stack, item -> {
            item.getPersistentDataContainer().set(dropKey, PersistentDataType.STRING, type);
            item.setVelocity(new Vector(0, 0, 0));
            item.setUnlimitedLifetime(true);
            item.setPersistent(true);
            if (objective) {
                item.setGlowing(true);
                item.setPickupDelay(0);
            }
        });
    }

    /** The generator id a tagged drop came from, or null for foreign items. */
    public String dropTypeOf(Item item) {
        return item.getPersistentDataContainer().get(dropKey, PersistentDataType.STRING);
    }

    public boolean isRushDrop(Entity entity) {
        return entity instanceof Item item && dropTypeOf(item) != null;
    }

    // -------------------------------------------------------------- dealers

    public Villager spawnDealer(Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.getPersistentDataContainer().set(dealerKey, PersistentDataType.BYTE, (byte) 1);
            villager.setAI(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            villager.setProfession(Villager.Profession.LIBRARIAN);
        });
    }

    public boolean isDealer(Entity entity) {
        return entity instanceof Villager villager
                && villager.getPersistentDataContainer().has(dealerKey, PersistentDataType.BYTE);
    }

    /** Opens the mirrored MBedwars shop, or explains why it can't. */
    public void openShop(Player player) {
        if (!MBedwarsHook.available()) {
            plugin.messages().send(player, "rush.shop-unavailable");
            return;
        }
        RushShopData shop = MBedwarsHook.shopSnapshot(player);
        if (shop.pages().isEmpty()) {
            plugin.messages().send(player, "rush.shop-empty");
            return;
        }
        new me.beekrbonkr.practicecore.gui.RushShopMenu(plugin, player, shop).open();
    }

    // ----------------------------------------------------- generator ticking

    public void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD, TICK_PERIOD);
    }

    public void restartTask() {
        shutdown();
        startTask();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        for (PracticeSession session : plugin.sessions().all()) {
            if (!(session.mode() instanceof RushMode)
                    || !(session.modeState() instanceof RushState state)) {
                continue;
            }
            SessionState sessionState = session.state();
            if (sessionState != SessionState.READY && sessionState != SessionState.ACTIVE) {
                continue;
            }
            for (RushState.ActiveGenerator generator : state.generators()) {
                generator.countdown -= TICK_PERIOD;
                if (generator.countdown > 0) {
                    continue;
                }
                generator.countdown = generator.intervalTicks;
                if (nearbyDrops(generator.dropSpot(), generator.drops())
                        < plugin.pcConfig().rushGeneratorItemCap()) {
                    dropTracked(generator.dropSpot(), new ItemStack(generator.drops()),
                            generator.drops() == Material.IRON_INGOT ? "iron" : "gold", false);
                }
            }
        }
    }

    private int nearbyDrops(Location spot, Material material) {
        int total = 0;
        for (Entity entity : spot.getWorld().getNearbyEntities(spot, 2, 2, 2)) {
            if (entity instanceof Item item && item.getItemStack().getType() == material
                    && spot.distanceSquared(item.getLocation()) <= CAP_RADIUS_SQ) {
                total += item.getItemStack().getAmount();
            }
        }
        return total;
    }
}
