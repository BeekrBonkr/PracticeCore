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
        int maxBots = plugin.pcConfig().rushBotsMaxPerTeam();
        int bots = 0;
        try {
            bots = Math.clamp(Integer.parseInt(
                    stats.pref(player, "rush.bots", "0")), 0, maxBots);
        } catch (NumberFormatException ignored) {
        }
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
                bots,
                stats.pref(player, "rush.bot-difficulty", defaults.botDifficulty()),
                RushSelection.enumOr(RushSelection.BotArmor.class,
                        stats.pref(player, "rush.bot-armor", null), defaults.botArmor()),
                RushSelection.enumOr(RushSelection.BotSword.class,
                        stats.pref(player, "rush.bot-sword", null), defaults.botSword()),
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
     * preset, base generators running, defender bots at the configured lineup
     * — so its leaderboards compare like with like. The player's stored
     * casual modifiers survive untouched underneath.
     */
    public RushSelection selection(UUID player, ArenaTemplate template, RushMapData data) {
        RushSelection selection = rawSelection(player, template, data);
        if (!selection.competitive()) {
            return selection;
        }
        var config = plugin.pcConfig();
        return selection
                .withBlocks(RushSelection.BlockTier.NONE)
                .withCurrency(RushSelection.CurrencyTier.NONE)
                .withPickaxe(RushSelection.PickaxeTier.NONE)
                .withDefense(config.rushCompetitiveDefense())
                .withBaseGenerators(true)
                .withBots(config.rushBotsCompetitivePerTeam())
                .withBotDifficulty(config.rushBotsCompetitiveDifficulty())
                .withBotArmor(config.rushBotsCompetitiveArmor())
                .withBotSword(config.rushBotsCompetitiveSword());
    }

    /** Whether the player's next rush run is competitive. Set by the menu buttons. */
    public void setCompetitive(UUID player, boolean competitive) {
        plugin.stats().setPref(player, "rush.competitive", competitive);
    }

    /** Persists everything but the team, which is remembered per arena. One write, not nine. */
    public void saveSelection(UUID player, RushSelection selection) {
        plugin.stats().setPrefs(player, java.util.Map.of(
                "rush.blocks", selection.blocks().name(),
                "rush.currency", selection.currency().name(),
                "rush.pickaxe", selection.pickaxe().name(),
                "rush.defense", selection.defense().name(),
                "rush.base-generators", selection.baseGenerators(),
                "rush.bots", selection.bots(),
                "rush.bot-difficulty", selection.botDifficulty(),
                "rush.bot-armor", selection.botArmor().name(),
                "rush.bot-sword", selection.botSword().name()));
    }

    public void saveTeam(UUID player, ArenaTemplate template, String team) {
        plugin.stats().setPref(player, "rush.team." + template.name(), team);
    }

    /** The objectives this map can arm for a bot-free race, in board order. */
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

    /**
     * The objectives a run under this selection actually arms. Combat runs
     * (defender bots enabled) arm only the team wipe — beds gate respawns
     * and generator pickups are just resources, exactly like a real game.
     */
    public List<RushObjective> supportedObjectives(RushMapData data, RushSelection selection) {
        if (selection != null && selection.combat() && data.playableTeams().size() >= 2) {
            return List.of(RushObjective.TEAM_WIPE);
        }
        return supportedObjectives(data);
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
                org.bukkit.entity.Fireball.class,
                direction.multiply(plugin.pcConfig().rushFireballSpeed()));
        fireball.setIsIncendiary(false);
        fireball.setYield((float) plugin.pcConfig().rushFireballPower());
        plugin.sounds().playAt(player.getLocation(), "rush.fireball");
    }

    /** MBedwars-style auto-ignited TNT: no block, just the primed entity. */
    public void primeTnt(Player player, Location blockLocation) {
        blockLocation.getWorld().spawn(blockLocation.toCenterLocation(),
                org.bukkit.entity.TNTPrimed.class, tnt -> {
                    tnt.setFuseTicks(plugin.pcConfig().rushTntFuseTicks());
                    tnt.setSource(player);
                });
        plugin.sounds().playAt(blockLocation, "rush.tnt-primed");
    }

    /**
     * Explosion side-effects the cancelled damage event swallows: bedwars
     * players expect TNT jumps and fireball jumps, so the knockback is applied
     * directly. Strength falls off linearly to nothing at {@code radius}.
     */
    public void applyExplosionKnockback(Location center) {
        double radius = plugin.pcConfig().rushExplosionRadius();
        if (radius <= 0) {
            return;
        }
        double power = plugin.pcConfig().rushExplosionStrength();
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
            double strength = power * (1.0 - distance / radius);
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
                player.getEyeLocation().getDirection()
                        .multiply(plugin.pcConfig().rushBridgeEggSpeed()));
        plugin.sounds().playAt(player.getLocation(), "rush.bridge-egg");
        Material wool = bridgeWool(player);
        int lifetime = plugin.pcConfig().rushBridgeEggLifetimeTicks();
        int below = plugin.pcConfig().rushBridgeEggDropBelow();
        BukkitTask[] builder = new BukkitTask[1];
        builder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!egg.isValid() || plugin.sessions().get(player.getUniqueId()) != session
                    || egg.getTicksLived() > lifetime) {
                egg.remove();
                builder[0].cancel();
                return;
            }
            Location spot = egg.getLocation().subtract(0, below, 0);
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
        int radius = plugin.pcConfig().rushRescuePlatformRadius();
        Material material = plugin.pcConfig().rushRescuePlatformMaterial();
        Location center = player.getLocation()
                .subtract(0, plugin.pcConfig().rushRescuePlatformDepth(), 0);
        boolean built = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    continue; // rounded corners, like the MBedwars platform
                }
                built |= placeTracked(session,
                        center.clone().add(dx, 0, dz).getBlock(), material);
            }
        }
        if (built) {
            plugin.sounds().playAt(center, "rush.rescue-platform");
        }
        return built;
    }

    // ------------------------------------------------- more special items

    /** Live teleporter channels; value = the countdown task. */
    private final java.util.Map<UUID, BukkitTask> teleporterChannels = new java.util.HashMap<>();

    /**
     * The MBedwars teleporter: stand still while it channels, then snap back
     * to your base spawn. Moving a block cancels it and keeps the item; the
     * one that fires is consumed by the caller-supplied hook.
     */
    public void startTeleporter(Player player, PracticeSession session, Runnable consume) {
        UUID id = player.getUniqueId();
        cancelTeleporter(id);
        int total = plugin.pcConfig().rushTeleporterChannelTicks();
        Location start = player.getLocation();
        plugin.sounds().play(player, "rush.teleporter-start");
        BukkitTask[] task = new BukkitTask[1];
        int[] left = {total};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || plugin.sessions().get(id) != session
                    || (session.state() != SessionState.ACTIVE
                        && session.state() != SessionState.READY)) {
                cancelTeleporter(id);
                return;
            }
            if (player.getLocation().getBlockX() != start.getBlockX()
                    || player.getLocation().getBlockY() != start.getBlockY()
                    || player.getLocation().getBlockZ() != start.getBlockZ()) {
                cancelTeleporter(id);
                plugin.messages().actionBar(player, "rush.teleporter-cancelled");
                return;
            }
            left[0] -= 5;
            if (left[0] > 0) {
                plugin.messages().actionBar(player, "rush.teleporter-channel",
                        "seconds", String.format(java.util.Locale.ROOT, "%.1f", left[0] / 20.0));
                return;
            }
            cancelTeleporter(id);
            consume.run();
            plugin.sessions().teleportInternal(player, session.spawn());
            plugin.sounds().play(player, "rush.teleporter");
            plugin.messages().actionBar(player, "rush.teleporter-done");
        }, 5L, 5L);
        teleporterChannels.put(id, task[0]);
    }

    private void cancelTeleporter(UUID player) {
        BukkitTask task = teleporterChannels.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * The tracker: points the player at the nearest living defender bot, or
     * at the nearest standing enemy bed when nothing is alive to hunt. The
     * compass in hand starts pointing there too.
     *
     * @return false when there is nothing to track on this run
     */
    public boolean useTracker(Player player, PracticeSession session) {
        RushState state = session.modeState() instanceof RushState s ? s : null;
        if (state == null) {
            return false;
        }
        Location target = null;
        String label = null;
        var bot = plugin.rushBots().nearestBot(session, player.getLocation());
        if (bot != null) {
            target = bot.entity().getLocation();
            label = me.beekrbonkr.practicecore.mode.RushMode.prettyTeam(bot.team());
        } else {
            double best = Double.MAX_VALUE;
            for (RushState.TargetBed bed : state.enemyBeds()) {
                if (state.isBedBroken(bed.team())) {
                    continue;
                }
                double distance = bed.head().distanceSquared(player.getLocation());
                if (distance < best) {
                    best = distance;
                    target = bed.head();
                    label = me.beekrbonkr.practicecore.mode.RushMode.prettyTeam(bed.team());
                }
            }
        }
        if (target == null) {
            return false;
        }
        player.setCompassTarget(target);
        plugin.messages().actionBar(player, "rush.tracker",
                "team", label,
                "distance", String.valueOf(
                        (int) Math.round(target.distance(player.getLocation()))));
        plugin.sounds().play(player, "rush.tracker");
        return true;
    }

    /**
     * The TNT sheep: waddles toward the nearest enemy defender (or straight
     * ahead with nobody to hunt) and detonates when the fuse runs out. The
     * explosion goes through the same listener as TNT and fireballs, so it
     * breaks placed blocks and defenses, never the map.
     */
    public void spawnTntSheep(Player player, PracticeSession session) {
        Location loc = player.getLocation().add(player.getLocation()
                .getDirection().setY(0).normalize().multiply(1.2));
        if (!session.containsBlock(loc)) {
            loc = player.getLocation();
        }
        int fuse = plugin.pcConfig().rushTntSheepFuseTicks();
        double power = plugin.pcConfig().rushTntSheepPower();
        double speed = plugin.pcConfig().rushTntSheepSpeed();
        org.bukkit.entity.Sheep sheep = loc.getWorld().spawn(loc,
                org.bukkit.entity.Sheep.class, s -> {
                    s.setColor(org.bukkit.DyeColor.RED);
                    s.setGlowing(true);
                    s.setPersistent(false);
                    s.setRemoveWhenFarAway(false);
                });
        Bukkit.getMobGoals().removeAllGoals(sheep);
        plugin.sounds().playAt(loc, "rush.tnt-sheep");
        BukkitTask[] walker = new BukkitTask[1];
        long spawnedAt = sheep.getTicksLived();
        walker[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sheep.isValid() || plugin.sessions().get(player.getUniqueId()) != session) {
                sheep.remove();
                walker[0].cancel();
                return;
            }
            if (sheep.getTicksLived() - spawnedAt >= fuse) {
                walker[0].cancel();
                Location at = sheep.getLocation();
                sheep.remove();
                at.getWorld().createExplosion(player, at, (float) power, false, true);
                return;
            }
            var bot = plugin.rushBots().nearestBot(session, sheep.getLocation());
            if (bot != null) {
                sheep.getPathfinder().moveTo(bot.entity(), speed);
            } else if (!sheep.getPathfinder().hasPath()) {
                Location ahead = sheep.getLocation().add(
                        player.getLocation().getDirection().setY(0).normalize().multiply(6));
                sheep.getPathfinder().moveTo(ahead, speed);
            }
        }, 4L, 4L);
    }

    /**
     * The guard dog: a wolf loyal to the player. Vanilla tame behavior does
     * the rest — it follows, retaliates for its owner, and joins any fight
     * the owner starts; the sweep below also points it at nearby defenders
     * on its own. Wiped by the arena reset like every other entity.
     */
    public void spawnGuardDog(Player player, PracticeSession session) {
        org.bukkit.entity.Wolf wolf = player.getWorld().spawn(player.getLocation(),
                org.bukkit.entity.Wolf.class, w -> {
                    w.setOwner(player);
                    w.setTamed(true);
                    w.setPersistent(false);
                    w.setRemoveWhenFarAway(false);
                    w.setCanPickupItems(false);
                });
        plugin.sounds().playAt(player.getLocation(), "rush.guard-dog");
        BukkitTask[] sweep = new BukkitTask[1];
        sweep[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!wolf.isValid() || plugin.sessions().get(player.getUniqueId()) != session) {
                sweep[0].cancel();
                return;
            }
            if (wolf.getTarget() == null || !wolf.getTarget().isValid()) {
                var bot = plugin.rushBots().nearestBot(session, wolf.getLocation());
                if (bot != null && bot.entity().getLocation()
                        .distanceSquared(wolf.getLocation()) < 12 * 12) {
                    wolf.setTarget(bot.entity());
                }
            }
        }, 20L, 20L);
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
            villager.setProfession(dealerProfession());
        });
    }

    /**
     * The villager profession dealers wear. Resolved through the registry
     * rather than the enum, so a profession added by a later release works and
     * an unknown name degrades to a plain villager instead of throwing.
     */
    private Villager.Profession dealerProfession() {
        String name = plugin.pcConfig().rushDealerProfession();
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey
                .fromString(name.trim().toLowerCase(java.util.Locale.ROOT));
        Villager.Profession profession = key == null ? null
                : org.bukkit.Registry.VILLAGER_PROFESSION.get(key);
        if (profession == null) {
            plugin.getLogger().warning("config.yml: rush.dealer-profession '" + name
                    + "' is not a villager profession this server knows — using NONE.");
            return Villager.Profession.NONE;
        }
        return profession;
    }

    public boolean isDealer(Entity entity) {
        return entity instanceof Villager villager
                && villager.getPersistentDataContainer().has(dealerKey, PersistentDataType.BYTE);
    }

    /** Snapshot of the MBedwars shop; its config is static at runtime, so one walk serves every open. */
    private RushShopData shopCache;
    /** Per-player quick-buy pins, mirrored from the MBedwars profile. */
    private final java.util.Map<UUID, List<String>> quickBuy = new java.util.HashMap<>();

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
            primeQuickBuy(player);
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
        plugin.sounds().play(player, "rush.shop-open");
        new me.beekrbonkr.practicecore.gui.RushShopMenu(plugin, player, shop).open();
    }

    /**
     * Makes sure the player's MBedwars quick-buy pins are in the local
     * mirror. Usually they are cached and land synchronously; a cold profile
     * loads async and refreshes any shop menu still open when it arrives.
     */
    private void primeQuickBuy(Player player) {
        if (quickBuy.containsKey(player.getUniqueId())) {
            return;
        }
        List<String> cached = MBedwarsHook.quickBuyIds(player);
        if (cached != null) {
            quickBuy.put(player.getUniqueId(), new ArrayList<>(cached));
            return;
        }
        MBedwarsHook.loadQuickBuy(plugin, player, ids -> {
            quickBuy.put(player.getUniqueId(), new ArrayList<>(ids));
            if (player.isOnline() && player.getOpenInventory().getTopInventory()
                    .getHolder() instanceof me.beekrbonkr.practicecore.gui.RushShopMenu menu) {
                menu.refresh();
            }
        });
    }

    /**
     * The player's quick-buy pins, positional (null = empty slot). Empty
     * while the MBedwars profile is still loading. Copied null-tolerantly —
     * List.copyOf refuses the nulls that ARE the empty slots.
     */
    public List<String> quickBuyIds(UUID player) {
        List<String> ids = quickBuy.get(player);
        return ids == null ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(ids));
    }

    /**
     * Pins an item into the first free quick-buy slot, both here and in the
     * player's MBedwars profile.
     *
     * @return false when every slot is taken or the profile is not loaded yet
     */
    /** The Hypixel-style quick-buy grid: 3 rows of 7. */
    private static final int QUICK_BUY_SLOTS = 21;

    public boolean pinQuickBuy(Player player, String itemId) {
        List<String> ids = quickBuy.get(player.getUniqueId());
        if (ids == null || ids.contains(itemId)) {
            return false;
        }
        int free = ids.indexOf(null);
        if (free >= 0) {
            ids.set(free, itemId);
        } else if (ids.size() < QUICK_BUY_SLOTS) {
            // A fresh profile hands back a short (even empty) array — grow it.
            ids.add(itemId);
        } else {
            return false;
        }
        writeQuickBuy(player, ids);
        return true;
    }

    /** Unpins an item from the quick-buy page, mirrored back to MBedwars. */
    public boolean unpinQuickBuy(Player player, String itemId) {
        List<String> ids = quickBuy.get(player.getUniqueId());
        if (ids == null || !ids.contains(itemId)) {
            return false;
        }
        ids.set(ids.indexOf(itemId), null);
        writeQuickBuy(player, ids);
        return true;
    }

    public boolean isQuickBuyPinned(UUID player, String itemId) {
        List<String> ids = quickBuy.get(player);
        return ids != null && ids.contains(itemId);
    }

    private void writeQuickBuy(Player player, List<String> ids) {
        try {
            MBedwarsHook.saveQuickBuy(player, ids);
        } catch (LinkageError e) {
            plugin.getLogger().severe("Could not write quick-buy pins to MBedwars: " + e);
        }
    }

    public void forgetQuickBuy(UUID player) {
        quickBuy.remove(player);
    }

    // ----------------------------------------------------- generator ticking

    public void startTask() {
        long period = plugin.pcConfig().rushGeneratorTickPeriod();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, period, period);
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
        for (BukkitTask channel : List.copyOf(teleporterChannels.values())) {
            channel.cancel();
        }
        teleporterChannels.clear();
        shopCache = null; // /practice reload may follow an MBedwars shop edit
    }

    private void tickAll() {
        int period = plugin.pcConfig().rushGeneratorTickPeriod();
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
                generator.countdown -= period;
                if (generator.countdown > 0) {
                    continue;
                }
                generator.countdown = generator.intervalTicks;
                if (nearbyDrops(generator.dropSpot(), generator.drops())
                        < plugin.pcConfig().rushGeneratorItemCap()) {
                    dropTracked(generator.dropSpot(), new ItemStack(generator.drops()),
                            generator.type(), false);
                }
            }
        }
    }

    private int nearbyDrops(Location spot, Material material) {
        double radius = plugin.pcConfig().rushGeneratorCapRadius();
        double radiusSq = radius * radius;
        int total = 0;
        for (Entity entity : spot.getWorld().getNearbyEntities(spot, radius, radius, radius)) {
            if (entity instanceof Item item && item.getItemStack().getType() == material
                    && spot.distanceSquared(item.getLocation()) <= radiusSq) {
                total += item.getItemStack().getAmount();
            }
        }
        return total;
    }
}
