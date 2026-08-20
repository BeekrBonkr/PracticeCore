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
 * configured CPS, jump-crits, W-tap shoves, rod casts, bow shots, 1.8 sword
 * blocks and healing with its kit's own pots, stew and gapples — is driven
 * from {@link #tick} using only Paper API. Combat
 * *mechanics* (attack cooldowns, knockback shaping) are deliberately left to
 * the server's own plugins (OldCombatMechanics, vanilla-sword-blocking).
 */
public final class PvpBotService {

    private final PracticeCorePlugin plugin;
    /** Bot entities; value = owning player's UUID. */
    private final NamespacedKey botKey;
    /** Packet-level player-model disguise; null without ProtocolLib. */
    private PlayerDisguise disguise;
    private BukkitTask task;
    private int refillCountdown = 1;

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

    private BotTuning tuning() {
        return plugin.botTuning();
    }

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

    /**
     * The shared packet-level player-model disguise, or null without
     * ProtocolLib. The rush defender bots ride the same instance, so one
     * packet listener serves every disguised entity.
     */
    public PlayerDisguise disguise() {
        return disguise;
    }

    public static BotFight fightOf(PracticeSession session) {
        return session != null && session.mode() instanceof PvpBotMode
                && session.modeState() instanceof BotFight fight ? fight : null;
    }

    /** Builds the fight for a fresh READY session and spawns the bot. */
    public void beginFight(Player player, PracticeSession session) {
        BotFight fight = new BotFight();
        fight.settings = BotSettings.load(plugin, session.playerId());
        fight.playerSpawn = session.spawn().clone();
        fight.botSpawn = resolveBotSpawn(session);
        fight.resetStock(tuning().stockGraceTicks(),
                tuning().flatTicks("behavior.feint.initial-cooldown", 60));
        session.setModeState(fight);
        // A restart in the middle of a death hold must not carry the corpse's
        // blindness and slowness into the fresh fight.
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
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
        Location loc = spawn.clone().add(ahead.normalize().multiply(tuning().spawnDistance()));
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
                attack.setBaseValue(tuning().baseAttackDamage());
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
                follow.setBaseValue(tuning().followRange());
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
                    scale.setBaseValue(tuning().undisguisedScale());
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
    private Location tagLocation(Husk bot) {
        return bot.getLocation().add(0, bot.getHeight() + tuning().nameTagOffset(), 0);
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
        BotSettings fresh = BotSettings.load(plugin, session.playerId());
        // Compared by id, not identity: a /practice reload rebuilds every kit
        // object, and re-dealing an unchanged loadout mid-fight would rip the
        // player's inventory out from under them.
        boolean kitChanged = !sameKit(fresh.kit(), fight.settings.kit());
        boolean gearChanged = kitChanged || fresh.gear() != fight.settings.gear();
        boolean anyChanged = kitChanged || gearChanged || !sameKnobs(fresh, fight.settings);
        if (kitChanged) {
            captureLayout(player, fight); // the outgoing kit's arrangement
        }
        fight.settings = fresh;
        if (kitChanged) {
            fight.restockConsumables(); // the new kit brings its own supply
            plugin.sessions().regiveKit(player, session);
            syncSwordComponents(player);
        }
        if (gearChanged) {
            equipBot(fight);
        }
        if (anyChanged) {
            // Session stats describe one opponent. The opponent just changed,
            // so the numbers start over — and the player is told why.
            fight.resetSessionStats();
            plugin.messages().send(player, "pvpbot.chat.stats-reset");
        }
    }

    private static boolean sameKit(PvpKit a, PvpKit b) {
        return a == null ? b == null : b != null && a.id().equals(b.id());
    }

    /** Whether every AI knob and arsenal toggle is unchanged. */
    private static boolean sameKnobs(BotSettings a, BotSettings b) {
        return a.evasiveness() == b.evasiveness()
                && a.cps() == b.cps()
                && a.accuracy() == b.accuracy()
                && a.combos() == b.combos()
                && a.reach() == b.reach()
                && a.aggression() == b.aggression()
                && a.rod() == b.rod()
                && a.bow() == b.bow()
                && a.block() == b.block();
    }

    // ----------------------------------------------------------- kit layout

    /** The kit-layout storage key for one preset: shared across every arena. */
    public static String layoutKey(PvpKit kit) {
        return "pvpbot-" + kit.id();
    }

    /**
     * Wherever the current kit's items sit right now is how the player wants
     * them — remembered per preset and re-applied by arrangeKit on every deal.
     * Armor slots are excluded; they always go back on the body.
     */
    public void captureLayout(Player player, BotFight fight) {
        if (player == null || !player.isOnline() || fight == null
                || fight.settings == null || fight.settings.kit() == null) {
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

    /**
     * The player would have died — no death screen, just a lost stock and a
     * 3-second "dead" hold. Both fighters are sent home the moment the death
     * lands: the player waits out the hold at their own spawn, blind and
     * untouchable under the countdown title, and the bot walks off the kill
     * by being put straight back on its mark. The heal, fresh kit and stock
     * reset land when the timer runs out.
     */
    public void playerDied(Player player, PracticeSession session, BotFight fight) {
        if (fight.playerDead()) {
            // Already dead — a stumble mid-hold. Back onto the spawn anchor;
            // the running countdown carries on.
            holdAtAnchor(player, fight);
            return;
        }
        fight.deaths++;
        String botHearts = healthPoints(
                fight.bot != null && fight.bot.isValid() ? fight.bot.getHealth() : 0);
        plugin.messages().send(player, "pvpbot.chat.bot-killed-player", "hearts", botHearts);
        // Spectators get the round's result too — who fell, and how much
        // health the victor had left.
        for (Player watcher : plugin.spectate().watchersOf(session.playerId())) {
            plugin.messages().send(watcher, "pvpbot.chat.spectator-bot-killed",
                    "player", player.getName(), "hearts", botHearts);
        }
        plugin.sounds().play(player, "pvpbot.player-death");
        // Both fighters teleport home immediately — the death is where the
        // round ends, not three seconds later.
        fight.deathAnchor = fight.playerSpawn;
        holdAtAnchor(player, fight);
        sendBotHome(fight);
        int hold = tuning().respawnTicks();
        fight.playerRespawnTicks = hold;
        // A shade longer than the hold so they never flicker out early; the
        // respawn removes them explicitly. Slowness pins the corpse without
        // rubber-banding it — the anchor teleport only has to undo gravity.
        player.addPotionEffect(new PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS,
                hold + 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS,
                hold + 20, 6, false, false));
        sendDeathCountdown(player, secondsLeft(hold));
    }

    /** Puts the (victorious) bot straight back on its spawn mark. */
    private void sendBotHome(BotFight fight) {
        if (fight.bot == null || !fight.bot.isValid()) {
            return;
        }
        fight.bot.getPathfinder().stopPathfinding();
        fight.bot.teleport(fight.botSpawn);
        fight.bot.setVelocity(new Vector(0, 0, 0));
        fight.bot.setFallDistance(0);
        if (fight.nameTag != null && fight.nameTag.isValid()) {
            fight.nameTag.teleport(tagLocation(fight.bot));
        }
    }

    /** Pins the corpse: no drifting, no falling, no walking it off the arena. */
    private void holdAtAnchor(Player player, BotFight fight) {
        Location anchor = fight.deathAnchor != null ? fight.deathAnchor : fight.playerSpawn;
        plugin.sessions().teleportInternal(player, anchor);
        player.setVelocity(new Vector(0, 0, 0));
    }

    private void sendDeathCountdown(Player player, int seconds) {
        plugin.messages().title(player, "pvpbot.title.death", "pvpbot.title.respawn-sub",
                "seconds", String.valueOf(seconds));
    }

    /**
     * Whole seconds left on a hold, rounded up — a countdown must never show
     * "0" while the player is still pinned, which a hold that is not a round
     * number of seconds would otherwise do.
     */
    private static int secondsLeft(int ticks) {
        return Math.max(1, (ticks + 19) / 20);
    }

    /**
     * The bot dropped — a kill on the board, and its body is gone for the
     * 3-second respawn timer, counted down above the player's hotbar. The
     * winner is sent back to their own spawn immediately, so a kill can never
     * become a spawn camp.
     */
    public void botDied(Player player, PracticeSession session, BotFight fight) {
        if (fight.botDead()) {
            return;
        }
        fight.kills++;
        String playerHearts = healthPoints(player.getHealth());
        plugin.messages().title(player, "pvpbot.title.kill", "pvpbot.title.kill-sub",
                "kills", String.valueOf(fight.kills),
                "deaths", String.valueOf(fight.deaths));
        plugin.messages().send(player, "pvpbot.chat.player-killed-bot",
                "hearts", playerHearts);
        for (Player watcher : plugin.spectate().watchersOf(session.playerId())) {
            plugin.messages().send(watcher, "pvpbot.chat.spectator-player-killed",
                    "player", player.getName(), "hearts", playerHearts);
        }
        plugin.sounds().play(player, "pvpbot.bot-death");
        // Health, kit and spawn teleport land now; the bot's body disappears
        // until the timer brings it back at its own mark.
        resetStock(player, session, fight, true);
        despawn(fight);
        fight.botRespawnTicks = tuning().respawnTicks();
        plugin.messages().actionBar(player, "pvpbot.respawn-bar",
                "seconds", String.valueOf(secondsLeft(fight.botRespawnTicks)));
    }

    private void resetStock(Player player, PracticeSession session, BotFight fight,
                            boolean respawnPlayer) {
        captureLayout(player, fight); // keep mid-fight rearrangements
        fight.resetStock(tuning().stockGraceTicks(),
                tuning().flatTicks("behavior.feint.initial-cooldown", 60));
        session.tracker().revertAll(); // block kits: the arena resets each stock
        if (respawnPlayer) {
            plugin.sessions().teleportInternal(player, fight.playerSpawn);
            plugin.sounds().play(player, "pvpbot.player-respawn", fight.playerSpawn);
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
            fight.bot.setPose(org.bukkit.entity.Pose.STANDING, false); // uncrouch
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
            refillCountdown = tuning().refillTicks();
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
        if (fight.settings.kit() == null) {
            return;
        }
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
        // Drained bottles and bowls pile up over an endless spar; they are
        // never part of a kit, so they simply go.
        for (Material empty : tuning().discardedEmpties()) {
            player.getInventory().remove(empty);
        }
        // The bot's own supply refreshes on the same beat the player's does.
        fight.restockConsumables();
    }

    // ------------------------------------------------------------ the brain

    private void tick(Player player, PracticeSession session, BotFight fight) {
        // Respawn holds run before anything else: a dead player counts down
        // under the death title, a dead bot counts down above the hotbar.
        // The two can overlap (a blind edge-walk while the bot is down), so
        // both timers tick independently.
        boolean playerHeld = fight.playerDead();
        if (playerHeld) {
            fight.playerRespawnTicks--;
            if (fight.playerRespawnTicks == 0) {
                // Alive again, and only now: the stock reset — spawn teleport,
                // heal, fresh kit, the bot back on its own mark — lands as the
                // countdown ends, followed by a beat of grace.
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                fight.deathAnchor = null;
                resetStock(player, session, fight, true);
                plugin.messages().title(player, "pvpbot.title.respawned",
                        "pvpbot.title.respawned-sub");
                fight.graceTicks = Math.max(fight.graceTicks, tuning().shortGraceTicks());
            } else {
                // The body doesn't wander: gravity, leftover momentum and a
                // blind player's own input are all undone at the anchor.
                if (fight.deathAnchor != null
                        && player.getWorld() == fight.deathAnchor.getWorld()
                        && player.getLocation().distanceSquared(fight.deathAnchor) > 0.04) {
                    holdAtAnchor(player, fight);
                }
                if (fight.playerRespawnTicks % 20 == 0) {
                    sendDeathCountdown(player, secondsLeft(fight.playerRespawnTicks));
                }
            }
        }
        if (fight.botDead()) {
            fight.botRespawnTicks--;
            if (fight.botRespawnTicks == 0) {
                // The winner already went home the moment the kill landed —
                // only the body needs to come back.
                spawnBot(session, fight);
                fight.graceTicks = Math.max(fight.graceTicks, tuning().shortGraceTicks());
                plugin.sounds().play(player, "pvpbot.bot-respawn", fight.botSpawn);
            } else if (fight.botRespawnTicks % 20 == 0) {
                plugin.messages().actionBar(player, "pvpbot.respawn-bar",
                        "seconds", String.valueOf(secondsLeft(fight.botRespawnTicks)));
            }
            return; // no body to drive yet
        }
        if (playerHeld) {
            // The bot waits out the player's death hold at its spawn.
            if (fight.bot != null && fight.bot.isValid()) {
                fight.bot.getPathfinder().stopPathfinding();
                if (fight.nameTag != null && fight.nameTag.isValid()) {
                    fight.nameTag.teleport(tagLocation(fight.bot));
                }
            }
            return;
        }
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
            fight.nameTicks = tuning().nameRefreshTicks();
            fight.nameTag.text(botName(bot.getHealth()));
        }

        // A consumable in progress keeps ticking through everything — real
        // players finish their pot mid-combo too. The heal lands at zero.
        if (fight.consuming() && --fight.consumeTicks <= 0) {
            finishConsume(bot, fight);
        }

        // Crouch bookkeeping runs even through hitstun — riding knockback is
        // exactly when the reduced-knockback crouch earns its keep.
        if (fight.crouchCooldown > 0) {
            fight.crouchCooldown--;
        }
        if (fight.crouchTicks > 0 && --fight.crouchTicks == 0) {
            endCrouch(fight);
        }
        // Same reasoning for the s-tap window and the combo chase: both are
        // at their most useful during the knockback they are answering.
        if (fight.stapTicks > 0) {
            fight.stapTicks--;
        }
        if (fight.stapCooldown > 0) {
            fight.stapCooldown--;
        }
        if (fight.comboFollowTicks > 0) {
            fight.comboFollowTicks--;
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
            if (!fight.paused && fight.graceTicks == 0 && !fight.consuming()) {
                bot.lookAt(player);
                BotSettings settings = fight.settings;
                if (fight.attackCooldown == 0 && bot.getEyeLocation()
                        .distance(player.getEyeLocation()) <= settings.reachBlocks()) {
                    if (settings.cerebral() && immune(player)) {
                        // Save the real click for the tick the window opens.
                        if (settings.chance("mime-swing-chance", 0.3)) {
                            bot.swingMainHand();
                        }
                    } else {
                        fight.attackCooldown = settings.attackIntervalTicks()
                                + (settings.chance("attack-jitter-chance", 0.4) ? 1 : 0);
                        fight.botAttacks++;
                        if (chance(settings.hitChance())) {
                            strike(bot, player);
                        } else {
                            bot.swingMainHand();
                        }
                    }
                }
                // An unfair bot slips combos a hit earlier and more reliably —
                // chains still land, but never carry it across the arena. A
                // very evasive bot manages it without the thinking layers.
                double escapeChance = settings.knob("combo-escape.chance", 0.4);
                if (tuning().evasiveAtLeast("behavior.combo-escape.evasive-from",
                        settings.evasiveness(), BotSettings.Evasiveness.EXTREME)) {
                    escapeChance = Math.max(escapeChance,
                            tuning().flat("behavior.combo-escape.evasive-chance", 0.65));
                }
                if (fight.combo >= settings.knobTicks("combo-escape.after-hits", 3)
                        && bot.isOnGround() && chance(escapeChance)) {
                    Vector along = player.getLocation().toVector()
                            .subtract(bot.getLocation().toVector());
                    along.setY(0);
                    Vector escape = perpendicular(along).multiply(fight.strafeSign)
                            .multiply(settings.knob("combo-escape.leap-strength", 0.5));
                    // Never leap out of a combo straight over the rim.
                    escape = steerInside(bot.getLocation(), escape, session.bounds(), settings);
                    endCrouch(fight); // can't leap out of a crouch
                    bot.setVelocity(escape.setY(tuning().jumpVelocity()));
                    fight.hitstunTicks = 0;
                    // Suffer doesn't just escape the combo — it turns the
                    // slip into an immediate counter-offensive.
                    if (settings.suffer()) {
                        fight.stance = BotFight.PRESSURE;
                        fight.stanceTicks = settings.knobTicks("combo-escape.counter-ticks", 30);
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
        if (fight.consumeCooldown > 0) {
            fight.consumeCooldown--;
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
        double moveThreshold = tuning().afkMoveThreshold();
        double lookThreshold = tuning().afkLookThreshold();
        int afkTicks = tuning().afkTicks();
        if (fight.afkLoc == null || fight.afkLoc.getWorld() != playerNow.getWorld()
                || fight.afkLoc.distanceSquared(playerNow) > moveThreshold
                || Math.abs(fight.afkLoc.getYaw() - playerNow.getYaw()) > lookThreshold
                || Math.abs(fight.afkLoc.getPitch() - playerNow.getPitch()) > lookThreshold) {
            fight.afkLoc = playerNow;
            fight.wake(tuning().wakeGraceTicks(), afkTicks);
        } else if (fight.afkTicks < afkTicks) {
            fight.afkTicks++;
        }
        if (fight.afk(afkTicks)) {
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
            fight.jumpHabit = Math.min(s.knob("whiff-punish.habit-cap", 8), fight.jumpHabit + 1);
        }
        fight.playerWasOnGround = playerOnGround;
        double decay = s.knob("habit-decay", 0.995);
        fight.jumpHabit *= decay;
        fight.whiffHabit *= decay;
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
            fight.stanceTicks = s.knobTicks("stance.review-ticks", 10);
            boolean losing = s.cerebral()
                    && bot.getHealth() <= s.knob("stance.reset-health", 7)
                    && player.getHealth() - bot.getHealth() >= s.knob("stance.reset-deficit", 4);
            if (fight.stance == BotFight.RESET) {
                if (fight.resetBudget <= 0 || !losing) {
                    // Kite spent: wheel around and attack, committed for a
                    // couple of seconds before the next review.
                    fight.stance = BotFight.PRESSURE;
                    fight.stanceTicks = s.knobTicks("stance.commit-ticks", 40);
                    fight.resetLockout = s.knobRoll("stance.reset-lockout", 200, 400);
                }
            } else if (losing && fight.resetLockout == 0) {
                fight.stance = BotFight.RESET;
                fight.resetBudget = s.knobRoll("stance.reset-budget", 100, 200);
            } else if (fight.combo >= s.knobTicks("stance.pressure-combo", 2)
                    || player.getHealth() <= s.knob("stance.pressure-health", 6)
                    || fight.punishTicks > 0) {
                fight.stance = BotFight.PRESSURE;
            } else {
                fight.stance = BotFight.NEUTRAL;
            }
        }

        // A jump-crit in progress strikes when its fall window arrives.
        if (fight.critTicks >= 0) {
            fight.critTicks--;
            if (fight.critTicks < 0
                    && eyeDist <= s.reachBlocks() + s.knob("jump-crit.window", 0.5)) {
                fight.critBonusNextHit = true;
                fight.botAttacks++;
                strike(bot, player);
            }
        }

        // Hurt and holding a kit with heals in it? Use them — pots splash
        // fast, stew is a quick slurp. The gapple is different: a long chew
        // a player never starts in someone's face, so it runs through the
        // retreat dance in tickGappleRetreat instead of firing point-blank.
        if (fight.gappleRetreatTicks > 0) {
            tickGappleRetreat(bot, player, fight, s, dist);
        } else if (tuning().consumablesEnabled() && !fight.consuming()
                && fight.consumeCooldown == 0 && fight.graceTicks == 0
                && !fight.blocking() && !fight.crouching() && fight.critTicks < 0
                && bot.getHealth() <= s.knob("consumables.heal-at", 12)
                && s.chance("consumables.chance", 0.15)) {
            tryConsume(bot, player, fight, s, dist);
        }

        // Ranged options fire from spacing the melee brain won't close fast.
        // A resetting bot leans on them: the rod is its space-maker — and the
        // gapple retreat borrows the same trick to buy room to chew. The kit
        // decides what's available (a BuildUHC bot always rods and shoots);
        // the settings toggles force the option onto kits that lack the item.
        if (fight.graceTicks == 0 && !fight.blocking() && !fight.consuming()) {
            boolean resetting = fight.stance == BotFight.RESET
                    || fight.gappleRetreatTicks > 0;
            if (s.usesRod() && fight.rodCooldown == 0
                    && dist > s.knob(resetting ? "rod.reset-min-distance" : "rod.min-distance",
                            resetting ? 3.0 : 3.5)
                    && dist < s.knob("rod.max-distance", 8)
                    && chance(resetting ? s.knob("rod.reset-chance", 0.22)
                            : s.knob("rod.chance", 0.06))) {
                castRod(bot, player, fight, s);
            } else if (s.usesBow() && fight.bowCooldown == 0
                    && dist >= s.knob(resetting ? "bow.reset-min-distance" : "bow.min-distance",
                            resetting ? 7 : 8)
                    && chance(resetting ? s.knob("bow.reset-chance", 0.15)
                            : s.knob("bow.chance", 0.08))) {
                shootBow(bot, player, fight, s);
            }
        }

        // Spacing target by stance: pressure crowds in, a reset kites far out.
        double gap = s.spacingGap();
        if (fight.stance == BotFight.PRESSURE) {
            gap = Math.max(s.knob("spacing.pressure-min", 1.6),
                    gap - s.knob("spacing.pressure-close", 0.6));
        } else if (fight.stance == BotFight.RESET) {
            gap = s.knob("spacing.reset-gap", 6.5);
        }
        // Reach discipline, the duellist tiers' spacing game: instead of
        // hugging the player they hold the tip of their own reach — close
        // enough to land, far enough that a three-block swing has to be timed
        // — and slide back out of it while the player's immunity window burns,
        // so a wasted click is thrown at empty air. Demon plays a tighter,
        // more forgiving version of the same idea.
        //
        // The step-out ends a few ticks early on purpose: the bot has to be
        // back at the tip of its reach by the time the window reopens, or the
        // dance turns into standing off at arm's length doing nothing.
        if (s.duellist() && fight.stance != BotFight.RESET) {
            gap = Math.max(gap, s.reachBlocks() - s.knob("spacing.reach-margin", 0.7));
            if (immunityLeft(player) > s.knob("spacing.step-out-window", 5)) {
                gap += s.knob("spacing.step-out", 0.35);
            }
        }

        // Feint in progress: back off as if disengaging. A player who takes
        // the bait and charges eats a timed counter and a shove; one who
        // holds their ground just watches the bot circle back in.
        if (fight.feintTicks > 0) {
            fight.feintTicks--;
            bot.getPathfinder().stopPathfinding();
            Vector back = toBot.clone().normalize().multiply(s.knob("feint.backpedal", 0.22));
            bot.setVelocity(new Vector(back.getX(), bot.getVelocity().getY(), back.getZ()));
            boolean bit = eyeDist <= s.reachBlocks() && fight.attackCooldown == 0
                    && !immune(player)
                    && player.getVelocity().dot(toBot.clone().normalize())
                            > s.knob("feint.bite-approach", 0.1);
            if (bit) {
                fight.feintTicks = 0;
                fight.attackCooldown = s.attackIntervalTicks();
                strike(bot, player);
                shove(player, botLoc, session, s, s.knob("feint.counter-shove", 0.3));
                fight.stance = BotFight.PRESSURE;
                fight.stanceTicks = s.knobTicks("feint.pressure-ticks", 20);
            }
            if (fight.feintTicks == 0) {
                fight.feintCooldown = s.knobRoll("feint.cooldown", 100, 160);
            }
            fight.lastX = botLoc.getX();
            fight.lastZ = botLoc.getZ();
            return;
        }
        if (fight.stance == BotFight.NEUTRAL && s.cerebral() && !fight.consuming()
                && fight.gappleRetreatTicks == 0
                && s.comboChance() > 0 && fight.feintCooldown == 0
                && dist > gap - s.knob("feint.window-near", 0.3)
                && dist < gap + s.knob("feint.window-far", 1.5)
                && s.chance("feint.chance", 0.012)) {
            fight.feintTicks = s.knobRoll("feint.duration", 12, 19);
        }

        // Movement: kite when resetting — or when backing off to chew a
        // gapple, which is the same run with a different payoff (the chew
        // itself keeps fleeing too, at eating speed, so the bot heals moving
        // away from you rather than strolling into your sword) — pathfind in
        // from far out, strafe once in the fight.
        boolean fleeing = fight.stance == BotFight.RESET
                || fight.gappleRetreatTicks > 0
                || (fight.consuming() && fight.consumeItem == Material.GOLDEN_APPLE);
        if (fleeing) {
            if (--fight.repathTicks <= 0) {
                fight.repathTicks = s.knobTicks("reset.repath-ticks", 4);
                bot.getPathfinder().moveTo(
                        fleeTarget(botLoc, perceived, session.bounds(), s),
                        s.approachSpeed() * s.knob("reset.speed-multiplier", 1.15)
                                * (fight.consuming()
                                        ? s.knob("approach.blocking-speed", 0.5) : 1.0));
            }
            if (s.block() && !fight.blocking() && !fight.consuming()
                    && dist < s.knob("reset.block-distance", 3.2)
                    && s.chance("reset.block-chance", 0.2)) {
                fight.blockTicks = s.knobRoll("reset.block-ticks", 10, 15);
            }
        } else if (fight.comboFollowTicks > 0 && dist > gap && !fight.blocking()) {
            // Chasing its own knockback. A combo only continues if the bot
            // travels with the hit it just landed instead of waiting for the
            // player to drift back into range — the follow runs at a sprint,
            // never faster, and gives up when the window closes.
            fight.repathTicks = 0;
            bot.getPathfinder().stopPathfinding();
            Vector chase = toBot.clone().multiply(-1).normalize()
                    .multiply(s.knob("combo-follow.speed", 0.27));
            chase = steerInside(botLoc, chase, session.bounds(), s);
            double chaseY = bot.getVelocity().getY();
            if (bot.isOnGround() && !fight.crouching()
                    && dist > gap + s.knob("combo-follow.jump-gap", 1.5)
                    && s.chance("combo-follow.jump-chance", 0.2)) {
                chaseY = tuning().jumpVelocity(); // sprint-jump to keep up with the knockback
            }
            bot.setVelocity(new Vector(chase.getX(), chaseY, chase.getZ()));
        } else if (dist > gap + s.knob("approach.engage-margin", 1.2)) {
            if (--fight.repathTicks <= 0) {
                fight.repathTicks = tuning().aggressiveAtLeast("behavior.approach.fast-from",
                        s.aggression(), BotSettings.Aggression.FRENZIED)
                        ? s.knobTicks("approach.repath-ticks-fast", 3)
                        : s.knobTicks("approach.repath-ticks", 5);
                bot.getPathfinder().moveTo(player, s.approachSpeed()
                        * (fight.blocking() || fight.consuming()
                                ? s.knob("approach.blocking-speed", 0.5) : 1.0));
            }
            // Nobody good walks a straight line in: the duellist tiers weave
            // across the approach so closing the distance stays a moving shot.
            // The side has to alternate on its own here — the strafe branch is
            // what normally flips it, and a lateral push that never changes
            // sign curves the whole approach into an orbit the bot never
            // closes.
            if (s.duellist() && !fight.blocking() && !fight.crouching()
                    && !fight.consuming()) {
                if (fight.strafeFlipTicks == 0) {
                    fight.strafeSign = -fight.strafeSign;
                    fight.strafeFlipTicks = s.knobRoll("approach.weave-flip", 10, 17);
                }
                bot.setVelocity(bot.getVelocity().add(perpendicular(toBot)
                        .multiply(fight.strafeSign * s.knob("approach.weave-strength", 0.07))));
            }
            // Sprint-jumping: closing like a player holding W and spamming
            // space — always under frenzy, and under pressure or a punish.
            if ((tuning().aggressiveAtLeast("behavior.approach.fast-from", s.aggression(),
                        BotSettings.Aggression.FRENZIED)
                    || fight.stance == BotFight.PRESSURE || fight.punishTicks > 0)
                    && bot.isOnGround() && dist > gap + s.knob("approach.sprint-jump.gap", 2)
                    && !fight.blocking() && !fight.crouching() && !fight.consuming()
                    && s.chance("approach.sprint-jump.chance", 0.15)) {
                Vector forward = toBot.clone().multiply(-1).normalize();
                bot.setVelocity(bot.getVelocity()
                        .add(forward.multiply(s.knob("approach.sprint-jump.strength", 0.35)))
                        .setY(tuning().jumpVelocity()));
            }
        } else {
            fight.repathTicks = 0;
            bot.getPathfinder().stopPathfinding();
            strafe(bot, player, fight, s, toBot, dist, gap, session.bounds());
        }

        // Whiff-punish: the player has been clicking at air just out of
        // range — lunge through the gap behind a wasted swing, arriving
        // before the next click is loaded.
        if (fight.punishTicks > 0 && bot.isOnGround()
                && dist > s.knob("whiff-punish.min-distance", 1.8)
                && !fight.blocking() && !fight.crouching() && !fight.consuming()
                && s.chance("whiff-punish.chance", 0.5)) {
            Vector lunge = toBot.clone().multiply(-1).normalize();
            bot.setVelocity(bot.getVelocity()
                    .add(lunge.multiply(s.knob("whiff-punish.strength", 0.4)))
                    .setY(s.knob("whiff-punish.lift", 0.25)));
        }

        // Hop over lips and out of corners when movement stalls.
        double stallThreshold = s.knob("unstick.threshold", 0.01);
        if (bot.isOnGround() && dist > s.reachBlocks() && !fight.crouching()
                && Math.abs(botLoc.getX() - fight.lastX) < stallThreshold
                && Math.abs(botLoc.getZ() - fight.lastZ) < stallThreshold
                && s.chance("unstick.chance", 0.5)) {
            bot.setVelocity(bot.getVelocity().setY(tuning().jumpVelocity()));
        }
        fight.lastX = botLoc.getX();
        fight.lastZ = botLoc.getZ();

        // Edge play, defensive half: with its back to the rim and the player
        // right on top of it, a thinking bot crouches — the listener scales
        // the knockback it takes while crouched, trading strafe speed for
        // ground it cannot afford to give. Gated by the same cerebral tier
        // that unlocks the offensive edge-shoves; the unfair tiers read the
        // danger faster.
        if (s.cerebral() && !fight.crouching() && fight.crouchCooldown == 0
                && !fight.blocking() && !fight.consuming()
                && dist < s.knob("edge-crouch.distance", 4)
                && rimDistance(botLoc, session.bounds()) < s.knob("edge-crouch.rim", 2.5)
                && s.chance("edge-crouch.chance", 0.2)) {
            startCrouch(fight, s.knobRoll("edge-crouch.ticks", 15, 24));
        }

        // A jumpy player gets crit-fished: as they rise, the bot leaves the
        // ground too, timed so its falling strike meets them on the way down.
        if (s.cerebral() && fight.jumpHabit >= s.knob("crit-fish.habit", 3)
                && !playerOnGround && player.getVelocity().getY() > 0.1
                && fight.critTicks < 0 && bot.isOnGround() && fight.graceTicks == 0
                && !fight.blocking() && !fight.crouching() && !fight.consuming()
                && dist < s.reachBlocks() + s.knob("crit-fish.range-bonus", 1.5)
                && s.chance("crit-fish.chance", 0.3)) {
            bot.setVelocity(bot.getVelocity().setY(tuning().jumpVelocity()));
            fight.critTicks = s.knobTicks("jump-crit.fall-ticks", 6);
        }

        // Melee. A cerebral bot never wastes the click: while the player's
        // immunity window is up it only mimes the clicking, saving the real
        // swing for the exact tick the window expires — the locked-in rhythm
        // of a good 1.8 player. It also takes the free clean hit on a falling
        // player instead of gambling on a whiff or a crit windup.
        // Block-hitting: the 1.8 trick of swinging with the sword still up, so
        // the answer to your hit lands at half. Demon and up only, and only
        // when the player armed the bot with sword blocking at all.
        boolean blockHits = s.duellist() && s.block();
        boolean mayHit = fight.graceTicks == 0 && (blockHits || !fight.blocking())
                && !fight.consuming()
                && fight.critTicks < 0 && fight.attackCooldown == 0
                && eyeDist <= s.reachBlocks();
        if (mayHit && s.cerebral() && immune(player)) {
            if (s.chance("mime-swing-chance", 0.3)) {
                bot.swingMainHand(); // keeps the spam-click look
            }
        } else if (mayHit) {
            fight.attackCooldown = s.attackIntervalTicks()
                    + (s.chance("attack-jitter-chance", 0.4) ? 1 : 0);
            fight.botAttacks++;
            boolean airPunish = s.cerebral() && !playerOnGround
                    && player.getVelocity().getY() < 0;
            if (!airPunish && !chance(s.hitChance())) {
                bot.swingMainHand(); // a whiff, like a real missed click
            } else if (!airPunish && s.comboChance() > 0 && bot.isOnGround()
                    && !fight.crouching() && !fight.blocking()
                    && chance(s.comboChance() * s.knob("jump-crit.combo-factor", 0.5))) {
                // Jump-crit: leave the ground now, strike mid-fall.
                bot.setVelocity(bot.getVelocity().setY(tuning().jumpVelocity()));
                fight.critTicks = s.knobTicks("jump-crit.fall-ticks", 6);
            } else {
                strike(bot, player);
                // W-tap: the sprint-reset shove right after a clean hit,
                // thrown more freely while pressing an advantage.
                if (chance(s.comboChance() + (fight.stance == BotFight.PRESSURE
                        ? s.knob("wtap.pressure-bonus", 0.2) : 0))) {
                    shove(player, botLoc, session, s, s.knob("wtap.strength", 0.22));
                }
                // ...then straight back behind the sword: the player's answer
                // to a clean hit arrives inside exactly this window.
                if (blockHits && !fight.blocking() && s.chance("block-hit.chance", 0.25)) {
                    fight.blockTicks = s.knobRoll("block-hit.ticks", 6, 9);
                }
            }
        } else if (s.cerebral() && fight.stance == BotFight.NEUTRAL
                && !fight.consuming()
                && fight.attackCooldown == 0 && eyeDist > s.reachBlocks()
                && eyeDist < s.reachBlocks() + s.knob("bait-click.range", 1)
                && s.chance("bait-click.chance", 0.03)) {
            bot.swingMainHand(); // max-range bait click, inviting a counter
        }

        // Getting comboed? Raise the 1.8 sword block for a moment.
        if (s.block() && !fight.blocking() && !fight.consuming()
                && fight.recentHitsTaken >= s.knobTicks("defensive-block.after-hits", 2)
                && s.chance("defensive-block.chance", 0.15)) {
            fight.blockTicks = s.knobRoll("defensive-block.ticks", 12, 19);
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
                // Aimed dead-on: dodge to the held side, flipping now and
                // then. The unfair tiers juke instead of drifting — the side
                // changes on a short, unpredictable beat, so a crosshair that
                // has learned the rhythm is already wrong.
                if (fight.strafeFlipTicks == 0) {
                    if (s.chance("strafe.flip-chance", 0.35)) {
                        fight.strafeSign = -fight.strafeSign;
                    }
                    fight.strafeFlipTicks = s.knobRoll("strafe.flip-interval", 20, 39);
                }
                // A cerebral bot cuts the corner instead: it matches the
                // player's own lateral drift, so orbiting away never works.
                if (s.cerebral() && fight.seenVel != null) {
                    double lateral = fight.seenVel.dot(perpendicular(toBot));
                    if (Math.abs(lateral) > s.knob("strafe.lateral-threshold", 0.08)) {
                        fight.strafeSign = lateral > 0 ? 1 : -1;
                    }
                }
                strafeDir = perpendicular(toBot).multiply(fight.strafeSign);
                // The unfair tiers don't drift off the crosshair — they dart:
                // a burst sidestep the instant the aim settles dead-on.
                burst = tuning().byEvasiveness("behavior.strafe.burst", s.evasiveness(), 1.0);
            } else {
                strafeDir = away.normalize();
            }
        }
        // Anti-orbit. Dodging away from the crosshair points the same way
        // around the player every tick, and a player who chases with their aim
        // keeps it pointing that way — left alone it reads as a bot on rails,
        // circling forever. So the circle is timed: once it has held one
        // direction for a few seconds the bot cuts back through the aim line,
        // which is exactly how a real strafe reverses.
        int sense = strafeDir.dot(perpendicular(toBot)) >= 0 ? 1 : -1;
        if (fight.cutbackTicks > 0) {
            fight.cutbackTicks--;
            strafeDir = perpendicular(toBot).multiply(-fight.orbitSense);
        } else if (sense == fight.orbitSense) {
            if (++fight.orbitTicks > s.knobTicks("strafe.orbit-patience", 34)) {
                fight.cutbackTicks = s.knobRoll("strafe.cutback", 8, 15);
                fight.orbitTicks = 0;
                fight.strafeSign = -sense;
            }
        } else {
            fight.orbitSense = sense;
            fight.orbitTicks = 0;
        }
        // The full-speed juke is honest against a drawn bow — sidestepping an
        // arrow is exactly what strafing is for. In sword range the same dart
        // reads as teleporty, so without a projectile threat the dodge runs
        // damped. The unfair layer keeps every bit of it either way (the
        // melee-dodge-factor knob scales to 1.0 there): unnatural movement is
        // that tier's whole identity.
        double dodge = s.strafeSpeed();
        if (!projectileThreat(player)) {
            dodge *= s.knob("strafe.melee-dodge-factor", 0.7);
            if (!s.unfair()) {
                burst = 1.0; // the dead-on dart is a projectile answer too
            }
        }
        Vector velocity = strafeDir.multiply(dodge * burst
                * (fight.blocking() ? s.knob("strafe.blocking-speed", 0.4)
                        : fight.crouching() ? s.knob("strafe.crouching-speed", 0.6)
                        : fight.consuming() ? s.knob("strafe.eating-speed", 0.5) : 1.0));
        // Radial correction toward the spacing target, proportional to how far
        // off it is. A flat nudge was too slow for the reach dance: a bot that
        // steps out of range while the immunity window burns has to be able to
        // step back in before the window reopens, or it just circles at arm's
        // length never throwing a punch.
        double error = dist - gap;
        if (Math.abs(error) > s.knob("strafe.correction.deadzone", 0.25)) {
            double urgency = fight.stance == BotFight.PRESSURE
                    ? s.knob("strafe.correction.pressure-urgency", 0.22)
                    : s.knob("strafe.correction.urgency", 0.16);
            double pull = Math.min(s.knob("strafe.correction.max-pull", 0.28),
                    Math.abs(error) * urgency + s.knob("strafe.correction.base-pull", 0.06));
            velocity.add(toBot.clone().normalize().multiply(error > 0 ? -pull : pull));
        }
        // Never strafe over the rim: a drift that would carry the bot off the
        // arena is bent back toward the middle, and the held side flips so
        // the next dodge works with the bend instead of fighting it.
        Vector steered = steerInside(bot.getLocation(), velocity, bounds, s);
        if (steered != velocity) {
            fight.strafeSign = -fight.strafeSign;
            velocity = steered;
        }
        // An extreme strafer also hops mid-fight the way real 1.8 duellers
        // bounce around — vertical motion the crosshair has to chase too.
        double y = bot.getVelocity().getY();
        if (tuning().evasiveAtLeast("behavior.strafe.hop.from", s.evasiveness(),
                    BotSettings.Evasiveness.EXTREME)
                && bot.isOnGround() && !fight.blocking() && !fight.crouching()
                && !fight.consuming()
                && chance(tuning().byEvasiveness("behavior.strafe.hop.chance",
                        s.evasiveness(), 0.06))) {
            y = tuning().jumpVelocity();
        }
        bot.setVelocity(new Vector(velocity.getX(), y, velocity.getZ()));
    }

    /** Inside the vanilla half-window where another equal hit does nothing. */
    private static boolean immune(Player player) {
        return player.getNoDamageTicks() > player.getMaximumNoDamageTicks() / 2;
    }

    /** Ticks until that window reopens; zero or less means hittable now. */
    private static int immunityLeft(Player player) {
        return player.getNoDamageTicks() - player.getMaximumNoDamageTicks() / 2;
    }

    // ------------------------------------------------------------- crouching

    /**
     * Drops the bot into a sneak for a moment: while crouched the listener
     * scales incoming knockback by {@link #CROUCH_KNOCKBACK_FACTOR}, the bot
     * strafes slower and stays off its jumps — the same anchor-in-place trade
     * a real player makes holding shift at the rim. The pose is fixed so the
     * player model visibly sneaks.
     */
    public void startCrouch(BotFight fight, int ticks) {
        fight.crouchTicks = ticks;
        fight.crouchCooldown = tuning().roll("behavior.crouch.cooldown", 60, 99);
        if (fight.bot != null && fight.bot.isValid()) {
            fight.bot.setPose(org.bukkit.entity.Pose.SNEAKING, true);
        }
    }

    private static void endCrouch(BotFight fight) {
        fight.crouchTicks = 0;
        if (fight.bot != null && fight.bot.isValid()) {
            fight.bot.setPose(org.bukkit.entity.Pose.STANDING, false);
        }
    }

    /** Horizontal distance from the arena rim, whichever edge is nearest. */
    private static double rimDistance(Location loc, BoundingBox bounds) {
        return Math.min(
                Math.min(loc.getX() - bounds.getMinX(), bounds.getMaxX() - loc.getX()),
                Math.min(loc.getZ() - bounds.getMinZ(), bounds.getMaxZ() - loc.getZ()));
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
                        .multiply(s.knob("shove.rim-lean", 0.35))).normalize();
            }
        }
        player.setVelocity(player.getVelocity()
                .add(shove.multiply(strength).setY(s.knob("shove.lift", 0.05))));
    }

    /** A kite waypoint: straight away from the player, held inside the rim. */
    private static Location fleeTarget(Location botLoc, Location playerLoc,
                                       BoundingBox bounds, BotSettings s) {
        Vector away = botLoc.toVector().subtract(playerLoc.toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        double margin = s.knob("reset.flee-margin", 2);
        Location target = botLoc.clone()
                .add(away.normalize().multiply(s.knob("reset.flee-distance", 6)));
        target.setX(Math.min(bounds.getMaxX() - margin,
                Math.max(bounds.getMinX() + margin, target.getX())));
        target.setZ(Math.min(bounds.getMaxZ() - margin,
                Math.max(bounds.getMinZ() + margin, target.getZ())));
        return target;
    }

    /**
     * Bends a horizontal velocity back toward the arena middle when its
     * continuation would carry the entity over the rim. Returns the input
     * object untouched when the move is safe — callers compare identity.
     */
    private static Vector steerInside(Location from, Vector velocity, BoundingBox bounds,
                                      BotSettings s) {
        double lookahead = s.knob("steer.lookahead", 6);
        double margin = s.knob("steer.margin", 1.5);
        double aheadX = from.getX() + velocity.getX() * lookahead;
        double aheadZ = from.getZ() + velocity.getZ() * lookahead;
        if (aheadX > bounds.getMinX() + margin && aheadX < bounds.getMaxX() - margin
                && aheadZ > bounds.getMinZ() + margin && aheadZ < bounds.getMaxZ() - margin) {
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
    private void castRod(Husk bot, Player player, BotFight fight, BotSettings s) {
        fight.rodCooldown = s.knobRoll("rod.cooldown", 50, 79);
        bot.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        fight.heldRevertTicks = s.knobTicks("rod.flourish-ticks", 8);
        bot.swingMainHand();
        Vector lead = player.getVelocity().clone().multiply(s.knob("rod.lead", 3));
        Vector direction = player.getEyeLocation().toVector().add(lead)
                .subtract(bot.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        Snowball hook = bot.launchProjectile(Snowball.class,
                direction.normalize().multiply(s.knob("rod.speed", 1.7)));
        hook.setItem(new ItemStack(Material.SNOWBALL));
        plugin.sounds().playAt(bot.getLocation(), "pvpbot.rod-cast");
    }

    private void shootBow(Husk bot, Player player, BotFight fight, BotSettings s) {
        fight.bowCooldown = s.knobRoll("bow.cooldown", 60, 89);
        bot.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        fight.heldRevertTicks = s.knobTicks("bow.flourish-ticks", 14);
        Vector lead = player.getVelocity().clone().multiply(s.knob("bow.lead", 4));
        Vector direction = player.getEyeLocation().toVector().add(lead)
                .subtract(bot.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        double spread = (1.0 - s.hitChance()) * s.knob("bow.spread", 0.3);
        direction.normalize().multiply(s.knob("bow.speed", 2.3)).add(new Vector(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread * 0.5,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread));
        Arrow arrow = bot.launchProjectile(Arrow.class, direction);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(false);
        plugin.sounds().playAt(bot.getLocation(), "pvpbot.bow-shot");
    }

    // ---------------------------------------------------------- consumables

    /**
     * The bot reaching for its kit's own heals, mirroring what the player
     * has: a splash pot is a quick throw, stew a quick slurp — both fine in
     * close. The golden apple is a long committed chew a player never starts
     * in someone's face, so it goes through a break-away first: run, open the
     * gap, chew on the move, then wheel straight back in. While anything is
     * in progress the bot holds the item, stops attacking and moves at eating
     * speed; the effect lands when the countdown runs out. Stock mirrors the
     * kit's numbers and refreshes on the same beat as the player's refill.
     */
    private void tryConsume(Husk bot, Player player, BotFight fight, BotSettings s,
                            double dist) {
        if (fight.botPots > 0) {
            fight.botPots--;
            startConsume(bot, fight, Material.SPLASH_POTION,
                    s.knobTicks("consumables.pot.ticks", 8));
        } else if (fight.botStews > 0 && tuning().soupHealHearts() > 0) {
            fight.botStews--;
            startConsume(bot, fight, Material.MUSHROOM_STEW,
                    s.knobTicks("consumables.stew.ticks", 7));
        } else if (fight.botGapples > 0
                && !bot.hasPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION)
                && !riskyFinish(bot, player, fight, s)) {
            if (dist >= s.knob("consumables.gapple.min-distance", 6.0)) {
                fight.botGapples--;
                startConsume(bot, fight, Material.GOLDEN_APPLE,
                        s.knobTicks("consumables.gapple.ticks", 32));
            } else {
                // Too close to chew — break away first, like a player would.
                fight.gappleRetreatTicks =
                        s.knobTicks("consumables.gapple.retreat-ticks", 60);
            }
        }
    }

    /**
     * The disengage before a gapple. The bot is low and wants to chew, but
     * chewing next to the player is how you die mid-bite: it kites (the
     * movement branch treats the retreat exactly like a RESET, rod included)
     * until the gap is open, starts the chew the moment it is, and gives up
     * and fights on with what it has when it cannot shake the player in time.
     */
    private void tickGappleRetreat(Husk bot, Player player, BotFight fight,
                                   BotSettings s, double dist) {
        if (fight.consuming() || fight.botGapples <= 0
                || bot.getHealth() > s.knob("consumables.heal-at", 12)) {
            fight.gappleRetreatTicks = 0; // already chewing, or healed another way
            return;
        }
        if (riskyFinish(bot, player, fight, s)) {
            return; // mid-retreat the player got hurt worse — turn and take it
        }
        if (dist >= s.knob("consumables.gapple.min-distance", 6.0)
                && !fight.blocking() && !fight.crouching()) {
            fight.gappleRetreatTicks = 0;
            fight.botGapples--;
            startConsume(bot, fight, Material.GOLDEN_APPLE,
                    s.knobTicks("consumables.gapple.ticks", 32));
        } else if (--fight.gappleRetreatTicks == 0) {
            // Could not shake the player — fight on and try again later.
            fight.consumeCooldown = s.knobRoll("consumables.cooldown", 30, 49);
        }
    }

    /**
     * The kill-first read: low enough to want the gapple, but the player is
     * hurt at least as badly — a player smells blood there and goes for the
     * finish instead of giving the fight time to reset. Skips (or aborts)
     * the heal and commits to pressure instead.
     */
    private boolean riskyFinish(Husk bot, Player player, BotFight fight, BotSettings s) {
        if (!tuning().gappleRiskyFinish() || bot.getHealth() < player.getHealth()) {
            return false;
        }
        fight.gappleRetreatTicks = 0;
        fight.stance = BotFight.PRESSURE;
        fight.stanceTicks = Math.max(fight.stanceTicks,
                s.knobTicks("consumables.gapple.recommit-ticks", 40));
        fight.consumeCooldown = Math.max(fight.consumeCooldown,
                s.knobRoll("consumables.cooldown", 30, 49));
        return true;
    }

    /**
     * Whether the player is lining up a projectile: drawing a bow, crossbow
     * or trident, carrying a loaded crossbow, or holding a throwable. This is
     * what unlocks the full-speed crosshair dodge — sidestepping a shot is
     * honest; doing it against a sword swing reads as teleporting.
     */
    private boolean projectileThreat(Player player) {
        ItemStack inUse = player.getActiveItem(); // empty unless mid-draw
        if (inUse.getType() == Material.BOW
                || inUse.getType() == Material.CROSSBOW
                || inUse.getType() == Material.TRIDENT) {
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.CROSSBOW
                && held.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta meta
                && !meta.getChargedProjectiles().isEmpty()) {
            return true;
        }
        return tuning().projectileThreats().contains(held.getType());
    }

    private void startConsume(Husk bot, BotFight fight, Material item, int ticks) {
        fight.consumeItem = item;
        fight.consumeTicks = Math.max(1, ticks);
        fight.heldRevertTicks = 0; // the consumable owns the hand now
        bot.getEquipment().setItemInMainHand(new ItemStack(item));
        if (item != Material.SPLASH_POTION) {
            plugin.sounds().playAt(bot.getLocation(), "pvpbot.bot-eat");
        }
    }

    /** The countdown ran out: the consumable's effect lands and the sword returns. */
    private void finishConsume(Husk bot, BotFight fight) {
        Material item = fight.consumeItem;
        fight.consumeItem = null;
        fight.consumeTicks = 0;
        fight.consumeCooldown = fight.settings == null ? 30
                : fight.settings.knobRoll("consumables.cooldown", 30, 49);
        equipSword(fight);
        if (item == null || !bot.isValid()) {
            return;
        }
        BotSettings s = fight.settings;
        switch (item) {
            case SPLASH_POTION -> {
                // Instant Health II at the feet, applied directly — a real
                // splash potion would harm the bot, since husks are undead.
                heal(bot, s.knob("consumables.pot.heal", 8));
                plugin.sounds().playAt(bot.getLocation(), "pvpbot.bot-pot");
                bot.getWorld().spawnParticle(Particle.HEART,
                        bot.getLocation().add(0, 1, 0), 6, 0.4, 0.5, 0.4, 0);
            }
            case MUSHROOM_STEW -> {
                // Same instant heal the player's soup gives (bot.soup-heal-hearts).
                heal(bot, tuning().soupHealHearts() * 2);
                plugin.sounds().playAt(bot.getLocation(), "pvpbot.bot-burp");
            }
            case GOLDEN_APPLE -> {
                bot.addPotionEffect(new PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION,
                        s.knobTicks("consumables.gapple.regen-ticks", 100),
                        s.knobTicks("consumables.gapple.regen-amplifier", 1)));
                bot.addPotionEffect(new PotionEffect(
                        org.bukkit.potion.PotionEffectType.ABSORPTION,
                        s.knobTicks("consumables.gapple.absorption-ticks", 2400), 0));
                plugin.sounds().playAt(bot.getLocation(), "pvpbot.bot-burp");
                // Healed — wheel straight back in, the way a player rushes
                // back the moment the chew lands.
                fight.stance = BotFight.PRESSURE;
                fight.stanceTicks = s.knobTicks("consumables.gapple.recommit-ticks", 40);
            }
            default -> {
            }
        }
    }

    private static void heal(Husk bot, double amount) {
        AttributeInstance max = bot.getAttribute(PlayerSnapshot.maxHealthAttribute());
        bot.setHealth(Math.min(max != null ? max.getValue() : 20.0,
                Math.max(0, bot.getHealth() + amount)));
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
        return plugin.messages().component("pvpbot.bot-tag", "health", healthPoints(health));
    }

    /**
     * Health rendered in health points — half-hearts, the 0-20 scale the
     * scoreboard uses — with a trailing .0 trimmed so whole values read clean.
     */
    private static String healthPoints(double health) {
        String formatted = String.format(java.util.Locale.ROOT, "%.1f", health);
        return formatted.endsWith(".0")
                ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    /**
     * A DeluxeCombat-style damage indicator: a small floating hologram at the
     * hit spot showing the hearts dealt, drifting upward for a moment before
     * vanishing. Display entities, not items — the practice world stays clean
     * and nothing can be picked up.
     */
    public void spawnDamageIndicator(org.bukkit.entity.LivingEntity victim, double damage) {
        if (!tuning().damageIndicators()) {
            return;
        }
        Location loc = victim.getLocation().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8,
                victim.getHeight() * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.3),
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);
        org.bukkit.entity.TextDisplay display = loc.getWorld().spawn(loc,
                org.bukkit.entity.TextDisplay.class, text -> {
                    text.text(plugin.messages().component("pvpbot.damage-indicator",
                            "damage", healthPoints(damage)));
                    text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    text.setDefaultBackground(false);
                    text.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    text.setShadowed(true);
                    text.setPersistent(false);
                    text.setTeleportDuration(12); // glide, don't jump
                });
        double rise = tuning().damageIndicatorRise();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (display.isValid()) {
                display.teleport(loc.clone().add(0, rise, 0));
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, display::remove,
                tuning().damageIndicatorTicks());
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
