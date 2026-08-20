package me.beekrbonkr.practicecore.rushbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.RushMode;
import me.beekrbonkr.practicecore.pvpbot.BotPreset;
import me.beekrbonkr.practicecore.pvpbot.BotSettings;
import me.beekrbonkr.practicecore.pvpbot.BotTuning;
import me.beekrbonkr.practicecore.pvpbot.PlayerDisguise;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.rush.RushSelection;
import me.beekrbonkr.practicecore.rush.RushState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defender bots for rush combat runs: a squad guarding every enemy base,
 * built from the player's rush selection (count, difficulty preset, armor and
 * sword tiers). Each defender holds its post, engages the player inside its
 * aggro range (or when hit, or when its bed is touched), fights with the
 * cadence/accuracy/spacing of the chosen pvpbot.yml preset, and leashes back
 * home rather than hunting across the map.
 *
 * Deaths are bed-linked, exactly like a real game: a defender killed while
 * its bed stands respawns after a delay; one killed after its bed is broken
 * is out for good. The run ends when one enemy team is fully wiped — bed gone
 * and every defender out — which is the {@code TEAM_WIPE} objective.
 *
 * The player respawns too: a defender kill sends them straight back to their
 * own base (their bed never breaks) with a fresh kit and a short blind hold,
 * while the run clock keeps going.
 */
public final class RushBotService {

    private final PracticeCorePlugin plugin;
    /** Bot entities; value = owning player's UUID. */
    private final NamespacedKey botKey;
    private BukkitTask task;

    public RushBotService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.botKey = new NamespacedKey(plugin, "rush-bot");
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

    private PlayerDisguise disguise() {
        return plugin.pvpBot().disguise();
    }

    /**
     * Builds and spawns the whole defender squad for a fresh combat run.
     * Called from RushState.rebuild, after beds and defenses are in place.
     */
    public void spawnAll(PracticeSession session, RushState state) {
        state.bots().clear();
        state.setPlayerHoldTicks(0);
        RushSelection selection = state.selection();
        BotSettings settings = resolveSettings(selection.botDifficulty());
        Location origin = session.origin();
        for (RushState.TargetBed bed : state.enemyBeds()) {
            RushMapData.TeamBase team = state.data().team(bed.team());
            if (team == null) {
                continue;
            }
            Location spawn = new Location(origin.getWorld(),
                    origin.getX() + team.spawn().getX(),
                    origin.getY() + team.spawn().getY(),
                    origin.getZ() + team.spawn().getZ(),
                    team.yaw(), 0);
            for (int i = 0; i < selection.bots(); i++) {
                // Fan the squad out in a small arc around the team spawn so
                // they never stack inside each other.
                double angle = (2 * Math.PI * i) / Math.max(1, selection.bots());
                Location post = spawn.clone().add(
                        Math.cos(angle) * 1.4, 0, Math.sin(angle) * 1.4);
                if (!session.containsBlock(post)
                        || !post.getBlock().getType().isAir()) {
                    post = spawn.clone();
                }
                post.setYaw(spawn.getYaw());
                RushBot bot = new RushBot(bed.team(), post, settings);
                state.bots().add(bot);
                spawnEntity(session, state, bot);
            }
        }
    }

    /**
     * The difficulty behind a preset id: the preset's tiers where it sets
     * them, pvpbot.yml's defaults where it doesn't. An unknown or blank id
     * lands on the defaults, so a renamed preset degrades instead of failing.
     */
    private BotSettings resolveSettings(String presetId) {
        BotTuning tuning = plugin.botTuning();
        BotPreset preset = presetId == null || presetId.isBlank()
                ? null : tuning.preset(presetId);
        BotSettings defaults = new BotSettings(tuning, null, BotSettings.GearTier.NONE,
                tuning.defaultTier("evasiveness", BotSettings.Evasiveness.class,
                        BotSettings.Evasiveness.MEDIUM),
                tuning.defaultTier("cps", BotSettings.Cps.class, BotSettings.Cps.EIGHT),
                tuning.defaultTier("accuracy", BotSettings.Accuracy.class,
                        BotSettings.Accuracy.MEDIUM),
                tuning.defaultTier("combos", BotSettings.Combos.class, BotSettings.Combos.SOME),
                tuning.defaultTier("reach", BotSettings.Reach.class, BotSettings.Reach.NORMAL),
                tuning.defaultTier("aggression", BotSettings.Aggression.class,
                        BotSettings.Aggression.BALANCED),
                false, false, false);
        if (preset == null) {
            return defaults;
        }
        return new BotSettings(tuning, null, BotSettings.GearTier.NONE,
                preset.evasiveness() != null ? preset.evasiveness() : defaults.evasiveness(),
                preset.cps() != null ? preset.cps() : defaults.cps(),
                preset.accuracy() != null ? preset.accuracy() : defaults.accuracy(),
                preset.combos() != null ? preset.combos() : defaults.combos(),
                preset.reach() != null ? preset.reach() : defaults.reach(),
                preset.aggression() != null ? preset.aggression() : defaults.aggression(),
                false, false, false);
    }

