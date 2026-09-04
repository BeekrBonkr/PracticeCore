package me.beekrbonkr.practicecore.listener;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefenseService;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Phase;
import me.beekrbonkr.practicecore.beddefense.BlockKinds;
import me.beekrbonkr.practicecore.gui.BedDefenseEditMenu;
import me.beekrbonkr.practicecore.gui.BedDefenseSessionMenu;
import me.beekrbonkr.practicecore.mode.BedDefenseMode;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Bed defense events: placement rules per phase (radius and allowed blocks
 * in the editor, strict order in play), completion checks, the drop-to-
 * preview gesture, the mode's hotbar items, and keeping those items where
 * they belong.
 */
public final class BedDefenseListener implements Listener {

    private final PracticeCorePlugin plugin;

    public BedDefenseListener(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    private BedDefenseService service() {
        return plugin.bedDefenses();
    }

    /** The session and state, or null when the player is not in this mode. */
    private BedDefenseState stateOf(Player player) {
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        return session != null && session.mode() instanceof BedDefenseMode
                ? BedDefenseMode.state(session) : null;
    }

    private static boolean live(PracticeSession session) {
        return session.state() == SessionState.READY || session.state() == SessionState.ACTIVE;
    }

    // --------------------------------------------------------------- placing

    /**
     * Before BlockListener's HIGHEST handler tracks the block: phase rules
     * decide whether it may land at all. A refusal here is what keeps the
     * editor inside its radius and strict order strict.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof BedDefenseMode) || !live(session)) {
            return;
        }
        BedDefenseState state = BedDefenseMode.state(session);
        if (state == null || !session.containsBlock(event.getBlock().getLocation())) {
            return; // BlockListener reports the bounds
        }
        Material kind = BlockKinds.kindOf(event.getBlock());
        String refusal = service().checkPlace(player, session, state, event.getBlock(), kind);
        if (refusal != null) {
            event.setCancelled(true);
            if (!refusal.isEmpty()) {
                plugin.messages().actionBar(player, refusal,
                        "material", BlockKinds.pretty(kind),
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius()));
            }
            return;
        }
        if (state.phase() == Phase.EDIT) {
            service().editPlaced(player, state, event.getBlock());
        }
    }

    /** After the block is tracked and the timer possibly started: progress. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof BedDefenseMode)) {
            return;
        }
        BedDefenseState state = BedDefenseMode.state(session);
        if (state != null && live(session)) {
            service().afterPlace(player, session, state, event.getBlock().getLocation());
        }
    }

    /**
     * Water: the bucket empties before the liquid exists, so the rules run
     * now and the progress check a tick later, once the source block is in.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof BedDefenseMode) || !live(session)) {
            return;
        }
        BedDefenseState state = BedDefenseMode.state(session);
        Block block = event.getBlock();
        if (state == null || !session.containsBlock(block.getLocation())) {
            return;
        }
        if (event.getBucket() != Material.WATER_BUCKET) {
            event.setCancelled(true); // lava has no place in a bed defense
            return;
        }
        String refusal = service().checkPlace(player, session, state, block, Material.WATER);
        if (refusal != null) {
            event.setCancelled(true);
            if (!refusal.isEmpty()) {
                plugin.messages().actionBar(player, refusal,
                        "material", BlockKinds.pretty(Material.WATER),
                        "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius()));
            }
            return;
        }
        Location loc = block.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.sessions().get(player.getUniqueId()) != session || !live(session)) {
                return;
            }
            if (state.phase() == Phase.EDIT) {
                if (BlockKinds.kindOf(loc.getBlock()) == Material.WATER) {
                    service().editPlaced(player, state, loc.getBlock());
                } else {
                    // Poured into a waterloggable block: not a water block,
                    // but the bucket still comes back full in the editor.
                    service().editPlaced(player, state, loc.getBlock());
                    state.editSequence().remove(loc.getBlock().getLocation());
                }
                return;
            }
            service().afterPlace(player, session, state, loc);
        });
    }

    /** Editing is for designing, not mining: every build block breaks instantly. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        BedDefenseState state = stateOf(event.getPlayer());
        if (state != null && state.phase() == Phase.EDIT
                && state.isEditBlock(event.getBlock().getLocation())) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BedDefenseState state = stateOf(event.getPlayer());
        if (state != null && state.phase() == Phase.PREVIEW) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "beddefense.preview.no-building");
        }
    }

    // ---------------------------------------------------------------- dropping

    /**
     * Dropping anything is the preview gesture — handled before the
     * practice world's blanket drop ban, which skips cancelled events. The
     * mode's own items are never dropped either way.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof BedDefenseMode)) {
            return;
        }
        event.setCancelled(true);
        BedDefenseState state = BedDefenseMode.state(session);
        if (state == null || !live(session)) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (service().itemRole(dropped) != null || plugin.menuItems().isMenuItem(dropped)) {
            plugin.messages().actionBar(player, "menu.item-locked");
            return;
        }
        if (state.phase() == Phase.EDIT) {
            plugin.messages().actionBar(player, "beddefense.preview.not-in-editor");
            return;
        }
        // A cancelled drop goes back into the inventory after the event —
        // stashing the inventory now would lose that stack, so act a tick on.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.sessions().get(player.getUniqueId()) == session && live(session)) {
                service().previewOrGuide(player, session, state);
            }
        });
    }

    // ------------------------------------------------------------------- items

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        String role = service().itemRole(event.getItem());
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PracticeSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !(session.mode() instanceof BedDefenseMode) || !live(session)) {
            return;
        }
        BedDefenseState state = BedDefenseMode.state(session);
        if (state == null) {
            return;
        }
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick && !role.equals(BedDefenseService.ITEM_MENU)) {
            return; // preview controls answer to right click only
        }
        switch (role) {
            case BedDefenseService.ITEM_MENU -> {
                if (!rightClick) {
                    return;
                }
                if (state.phase() == Phase.EDIT) {
                    new BedDefenseEditMenu(plugin, player, session, state).open();
                } else {
                    new BedDefenseSessionMenu(plugin, player, session, state).open();
                }
            }
            case BedDefenseService.ITEM_PREVIOUS -> service().previewStep(player, state, -1);
            case BedDefenseService.ITEM_NEXT -> service().previewStep(player, state, 1);
            case BedDefenseService.ITEM_PLAY -> service().previewToggle(player, state);
            case BedDefenseService.ITEM_GUIDED -> service().enterGuided(player, session, state, false);
            case BedDefenseService.ITEM_EXIT -> service().exitPreview(player, session, state, true);
            default -> {
            }
        }
    }

    /** The mode's items stay put: no moving, swapping or stacking them away. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || stateOf(player) == null) {
            return;
        }
        BedDefenseState state = stateOf(player);
        if (state.phase() == Phase.PREVIEW) {
            event.setCancelled(true); // the preview hotbar is not an inventory
            return;
        }
        if (service().itemRole(event.getCurrentItem()) != null
                || service().itemRole(event.getCursor()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        BedDefenseState state = stateOf(event.getPlayer());
        if (state == null) {
            return;
        }
        if (state.phase() == Phase.PREVIEW
                || service().itemRole(event.getMainHandItem()) != null
                || service().itemRole(event.getOffHandItem()) != null) {
            event.setCancelled(true);
        }
    }
}
