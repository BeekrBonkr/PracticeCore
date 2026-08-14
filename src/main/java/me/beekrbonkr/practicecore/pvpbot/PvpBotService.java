package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.PvpBotMode;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The PvP sparring partner: lifecycle (spawn, gear, stock resets), the
 * per-tick combat brain, and the consumable refill that keeps kits stocked.
 *
 * The bot is a plain husk with its vanilla goals stripped; everything it does
 * — strafing out of the player's crosshair, spacing, spam-clicking at the
 * configured CPS, jump-crits, W-tap shoves, rod casts, bow shots and 1.8
 * sword blocks — is driven from {@link #tick} using only Paper API. Combat
 * *mechanics* (attack cooldowns, knockback shaping) are deliberately left to
 * the server's own plugins (OldCombatMechanics, vanilla-sword-blocking).
 */
public final class PvpBotService {

    /** Ticks of top-up cadence for kit consumables. */
    private static final int REFILL_TICKS = 60;
    /** Ticks between health-name refreshes over the bot's head. */
    private static final int NAME_TICKS = 5;

    private final PracticeCorePlugin plugin;
    /** Bot entities; value = owning player's UUID. */
    private final NamespacedKey botKey;
    /** Packet-level player-model disguise; null without ProtocolLib. */
    private PlayerDisguise disguise;
    private BukkitTask task;
    private int refillCountdown = REFILL_TICKS;

    public PvpBotService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.botKey = new NamespacedKey(plugin, "pvpbot-bot");
        try {
            if (PlayerDisguise.available()) {
                disguise = new PlayerDisguise(plugin);
            }
        } catch (LinkageError e) {
            // An incompatible ProtocolLib build — the husk fallback carries on.
            plugin.getLogger().severe("ProtocolLib hook failed; the PvP bot stays a husk: " + e);
        }
    }

    // ------------------------------------------------------------ lifecycle

    public void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
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

    /**
     * A joiner missed the profile broadcasts of every bot already fighting —
     * without them the disguised bots render as nothing at all (spectators
     * were the visible symptom). Replay the profiles before their client can
     * start tracking any bot entity.
     */
    public void handleJoin(Player player) {
        if (disguise != null && disguise.active()) {
            disguise.sendProfilesTo(player);
        }
    }

    public static BotFight fightOf(PracticeSession session) {
        return session != null && session.mode() instanceof PvpBotMode
                && session.modeState() instanceof BotFight fight ? fight : null;
    }

    /** Builds the fight for a fresh READY session and spawns the bot. */
    public void beginFight(Player player, PracticeSession session) {
        BotFight fight = new BotFight();
        fight.settings = BotSettings.load(plugin.stats(), session.playerId());
        fight.playerSpawn = session.spawn().clone();
        fight.botSpawn = resolveBotSpawn(session);
        fight.resetStock();
        session.setModeState(fight);
        spawnBot(session, fight);
        syncSwordComponents(player);
    }

    public void despawn(BotFight fight) {
        if (fight == null) {
            return;
        }
        if (fight.bot != null) {
            if (disguise != null) {
                disguise.remove(fight.bot);
            }
            fight.bot.remove();
            fight.bot = null;
        }
        if (fight.nameTag != null) {
            fight.nameTag.remove();
            fight.nameTag = null;
        }
    }

    /**
     * The bot's spawn: the arena's {@code settings.pvpbot.bot-spawn} offset
     * when the admin marked one, otherwise a few blocks ahead of the player
     * spawn, facing back at it.
     */
    private Location resolveBotSpawn(PracticeSession session) {
        Location origin = session.origin();
        ConfigurationSection cfg = session.template().settingsSection()
                .getConfigurationSection("pvpbot.bot-spawn");
        if (cfg != null) {
            return new Location(origin.getWorld(),
                    origin.getX() + cfg.getDouble("x"),
                    origin.getY() + cfg.getDouble("y"),
                    origin.getZ() + cfg.getDouble("z"),
                    (float) cfg.getDouble("yaw"), 0);
        }
        Location spawn = session.spawn().clone();
        Vector ahead = spawn.getDirection();
        ahead.setY(0);
        if (ahead.lengthSquared() < 0.01) {
            ahead = new Vector(0, 0, 1);
        }
        Location loc = spawn.clone().add(ahead.normalize().multiply(5));
        if (!session.containsBlock(loc)) {
            loc = spawn.clone();
        }
        loc.setYaw(spawn.getYaw() + 180);
        loc.setPitch(0);
        return loc;
    }

    private void spawnBot(PracticeSession session, BotFight fight) {
        Location loc = fight.botSpawn;
        Player owner = Bukkit.getPlayer(session.playerId());
        fight.bot = loc.getWorld().spawn(loc, Husk.class, husk -> {
            husk.getPersistentDataContainer().set(botKey, PersistentDataType.STRING,
                    session.playerId().toString());
            husk.setAdult();
            husk.setPersistent(true);
            husk.setRemoveWhenFarAway(false);
            husk.setCanPickupItems(false);
            husk.setSilent(true); // groans would give away a "player" opponent
            // A held sword adds its attribute on top; base 1 lands the total
            // near a real player's sword damage instead of a zombie's maul.
            AttributeInstance attack = husk.getAttribute(attackDamageAttribute());
            if (attack != null) {
                attack.setBaseValue(1.0);
            }
            // Zombies roll a little random knockback resistance — zero it so
            // hits launch the bot exactly like a player and combos connect.
            AttributeInstance knockback = husk.getAttribute(knockbackResistanceAttribute());
            if (knockback != null) {
                knockback.setBaseValue(0.0);
            }
            // A zombie's ~35-block follow range also caps how far the Paper
            // pathfinder will compute a path — too short for a big arena. A
            // long leash lets the bot spot and chase the player from anywhere.
            AttributeInstance follow = husk.getAttribute(followRangeAttribute());
            if (follow != null) {
                follow.setBaseValue(128.0);
            }
            // The disguise must be registered before the entity tracker sends
            // the spawn packet, i.e. inside the spawn consumer.
            if (disguise != null && disguise.active() && owner != null) {
                disguise.apply(husk, owner);
            } else {
                // No player model available — at least match a player's height
                // (husks are 1.95 tall, players 1.8).
                AttributeInstance scale = husk.getAttribute(scaleAttribute());
                if (scale != null) {
                    scale.setBaseValue(0.925);
                }
            }
        });
        // No vanilla brain: targeting, wandering and door-smashing all gone.
        // Movement is velocity/pathfinder-driven from tick().
        Bukkit.getMobGoals().removeAllGoals(fight.bot);
        equipBot(fight);
        // Name and health float above the bot as a display entity — a player
        // model shows its profile name, not a mob custom name, so the tag has
        // to be its own entity either way. It follows by teleport each tick
        // rather than riding as a passenger: mount packets around the
        // disguise's rewritten spawn render unreliably on the client, which
        // left the health bar invisible.
        spawnNameTag(fight);
    }

    private void spawnNameTag(BotFight fight) {
        Location loc = tagLocation(fight.bot);
        fight.nameTag = loc.getWorld().spawn(loc,
                org.bukkit.entity.TextDisplay.class, tag -> {
            tag.text(botName(fight.bot.getHealth()));
            tag.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            tag.setDefaultBackground(false);
            tag.setBackgroundColor(org.bukkit.Color.fromARGB(64, 0, 0, 0));
            tag.setShadowed(true);
            tag.setPersistent(false);
            tag.setTeleportDuration(2); // glide between per-tick follows
        });
    }

    /** Where the floating tag sits: where a nameplate would (the real one is hidden). */
    private static Location tagLocation(Husk bot) {
        return bot.getLocation().add(0, bot.getHeight() + 0.35, 0);
    }

    /** Dresses the bot per the settings (kit mirror or gear-tier override). */
    public void equipBot(BotFight fight) {
        if (fight.bot == null) {
            return;
        }
        ItemStack[] gear = fight.settings.botGear(); // helmet,chest,legs,boots,sword
        EntityEquipment equipment = fight.bot.getEquipment();
        equipment.setHelmet(copy(gear[0]));
        equipment.setChestplate(copy(gear[1]));
        equipment.setLeggings(copy(gear[2]));
        equipment.setBoots(copy(gear[3]));
        equipment.setItemInMainHand(copy(gear[4]));
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);
        equipment.setItemInMainHandDropChance(0);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private void equipSword(BotFight fight) {
        if (fight.bot != null) {
            fight.bot.getEquipment().setItemInMainHand(copy(fight.settings.botGear()[4]));
        }
    }

    // ------------------------------------------------------- identification

    public boolean isBot(Entity entity) {
        return entity instanceof Husk husk
                && husk.getPersistentDataContainer().has(botKey, PersistentDataType.STRING);
    }

    /** The owning player's UUID, or null for foreign entities. */
    public UUID ownerOf(Entity entity) {
        if (!(entity instanceof Husk husk)) {
            return null;
        }
        String raw = husk.getPersistentDataContainer().get(botKey, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether this damage event is the sanctioned spar combat the practice
     * world's blanket damage ban must let through: the session's own bot
     * attacking, or one of its projectiles landing.
     */
    public boolean allowsDamage(PracticeSession session, EntityDamageEvent event) {
        BotFight fight = fightOf(session);
        if (fight == null || fight.bot == null
                || !(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager.equals(fight.bot)) {
            return true;
        }
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter
                && shooter.equals(fight.bot);
    }

    // ------------------------------------------------------------ settings

    /**
     * Re-reads settings after the GUI closed and applies what changed: a new
     * kit re-deals the loadout, a new gear choice re-dresses the bot. The AI
     * knobs simply take effect on the next tick.
     */
    public void applySettings(Player player, PracticeSession session, BotFight fight) {
        BotSettings fresh = BotSettings.load(plugin.stats(), session.playerId());
        boolean kitChanged = fresh.kit() != fight.settings.kit();
        boolean gearChanged = kitChanged || fresh.gear() != fight.settings.gear();
        if (kitChanged) {
            captureLayout(player, fight); // the outgoing kit's arrangement
        }
        fight.settings = fresh;
        if (kitChanged) {
            plugin.sessions().regiveKit(player, session);
            syncSwordComponents(player);
        }
        if (gearChanged) {
            equipBot(fight);
        }
    }

    // ----------------------------------------------------------- kit layout

    /** The kit-layout storage key for one preset: shared across every arena. */
    public static String layoutKey(PvpKit kit) {
        return "pvpbot-" + kit.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Wherever the current kit's items sit right now is how the player wants
     * them — remembered per preset and re-applied by arrangeKit on every deal.
     * Armor slots are excluded; they always go back on the body.
     */
    public void captureLayout(Player player, BotFight fight) {
        if (player == null || !player.isOnline() || fight == null || fight.settings == null) {
            return;
        }
        var kitTypes = new java.util.HashSet<Material>();
        boolean kitHasWool = false;
        for (ItemStack item : fight.settings.kit().kit().values()) {
            kitTypes.add(item.getType());
            kitHasWool |= item.getType().name().endsWith("_WOOL");
        }
        java.util.Map<Integer, String> layout = new java.util.LinkedHashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < Math.min(contents.length, 36); slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }
            Material material = item.getType();
            // Recolored kit wool is stored under the kit's own wool so the
            // saved layout matches arrangeKit's pre-recolor materials.
            if (kitHasWool && material.name().endsWith("_WOOL")) {
                material = Material.WHITE_WOOL;
            }
            if (kitTypes.contains(material)) {
                layout.put(slot, material.name());
            }
        }
        if (!layout.isEmpty()) {
            plugin.stats().saveKitLayout(player.getUniqueId(),
                    layoutKey(fight.settings.kit()), layout);
        }
    }

    /**
     * The vanilla-sword-blocking plugin stamps its blocking component onto
     * swords when it sees a held-item change — an event a freshly dealt kit
     * never fires. A synthetic same-slot event nudges it into re-scanning the
     * inventory so kit swords can block immediately.
     */
    public void syncSwordComponents(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("vanilla-sword-blocking")) {
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        Bukkit.getPluginManager().callEvent(
                new org.bukkit.event.player.PlayerItemHeldEvent(player, slot, slot));
    }

    // ---------------------------------------------------------- stock flow

    /** The player would have died — no death screen, just a lost stock. */
    public void playerDied(Player player, PracticeSession session, BotFight fight) {
        fight.deaths++;
        plugin.messages().title(player, "pvpbot.title.death", "pvpbot.title.death-sub",
                "kills", String.valueOf(fight.kills),
                "deaths", String.valueOf(fight.deaths));
        plugin.messages().send(player, "pvpbot.chat.bot-killed-player", "hearts",
                hearts(fight.bot != null && fight.bot.isValid() ? fight.bot.getHealth() : 0));
        if (plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 0.7f, 1.0f);
        }
        resetStock(player, session, fight, true);
    }

    /** The bot dropped — a kill on the board and a fresh stock. */
    public void botDied(Player player, PracticeSession session, BotFight fight) {
        fight.kills++;
        plugin.messages().title(player, "pvpbot.title.kill", "pvpbot.title.kill-sub",
                "kills", String.valueOf(fight.kills),
                "deaths", String.valueOf(fight.deaths));
        plugin.messages().send(player, "pvpbot.chat.player-killed-bot",
                "hearts", hearts(player.getHealth()));
        if (plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
        // The winner keeps their ground: health and kit refresh in place, only
        // the bot goes back to its spawn.
        resetStock(player, session, fight, false);
    }

    private void resetStock(Player player, PracticeSession session, BotFight fight,
                            boolean respawnPlayer) {
        captureLayout(player, fight); // keep mid-fight rearrangements
        fight.resetStock();
        session.tracker().revertAll(); // block kits: the arena resets each stock
        if (respawnPlayer) {
            plugin.sessions().teleportInternal(player, fight.playerSpawn);
            if (plugin.pcConfig().sounds()) {
                player.playSound(fight.playerSpawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            }
        }
        AttributeInstance maxHealth = player.getAttribute(PlayerSnapshot.maxHealthAttribute());
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.setFallDistance(0); // no teleport on a bot kill to clear it
        player.setArrowsInBody(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        plugin.settings().applyToSession(player); // night vision survives the wipe
        plugin.sessions().regiveKit(player, session);
        syncSwordComponents(player);
        if (fight.bot != null && fight.bot.isValid()) {
            fight.bot.teleport(fight.botSpawn);
            AttributeInstance botMax = fight.bot.getAttribute(PlayerSnapshot.maxHealthAttribute());
            fight.bot.setHealth(botMax != null ? botMax.getValue() : 20.0);
            fight.bot.setFireTicks(0);
            fight.bot.setVelocity(new Vector(0, 0, 0));
            equipBot(fight);
            if (fight.nameTag != null && fight.nameTag.isValid()) {
                fight.nameTag.teleport(tagLocation(fight.bot));
            }
        }
    }

    // ---------------------------------------------------------- tick driver

    private void tickAll() {
        boolean refill = --refillCountdown <= 0;
        if (refill) {
            refillCountdown = REFILL_TICKS;
        }
        for (PracticeSession session : plugin.sessions().all()) {
            BotFight fight = fightOf(session);
            if (fight == null) {
                continue;
            }
            SessionState state = session.state();
            if (state != SessionState.READY && state != SessionState.ACTIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (refill) {
                topUp(player, fight);
            }
            tick(player, session, fight);
        }
    }

    /** Keeps kit consumables in stock and clears drained bottles and bowls. */
    private void topUp(Player player, BotFight fight) {
        for (Map.Entry<ItemStack, Integer> entry : fight.settings.kit().refills().entrySet()) {
            // Recolor first: a wool kit's refill must count and hand out the
            // player's chosen wool color, not pile white on top of it.
            ItemStack template = plugin.settings()
                    .recolor(player.getUniqueId(), entry.getKey().clone());
            Material material = template.getType();
            int have = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    have += item.getAmount();
                }
            }
            int missing = entry.getValue() - have;
            while (missing > 0) {
                int size = Math.min(missing, template.getMaxStackSize());
                player.getInventory().addItem(template.asQuantity(size));
                missing -= size;
            }
        }
        player.getInventory().remove(Material.GLASS_BOTTLE);
        player.getInventory().remove(Material.BOWL);
    }

    // ------------------------------------------------------------ the brain

    private void tick(Player player, PracticeSession session, BotFight fight) {
        if (fight.bot == null || !fight.bot.isValid()) {
            // Something unexpected removed the entity — bring the fight back.
            spawnBot(session, fight);
            if (fight.bot == null) {
                return;
            }
        }
        Husk bot = fight.bot;
        // Ring-out: the bot went over the edge, same rule as the player.
        if (bot.getLocation().getY()
                < session.bounds().getMinY() + plugin.pcConfig().failYOffset()) {
            botDied(player, session, fight);
            return;
        }

        // The floating tag doubles as the health bar over the bot's head: it
        // trails the bot every tick, its text refreshing on a slower beat.
        // Whatever removed it (arena wipe, /kill, a plugin sweep), it must
        // come back — a fight without the health bar is flying blind.
        if (fight.nameTag == null || !fight.nameTag.isValid()) {
            spawnNameTag(fight);
        }
        fight.nameTag.teleport(tagLocation(bot));
        if (--fight.nameTicks <= 0) {
            fight.nameTicks = NAME_TICKS;
            fight.nameTag.text(botName(bot.getHealth()));
        }

        // Hitstun: a bot that just got hit rides the knockback like a real
        // player instead of instantly overriding its own velocity — this is
        // what makes combos possible at all. It only suspends steering:
        // like a real comboed player the bot keeps clicking back, and a long
        // combo (classically: pinned against a wall) triggers a sideways
        // escape leap the first time it touches ground.
        if (fight.hitstunTicks > 0) {
            fight.hitstunTicks--;
            bot.getPathfinder().stopPathfinding();
            if (fight.attackCooldown > 0) {
                fight.attackCooldown--;
            }
            if (!fight.paused && fight.graceTicks == 0) {
                bot.lookAt(player);
                BotSettings settings = fight.settings;
                if (fight.attackCooldown == 0 && bot.getEyeLocation()
                        .distance(player.getEyeLocation()) <= settings.reach().blocks()) {
                    if (settings.cerebral() && immune(player)) {
                        // Save the real click for the tick the window opens.
                        if (chance(0.3)) {
                            bot.swingMainHand();
                        }
                    } else {
                        fight.attackCooldown =
                                settings.cps().intervalTicks() + (chance(0.4) ? 1 : 0);
                        if (chance(settings.accuracy().chance())) {
                            strike(bot, player);
                        } else {
                            bot.swingMainHand();
                        }
                    }
                }
                // An unfair bot slips combos a hit earlier and more reliably —
                // chains still land, but never carry it across the arena.
                double escapeChance = settings.suffer() ? 0.9 : settings.unfair() ? 0.8
                        : settings.evasiveness().ordinal()
                                >= BotSettings.Evasiveness.EXTREME.ordinal() ? 0.65 : 0.4;
                if (fight.combo >= (settings.unfair() ? 2 : 3)
                        && bot.isOnGround() && chance(escapeChance)) {
                    Vector along = player.getLocation().toVector()
                            .subtract(bot.getLocation().toVector());
                    along.setY(0);
                    Vector escape = perpendicular(along)
                            .multiply(fight.strafeSign).multiply(0.5);
                    // Never leap out of a combo straight over the rim.
                    escape = steerInside(bot.getLocation(), escape, session.bounds());
                    bot.setVelocity(escape.setY(0.42));
                    fight.hitstunTicks = 0;
                    // Suffer doesn't just escape the combo — it turns the
                    // slip into an immediate counter-offensive.
                    if (settings.suffer()) {
                        fight.stance = BotFight.PRESSURE;
                        fight.stanceTicks = 30;
                    }
                }
            }
            fight.lastX = bot.getLocation().getX();
            fight.lastZ = bot.getLocation().getZ();
            return;
        }

        if (fight.attackCooldown > 0) {
            fight.attackCooldown--;
        }
        if (fight.graceTicks > 0) {
            fight.graceTicks--;
        }
        if (fight.rodCooldown > 0) {
            fight.rodCooldown--;
        }
        if (fight.bowCooldown > 0) {
            fight.bowCooldown--;
        }
        if (fight.blockTicks > 0) {
            fight.blockTicks--;
        }
        if (fight.strafeFlipTicks > 0) {
            fight.strafeFlipTicks--;
        }
        if (fight.recentHitsWindow > 0 && --fight.recentHitsWindow == 0) {
            fight.recentHitsTaken = 0;
        }
        if (fight.heldRevertTicks > 0 && --fight.heldRevertTicks == 0) {
            equipSword(fight);
        }

        // A husk's melee applies the vanilla hunger effect — an artifact the
        // spar doesn't want (hunger itself is frozen anyway; the icon lies).
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.HUNGER)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.HUNGER);
        }

        if (fight.paused) {
            bot.getPathfinder().stopPathfinding();
            bot.lookAt(player);
            return;
        }

        // AFK watch: position, aim and swings all frozen for a few seconds
        // means the player stepped away — the bot stands down, holds fire and
        // waits, resuming (after a short mercy beat) the moment anything
        // stirs. Swings count as activity too, via the animation listener.
        Location playerNow = player.getLocation();
        if (fight.afkLoc == null || fight.afkLoc.getWorld() != playerNow.getWorld()
                || fight.afkLoc.distanceSquared(playerNow) > 0.0004
                || Math.abs(fight.afkLoc.getYaw() - playerNow.getYaw()) > 0.5
                || Math.abs(fight.afkLoc.getPitch() - playerNow.getPitch()) > 0.5) {
            fight.afkLoc = playerNow;
            fight.wake();
        } else if (fight.afkTicks < BotFight.AFK_TICKS) {
            fight.afkTicks++;
        }
        if (fight.afk()) {
            bot.getPathfinder().stopPathfinding();
            bot.lookAt(player);
            return;
        }

        BotSettings s = fight.settings;

        // Perception: the bot acts on where it last *registered* the player,
        // stale by its reaction time on the lower tiers. A cerebral bot both
        // refreshes faster and dead-reckons the picture forward along the
        // player's motion — which reads as prediction: it works off where
        // you're going, not where you were.
        if (--fight.seenIn <= 0 || fight.seenLoc == null) {
            fight.seenIn = s.reactionTicks() + 1;
            fight.seenLoc = player.getLocation();
            fight.seenVel = player.getVelocity().clone().setY(0);
        }
        Location perceived = fight.seenLoc.clone();
        if (s.cerebral()) {
            perceived.add(fight.seenVel.clone().multiply(s.reactionTicks() + 1));
        }
        bot.lookAt(perceived.clone().add(0, player.getEyeHeight(), 0));

        Location botLoc = bot.getLocation();
        Vector toBot = botLoc.toVector().subtract(perceived.toVector());
        toBot.setY(0);
        double dist = Math.max(0.01, toBot.length());
        // Hits stay honest: reach is always measured against the real player.
        double eyeDist = bot.getEyeLocation().distance(player.getEyeLocation());
        // Client-reported ground state — spoofable, but this only feeds the
        // bot's reads, not anything security-relevant.
        boolean playerOnGround = ((Entity) player).isOnGround();

        // Habit reads, decaying slowly: hops and air-swings are patterns, and
        // patterns get punished. (whiffHabit is fed by the swing listener.)
        if (fight.playerWasOnGround && !playerOnGround
                && player.getVelocity().getY() > 0.2) {
            fight.jumpHabit = Math.min(8, fight.jumpHabit + 1);
        }
        fight.playerWasOnGround = playerOnGround;
        fight.jumpHabit *= 0.995;
        fight.whiffHabit *= 0.995;
        if (fight.punishTicks > 0) {
            fight.punishTicks--;
        }
        if (fight.feintCooldown > 0) {
            fight.feintCooldown--;
        }

        // Stance: a slow-cadence read of the whole fight. PRESSURE while a
        // combo is rolling, the player is hurt, or a punish is on; RESET
        // (kite, rod, block) when the bot is badly losing; NEUTRAL otherwise.
        // A kite runs on a budget — a few seconds of space-making, then the
        // bot wheels around and goes all-in instead of backpedaling forever,
        // and a lockout keeps it from turning tail again right away.
        if (fight.resetLockout > 0) {
            fight.resetLockout--;
        }
        if (fight.stance == BotFight.RESET) {
            fight.resetBudget--;
        }
        if (--fight.stanceTicks <= 0) {
            // Unfair re-reads the fight almost twice as often and starts
            // saving itself before it is actually desperate; suffer reads
            // it near-continuously.
            fight.stanceTicks = s.suffer() ? 4 : s.unfair() ? 6 : 10;
            boolean losing = s.cerebral() && bot.getHealth() <= (s.suffer() ? 10 : s.unfair() ? 9 : 7)
                    && player.getHealth() - bot.getHealth() >= (s.suffer() ? 2 : s.unfair() ? 3 : 4);
            if (fight.stance == BotFight.RESET) {
                if (fight.resetBudget <= 0 || !losing) {
                    // Kite spent: wheel around and attack, committed for a
                    // couple of seconds before the next review.
                    fight.stance = BotFight.PRESSURE;
                    fight.stanceTicks = 40;
                    fight.resetLockout = 200 + rnd(200);
                }
            } else if (losing && fight.resetLockout == 0) {
                fight.stance = BotFight.RESET;
                fight.resetBudget = 100 + rnd(100); // 5-10s of running
            } else if (fight.combo >= 2 || player.getHealth() <= 6
                    || fight.punishTicks > 0) {
                fight.stance = BotFight.PRESSURE;
            } else {
                fight.stance = BotFight.NEUTRAL;
            }
        }

        // A jump-crit in progress strikes when its fall window arrives.
        if (fight.critTicks >= 0) {
            fight.critTicks--;
            if (fight.critTicks < 0 && eyeDist <= s.reach().blocks() + 0.5) {
                fight.critBonusNextHit = true;
                strike(bot, player);
            }
        }

        // Ranged options fire from spacing the melee brain won't close fast.
        // A resetting bot leans on them: the rod is its space-maker.
        if (fight.graceTicks == 0 && !fight.blocking()) {
            boolean resetting = fight.stance == BotFight.RESET;
            if (s.rod() && fight.rodCooldown == 0 && dist > (resetting ? 3.0 : 3.5)
                    && dist < 8
                    && chance(resetting ? 0.22 : s.suffer() ? 0.18 : s.unfair() ? 0.12 : 0.06)) {
                castRod(bot, player, fight);
            } else if (s.bow() && fight.bowCooldown == 0 && dist >= (resetting ? 7 : 8)
                    && chance(resetting ? 0.15 : s.suffer() ? 0.18 : s.unfair() ? 0.14 : 0.08)) {
                shootBow(bot, player, fight, s);
            }
        }

        // Spacing target by stance: pressure crowds in, a reset kites far out.
        double gap = s.aggression().gap();
        if (fight.stance == BotFight.PRESSURE) {
            gap = Math.max(1.6, gap - 0.6);
        } else if (fight.stance == BotFight.RESET) {
            gap = 6.5;
        }

        // Feint in progress: back off as if disengaging. A player who takes
        // the bait and charges eats a timed counter and a shove; one who
        // holds their ground just watches the bot circle back in.
        if (fight.feintTicks > 0) {
            fight.feintTicks--;
            bot.getPathfinder().stopPathfinding();
            Vector back = toBot.clone().normalize().multiply(0.22);
            bot.setVelocity(new Vector(back.getX(), bot.getVelocity().getY(), back.getZ()));
            boolean bit = eyeDist <= s.reach().blocks() && fight.attackCooldown == 0
                    && !immune(player)
                    && player.getVelocity().dot(toBot.clone().normalize()) > 0.1;
            if (bit) {
                fight.feintTicks = 0;
                fight.attackCooldown = s.cps().intervalTicks();
                strike(bot, player);
                shove(player, botLoc, session, s, 0.3);
                fight.stance = BotFight.PRESSURE;
                fight.stanceTicks = 20;
            }
            if (fight.feintTicks == 0) {
                fight.feintCooldown = 100 + rnd(60);
            }
            fight.lastX = botLoc.getX();
            fight.lastZ = botLoc.getZ();
            return;
        }
        if (fight.stance == BotFight.NEUTRAL && s.cerebral()
                && s.combos().chance() > 0 && fight.feintCooldown == 0
                && dist > gap - 0.3 && dist < gap + 1.5
                && chance(s.suffer() ? 0.05 : s.unfair() ? 0.03 : 0.012)) {
            fight.feintTicks = 12 + rnd(8);
        }

        // Movement: kite when resetting, pathfind in from far out, strafe
        // once in the fight.
        if (fight.stance == BotFight.RESET) {
            if (--fight.repathTicks <= 0) {
                fight.repathTicks = 4;
                bot.getPathfinder().moveTo(
                        fleeTarget(botLoc, perceived, session.bounds()),
                        s.aggression().speed() * 1.15);
            }
            if (s.block() && !fight.blocking() && dist < 3.2 && chance(0.2)) {
                fight.blockTicks = 10 + rnd(6);
            }
        } else if (dist > gap + 1.2) {
            if (--fight.repathTicks <= 0) {
                fight.repathTicks = s.aggression().ordinal()
                        >= BotSettings.Aggression.FRENZIED.ordinal() ? 3 : 5;
                bot.getPathfinder().moveTo(player,
                        s.aggression().speed() * (fight.blocking() ? 0.5 : 1.0));
            }
            // Sprint-jumping: closing like a player holding W and spamming
            // space — always under frenzy, and under pressure or a punish.
            if ((s.aggression().ordinal() >= BotSettings.Aggression.FRENZIED.ordinal()
                    || fight.stance == BotFight.PRESSURE || fight.punishTicks > 0)
                    && bot.isOnGround() && dist > gap + 2
                    && !fight.blocking() && chance(0.15)) {
                Vector forward = toBot.clone().multiply(-1).normalize();
                bot.setVelocity(bot.getVelocity().add(forward.multiply(0.35)).setY(0.42));
            }
        } else {
            fight.repathTicks = 0;
            bot.getPathfinder().stopPathfinding();
            strafe(bot, player, fight, s, toBot, dist, gap, session.bounds());
        }

        // Whiff-punish: the player has been clicking at air just out of
        // range — lunge through the gap behind a wasted swing, arriving
        // before the next click is loaded.
        if (fight.punishTicks > 0 && bot.isOnGround() && dist > 1.8
                && !fight.blocking() && chance(s.suffer() ? 0.85 : s.unfair() ? 0.7 : 0.5)) {
            Vector lunge = toBot.clone().multiply(-1).normalize();
            bot.setVelocity(bot.getVelocity().add(lunge.multiply(0.4)).setY(0.25));
        }

        // Hop over lips and out of corners when movement stalls.
        if (bot.isOnGround() && dist > s.reach().blocks()
                && Math.abs(botLoc.getX() - fight.lastX) < 0.01
                && Math.abs(botLoc.getZ() - fight.lastZ) < 0.01
                && chance(0.5)) {
            bot.setVelocity(bot.getVelocity().setY(0.42));
        }
        fight.lastX = botLoc.getX();
        fight.lastZ = botLoc.getZ();

        // A jumpy player gets crit-fished: as they rise, the bot leaves the
        // ground too, timed so its falling strike meets them on the way down.
        if (s.cerebral() && fight.jumpHabit >= (s.unfair() ? 2 : 3)
                && !playerOnGround && player.getVelocity().getY() > 0.1
                && fight.critTicks < 0 && bot.isOnGround() && fight.graceTicks == 0
                && !fight.blocking() && dist < s.reach().blocks() + 1.5
                && chance(s.suffer() ? 0.6 : s.unfair() ? 0.45 : 0.3)) {
            bot.setVelocity(bot.getVelocity().setY(0.42));
            fight.critTicks = 6;
        }

        // Melee. A cerebral bot never wastes the click: while the player's
        // immunity window is up it only mimes the clicking, saving the real
        // swing for the exact tick the window expires — the locked-in rhythm
        // of a good 1.8 player. It also takes the free clean hit on a falling
        // player instead of gambling on a whiff or a crit windup.
        boolean mayHit = fight.graceTicks == 0 && !fight.blocking()
                && fight.critTicks < 0 && fight.attackCooldown == 0
                && eyeDist <= s.reach().blocks();
        if (mayHit && s.cerebral() && immune(player)) {
            if (chance(0.3)) {
                bot.swingMainHand(); // keeps the spam-click look
            }
        } else if (mayHit) {
            fight.attackCooldown = s.cps().intervalTicks() + (chance(0.4) ? 1 : 0);
            boolean airPunish = s.cerebral() && !playerOnGround
                    && player.getVelocity().getY() < 0;
            if (!airPunish && !chance(s.accuracy().chance())) {
                bot.swingMainHand(); // a whiff, like a real missed click
            } else if (!airPunish && s.combos().chance() > 0 && bot.isOnGround()
                    && chance(s.combos().chance() * 0.5)) {
                // Jump-crit: leave the ground now, strike mid-fall.
                bot.setVelocity(bot.getVelocity().setY(0.42));
                fight.critTicks = 6;
            } else {
                strike(bot, player);
                // W-tap: the sprint-reset shove right after a clean hit,
                // thrown more freely while pressing an advantage.
                if (chance(s.combos().chance()
                        + (fight.stance == BotFight.PRESSURE ? 0.2 : 0))) {
                    shove(player, botLoc, session, s, 0.22);
                }
            }
        } else if (s.cerebral() && fight.stance == BotFight.NEUTRAL
                && fight.attackCooldown == 0 && eyeDist > s.reach().blocks()
                && eyeDist < s.reach().blocks() + 1
                && chance(s.suffer() ? 0.1 : s.unfair() ? 0.06 : 0.03)) {
            bot.swingMainHand(); // max-range bait click, inviting a counter
        }

        // Getting comboed? Raise the 1.8 sword block for a moment.
        if (s.block() && !fight.blocking() && fight.recentHitsTaken >= 2 && chance(0.15)) {
            fight.blockTicks = 12 + rnd(8);
            fight.recentHitsTaken = 0;
        }
    }

    private void strike(Husk bot, Player player) {
        bot.swingMainHand();
        bot.attack(player);
    }

    /**
     * Strafes out of the player's crosshair: the velocity points away from
     * where the aim ray sits at the bot's distance, so the better the player
     * tracks, the harder the bot works to slip off their cursor. A radial
     * term holds the aggression-defined spacing.
     */
    private void strafe(Husk bot, Player player, BotFight fight, BotSettings s,
                        Vector toBot, double dist, double gap, BoundingBox bounds) {
        Vector look = player.getEyeLocation().getDirection();
        look.setY(0);
        Vector strafeDir;
        double burst = 1.0;
        if (look.lengthSquared() < 0.01) {
            strafeDir = perpendicular(toBot).multiply(fight.strafeSign);
        } else {
            look.normalize();
            Vector away = toBot.clone().subtract(look.multiply(dist));
            away.setY(0);
            if (away.lengthSquared() < 0.09) {
                // Aimed dead-on: dodge to the held side, flipping now and then.
                if (fight.strafeFlipTicks == 0) {
                    if (chance(0.35)) {
                        fight.strafeSign = -fight.strafeSign;
                    }
                    fight.strafeFlipTicks = 20 + rnd(20);
                }
                // A cerebral bot cuts the corner instead: it matches the
                // player's own lateral drift, so orbiting away never works.
                if (s.cerebral() && fight.seenVel != null) {
                    double lateral = fight.seenVel.dot(perpendicular(toBot));
                    if (Math.abs(lateral) > 0.08) {
                        fight.strafeSign = lateral > 0 ? 1 : -1;
                    }
                }
                strafeDir = perpendicular(toBot).multiply(fight.strafeSign);
                // The unfair tiers don't drift off the crosshair — they dart:
                // a burst sidestep the instant the aim settles dead-on.
                if (s.evasiveness() == BotSettings.Evasiveness.SUFFER) {
                    burst = 1.45;
                } else if (s.evasiveness() == BotSettings.Evasiveness.UNFAIR) {
                    burst = 1.3;
                }
            } else {
                strafeDir = away.normalize();
            }
        }
        Vector velocity = strafeDir.multiply(
                s.evasiveness().speed() * burst * (fight.blocking() ? 0.4 : 1.0));
        double closeIn = fight.stance == BotFight.PRESSURE ? 0.14 : 0.08;
        if (dist < gap - 0.4) {
            velocity.add(toBot.clone().normalize().multiply(0.08)); // back off
        } else if (dist > gap + 0.4) {
            velocity.add(toBot.clone().normalize().multiply(-closeIn)); // close in
        }
        // Never strafe over the rim: a drift that would carry the bot off the
        // arena is bent back toward the middle, and the held side flips so
        // the next dodge works with the bend instead of fighting it.
        Vector steered = steerInside(bot.getLocation(), velocity, bounds);
        if (steered != velocity) {
            fight.strafeSign = -fight.strafeSign;
            velocity = steered;
        }
        // An extreme strafer also hops mid-fight the way real 1.8 duellers
        // bounce around — vertical motion the crosshair has to chase too.
        double y = bot.getVelocity().getY();
        if (s.evasiveness().ordinal() >= BotSettings.Evasiveness.EXTREME.ordinal()
                && bot.isOnGround() && !fight.blocking()
                && chance(switch (s.evasiveness()) {
                    case SUFFER -> 0.16;
                    case UNFAIR -> 0.12;
                    default -> 0.06;
                })) {
            y = 0.42;
        }
        bot.setVelocity(new Vector(velocity.getX(), y, velocity.getZ()));
    }

    /** Inside the vanilla half-window where another equal hit does nothing. */
    private static boolean immune(Player player) {
        return player.getNoDamageTicks() > player.getMaximumNoDamageTicks() / 2;
    }

    /**
     * The sprint-reset shove after a clean hit. A cerebral bot leans it
     * toward the arena rim — knockback with a destination, walking the
     * player toward the edge one trade at a time.
     */
    private void shove(Player player, Location botLoc, PracticeSession session,
                       BotSettings s, double strength) {
        Vector shove = player.getLocation().toVector().subtract(botLoc.toVector());
        shove.setY(0);
        if (shove.lengthSquared() < 0.01) {
            return;
        }
        shove.normalize();
        if (s.cerebral()) {
            BoundingBox b = session.bounds();
            Vector outward = new Vector(
                    player.getLocation().getX() - (b.getMinX() + b.getMaxX()) / 2, 0,
                    player.getLocation().getZ() - (b.getMinZ() + b.getMaxZ()) / 2);
            if (outward.lengthSquared() > 0.01) {
                shove.add(outward.normalize()
                        .multiply(s.suffer() ? 0.6 : s.unfair() ? 0.5 : 0.35)).normalize();
            }
        }
        player.setVelocity(player.getVelocity().add(shove.multiply(strength).setY(0.05)));
    }

    /** A kite waypoint: straight away from the player, held inside the rim. */
    private static Location fleeTarget(Location botLoc, Location playerLoc,
                                       BoundingBox bounds) {
        Vector away = botLoc.toVector().subtract(playerLoc.toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        Location target = botLoc.clone().add(away.normalize().multiply(6));
        target.setX(Math.min(bounds.getMaxX() - 2, Math.max(bounds.getMinX() + 2, target.getX())));
        target.setZ(Math.min(bounds.getMaxZ() - 2, Math.max(bounds.getMinZ() + 2, target.getZ())));
        return target;
    }

    /**
     * Bends a horizontal velocity back toward the arena middle when its
     * continuation would carry the entity over the rim. Returns the input
     * object untouched when the move is safe — callers compare identity.
     */
    private static Vector steerInside(Location from, Vector velocity, BoundingBox bounds) {
        double aheadX = from.getX() + velocity.getX() * 6;
        double aheadZ = from.getZ() + velocity.getZ() * 6;
        if (aheadX > bounds.getMinX() + 1.5 && aheadX < bounds.getMaxX() - 1.5
                && aheadZ > bounds.getMinZ() + 1.5 && aheadZ < bounds.getMaxZ() - 1.5) {
            return velocity;
        }
        Vector inward = new Vector(
                (bounds.getMinX() + bounds.getMaxX()) / 2 - from.getX(), 0,
                (bounds.getMinZ() + bounds.getMaxZ()) / 2 - from.getZ());
        return inward.lengthSquared() < 0.01 ? velocity
                : inward.normalize().multiply(velocity.length());
    }

    private static Vector perpendicular(Vector horizontal) {
        Vector perp = new Vector(-horizontal.getZ(), 0, horizontal.getX());
        return perp.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : perp.normalize();
    }

    /**
     * The rod: a short-lived projectile that shoves like a hook — resets the
     * player's combo rhythm at range, the classic 1.8 duel move. The bot
     * flourishes a rod in hand for a few ticks so the cast reads.
     */
    private void castRod(Husk bot, Player player, BotFight fight) {
        fight.rodCooldown = 50 + rnd(30);
        bot.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        fight.heldRevertTicks = 8;
        bot.swingMainHand();
        Vector lead = player.getVelocity().clone().multiply(3);
        Vector direction = player.getEyeLocation().toVector().add(lead)
                .subtract(bot.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        Snowball hook = bot.launchProjectile(Snowball.class, direction.normalize().multiply(1.7));
        hook.setItem(new ItemStack(Material.SNOWBALL));
        if (plugin.pcConfig().sounds()) {
            bot.getWorld().playSound(bot.getLocation(),
                    Sound.ENTITY_FISHING_BOBBER_THROW, 0.6f, 1.0f);
        }
    }

    private void shootBow(Husk bot, Player player, BotFight fight, BotSettings s) {
        fight.bowCooldown = 60 + rnd(30);
        bot.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        fight.heldRevertTicks = 14;
        Vector lead = player.getVelocity().clone().multiply(4);
        Vector direction = player.getEyeLocation().toVector().add(lead)
                .subtract(bot.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        double spread = (1.0 - s.accuracy().chance()) * 0.3;
        direction.normalize().multiply(2.3).add(new Vector(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 0.5,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread));
        Arrow arrow = bot.launchProjectile(Arrow.class, direction);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(false);
        if (plugin.pcConfig().sounds()) {
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 0.7f, 1.1f);
        }
    }

    /** Crit flair for the listener: particles over the player, like a real crit. */
    public void playCritEffect(Player player) {
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0, 1.2, 0), 8, 0.3, 0.3, 0.3, 0.2);
    }

    /**
     * The floating tag text: one line carrying name and health together.
     * The profile nameplate is suppressed by a scoreboard team (see
     * PlayerDisguise), so this display is the bot's only overhead text.
     */
    private net.kyori.adventure.text.Component botName(double health) {
        return plugin.messages().component("pvpbot.bot-tag", "health", hearts(health));
    }

    /** Health points rendered as hearts, one decimal. */
    private static String hearts(double health) {
        return String.format(java.util.Locale.ROOT, "%.1f", health / 2.0);
    }

    /**
     * A DeluxeCombat-style damage indicator: a small floating hologram at the
     * hit spot showing the hearts dealt, drifting upward for a moment before
     * vanishing. Display entities, not items — the practice world stays clean
     * and nothing can be picked up.
     */
    public void spawnDamageIndicator(org.bukkit.entity.LivingEntity victim, double damage) {
        Location loc = victim.getLocation().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8,
                victim.getHeight() * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.3),
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);
        org.bukkit.entity.TextDisplay display = loc.getWorld().spawn(loc,
                org.bukkit.entity.TextDisplay.class, text -> {
                    text.text(plugin.messages().component("pvpbot.damage-indicator",
                            "damage", hearts(damage)));
                    text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    text.setDefaultBackground(false);
                    text.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    text.setShadowed(true);
                    text.setPersistent(false);
                    text.setTeleportDuration(12); // glide, don't jump
                });
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (display.isValid()) {
                display.teleport(loc.clone().add(0, 0.7, 0));
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, display::remove, 16L);
    }

    // -------------------------------------------------------------- helpers

    private static boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static int rnd(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /** Registry-resolved like max health — the enum constants were renamed in 1.21.3. */
    private static Attribute attackDamageAttribute() {
        return attribute("generic.attack_damage", "attack_damage");
    }

    private static Attribute knockbackResistanceAttribute() {
        return attribute("generic.knockback_resistance", "knockback_resistance");
    }

    private static Attribute scaleAttribute() {
        return attribute("generic.scale", "scale");
    }

    private static Attribute followRangeAttribute() {
        return attribute("generic.follow_range", "follow_range");
    }

    private static Attribute attribute(String legacy, String modern) {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacy));
        if (attr == null) {
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modern));
        }
        if (attr == null) {
            throw new IllegalStateException("Attribute missing from the registry: " + modern);
        }
        return attr;
    }
}
