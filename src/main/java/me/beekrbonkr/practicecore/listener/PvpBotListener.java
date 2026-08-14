package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.Menu;
import me.beekrbonkr.practicecore.pvpbot.BotFight;
import me.beekrbonkr.practicecore.pvpbot.PvpBotService;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.UUID;

/**
 * PvP bot events: the two halves of the damage plumbing (the bot's hits on
 * the player, the player's hits on the bot — both with the fatal blow
 * intercepted into a stock reset), the 1.8 sword-block correction, and
 * pausing the bot while a menu is open.
 */
public final class PvpBotListener implements Listener {

    private final PracticeCorePlugin plugin;

    public PvpBotListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------- player taking damage

    /** Crit flourish: the bot's jump-crit hits land half again as hard. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BotFight fight = PvpBotService.fightOf(plugin.sessions().get(player.getUniqueId()));
        if (fight == null || fight.bot == null || !event.getDamager().equals(fight.bot)) {
            return;
        }
        if (fight.critBonusNextHit) {
            fight.critBonusNextHit = false;
            event.setDamage(event.getDamage() * 1.5);
            plugin.pvpBot().playCritEffect(player);
        }
    }

    /**
     * 1.8 sword blocking, corrected: on 1.21 the server treats any raised
     * item with the block animation as a <em>shield</em>, so the
     * vanilla-sword-blocking component made swords swallow entire hits.
     * The shield's full negation is stripped here; the 50% reduction is the
     * sword-blocking plugin's (or, without it, applied to the base directly).
     * Runs at HIGH — after that plugin's NORMAL-priority multiplier.
     */
    @SuppressWarnings("deprecation") // DamageModifier is the only way to edit
    // one modifier without recomputing the rest; long-deprecated, still the API.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockedHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || PvpBotService.fightOf(plugin.sessions().get(player.getUniqueId())) == null
                || !event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                || event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) >= 0) {
            return;
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        if (!Bukkit.getPluginManager().isPluginEnabled("vanilla-sword-blocking")) {
            event.setDamage(EntityDamageEvent.DamageModifier.BASE,
                    event.getDamage(EntityDamageEvent.DamageModifier.BASE) * 0.5);
        }
    }

    /**
     * The no-death-screen invariant: a hit that would kill is cancelled and
     * turned into a lost stock instead. Runs at HIGHEST, after the server's
     * combat plugins have applied their final numbers. ProtectionListener has
     * already cancelled everything that is not the session's own bot combat.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        BotFight fight = PvpBotService.fightOf(session);
        if (fight == null) {
            return;
        }
        if (event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            plugin.pvpBot().playerDied(player, session, fight);
            return;
        }
        if (event.getFinalDamage() > 0) { // rod shoves knock, but aren't hits
            fight.countHitTaken();
        }
    }

    // ---------------------------------------------------- bot taking damage

    /**
     * Gatekeeper for the bot's health bar: only its own player hurts it, a
     * raised 1.8 sword block halves what lands (the vanilla-sword-blocking
     * plugin only covers players), and a paused bot is out of bounds — free
     * hits while the settings GUI is open would cheapen the stats.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBotDamaged(EntityDamageEvent event) {
        if (!plugin.pvpBot().isBot(event.getEntity())) {
            return;
        }
        UUID owner = plugin.pvpBot().ownerOf(event.getEntity());
        BotFight fight = owner == null ? null
                : PvpBotService.fightOf(plugin.sessions().get(owner));
        if (fight == null || !event.getEntity().equals(fight.bot)
                || fight.paused
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !isFromOwner(byEntity.getDamager(), owner)) {
            event.setCancelled(true);
            return;
        }
        if (fight.blocking()) {
            event.setDamage(event.getDamage() * 0.5);
            event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                    Sound.ITEM_SHIELD_BLOCK, 0.6f, 1.4f);
        }
    }

    private static boolean isFromOwner(Entity damager, UUID owner) {
        if (damager.getUniqueId().equals(owner)) {
            return true;
        }
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter
                && shooter.getUniqueId().equals(owner);
    }

    /** The killing blow on the bot becomes a kill and a fresh stock. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotFatal(EntityDamageEvent event) {
        if (!plugin.pvpBot().isBot(event.getEntity())) {
            return;
        }
        UUID owner = plugin.pvpBot().ownerOf(event.getEntity());
        PracticeSession session = owner == null ? null : plugin.sessions().get(owner);
        BotFight fight = PvpBotService.fightOf(session);
        if (fight == null || !event.getEntity().equals(fight.bot)) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        if (event.getFinalDamage() > 0) { // a rod tag knocks, but isn't a hit
            fight.countHitLanded();
            plugin.pvpBot().spawnDamageIndicator(fight.bot, event.getFinalDamage());
        }
        // Ride the knockback instead of instantly strafing out of it — the
        // window that makes real 1.8-style combos possible. Deep in a combo
        // the stun shortens so the bot gets chances to fight back instead of
        // being carried helplessly across the arena, and an extreme-evasive
        // bot rides less of it to begin with — combos still land, but each
        // one has to be earned.
        int stun = fight.settings != null && fight.settings.evasiveness()
                == me.beekrbonkr.practicecore.pvpbot.BotSettings.Evasiveness.EXTREME ? 8 : 10;
        fight.hitstunTicks = fight.combo >= 3 ? stun / 2 : stun;
        if (event.getFinalDamage() >= fight.bot.getHealth()) {
            event.setCancelled(true);
            plugin.pvpBot().botDied(player, session, fight);
        }
    }

    // ------------------------------------------------------------ bot pause

    /** Any of our menus opening freezes the bot — no free hits while browsing. */
    @EventHandler
    public void onMenuOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        BotFight fight = PvpBotService.fightOf(plugin.sessions().get(player.getUniqueId()));
        if (fight != null) {
            fight.paused = true;
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        BotFight fight = PvpBotService.fightOf(session);
        if (fight == null) {
            return;
        }
        fight.paused = false;
        fight.graceTicks = Math.max(fight.graceTicks, 10); // a beat to re-orient
        // Any of our menus may have changed a pref (the kit gallery, the
        // settings knobs) — re-reading is cheap and always correct.
        plugin.pvpBot().applySettings(player, session, fight);
    }
}
