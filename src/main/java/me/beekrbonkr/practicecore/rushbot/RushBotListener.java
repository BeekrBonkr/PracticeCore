package me.beekrbonkr.practicecore.rushbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.rush.RushState;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 * Damage plumbing for rush defender bots, mirroring the PvP bot's pattern:
 * only the owning session's player (and their projectiles, TNT, fireballs and
 * guard dog) may hurt a defender, a defender's fatal blow on the player turns
 * into a bedwars respawn instead of a death screen, and a defender's own
 * fatal blow turns into a bed-gated respawn or an elimination.
 */
public final class RushBotListener implements Listener {

    private final PracticeCorePlugin plugin;

    public RushBotListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------- bot taking damage

    /** Gatekeeper: only its own player's combat reaches a defender's health bar. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBotDamaged(EntityDamageEvent event) {
        if (!plugin.rushBots().isBot(event.getEntity())) {
            return;
        }
        UUID owner = plugin.rushBots().ownerOf(event.getEntity());
        PracticeSession session = owner == null ? null : plugin.sessions().get(owner);
        RushBot bot = plugin.rushBots().botOf(session, event.getEntity());
        RushState state = session != null
                && session.modeState() instanceof RushState s ? s : null;
        if (bot == null || state == null) {
            event.setCancelled(true);
            return;
        }
        boolean explosion = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        if (!explosion && (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !plugin.rushBots().isFromOwner(byEntity.getDamager(), owner))) {
            event.setCancelled(true);
            return;
        }
        if (state.playerHeld()) {
            event.setCancelled(true); // no free hits while the player is "dead"
            return;
        }
        plugin.rushBots().onBotDamaged(bot);
    }

    /** The killing blow on a defender becomes a respawn or an elimination. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotFatal(EntityDamageEvent event) {
        if (!plugin.rushBots().isBot(event.getEntity())) {
            return;
        }
        UUID owner = plugin.rushBots().ownerOf(event.getEntity());
        PracticeSession session = owner == null ? null : plugin.sessions().get(owner);
        RushBot bot = plugin.rushBots().botOf(session, event.getEntity());
        RushState state = session != null
                && session.modeState() instanceof RushState s ? s : null;
        if (bot == null || state == null) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        if (event.getFinalDamage() > 0) {
            // The same floating hearts-dealt numbers the PvP bot shows.
            plugin.pvpBot().spawnDamageIndicator(bot.entity, event.getFinalDamage());
        }
        if (event.getFinalDamage() >= bot.entity.getHealth()) {
            event.setCancelled(true);
            plugin.rushBots().killBot(player, session, state, bot);
        }
    }

    // ------------------------------------------------- player taking damage

    /**
     * The no-death-screen invariant for combat rush: a defender's hit that
     * would kill is cancelled and turned into a bedwars respawn at the
     * player's own base. Runs at HIGHEST, after the server's combat plugins
     * applied their numbers; ProtectionListener has already cancelled
     * everything that is not this session's own bot combat.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        RushState state = session != null && session.modeState() instanceof RushState s
                && s.combat() ? s : null;
        if (state == null) {
            return;
        }
        if (state.playerHeld()) {
            // Untouchable through the hold — a stray hit or explosion must
            // not chain a second death onto the first.
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            plugin.rushBots().killPlayer(player, session, state);
        }
    }
}
