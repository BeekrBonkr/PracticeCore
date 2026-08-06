package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.TriggerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class InteractListener implements Listener {

    private static final double MAX_TRIGGER_DISTANCE_SQ = 36; // 6 blocks

    private final PracticeCorePlugin plugin;

    public InteractListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Fires once per hand for a single click — only accept main hand.
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block != null
                    && session.template().triggerType() == TriggerType.BUTTON
                    && block.getLocation().equals(session.trigger())) {
                event.setCancelled(true); // no redstone pulse into the schematic
                tryFinish(player, session, block);
            }
        } else if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            if (session.template().triggerType() == TriggerType.PLATE
                    && block.getLocation().equals(session.trigger())) {
                event.setCancelled(true);
                tryFinish(player, session, block);
            } else {
                // Farmland trample, decorative plates in the schematic, …
                event.setCancelled(true);
            }
        }
    }

    private void tryFinish(Player player, PracticeSession session, Block block) {
        if (session.state() != SessionState.ACTIVE) {
            if (session.state() == SessionState.READY) {
                player.sendActionBar(Component.text("Timer hasn't started yet", NamedTextColor.YELLOW));
            }
            return;
        }
        if (player.getLocation().distanceSquared(block.getLocation().toCenterLocation())
                > MAX_TRIGGER_DISTANCE_SQ) {
            return;
        }
        plugin.sessions().finish(player, session);
    }
}