    /** The preset a rush difficulty id resolves to, or null — for menu labels. */
    public BotPreset presetOf(String presetId) {
        return presetId == null || presetId.isBlank() ? null
                : plugin.botTuning().preset(presetId);
    }

    private void spawnEntity(PracticeSession session, RushState state, RushBot bot) {
        Player owner = Bukkit.getPlayer(session.playerId());
        bot.entity = bot.post().getWorld().spawn(bot.post(), Husk.class, husk -> {
            husk.getPersistentDataContainer().set(botKey, PersistentDataType.STRING,
                    session.playerId().toString());
            husk.setAdult();
            husk.setPersistent(true);
            husk.setRemoveWhenFarAway(false);
            husk.setCanPickupItems(false);
            husk.setSilent(true);
            AttributeInstance attack = husk.getAttribute(attackDamageAttribute());
            if (attack != null) {
                attack.setBaseValue(plugin.botTuning().baseAttackDamage());
            }
            AttributeInstance knockback = husk.getAttribute(knockbackResistanceAttribute());
            if (knockback != null) {
                knockback.setBaseValue(0.0);
            }
            AttributeInstance follow = husk.getAttribute(followRangeAttribute());
            if (follow != null) {
                follow.setBaseValue(plugin.botTuning().followRange());
            }
            PlayerDisguise disguise = disguise();
            if (disguise != null && disguise.active() && owner != null) {
                disguise.apply(husk, owner);
            } else {
                AttributeInstance scale = husk.getAttribute(scaleAttribute());
                if (scale != null) {
                    scale.setBaseValue(plugin.botTuning().undisguisedScale());
                }
            }
        });
        Bukkit.getMobGoals().removeAllGoals(bot.entity);
        equip(state, bot);
        spawnTag(bot);
        bot.hitstunTicks = 0;
        bot.aggroTicks = 0;
        bot.attackCooldown = 10;
        bot.repathTicks = 0;
    }

