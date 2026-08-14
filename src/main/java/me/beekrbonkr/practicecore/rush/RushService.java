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
    /** Shop-bought special items; value = MBedwars special-item type id. */
    private final NamespacedKey specialKey;
    private BukkitTask task;

    public RushService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.dropKey = new NamespacedKey(plugin, "rush-drop");
        this.dealerKey = new NamespacedKey(plugin, "rush-dealer");
        this.specialKey = new NamespacedKey(plugin, "rush-special");
    }

    // ---------------------------------------------------------- selections

    /**
     * The player's remembered rush choices exactly as stored — what the
     * config menu shows and edits. Gameplay must use {@link #selection}
     * instead, which applies the competitive pins on top.
     */
    public RushSelection rawSelection(UUID player, ArenaTemplate template, RushMapData data) {
        var stats = plugin.stats();
        RushSelection defaults = RushSelection.defaults();
        RushSelection selection = new RushSelection(
                stats.pref(player, "rush.team." + template.name(), null),
                RushSelection.enumOr(RushSelection.BlockTier.class,
                        stats.pref(player, "rush.blocks", null), defaults.blocks()),
                RushSelection.enumOr(RushSelection.CurrencyTier.class,
                        stats.pref(player, "rush.currency", null), defaults.currency()),
                RushSelection.enumOr(RushSelection.PickaxeTier.class,
                        stats.pref(player, "rush.pickaxe", null), defaults.pickaxe()),
                RushSelection.enumOr(RushSelection.DefensePreset.class,
                        stats.pref(player, "rush.defense", null), defaults.defense()),
                stats.prefBool(player, "rush.base-generators",
                        plugin.pcConfig().rushBaseGeneratorsDefault()),
                stats.prefBool(player, "rush.competitive", false));

        if (data.team(selection.team()) == null || !data.team(selection.team()).playable()) {
            List<RushMapData.TeamBase> playable = data.playableTeams();
            selection = selection.withTeam(playable.isEmpty() ? null : playable.get(0).name());
        }
        return selection;
    }

    /**
     * The gameplay-effective selection: competitive mode pins the loadout
     * everyone races under — no starting items, defenses at the configured
     * preset, base generators running — so its leaderboards compare like with
     * like. The player's stored casual modifiers survive untouched underneath.
     */
    public RushSelection selection(UUID player, ArenaTemplate template, RushMapData data) {
        RushSelection selection = rawSelection(player, template, data);
        if (!selection.competitive()) {
            return selection;
        }
        return selection
                .withBlocks(RushSelection.BlockTier.NONE)
                .withCurrency(RushSelection.CurrencyTier.NONE)
                .withPickaxe(RushSelection.PickaxeTier.NONE)
                .withDefense(plugin.pcConfig().rushCompetitiveDefense())
                .withBaseGenerators(true);
    }

    /** Whether the player's next rush run is competitive. Set by the menu buttons. */
    public void setCompetitive(UUID player, boolean competitive) {
        plugin.stats().setPref(player, "rush.competitive", competitive);
    }

    /** Persists everything but the team, which is remembered per arena. One write, not five. */
    public void saveSelection(UUID player, RushSelection selection) {
        plugin.stats().setPrefs(player, java.util.Map.of(
                "rush.blocks", selection.blocks().name(),
                "rush.currency", selection.currency().name(),
                "rush.pickaxe", selection.pickaxe().name(),
                "rush.defense", selection.defense().name(),
                "rush.base-generators", selection.baseGenerators()));
    }

    public void saveTeam(UUID player, ArenaTemplate template, String team) {
        plugin.stats().setPref(player, "rush.team." + template.name(), team);
    }

    /** The objectives this map can actually arm, in board order. */
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

    // -------------------------------------------------------- special items

    /** Stamps a shop-bought special item so use-time listeners recognize it. */
    public ItemStack tagSpecial(ItemStack stack, String type) {
        stack.editMeta(meta -> meta.getPersistentDataContainer()
                .set(specialKey, PersistentDataType.STRING, type));
        return stack;
    }

    /** The MBedwars special-item type id of a stack, or null for plain items. */
    public String specialTypeOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(specialKey, PersistentDataType.STRING);
    }

    /** Takes one item off the given hand — a special item or TNT being used. */
    public void consumeHand(Player player, org.bukkit.inventory.EquipmentSlot hand) {
        boolean off = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        ItemStack held = off ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            held = null;
        } else {
            held = held.clone();
            held.setAmount(held.getAmount() - 1);
        }
        if (off) {
            player.getInventory().setItemInOffHand(held);
        } else {
            player.getInventory().setItemInMainHand(held);
        }
    }

    /**
     * Launches a fireball the way MBedwars does: straight along the aim line,
     * no fire trail (arenas must stay clean), explosion handled by the
     * explosion listener — placed blocks and defenses break, the map doesn't.
     */
    public void launchFireball(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        org.bukkit.entity.Fireball fireball = player.launchProjectile(
                org.bukkit.entity.Fireball.class, direction.multiply(1.5));
        fireball.setIsIncendiary(false);
        fireball.setYield(3.0f);
        if (plugin.pcConfig().sounds()) {
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.0f);
        }
    }

    /** MBedwars-style auto-ignited TNT: no block, just the primed entity. */
    public void primeTnt(Player player, Location blockLocation) {
        blockLocation.getWorld().spawn(blockLocation.toCenterLocation(),
                org.bukkit.entity.TNTPrimed.class, tnt -> {
                    tnt.setFuseTicks(60);
                    tnt.setSource(player);
                });
        if (plugin.pcConfig().sounds()) {
            blockLocation.getWorld().playSound(blockLocation,
                    org.bukkit.Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        }
    }

    /**
     * Explosion side-effects the cancelled damage event swallows: bedwars
     * players expect TNT jumps and fireball jumps, so the knockback is applied
     * directly. Strength falls off linearly to nothing at {@code radius}.
     */
    public void applyExplosionKnockback(Location center) {
        double radius = 5.0;
        for (Entity entity : center.getWorld()
                .getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player player)
                    || plugin.sessions().get(player.getUniqueId()) == null) {
                continue;
            }
            Location eye = player.getLocation().add(0, 0.5, 0);
            double distance = eye.distance(center);
            if (distance > radius) {
                continue;
            }
            double strength = 1.6 * (1.0 - distance / radius);
            Vector push = eye.toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 1, 0);
            }
            push = push.normalize().multiply(strength);
            // The vertical lift is what makes TNT jumps work.
            push.setY(Math.max(push.getY(), 0.55 * strength + 0.25));
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    /**
     * The bridge egg: an egg that trails a wool bridge under its flight path
     * for a few seconds, built out of tracked blocks so the reset removes it.
     */
    public void throwBridgeEgg(Player player, PracticeSession session) {
        org.bukkit.entity.Egg egg = player.launchProjectile(org.bukkit.entity.Egg.class,
                player.getEyeLocation().getDirection().multiply(1.4));
        if (plugin.pcConfig().sounds()) {
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_EGG_THROW, 0.8f, 0.9f);
        }
        Material wool = bridgeWool(player);
        BukkitTask[] builder = new BukkitTask[1];
        builder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!egg.isValid() || plugin.sessions().get(player.getUniqueId()) != session
                    || egg.getTicksLived() > 60) {
                egg.remove();
                builder[0].cancel();
                return;
            }
            Location spot = egg.getLocation().subtract(0, 2, 0);
            placeTracked(session, spot.getBlock(), wool);
        }, 1L, 1L);
    }

    /**
     * The rescue platform: a slime disc a couple of blocks under the player,
     * tracked so the reset removes it.
     *
     * @return false when nothing could be built (entirely out of bounds)
     */
    public boolean buildRescuePlatform(Player player, PracticeSession session) {
        Location center = player.getLocation().subtract(0, 2, 0);
        boolean built = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue; // rounded corners, like the MBedwars platform
                }
                built |= placeTracked(session,
                        center.clone().add(dx, 0, dz).getBlock(), Material.SLIME_BLOCK);
            }
        }
        if (built && plugin.pcConfig().sounds()) {
            player.getWorld().playSound(center, org.bukkit.Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 0.9f);
        }
        return built;
    }

    /** Places one tracked block if the spot is in-bounds air. */
    private boolean placeTracked(PracticeSession session, org.bukkit.block.Block block,
                                 Material material) {
        if (!session.containsBlock(block.getLocation()) || !block.getType().isAir()) {
            return false;
        }
        session.tracker().recordPlace(block, block.getBlockData());
        block.setType(material, false);
        return true;
    }

    /** The player's chosen wool color, defaulting to white — same as the kit. */
    private Material bridgeWool(Player player) {
        org.bukkit.DyeColor color = plugin.settings().woolColor(player.getUniqueId());
        Material wool = color == null ? null
                : me.beekrbonkr.practicecore.settings.SettingsService.woolOf(color);
        return wool != null ? wool : Material.WHITE_WOOL;
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

    /** Snapshot of the MBedwars shop; its config is static at runtime, so one walk serves every open. */
    private RushShopData shopCache;

    /** Opens the mirrored MBedwars shop, or explains why it can't. */
    public void openShop(Player player) {
        RushShopData shop;
        try {
            if (!MBedwarsHook.available()) {
                plugin.messages().send(player, "rush.shop-unavailable");
                return;
            }
            if (shopCache == null) {
                shopCache = MBedwarsHook.shopSnapshot();
            }
            shop = shopCache;
        } catch (LinkageError e) {
            // available() only proves MBedwars is enabled, not that this build
            // still has every class and method the hook links against.
            plugin.messages().send(player, "rush.shop-unavailable");
            plugin.getLogger().severe("MBedwars shop hook failed — incompatible MBedwars version? " + e);
            return;
        }
        if (shop.pages().isEmpty()) {
            plugin.messages().send(player, "rush.shop-empty");
            return;
        }
        if (plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_TRADE, 0.7f, 1.0f);
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
        shopCache = null; // /practice reload may follow an MBedwars shop edit
    }

    private void tickAll() {
        for (PracticeSession session : plugin.sessions().all()) {
            if (!(session.mode() instanceof RushMode)
                    || !(session.modeState() instanceof RushState state)) {
                continue;
            }
            // ACTIVE only: generators running at READY would let a competitive
            // player farm resources and shop before the timer ever starts.
            if (session.state() != SessionState.ACTIVE) {
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
