package me.beekrbonkr.practicecore.session;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a practicing player's inventory honest: nothing but the exact kit.
 *
 * Whatever another plugin, an operator /give or a crafting exploit slips in is
 * swept back out on a fixed schedule. Allowed are the kit's own materials, the
 * player's chosen wool recolor of them, and the tagged menu item — everything
 * else is removed and the player told once per sweep.
 */
public final class InventoryValidator {

    private final PracticeCorePlugin plugin;
    private BukkitTask task;

    public InventoryValidator(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        if (!plugin.pcConfig().validateInventory()) {
            return;
        }
        int ticks = plugin.pcConfig().validateInventoryTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, ticks, ticks);
    }

    /** Picks up changed validation settings after /practice reload. */
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

    private void sweepAll() {
        for (PracticeSession session : plugin.sessions().all()) {
            SessionState state = session.state();
            if (state != SessionState.READY && state != SessionState.ACTIVE) {
                continue;
            }
            if (!session.mode().validatesInventory()) {
                continue; // open-ended economies (rush shop) manage themselves
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                sweep(player, session);
            }
        }
    }

    /** Removes anything that is not a kit item; true when something was taken. */
    public boolean sweep(Player player, PracticeSession session) {
        Set<Material> allowed = allowedMaterials(player, session);
        ItemStack[] contents = player.getInventory().getContents();
        boolean stripped = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (plugin.menuItems().isMenuItem(item)) {
                continue; // always legitimate, however it got there
            }
            if (!allowed.contains(item.getType())) {
                player.getInventory().setItem(slot, null);
                stripped = true;
            }
        }
        if (stripped) {
            plugin.messages().actionBar(player, "inventory.stripped");
        }
        return stripped;
    }

    /**
     * The kit's materials, what they turn into when used (an emptied bucket,
     * a drunk potion's bottle), and the player's wool recolor of any kit wool.
     */
    private Set<Material> allowedMaterials(Player player, PracticeSession session) {
        Set<Material> allowed = new HashSet<>();
        boolean kitHasWool = false;
        for (ItemStack item : session.template().kit().values()) {
            if (item == null) {
                continue;
            }
            allowed.add(item.getType());
            Material used = usedForm(item.getType());
            if (used != null) {
                allowed.add(used);
            }
            kitHasWool |= item.getType().name().endsWith("_WOOL");
        }
        if (kitHasWool) {
            DyeColor color = plugin.settings().woolColor(player.getUniqueId());
            if (color != null) {
                Material recolored = me.beekrbonkr.practicecore.settings.SettingsService.woolOf(color);
                if (recolored != null) {
                    allowed.add(recolored);
                }
            }
        }
        return allowed;
    }

    /** What a kit item legitimately becomes mid-run, or null for none. */
    private static Material usedForm(Material material) {
        String name = material.name();
        if (name.endsWith("_BUCKET")) {
            return Material.BUCKET; // water/lava clutches, milk, fish buckets
        }
        if (material == Material.POTION || material == Material.HONEY_BOTTLE) {
            return Material.GLASS_BOTTLE;
        }
        if (name.endsWith("_STEW") || name.endsWith("_SOUP")) {
            return Material.BOWL;
        }
        return null;
    }
}
