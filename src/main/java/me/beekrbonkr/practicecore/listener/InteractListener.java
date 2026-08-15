package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.gui.MainMenu;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.TriggerType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class InteractListener implements Listener {

    private final PracticeCorePlugin plugin;

    public InteractListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Fires once per hand for a single click — only accept main hand.
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Block block = event.getClickedBlock();
            // A finish trigger wins over the menu item: right-clicking a
            // button to end a run must never open a GUI instead.
            if (session != null && block != null
                    && session.triggerTypeAt(block.getLocation()) == TriggerType.BUTTON) {
                event.setCancelled(true); // no redstone pulse into the schematic
                tryFinish(player, session, block);
                return;
            }
            if (openMenu(event, player)) {
                return;
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            openMenu(event, player);
        } else if (event.getAction() == Action.PHYSICAL) {
            if (session == null) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            if (session.triggerTypeAt(block.getLocation()) == TriggerType.PLATE) {
                event.setCancelled(true);
                tryFinish(player, session, block);
            } else {
                // Farmland trample, decorative plates in the schematic, …
                event.setCancelled(true);
            }
        }
    }

    /** Tool switches feed the modes that measure them (bedbreak reaction time). */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || session.state() != SessionState.ACTIVE) {
            return;
        }
        session.mode().onHeldItemChange(plugin, player, session,
                player.getInventory().getItem(event.getNewSlot()));
    }

    /** The F-key swap changes the held item without a PlayerItemHeldEvent. */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || session.state() != SessionState.ACTIVE) {
            return;
        }
        session.mode().onHeldItemChange(plugin, player, session, event.getMainHandItem());
    }

    /** @return true when the click was consumed by the menu item. */
    private boolean openMenu(PlayerInteractEvent event, Player player) {
        if (!plugin.pcConfig().menuItemEnabled() || !plugin.menuItems().isMenuItem(event.getItem())) {
            return false;
        }
        event.setCancelled(true);
        if (!player.hasPermission("practicecore.menu")) {
            plugin.messages().send(player, "permission.menu");
            return true;
        }
        new MainMenu(plugin, player).open();
        return true;
    }

    private void tryFinish(Player player, PracticeSession session, Block block) {
        if (session.state() != SessionState.ACTIVE) {
            if (session.state() == SessionState.READY) {
                plugin.messages().actionBar(player, "run.timer-not-started");
            }
            return;
        }
        double reach = plugin.pcConfig().triggerReach();
        if (player.getLocation().distanceSquared(block.getLocation().toCenterLocation())
                > reach * reach) {
            return;
        }
        plugin.sessions().finish(player, session);
    }
}
