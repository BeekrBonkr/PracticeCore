package me.beekrbonkr.practicecore.spectate;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spectator mode: watch another player's practice session from inside their
 * arena without being able to touch it.
 *
 * A spectator is deliberately <em>not</em> a session — they hold no grid slot,
 * no kit and no timer, so every session-gated listener (block edits, triggers,
 * mode logic, explosion knockback) already ignores them for free. What they
 * are instead: flying, invulnerable, non-collidable, hidden from everyone but
 * other spectators, carrying three tagged hotbar tools (teleport to target,
 * target picker, leave), and leashed to the target's arena by {@link #tick}.
 *
 * The pre-spectate state reuses the session {@link PlayerSnapshot} store, so
 * a crash mid-spectate restores the player on next login exactly like a
 * crashed session would. Ending spectating restores the snapshot in full.
 */
public final class SpectateService {

    public static final String ITEM_TELEPORT = "teleport";
    public static final String ITEM_MENU = "menu";
    public static final String ITEM_LEAVE = "leave";

    private final PracticeCorePlugin plugin;
    private final NamespacedKey itemKey;
    /** Spectator → the player they are watching. */
    private final Map<UUID, UUID> targets = new HashMap<>();
    private BukkitTask task;

    public SpectateService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "spectate-item");
    }

    // ------------------------------------------------------------ lifecycle

    public void startTask() {
        if (!plugin.pcConfig().spectateEnabled()) {
            return;
        }
        long period = plugin.pcConfig().spectateTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    /** Picks up a changed spectate.update-ticks after /practice reload. */
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

    // -------------------------------------------------------------- queries

    public boolean isSpectator(UUID player) {
        return targets.containsKey(player);
    }

    /** The watched player's UUID, or null when this player is not spectating. */
    public UUID targetOf(UUID spectator) {
        return targets.get(spectator);
    }

    public Set<UUID> spectators() {
        return Set.copyOf(targets.keySet());
    }

    public int watcherCount(UUID target) {
        int count = 0;
        for (UUID watched : targets.values()) {
            if (watched.equals(target)) {
                count++;
            }
        }
        return count;
    }

    /** The online spectators currently watching this player. */
    public List<Player> watchersOf(UUID target) {
        List<Player> watchers = new java.util.ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : targets.entrySet()) {
            if (!entry.getValue().equals(target)) {
                continue;
            }
            Player watcher = Bukkit.getPlayer(entry.getKey());
            if (watcher != null && watcher.isOnline()) {
                watchers.add(watcher);
            }
        }
        return watchers;
    }

    /** The MBedwars-style tag on a spectate hotbar tool, or null for plain items. */
    public String itemTypeOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- start

    /**
     * Starts watching {@code target}, or retargets an already-spectating
     * player without touching their stored snapshot. A spectator's own
     * running session is ended (fully restored) first — the snapshot captured
     * for spectating must be their real pre-practice state, never a kit.
     */
    public void start(Player spectator, Player target) {
        Messages msg = plugin.messages();
        UUID id = spectator.getUniqueId();
        if (!plugin.pcConfig().spectateEnabled()) {
            msg.send(spectator, "spectate.disabled");
            return;
        }
        if (spectator.equals(target)) {
            msg.send(spectator, "spectate.self");
            return;
        }
        if (plugin.setup().isAdmin(id)) {
            msg.send(spectator, "session.setup-in-progress");
            return;
        }
        if (plugin.sessions().isPreparingJoin(id)) {
            // Their own arena paste is in flight — becoming a spectator now
            // would let the finished paste hand them a session on top.
            msg.send(spectator, "session.preparing");
            return;
        }
        PracticeSession targetSession = plugin.sessions().get(target.getUniqueId());
        if (targetSession == null
                || targetSession.state() == SessionState.PREPARING
                || targetSession.state() == SessionState.ENDING) {
            msg.send(spectator, "spectate.target-not-practicing", "player", target.getName());
            return;
        }
        boolean switching = targets.containsKey(id);
        if (!switching) {
            if (plugin.sessions().get(id) != null) {
                // Their own run ends first, with a full restore — the state
                // captured below must be the real one, not an arena kit.
                plugin.sessions().leave(spectator, true);
            }
            if (!plugin.snapshots().has(id)) {
                plugin.snapshots().save(id, PlayerSnapshot.capture(spectator));
            }
            targets.put(id, target.getUniqueId());
            applySpectatorState(spectator);
            hideFromOthers(spectator);
            plugin.boards().create(spectator);
        } else {
            targets.put(id, target.getUniqueId());
        }
        giveItems(spectator, target);
        // Synchronous on purpose: the target session pins its chunks, so the
        // load is free — and the result says whether some other plugin
        // (world access control, region entry) vetoed the teleport. Admins
        // usually bypass those, which would make a swallowed failure here
        // look like a players-only bug.
        if (!spectator.teleport(perch(target))) {
            targets.remove(id);
            restore(spectator, true);
            msg.send(spectator, "spectate.entry-blocked");
            plugin.getLogger().warning("Another plugin cancelled " + spectator.getName()
                    + "'s teleport into the practice world — spectating aborted. "
                    + "Check world access / region entry rules for the practice world.");
            return;
        }
        msg.send(spectator, "spectate.started",
                "target", target.getName(),
                "arena", targetSession.template().displayName());
        msg.send(target, "spectate.watcher-joined", "player", spectator.getName());
        plugin.sounds().play(spectator, "spectate.start");
    }

    // ----------------------------------------------------------------- stop

    /**
     * Ends spectating and restores the snapshot. {@code restoreLocation} is
     * false when another plugin's teleport already decided where the player
     * goes. {@code messageKey} may be null (quit, shutdown).
     *
     * @return false when the player was not spectating
     */
    public boolean stop(Player spectator, boolean restoreLocation, String messageKey) {
        UUID id = spectator.getUniqueId();
        UUID watched = targets.remove(id);
        if (watched == null) {
            return false;
        }
        restore(spectator, restoreLocation);
        if (messageKey != null) {
            plugin.messages().send(spectator, messageKey);
        }
        Player target = Bukkit.getPlayer(watched);
        if (target != null && target.isOnline()) {
            plugin.messages().send(target, "spectate.watcher-left",
                    "player", spectator.getName());
        }
        return true;
    }

    /** The un-spectate mechanics shared by stop() and a failed start. */
    private void restore(Player spectator, boolean restoreLocation) {
        plugin.boards().remove(spectator);
        showToOthers(spectator);
        // Not part of the snapshot — undone by hand before it applies.
        spectator.setInvulnerable(false);
        spectator.setCollidable(true);
        plugin.snapshots().load(spectator.getUniqueId()).ifPresent(snapshot ->
                snapshot.apply(spectator, restoreLocation));
        plugin.snapshots().delete(spectator.getUniqueId());
    }

    /**
     * The voluntary exits (the bed tool, /practice spectate leave) and a
     * target who stopped practicing: the spectator is restored, then dropped
     * straight back into the default arena — on a practice server that is
     * "home". When no arena is available to them the restore alone stands.
     *
     * @return false when the player was not spectating
     */
    public boolean stopIntoDefaultArena(Player spectator, String messageKey) {
        if (!stop(spectator, true, messageKey)) {
            return false;
        }
        if (!plugin.pcConfig().spectateJoinDefaultArena()
                || !spectator.hasPermission("practicecore.use")) {
            return true;
        }
        me.beekrbonkr.practicecore.template.ArenaTemplate arena =
                plugin.templates().defaultFor(spectator);
        if (arena == null) {
            return true;
        }
        // Deferred a tick: stop() may run inside an event or the sweep, and
        // join teleports — never mid-unwind.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (spectator.isOnline()
                    && plugin.sessions().get(spectator.getUniqueId()) == null
                    && !isSpectator(spectator.getUniqueId())) {
                plugin.sessions().join(spectator, arena);
            }
        });
        return true;
    }

    /** onDisable and forced reloads: everyone restored synchronously. */
    public void endAllSync() {
        for (UUID id : List.copyOf(targets.keySet())) {
            Player spectator = Bukkit.getPlayer(id);
            if (spectator != null && spectator.isOnline()) {
                stop(spectator, true, null);
            } else {
                targets.remove(id);
            }
        }
    }

    // ---------------------------------------------------------------- tools

    /** Compass tool: snap back to the target. */
    public void teleportToTarget(Player spectator) {
        UUID watched = targets.get(spectator.getUniqueId());
        Player target = watched == null ? null : Bukkit.getPlayer(watched);
        if (target == null) {
            return;
        }
        spectator.teleport(perch(target));
        plugin.sounds().play(spectator, "spectate.teleport");
    }

    // ----------------------------------------------------------- the sweep

    /**
     * Once a second: drop spectators whose target stopped practicing, keep
     * flight on (something may have toggled it), and leash everyone to their
     * target's arena — following them automatically through arena switches.
     */
    private void tick() {
        for (Map.Entry<UUID, UUID> entry : Map.copyOf(targets).entrySet()) {
            Player spectator = Bukkit.getPlayer(entry.getKey());
            if (spectator == null || !spectator.isOnline()) {
                continue; // the quit listener owns this case
            }
            Player target = Bukkit.getPlayer(entry.getValue());
            PracticeSession session = plugin.sessions().get(entry.getValue());
            if (target == null || !target.isOnline() || session == null
                    || session.state() == SessionState.ENDING) {
                stopIntoDefaultArena(spectator, "spectate.target-left");
                continue;
            }
            if (!spectator.getAllowFlight()) {
                spectator.setAllowFlight(true);
                spectator.setFlying(true);
            }
            if (session.state() == SessionState.PREPARING) {
                continue; // mid arena-switch — the next pass leashes to the new bounds
            }
            Location loc = spectator.getLocation();
            BoundingBox leash = session.bounds().clone()
                    .expand(plugin.pcConfig().spectateLeashMargin());
            if (!plugin.worldService().isPracticeWorld(loc.getWorld())) {
                continue; // an outbound teleport — the teleport listener owns it
            }
            if (!leash.contains(loc.toVector())) {
                spectator.teleport(perch(target));
                plugin.messages().actionBar(spectator, "spectate.leash");
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private Location perch(Player target) {
        Location loc = target.getLocation().clone().add(0, 1, 0);
        loc.setPitch(Math.max(loc.getPitch(), 20)); // arrive looking slightly down at them
        return loc;
    }

    private void applySpectatorState(Player spectator) {
        spectator.closeInventory();
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.ADVENTURE);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
        spectator.setInvulnerable(true);
        spectator.setCollidable(false);
        PlayerSnapshot.healToFull(spectator);
        spectator.setFoodLevel(20);
        spectator.setSaturation(20);
        spectator.setFireTicks(0);
        spectator.setArrowsInBody(0);
        spectator.setFallDistance(0);
        for (PotionEffect effect : spectator.getActivePotionEffects()) {
            spectator.removePotionEffect(effect.getType());
        }
    }

    /**
     * Invisible to everyone except fellow spectators — the watched player
     * must never have a ghost in their peripheral vision mid-run.
     */
    private void hideFromOthers(Player spectator) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(spectator) && !isSpectator(online.getUniqueId())) {
                online.hidePlayer(plugin, spectator);
            }
        }
    }

    private void showToOthers(Player spectator) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, spectator);
        }
    }

    /** A fresh joiner must not see the spectators already flying around. */
    public void hideAllFrom(Player joiner) {
        for (UUID id : targets.keySet()) {
            Player spectator = Bukkit.getPlayer(id);
            if (spectator != null && !spectator.equals(joiner)) {
                joiner.hidePlayer(plugin, spectator);
            }
        }
    }

    private void giveItems(Player spectator, Player target) {
        var config = plugin.pcConfig();
        PlayerInventory inv = spectator.getInventory();
        inv.clear();
        inv.setItem(config.spectateItemSlot(ITEM_TELEPORT),
                tool(config.spectateItemMaterial(ITEM_TELEPORT), "spectate.item.teleport",
                        ITEM_TELEPORT, "target", target.getName()));
        inv.setItem(config.spectateItemSlot(ITEM_MENU),
                tool(config.spectateItemMaterial(ITEM_MENU), "spectate.item.menu", ITEM_MENU));
        inv.setItem(config.spectateItemSlot(ITEM_LEAVE),
                tool(config.spectateItemMaterial(ITEM_LEAVE), "spectate.item.leave", ITEM_LEAVE));
        inv.setHeldItemSlot(config.spectateItemSlot(ITEM_TELEPORT));
    }

    private ItemStack tool(Material material, String key, String type, String... placeholders) {
        return ItemBuilder.of(material)
                .name(plugin.messages().name(key + ".name", placeholders))
                .lore(plugin.messages().lore(key + ".lore", placeholders))
                .hideAttributes()
                .edit(meta -> meta.getPersistentDataContainer()
                        .set(itemKey, PersistentDataType.STRING, type))
                .build();
    }
}
