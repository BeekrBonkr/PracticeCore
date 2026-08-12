package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.mode.MlgMode;
import me.beekrbonkr.practicecore.session.PracticeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** MLG-specific events: dropping off the platform, splashing down, landing hard. */
public final class MlgListener implements Listener {

    private final PracticeCorePlugin plugin;

    public MlgListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private PracticeSession mlgSession(Player player) {
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        return session != null && session.mode() instanceof MlgMode ? session : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        PracticeSession session = mlgSession(event.getPlayer());
        if (session == null) {
            return;
        }
        ((MlgMode) session.mode()).handleMove(plugin, event.getPlayer(), session, event.getTo());
    }

    /**
     * Fall damage is canceled wholesale by {@link ProtectionListener} at
     * HIGHEST, but the event still tells us the clutch failed — observed here
     * at NORMAL, before that cancel.
     */
    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        PracticeSession session = mlgSession(player);
        if (session == null) {
            return;
        }
        ((MlgMode) session.mode()).handleGroundImpact(plugin, player, session);
    }

    /**
     * The bucket may only be emptied mid-fall — refusing platform placement is
     * what keeps a pre-placed pool from counting as a clutch. Runs at HIGH,
     * after {@link ProtectionListener}'s NORMAL-priority handler has applied
     * the general bucket rules (which MLG's allowsBuckets() waves through).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        PracticeSession session = mlgSession(event.getPlayer());
        if (session == null) {
            return;
        }
        if (!((MlgMode) session.mode()).mayPlaceWater(session)) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "mlg.jump-first");
        }
    }
}