    /** Armor and sword from the selection; leather is dyed the team's color. */
    private void equip(RushState state, RushBot bot) {
        RushSelection selection = state.selection();
        EntityEquipment equipment = bot.entity.getEquipment();
        org.bukkit.Color color = teamColor(bot.team());
        equipment.setHelmet(armorPiece(selection.botArmor(), "HELMET", color));
        equipment.setChestplate(armorPiece(selection.botArmor(), "CHESTPLATE", color));
        equipment.setLeggings(armorPiece(selection.botArmor(), "LEGGINGS", color));
        equipment.setBoots(armorPiece(selection.botArmor(), "BOOTS", color));
        equipment.setItemInMainHand(new ItemStack(selection.botSword().item()));
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);
        equipment.setItemInMainHandDropChance(0);
    }

    private static ItemStack armorPiece(RushSelection.BotArmor armor, String slot,
                                        org.bukkit.Color teamColor) {
        org.bukkit.Material material = armor.piece(slot);
        if (material == null) {
            return null;
        }
        ItemStack stack = new ItemStack(material);
        if (teamColor != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(teamColor);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The dye color matching an MBedwars team name, or null for exotic names. */
    private static org.bukkit.Color teamColor(String team) {
        String name = team.toUpperCase(Locale.ROOT);
        try {
            return DyeColor.valueOf(name.equals("AQUA") ? "LIGHT_BLUE" : name).getColor();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void spawnTag(RushBot bot) {
        Location loc = tagLocation(bot.entity);
        bot.tag = loc.getWorld().spawn(loc, org.bukkit.entity.TextDisplay.class, tag -> {
            tag.text(tagText(bot));
            tag.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            tag.setDefaultBackground(false);
            tag.setBackgroundColor(org.bukkit.Color.fromARGB(64, 0, 0, 0));
            tag.setShadowed(true);
            tag.setPersistent(false);
            tag.setTeleportDuration(2);
        });
    }

    private Location tagLocation(Husk entity) {
        return entity.getLocation().add(0,
                entity.getHeight() + plugin.botTuning().nameTagOffset(), 0);
    }

    private net.kyori.adventure.text.Component tagText(RushBot bot) {
        double health = bot.alive() ? bot.entity.getHealth() : 0;
        return plugin.messages().component("rush.bots.tag",
                "team", RushMode.prettyTeam(bot.team()),
                "health", healthPoints(health));
    }

    private static String healthPoints(double health) {
        String formatted = String.format(Locale.ROOT, "%.1f", health);
        return formatted.endsWith(".0")
                ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    /** Removes every defender body, tag and disguise (arena reset, session end). */
    public void cleanup(PracticeSession session) {
        RushState state = session != null
                && session.modeState() instanceof RushState s ? s : null;
        if (state == null) {
            return;
        }
        for (RushBot bot : state.bots()) {
            despawn(bot);
        }
        state.bots().clear();
    }

    private void despawn(RushBot bot) {
        if (bot.entity != null) {
            PlayerDisguise disguise = disguise();
            if (disguise != null) {
                disguise.remove(bot.entity);
            }
            bot.entity.remove();
            bot.entity = null;
        }
        if (bot.tag != null) {
            bot.tag.remove();
            bot.tag = null;
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

    /** The RushBot wrapping this entity within its session, or null. */
    public RushBot botOf(PracticeSession session, Entity entity) {
        RushState state = combatState(session);
        if (state == null) {
            return null;
        }
        for (RushBot bot : state.bots()) {
            if (bot.entity != null && bot.entity.equals(entity)) {
                return bot;
            }
        }
        return null;
    }

    private static RushState combatState(PracticeSession session) {
        return session != null && session.mode() instanceof RushMode
                && session.modeState() instanceof RushState state && state.combat()
                ? state : null;
    }

    /** The nearest living defender of this session, or null. Powers tracker & pets. */
    public RushBot nearestBot(PracticeSession session, Location from) {
        RushState state = combatState(session);
        if (state == null) {
            return null;
        }
        RushBot nearest = null;
        double best = Double.MAX_VALUE;
        for (RushBot bot : state.bots()) {
            if (!bot.alive()) {
                continue;
            }
            double distance = bot.entity.getLocation().distanceSquared(from);
            if (distance < best) {
                best = distance;
                nearest = bot;
            }
        }
        return nearest;
    }

    /**
     * Whether this damage event is sanctioned defender combat the practice
     * world's blanket damage ban must let through: one of the session's own
     * defenders landing a hit on the player.
     */
    public boolean allowsDamage(PracticeSession session, EntityDamageEvent event) {
        RushState state = combatState(session);
        if (state == null || !(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        for (RushBot bot : state.bots()) {
            if (bot.entity != null && bot.entity.equals(byEntity.getDamager())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the damager traces back to the session's own player. */
    public boolean isFromOwner(Entity damager, UUID owner) {
        if (damager.getUniqueId().equals(owner)) {
            return true;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return isFromOwner(shooter, owner);
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() != null) {
            return isFromOwner(tnt.getSource(), owner);
        }
        // The shop's guard dog fights for its owner.
        return damager instanceof Wolf wolf && wolf.getOwner() != null
                && wolf.getOwner().getUniqueId().equals(owner);
    }

    // --------------------------------------------------------------- deaths

    /** A hit landed on a defender: it wakes up, and it rides the knockback. */
    public void onBotDamaged(RushBot bot) {
        bot.aggroTicks = Math.max(bot.aggroTicks, 200);
        bot.hitstunTicks = bot.settings().hitstunTicks();
    }

    /**
     * A defender dropped. Its bed decides what happens next: standing, the
     * defender respawns at its post after the delay; broken, it is out — and
     * if the whole team is out with it, the run ends on the team wipe.
     */
    public void killBot(Player player, PracticeSession session, RushState state, RushBot bot) {
        despawn(bot);
        if (state.isBedBroken(bot.team())) {
            bot.out = true;
            plugin.messages().send(player, "rush.bots.defender-eliminated",
                    "team", RushMode.prettyTeam(bot.team()));
        } else {
            bot.respawnTicks = plugin.pcConfig().rushBotsRespawnTicks();
            plugin.messages().actionBar(player, "rush.bots.defender-down",
                    "team", RushMode.prettyTeam(bot.team()));
        }
        plugin.sounds().play(player, "rush.bot-death");
        checkTeamWipe(player, session, state);
    }

    /** Any enemy team fully out — bed gone, every defender eliminated — ends the run. */
    public void checkTeamWipe(Player player, PracticeSession session, RushState state) {
        for (RushState.TargetBed bed : state.enemyBeds()) {
            if (!state.isBedBroken(bed.team())) {
                continue;
            }
            boolean wiped = true;
            for (RushBot bot : state.bots()) {
                if (bot.team().equals(bed.team()) && !bot.out) {
                    wiped = false;
                    break;
                }
            }
            if (wiped && session.mode() instanceof RushMode mode) {
                plugin.messages().send(player, "rush.bots.team-wiped",
                        "team", RushMode.prettyTeam(bed.team()));
                mode.completeTeamWipe(plugin, player, session);
                return;
            }
        }
    }

    /**
     * A defender killed the player. Their own bed never breaks, so this is a
     * bedwars respawn, not a failed run: straight back to their base spawn
     * with a fresh kit — everything bought is lost, like a real death — and a
     * short blind hold while the clock keeps running.
     */
    public void killPlayer(Player player, PracticeSession session, RushState state) {
        if (state.playerHeld()) {
            return;
        }
        state.countPlayerDeath();
        int hold = plugin.pcConfig().rushBotsPlayerRespawnTicks();
        state.setPlayerHoldTicks(hold);
        // Back to base immediately — the hold is served at the spawn, not
        // wherever the killing blow landed.
        plugin.sessions().teleportInternal(player, session.spawn());
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        AttributeInstance maxHealth = player.getAttribute(PlayerSnapshot.maxHealthAttribute());
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                hold + 20, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                hold + 20, 6, false, false));
        plugin.settings().applyToSession(player);
        plugin.sessions().regiveKit(player, session);
        plugin.messages().send(player, "rush.bots.player-died");
        plugin.messages().title(player, "pvpbot.title.death", "pvpbot.title.respawn-sub",
                "seconds", String.valueOf(Math.max(1, (hold + 19) / 20)));
        plugin.sounds().play(player, "pvpbot.player-death");
        // Defenders go back to defending instead of camping the corpse.
        for (RushBot bot : state.bots()) {
            bot.aggroTicks = 0;
        }
    }

    /** The player touched a team's bed or defenses — nearby defenders notice. */
    public void alertNear(RushState state, Location loc, double radius) {
        double radiusSq = radius * radius;
        for (RushBot bot : state.bots()) {
            if (bot.alive() && bot.entity.getLocation().getWorld() == loc.getWorld()
                    && bot.entity.getLocation().distanceSquared(loc) <= radiusSq) {
                bot.aggroTicks = Math.max(bot.aggroTicks, 300);
            }
        }
    }

    // ---------------------------------------------------------- tick driver

    private void tickAll() {
        for (PracticeSession session : plugin.sessions().all()) {
            RushState state = combatState(session);
            if (state == null) {
                continue;
            }
            SessionState sessionState = session.state();
            if (sessionState != SessionState.READY && sessionState != SessionState.ACTIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            tickPlayerHold(player, session, state);
            for (RushBot bot : state.bots()) {
                tickBot(player, session, state, bot);
            }
        }
    }

    /** The blind hold after a defender kill: pinned at base until it runs out. */
    private void tickPlayerHold(Player player, PracticeSession session, RushState state) {
        if (!state.playerHeld()) {
            return;
        }
        state.setPlayerHoldTicks(state.playerHoldTicks() - 1);
        if (state.playerHoldTicks() == 0) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            plugin.messages().title(player, "pvpbot.title.respawned",
                    "pvpbot.title.respawned-sub");
            return;
        }
        if (player.getWorld() == session.spawn().getWorld()
                && player.getLocation().distanceSquared(session.spawn()) > 1.0) {
            plugin.sessions().teleportInternal(player, session.spawn());
        }
        if (state.playerHoldTicks() % 20 == 0) {
            plugin.messages().title(player, "pvpbot.title.death", "pvpbot.title.respawn-sub",
                    "seconds", String.valueOf(Math.max(1, (state.playerHoldTicks() + 19) / 20)));
        }
    }

    private void tickBot(Player player, PracticeSession session, RushState state, RushBot bot) {
        if (bot.out) {
            return;
        }
        if (!bot.alive()) {
            if (bot.entity != null) {
                // Something external removed the body — treat it as a death.
                bot.entity = null;
                if (bot.tag != null) {
                    bot.tag.remove();
                    bot.tag = null;
                }
                if (state.isBedBroken(bot.team())) {
                    bot.out = true;
                    checkTeamWipe(player, session, state);
                    return;
                }
                bot.respawnTicks = plugin.pcConfig().rushBotsRespawnTicks();
            }
            if (bot.respawnTicks > 0 && --bot.respawnTicks == 0) {
                spawnEntity(session, state, bot);
                plugin.sounds().play(player, "pvpbot.bot-respawn", bot.post());
            }
            return;
        }

        Husk entity = bot.entity;
        // Ring-out: over the edge is a death like any other.
        if (entity.getLocation().getY()
                < session.bounds().getMinY() + plugin.pcConfig().failYOffset()) {
            killBot(player, session, state, bot);
            return;
        }

        // Tag follows every tick, text refreshes on a slower beat.
        if (bot.tag == null || !bot.tag.isValid()) {
            spawnTag(bot);
        }
        bot.tag.teleport(tagLocation(entity));
        if (--bot.nameTicks <= 0) {
            bot.nameTicks = plugin.botTuning().nameRefreshTicks();
            bot.tag.text(tagText(bot));
        }

        BotSettings s = bot.settings();
        if (bot.attackCooldown > 0) {
            bot.attackCooldown--;
        }
        if (bot.strafeFlipTicks > 0) {
            bot.strafeFlipTicks--;
        }
        if (bot.aggroTicks > 0) {
            bot.aggroTicks--;
        }

        // A husk's melee applies the vanilla hunger effect — unwanted here too.
        if (player.hasPotionEffect(PotionEffectType.HUNGER)) {
            player.removePotionEffect(PotionEffectType.HUNGER);
        }

        Location botLoc = entity.getLocation();
        boolean playerVulnerable = !state.playerHeld();
        double dist = playerVulnerable
                ? horizontalDistance(botLoc, player.getLocation()) : Double.MAX_VALUE;
        double eyeDist = playerVulnerable
                ? entity.getEyeLocation().distance(player.getEyeLocation()) : Double.MAX_VALUE;

        // Sighting: the player inside aggro range wakes the defender.
        if (playerVulnerable && dist <= plugin.pcConfig().rushBotsAggroRange()) {
            bot.aggroTicks = Math.max(bot.aggroTicks, 100);
        }
        // Leash: too far from home means break off and walk back.
        double fromPost = horizontalDistance(botLoc, bot.post());
        boolean engaged = bot.aggroTicks > 0 && playerVulnerable
                && fromPost <= plugin.pcConfig().rushBotsLeashRange();
        if (bot.aggroTicks > 0 && fromPost > plugin.pcConfig().rushBotsLeashRange()) {
            bot.aggroTicks = 0;
        }

        // Riding knockback: steering pauses, retaliation doesn't.
        if (bot.hitstunTicks > 0) {
            bot.hitstunTicks--;
            entity.getPathfinder().stopPathfinding();
            if (engaged) {
                entity.lookAt(player);
                tryAttack(player, bot, eyeDist, s);
            }
            bot.lastX = botLoc.getX();
            bot.lastZ = botLoc.getZ();
            return;
        }

        if (!engaged) {
            // Off duty: walk home, then hold the post.
            if (fromPost > 2.5) {
                if (--bot.repathTicks <= 0) {
                    bot.repathTicks = 5;
                    entity.getPathfinder().moveTo(bot.post(), s.approachSpeed());
                }
            } else {
                entity.getPathfinder().stopPathfinding();
                if (playerVulnerable && dist < 24) {
                    entity.lookAt(player); // watchful, not hostile
                }
            }
            unstickHop(entity, bot, s, dist);
            bot.lastX = botLoc.getX();
            bot.lastZ = botLoc.getZ();
            return;
        }

        entity.lookAt(player);
        double gap = s.spacingGap();
        if (dist > gap + 1.2) {
            if (--bot.repathTicks <= 0) {
                bot.repathTicks = 4;
                entity.getPathfinder().moveTo(player, s.approachSpeed());
            }
        } else {
            bot.repathTicks = 0;
            entity.getPathfinder().stopPathfinding();
            strafe(entity, player, bot, s, dist, gap);
        }
        tryAttack(player, bot, eyeDist, s);
        unstickHop(entity, bot, s, dist);
        bot.lastX = botLoc.getX();
        bot.lastZ = botLoc.getZ();
    }

    private void tryAttack(Player player, RushBot bot, double eyeDist, BotSettings s) {
        if (bot.attackCooldown > 0 || eyeDist > s.reachBlocks()) {
            return;
        }
        bot.attackCooldown = s.attackIntervalTicks()
                + (ThreadLocalRandom.current().nextDouble() < 0.4 ? 1 : 0);
        bot.entity.swingMainHand();
        if (ThreadLocalRandom.current().nextDouble() < s.hitChance()) {
            bot.entity.attack(player);
        }
    }

    /**
     * A simplified duel strafe: circle the player at the tier's spacing,
     * flipping sides on a timer, with a radial pull back toward the gap.
     */
    private void strafe(Husk entity, Player player, RushBot bot, BotSettings s,
                        double dist, double gap) {
        Vector toBot = entity.getLocation().toVector()
                .subtract(player.getLocation().toVector());
        toBot.setY(0);
        if (toBot.lengthSquared() < 0.01) {
            toBot = new Vector(1, 0, 0);
        }
        toBot.normalize();
        if (bot.strafeFlipTicks == 0) {
            if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                bot.strafeSign = -bot.strafeSign;
            }
            bot.strafeFlipTicks = 20 + ThreadLocalRandom.current().nextInt(20);
        }
        Vector velocity = new Vector(-toBot.getZ(), 0, toBot.getX())
                .multiply(bot.strafeSign * s.strafeSpeed() * 0.8);
        double error = dist - gap;
        if (Math.abs(error) > 0.3) {
            velocity.add(toBot.clone().multiply(error > 0 ? -0.14 : 0.14));
        }
        entity.setVelocity(new Vector(velocity.getX(),
                entity.getVelocity().getY(), velocity.getZ()));
    }

    /** Hop over lips and out of corners when movement stalls. */
    private void unstickHop(Husk entity, RushBot bot, BotSettings s, double dist) {
        if (entity.isOnGround() && dist > s.reachBlocks()
                && Math.abs(entity.getLocation().getX() - bot.lastX) < 0.01
                && Math.abs(entity.getLocation().getZ() - bot.lastZ) < 0.01
                && entity.getPathfinder().hasPath()
                && ThreadLocalRandom.current().nextDouble() < 0.5) {
            entity.setVelocity(entity.getVelocity()
                    .setY(plugin.botTuning().jumpVelocity()));
        }
    }

    private static double horizontalDistance(Location a, Location b) {
        if (a.getWorld() != b.getWorld()) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // -------------------------------------------------------------- helpers

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
        Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacy));
        if (attr == null) {
            attr = org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modern));
        }
        if (attr == null) {
            throw new IllegalStateException("Attribute missing from the registry: " + modern);
        }
        return attr;
    }

    /** The live defender count, for the sidebar. */
    public int aliveDefenders(RushState state) {
        int alive = 0;
        for (RushBot bot : state.bots()) {
            if (!bot.out) {
                alive++;
            }
        }
        return alive;
    }
}
