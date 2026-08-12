package me.beekrbonkr.practicecore.session;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.grid.Slot;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.snapshot.PlayerSnapshot;
import me.beekrbonkr.practicecore.stats.LeaderboardService;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SessionManager {

    private final PracticeCorePlugin plugin;
    private final Map<UUID, PracticeSession> sessions = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> internalGamemode = new HashSet<>();

    public SessionManager(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public PracticeSession get(UUID player) {
        return sessions.get(player);
    }

    public Collection<PracticeSession> all() {
        return sessions.values();
    }

    public boolean isInternalTeleport(UUID player) {
        return internalTeleports.contains(player);
    }

    public boolean isInternalGamemode(UUID player) {
        return internalGamemode.contains(player);
    }

    // ------------------------------------------------------------------ join

    /**
     * Joins an arena, or switches to it from another one.
     *
     * Switching keeps the player's original snapshot: it is what they were
     * before any of this started, and it is what must eventually be restored,
     * so it is never re-captured from a player who is already standing in an
     * arena wearing a kit.
     *
     * Every validation happens before the previous session is torn down, so
     * "you can't join that" never costs someone the arena they were in.
     */
    public void join(Player player, ArenaTemplate template) {
        UUID id = player.getUniqueId();
        Messages msg = plugin.messages();
        PracticeSession current = sessions.get(id);
        if (current != null) {
            if (current.state() == SessionState.PREPARING || current.state() == SessionState.ENDING) {
                msg.send(player, "session.preparing");
                return;
            }
            if (current.template().name().equals(template.name())) {
                restart(player); // "switching" to where you already are is a restart
                return;
            }
        }
        if (plugin.setup().isAdmin(id)) {
            msg.send(player, "session.setup-in-progress");
            return;
        }
        if (!template.isComplete()) {
            msg.send(player, "arena.incomplete", "arena", template.name());
            return;
        }
        if (!player.hasPermission("practicecore.use")) {
            msg.send(player, "permission.use");
            return;
        }
        if (!plugin.templates().canUse(player, template)) {
            // The GUI hides or greys these out; the command path has to say so.
            msg.send(player, "arena.locked", "arena", template.displayName());
            return;
        }
        // Validate everything a hand-edited arena.yml could get wrong before a
        // slot is taken — an aborted join must leave no arena behind.
        me.beekrbonkr.practicecore.mode.Mode mode = plugin.modes().of(template);
        boolean needsTrigger = mode.requiresTrigger();
        if (template.spawnOffset() == null || (needsTrigger && !template.hasTriggers())) {
            msg.send(player, "arena.broken");
            return;
        }
        String refusal = mode.validateJoin(plugin, player, template);
        if (refusal != null) {
            msg.send(player, refusal);
            return;
        }
        Map<me.beekrbonkr.practicecore.template.ArenaTrigger, BlockData> triggerData =
                new java.util.LinkedHashMap<>();
        if (needsTrigger) {
            for (me.beekrbonkr.practicecore.template.ArenaTrigger arenaTrigger : template.triggers()) {
                try {
                    triggerData.put(arenaTrigger, Bukkit.createBlockData(arenaTrigger.blockData()));
                } catch (IllegalArgumentException e) {
                    msg.send(player, "arena.broken-trigger");
                    plugin.getLogger().severe("Invalid trigger block-data for '" + template.name()
                            + "': " + arenaTrigger.blockData());
                    return;
                }
            }
        }
        Clipboard clipboard;
        try {
            clipboard = plugin.schematics().load(template.schematicFile());
        } catch (IOException e) {
            msg.send(player, "arena.schematic-failed");
            plugin.getLogger().severe("Failed to load schematic for '" + template.name() + "': " + e.getMessage());
            return;
        }
        World world = plugin.worldService().world();
        if (world == null) {
            msg.send(player, "world.unavailable");
            return;
        }

        Slot slot = plugin.allocator().acquire(id);
        int spacing = plugin.pcConfig().gridSpacing();
        Location origin = new Location(world,
                (long) slot.gridX() * spacing, plugin.pcConfig().baseY(), (long) slot.gridZ() * spacing);

        BoundingBox bounds;
        try {
            bounds = plugin.schematics().paste(clipboard, origin);
        } catch (WorldEditException e) {
            plugin.allocator().release(slot);
            msg.send(player, "arena.paste-failed");
            plugin.getLogger().severe("Paste failed for '" + template.name() + "': " + e.getMessage());
            return;
        }
        slot.occupy();

        // The finish triggers are not part of the schematic (they were placed
        // during setup, after the //copy) — the plugin stamps them in.
        Map<Location, me.beekrbonkr.practicecore.template.TriggerType> triggers =
                new java.util.LinkedHashMap<>();
        for (Map.Entry<me.beekrbonkr.practicecore.template.ArenaTrigger, BlockData> entry
                : triggerData.entrySet()) {
            Location loc = entry.getKey().location(origin);
            loc.getBlock().setBlockData(entry.getValue(), false);
            triggers.put(loc, entry.getKey().type());
        }

        addChunkTickets(world, bounds);

        PracticeSession session = new PracticeSession(id, template, mode, slot, origin, bounds,
                mode.spawnLocation(plugin, player, template, origin), triggers);
        String statsKey = mode.statsKey(plugin, session);
        session.setBestTimeMs(plugin.stats().bestMs(id, statsKey));
        session.setLastTimeMs(plugin.stats().lastMs(id, statsKey));
        // Replaces `current` in the map; `current` is still ours to clean up.
        sessions.put(id, session);

        internalTeleports.add(id);
        player.teleportAsync(session.spawn()).whenComplete((ok, err) -> {
            internalTeleports.remove(id);
            if (err != null || !Boolean.TRUE.equals(ok)
                    || !player.isOnline() || sessions.get(id) != session) {
                abortJoin(player, session, current);
                return;
            }
            if (current != null) {
                releaseArena(current);
            }
            // First join only: the snapshot already on disk is the truth for
            // anyone switching, and overwriting it would save a kit as their
            // "real" inventory.
            if (!plugin.snapshots().has(id)) {
                plugin.snapshots().save(id, PlayerSnapshot.capture(player));
            }
            applyPracticeState(player, session);
            session.setState(SessionState.READY);
            session.mode().onReady(plugin, player, session);
            plugin.boards().create(player);
            if (current != null) {
                msg.send(player, "session.switched", "arena", template.displayName());
            } else {
                msg.send(player, "session.ready", "arena", template.displayName());
            }
        });
    }

    /**
     * The new arena could not be entered. Tear it down, and put the player
     * back — either into the session they were already in, or, if that is no
     * longer possible, all the way back to their pre-practice state.
     */
    private void abortJoin(Player player, PracticeSession failed, PracticeSession previous) {
        UUID id = player.getUniqueId();
        boolean wasCurrent = sessions.remove(id, failed);
        cleanupArena(failed, true);
        if (previous != null && wasCurrent && player.isOnline()
                && previous.state() != SessionState.ENDING && sessions.get(id) == null) {
            // Their old arena was never touched — hand it straight back.
            sessions.put(id, previous);
            plugin.messages().send(player, "session.teleport-failed");
            return;
        }
        if (previous != null) {
            releaseArena(previous);
        }
        if (!player.isOnline()) {
            return;
        }
        if (plugin.snapshots().has(id)) {
            plugin.settings().clearOnExit(player);
            plugin.snapshots().load(id).ifPresent(snapshot -> snapshot.apply(player, true));
            plugin.snapshots().delete(id);
            plugin.messages().send(player, "session.restored-after-failure");
        } else {
            plugin.messages().send(player, "session.teleport-failed");
        }
    }

    /** Frees a superseded session's arena without touching the player. */
    private void releaseArena(PracticeSession session) {
        notifyEnd(session);
        session.setState(SessionState.ENDING);
        cleanupArena(session, false);
    }

    /**
     * One-shot end-of-session mode hook. Runs before the player's snapshot is
     * restored on every path that ends a session, so a mode can still see the
     * in-run inventory and cancel anything it scheduled.
     */
    private void notifyEnd(PracticeSession session) {
        if (!session.markEndNotified()) {
            return;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        session.mode().onSessionEnd(plugin, player, session);
    }

    /**
     * Ends a session for the setup wizard: the arena is freed and the board
     * removed, but the player is left exactly where they stand and their
     * snapshot is deliberately kept, so the wizard restores them to their real
     * pre-practice state when it closes rather than bouncing them twice.
     *
     * @return the arena they were in, or null if they were not practicing
     */
    public String handOffToSetup(Player player) {
        PracticeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return null;
        }
        plugin.boards().remove(player);
        releaseArena(session);
        return session.template().displayName();
    }

    private void applyPracticeState(Player player, PracticeSession session) {
        internalGamemode.add(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        internalGamemode.remove(player.getUniqueId());
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        plugin.settings().applyToSession(player);
        giveKit(player, session);
    }

    private void giveKit(Player player, PracticeSession session) {
        ArenaTemplate template = session.template();
        player.getInventory().clear();
        for (Map.Entry<Integer, ItemStack> entry
                : session.mode().arrangeKit(plugin, player, template).entrySet()) {
            player.getInventory().setItem(entry.getKey(),
                    plugin.settings().recolor(player.getUniqueId(), entry.getValue().clone()));
        }
        // Retro-fit for kits saved before the menu item existed.
        if (plugin.pcConfig().menuItemEnabled() && plugin.pcConfig().menuItemForceInKit()
                && !plugin.menuItems().kitContainsMenuItem(template.kit())) {
            plugin.menuItems().forceIntoInventory(player);
        }
    }

    // -------------------------------------------------------- finish / fail

    public void finish(Player player, PracticeSession session) {
        if (session.state() != SessionState.ACTIVE) {
            return;
        }
        long millis = session.elapsedMs();
        session.setState(SessionState.RESETTING);
        session.setLastTimeMs(millis);

        // Modes with several boards per arena qualify both of these (rush:
        // "map#bed" / "Map (Bed)"); everything below keys off them.
        String arena = session.mode().statsKey(plugin, session);
        String displayName = session.mode().runDisplayName(plugin, session);
        LeaderboardService.Entry previousRecord = plugin.leaderboards().record(arena);
        boolean pbEligible = !session.template().requireBlocksForPb() || session.tracker().count() > 0;
        long previousBest = session.bestTimeMs();
        boolean pb = plugin.stats().record(player.getUniqueId(), arena, millis, pbEligible);
        Messages msg = plugin.messages();
        if (pb) {
            session.setBestTimeMs(millis);
            msg.send(player, "run.finished-pb", "time", TimeFormat.precise(millis),
                    "arena", displayName);
        } else if (previousBest >= 0) {
            msg.send(player, "run.finished", "time", TimeFormat.precise(millis),
                    "best", TimeFormat.precise(previousBest),
                    "arena", displayName);
        } else {
            msg.send(player, "run.finished-first", "time", TimeFormat.precise(millis),
                    "arena", displayName);
        }
        announceFinish(player, session, millis, pb, previousBest, displayName);
        if (pb) {
            boolean recordAnnounced = announceRecord(player, session, millis, previousRecord,
                    arena, displayName);
            // A subtle server-wide note for an improved personal best — but
            // never on top of the record fanfare, and not for first finishes.
            if (!recordAnnounced && previousBest >= 0 && plugin.pcConfig().broadcastPbs()) {
                plugin.messages().broadcast("run.pb-broadcast",
                        "player", player.getName(),
                        "arena", displayName,
                        "time", TimeFormat.precise(millis),
                        "improvement", TimeFormat.precise(previousBest - millis));
            }
        }
        resetArena(player, session);
    }

    /** Title, subtitle and sound on crossing the line. */
    private void announceFinish(Player player, PracticeSession session, long millis,
                                boolean pb, long previousBest, String displayName) {
        if (plugin.pcConfig().finishTitle()) {
            String subtitleKey;
            String delta = "";
            if (pb) {
                subtitleKey = "run.subtitle.pb";
            } else if (previousBest >= 0) {
                subtitleKey = "run.subtitle.behind";
                delta = TimeFormat.precise(millis - previousBest);
            } else {
                subtitleKey = "run.subtitle.first";
            }
            plugin.messages().title(player,
                    pb ? "run.title.pb" : "run.title.normal", subtitleKey,
                    "time", TimeFormat.precise(millis),
                    "delta", delta,
                    "arena", displayName);
        }
        if (plugin.pcConfig().sounds()) {
            player.playSound(player.getLocation(),
                    pb ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    0.8f, pb ? 1.4f : 1.8f);
        }
    }

    /**
     * Server-wide shout plus a small chime when someone takes the #1 spot on
     * an arena.
     *
     * @return true when the record was actually announced
     */
    private boolean announceRecord(Player player, PracticeSession session, long millis,
                                   LeaderboardService.Entry previousRecord,
                                   String arena, String displayName) {
        if (!plugin.pcConfig().broadcastRecords()) {
            return false;
        }
        if (plugin.leaderboards().rank(arena, player.getUniqueId()) != 1) {
            return false;
        }
        if (previousRecord != null && previousRecord.uuid().equals(player.getUniqueId())) {
            return false; // they already held it — beating yourself isn't news
        }
        if (previousRecord == null) {
            plugin.messages().broadcast("run.record-broadcast",
                    "player", player.getName(),
                    "arena", displayName,
                    "time", TimeFormat.precise(millis));
        } else {
            plugin.messages().broadcast("run.record-broadcast-beaten",
                    "player", player.getName(),
                    "arena", displayName,
                    "time", TimeFormat.precise(millis),
                    "previous-holder", previousRecord.displayName(),
                    "delta", TimeFormat.precise(previousRecord.millis() - millis));
        }
        if (plugin.pcConfig().sounds()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.6f);
            }
        }
        return true;
    }

    public void fail(Player player, PracticeSession session) {
        if (session.state() != SessionState.ACTIVE && session.state() != SessionState.READY) {
            return;
        }
        session.setState(SessionState.RESETTING);
        plugin.messages().send(player, "run.failed");
        resetArena(player, session);
    }

    /** Voluntary restart from the menu — same reset, no failure message. */
    public void restart(Player player) {
        PracticeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().send(player, "session.not-practicing");
            return;
        }
        if (session.state() != SessionState.ACTIVE && session.state() != SessionState.READY) {
            return;
        }
        session.setState(SessionState.RESETTING);
        resetArena(player, session);
        plugin.messages().send(player, "run.restarted",
                "arena", session.template().displayName());
    }

    private void resetArena(Player player, PracticeSession session) {
        session.mode().onArenaReset(plugin, player, session);
        session.tracker().revertAll();
        clearNonPlayerEntities(session);
        session.resetTimer();
        giveKit(player, session);
        internalTeleports.add(player.getUniqueId());
        player.teleport(session.spawn());
        internalTeleports.remove(player.getUniqueId());
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        session.setState(SessionState.READY);
        session.mode().onReady(plugin, player, session);
        // The stats key can change between runs (rush objective switched via
        // restart) — re-read the cached bests from whatever board is now live.
        String statsKey = session.mode().statsKey(plugin, session);
        session.setBestTimeMs(plugin.stats().bestMs(session.playerId(), statsKey));
        session.setLastTimeMs(plugin.stats().lastMs(session.playerId(), statsKey));
    }

    // ---------------------------------------------------------------- leave

    /**
     * Ends the session and restores the player. {@code restoreLocation} is
     * false when another plugin's teleport already decided where the player
     * goes (we restore everything else and let that teleport win).
     */
    public void leave(Player player, boolean restoreLocation) {
        PracticeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        notifyEnd(session);
        session.setState(SessionState.ENDING);
        plugin.boards().remove(player);
        plugin.settings().clearOnExit(player);
        plugin.snapshots().load(player.getUniqueId()).ifPresent(snapshot ->
                snapshot.apply(player, restoreLocation));
        plugin.snapshots().delete(player.getUniqueId());
        cleanupArena(session, false);
        plugin.messages().send(player, "session.ended",
                "arena", session.template().displayName());
    }

    public void handleQuit(Player player) {
        PracticeSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            notifyEnd(session);
            session.setState(SessionState.ENDING);
            plugin.boards().remove(player);
            plugin.settings().clearOnExit(player);
            // Mutations during PlayerQuitEvent persist to the player's data.
            plugin.snapshots().load(player.getUniqueId()).ifPresent(snapshot ->
                    snapshot.apply(player, true));
            plugin.snapshots().delete(player.getUniqueId());
            cleanupArena(session, false);
        }
        plugin.stats().unload(player.getUniqueId());
    }

    /** onDisable: restore everyone synchronously; the scheduler is gone. */
    public void endAllSync() {
        for (PracticeSession session : List.copyOf(sessions.values())) {
            sessions.remove(session.playerId());
            notifyEnd(session);
            session.setState(SessionState.ENDING);
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                plugin.boards().remove(player);
                plugin.settings().clearOnExit(player);
                plugin.snapshots().load(session.playerId()).ifPresent(snapshot ->
                        snapshot.apply(player, true));
                plugin.snapshots().delete(session.playerId());
            }
            cleanupArena(session, true);
        }
    }

    // -------------------------------------------------------------- cleanup

    private void cleanupArena(PracticeSession session, boolean immediateRelease) {
        notifyEnd(session); // backstop — a no-op on every path that already ran it
        Slot slot = session.slot();
        slot.markDirty();
        World world = plugin.worldService().world();
        session.tracker().revertAll();
        plugin.schematics().erase(world, session.bounds());
        clearNonPlayerEntities(session);
        removeChunkTickets(world, session.bounds());
        if (immediateRelease || !plugin.isEnabled()) {
            plugin.allocator().release(slot);
        } else {
            // With FAWE the erase may still be flushing off-thread; hold the
            // slot briefly so a new paste can never race the wipe.
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.allocator().release(slot), 60L);
        }
    }

    private void clearNonPlayerEntities(PracticeSession session) {
        World world = plugin.worldService().world();
        for (Entity entity : world.getNearbyEntities(session.bounds().clone().expand(4))) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        // A pearl still in flight after the session ends must never land.
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Projectile projectile
                        && player.equals(projectile.getShooter())) {
                    entity.remove();
                }
            }
        }
    }

    private void addChunkTickets(World world, BoundingBox box) {
        for (int cx = ((int) box.getMinX()) >> 4; cx <= ((int) box.getMaxX()) >> 4; cx++) {
            for (int cz = ((int) box.getMinZ()) >> 4; cz <= ((int) box.getMaxZ()) >> 4; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    private void removeChunkTickets(World world, BoundingBox box) {
        for (int cx = ((int) box.getMinX()) >> 4; cx <= ((int) box.getMaxX()) >> 4; cx++) {
            for (int cz = ((int) box.getMinZ()) >> 4; cz <= ((int) box.getMaxZ()) >> 4; cz++) {
                world.removePluginChunkTicket(cx, cz, plugin);
            }
        }
    }
}
