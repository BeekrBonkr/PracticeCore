package me.beekrbonkr.practicecore.beddefense;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Phase;
import me.beekrbonkr.practicecore.beddefense.BedDefenseState.Target;
import me.beekrbonkr.practicecore.gui.AnvilPrompts;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.mode.BedDefenseMode;
import me.beekrbonkr.practicecore.rush.RushMapData;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import me.beekrbonkr.practicecore.template.ArenaTemplate;
import me.beekrbonkr.practicecore.util.DyeColors;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything bed defense practice does outside the mode hooks: per-player
 * selections and favorites, the round build (targets, carve-out,
 * generators, hologram), the three side phases (preview, guided, edit) with
 * their hotbar items, save/duplicate handling, and the tick that drives
 * animations and generators.
 */
public final class BedDefenseService {

    /** The node that lets a player moderate everyone's defenses: visibility, reports, deletion. */
    public static final String MODERATE_PERMISSION = "practicecore.beddefense.moderate";

    /** Hotbar item roles, stored as the PDC value of {@link #itemKey}. */
    public static final String ITEM_MENU = "menu";
    public static final String ITEM_PREVIOUS = "previous";
    public static final String ITEM_PLAY = "play";
    public static final String ITEM_NEXT = "next";
    public static final String ITEM_GUIDED = "guided";
    public static final String ITEM_EXIT = "exit";

    private static final int TICK_PERIOD = 2;

    private final PracticeCorePlugin plugin;
    private final DefenseStore store;
    private final NamespacedKey itemKey;
    /** Players whose next round enters (or leaves) the editor; value = source defense id or "". */
    private final Map<UUID, String> pendingEdit = new HashMap<>();
    private final Map<UUID, Boolean> pendingPlay = new HashMap<>();
    /** The defense the upcoming round builds, decided once per round (shuffle draws here). */
    private final Map<UUID, BedDefense> roundDefense = new HashMap<>();
    /** The previous round's defense, so a shuffle never deals the same one twice running. */
    private final Map<UUID, String> lastRound = new HashMap<>();
    /** The last bed defense map each player played, for /practice beddefense play. */
    private final Map<UUID, String> lastMap = new HashMap<>();
    private BukkitTask task;
    private BukkitTask reminderTask;
    private final BedRepair repair;

    public BedDefenseService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.store = new DefenseStore(plugin);
        this.itemKey = new NamespacedKey(plugin, "beddefense-item");
        this.repair = new BedRepair(plugin, this);
        // Bed repair boards rank rounds survived, highest first, and read
        // as "<n> rounds" wherever a board shows a value.
        plugin.leaderboards().registerScoreKeys(BedDefenseService::isRepairStatsKey,
                rounds -> plugin.messages().raw("label.rounds").replace("<count>", String.valueOf(rounds)));
    }

    public DefenseStore store() {
        return store;
    }

    /** The bed repair engine: volleys, the exposure clock, the rounds score. */
    public BedRepair repair() {
        return repair;
    }


    // --------------------------------------------------------- block kinds

    /** Resolved once per load; cleared with the shop cache on reload. */
    private List<Material> allowedKinds;

    /**
     * The block kinds a defense may be made of. With
     * {@code beddefense.blocks-from-shop} on and MBedwars present, this is
     * whatever the server's own item shop sells as blocks — so a server
     * that stocks end stone bricks instead of end stone gets bricks in the
     * editor and in practice kits, and a competitive round can always buy
     * what a defense needs. Otherwise (or when the shop yields nothing) it
     * is the {@code beddefense.blocks} list.
     */
    public List<Material> allowedKinds() {
        if (allowedKinds != null) {
            return allowedKinds;
        }
        List<Material> kinds = new ArrayList<>();
        if (plugin.pcConfig().bedDefenseBlocksFromShop()) {
            kinds.addAll(shopBlockKinds());
        }
        if (kinds.isEmpty()) {
            kinds.addAll(plugin.pcConfig().bedDefenseBlocks());
        }
        allowedKinds = List.copyOf(kinds);
        return allowedKinds;
    }

    /** Every plain block product in the MBedwars shop, as normalized kinds, shop order. */
    private List<Material> shopBlockKinds() {
        List<Material> kinds = new ArrayList<>();
        var shop = plugin.rush().shopSnapshot();
        if (shop == null) {
            return kinds;
        }
        for (var page : shop.pages()) {
            for (var entry : page.entries()) {
                for (var product : entry.products()) {
                    Material material = product.stack().getType();
                    if (product.specialType() != null || !material.isBlock()
                            || material == Material.TNT || org.bukkit.Tag.BEDS.isTagged(material)
                            || org.bukkit.Tag.SHULKER_BOXES.isTagged(material)) {
                        continue; // explosives, beds and storage are not defense blocks
                    }
                    Material kind = BlockKinds.normalize(material);
                    if (!kinds.contains(kind)) {
                        kinds.add(kind);
                    }
                }
            }
        }
        return kinds;
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------ selection

    public BedDefenseSelection rawSelection(UUID player) {
        var stats = plugin.stats();
        BedDefenseSelection defaults = BedDefenseSelection.defaults();
        String defense = stats.pref(player, "beddefense.defense", null);
        if (defense != null && !visibleTo(player, store.get(defense))) {
            // Deleted, or made private since it was chosen.
            defense = null;
        }
        return new BedDefenseSelection(
                stats.prefBool(player, "beddefense.competitive", false),
                stats.prefBool(player, "beddefense.obsidian", false),
                stats.prefBool(player, "beddefense.repair", false),
                defense,
                BedDefenseSelection.enumOr(BedDefenseSelection.Shuffle.class,
                        stats.pref(player, "beddefense.shuffle", null), defaults.shuffle()),
                BedDefenseSelection.enumOr(BedDefenseSelection.TimerStart.class,
                        stats.pref(player, "beddefense.timer-start", null), defaults.timerStart()));
    }

    /**
     * The gameplay-effective selection: competitive pins shuffle off and the
     * timer to movement. Competitive also needs somewhere to buy blocks, so
     * without the MBedwars shop it plays as practice (and says so).
     */
    public BedDefenseSelection selection(UUID player) {
        BedDefenseSelection selection = rawSelection(player).effective();
        if (selection.competitive() && !shopAvailable()) {
            return selection.withCompetitive(false);
        }
        return selection;
    }

    /** Whether the mirrored MBedwars shop can open — the only block source competitive has. */
    public boolean shopAvailable() {
        try {
            return me.beekrbonkr.practicecore.rush.MBedwarsHook.available();
        } catch (LinkageError e) {
            return false;
        }
    }

    public void saveSelection(UUID player, BedDefenseSelection selection) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("beddefense.competitive", selection.competitive());
        values.put("beddefense.obsidian", selection.obsidian());
        values.put("beddefense.repair", selection.repair());
        values.put("beddefense.defense", selection.defense());
        values.put("beddefense.shuffle", selection.shuffle().name());
        values.put("beddefense.timer-start", selection.timerStart().name());
        plugin.stats().setPrefs(player, values);
    }

    public void selectDefense(UUID player, String id) {
        saveSelection(player, rawSelection(player).withDefense(id));
    }

    // --------------------------------------------------- obsidian and repair

    /**
     * Whether a defense can be played under the given choices: any defense
     * in a plain round; in obsidian practice only one with no obsidian on
     * the bed already (see {@link BedDefense#obsidianEligible()}); in bed
     * repair only one that seals the bed with solid, non-obsidian blocks
     * (see {@link BedDefense#repairEligible()}).
     */
    public static boolean playable(BedDefenseSelection selection, BedDefense defense) {
        return defense != null
                && (!selection.obsidian() || defense.obsidianEligible())
                && (!selection.repair() || defense.repairEligible());
    }

    /** The messages.yml key refusing a defense the choices cannot run on. */
    public static String ineligibleKey(BedDefenseSelection selection) {
        return selection.repair() ? "beddefense.repair.ineligible" : "beddefense.obsidian.ineligible";
    }

    /** The defenses this player can pick for obsidian practice. */
    public List<BedDefense> obsidianPlayableBy(UUID player) {
        List<BedDefense> out = new ArrayList<>();
        for (BedDefense defense : store.playableBy(player)) {
            if (defense.obsidianEligible()) {
                out.add(defense);
            }
        }
        return out;
    }

    /** The defenses this player can pick for bed repair. */
    public List<BedDefense> repairPlayableBy(UUID player) {
        List<BedDefense> out = new ArrayList<>();
        for (BedDefense defense : store.playableBy(player)) {
            if (defense.repairEligible()) {
                out.add(defense);
            }
        }
        return out;
    }

    /** The defenses this player can pick under the given choices. */
    public List<BedDefense> playableBy(UUID player, BedDefenseSelection selection) {
        return switch (selection.variant()) {
            case OBSIDIAN -> obsidianPlayableBy(player);
            case REPAIR -> repairPlayableBy(player);
            case NORMAL -> store.playableBy(player);
        };
    }

    /** The kit tools of an obsidian round, from config, with anything unknown dropped. */
    public List<Material> obsidianTools() {
        return plugin.pcConfig().bedDefenseObsidianTools();
    }

    /**
     * Obsidian practice or bed repair with nothing it can run on — every
     * defense the player can play has obsidian on the bed already, or none
     * seals the bed — is switched off, and the player told, so the round
     * falls back to an ordinary one rather than into the editor.
     * @return true when it was switched off
     */
    private boolean dropVariantIfNothingEligible(Player player) {
        UUID id = player.getUniqueId();
        BedDefenseSelection raw = rawSelection(id);
        if (raw.variant() == BedDefenseSelection.Variant.NORMAL
                || !playableBy(id, raw).isEmpty() || store.playableBy(id).isEmpty()) {
            return false;
        }
        boolean repairing = raw.repair();
        saveSelection(id, raw.withVariant(BedDefenseSelection.Variant.NORMAL));
        msg().send(player, repairing ? "beddefense.repair.none-eligible"
                : "beddefense.obsidian.none-eligible");
        return true;
    }

    // ------------------------------------------------------------ favorites

    public List<String> favoriteIds(UUID player) {
        List<String> ids = new ArrayList<>();
        String raw = plugin.stats().pref(player, "beddefense.favorites", "");
        for (String id : raw.split(",")) {
            if (!id.isBlank() && store.get(id.trim()) != null) {
                ids.add(id.trim());
            }
        }
        return ids;
    }

    public List<BedDefense> favorites(UUID player) {
        List<BedDefense> favorites = new ArrayList<>();
        for (String id : favoriteIds(player)) {
            BedDefense defense = store.get(id);
            if (visibleTo(player, defense)) {
                favorites.add(defense);
            }
        }
        return favorites;
    }

    public boolean isFavorite(UUID player, BedDefense defense) {
        return favoriteIds(player).contains(defense.id());
    }

    /** @return true when the defense is now a favorite */
    public boolean toggleFavorite(Player player, BedDefense defense) {
        List<String> ids = favoriteIds(player.getUniqueId());
        boolean now;
        if (ids.remove(defense.id())) {
            now = false;
        } else {
            ids.add(defense.id());
            now = true;
        }
        plugin.stats().setPref(player.getUniqueId(), "beddefense.favorites", String.join(",", ids));
        msg().send(player, now ? "beddefense.favorite.added" : "beddefense.favorite.removed",
                "name", defense.name());
        plugin.sounds().play(player, now ? "beddefense.favorite" : "menu.toggle-off");
        return now;
    }

    /** @return true when the player now likes it; null when they may not (their own) */
    public Boolean toggleLike(Player player, BedDefense defense) {
        if (defense.isAuthor(player.getUniqueId())) {
            msg().send(player, "beddefense.like.own");
            plugin.sounds().play(player, "menu.deny");
            return null;
        }
        boolean now;
        if (defense.likes().remove(player.getUniqueId())) {
            now = false;
        } else {
            defense.likes().add(player.getUniqueId());
            now = true;
        }
        store.save(defense);
        msg().send(player, now ? "beddefense.like.added" : "beddefense.like.removed",
                "name", defense.name());
        plugin.sounds().play(player, now ? "beddefense.like" : "menu.toggle-off");
        return now;
    }

    // ------------------------------------------------------------- joining

    /**
     * True when this arena is a bed defense map: an admin-made template of
     * this mode whose layout has a team base with a bed. Rush maps are not
     * bed defense maps any more — the two modes keep their own arenas.
     */
    public boolean supports(ArenaTemplate template) {
        return template.mode().equals(BedDefenseMode.ID)
                && RushMapData.parse(template).playable();
    }

    /** Every visible arena bed defense practice can run on. */
    public List<ArenaTemplate> maps(Player player) {
        return plugin.templates().visibleTo(player).stream().filter(this::supports).toList();
    }

    // ----------------------------------------------------------- visibility

    /** Whether this player may moderate everyone's defenses. */
    public boolean isModerator(org.bukkit.command.CommandSender who) {
        return who.hasPermission(MODERATE_PERMISSION);
    }

    /**
     * Whether a defense exists for this player: public ones, their own, and
     * — for a moderator who is online — everything. Null-safe, so a deleted
     * id answers false.
     */
    public boolean visibleTo(UUID player, BedDefense defense) {
        if (defense == null) {
            return false;
        }
        if (defense.published() || defense.isAuthor(player)) {
            return true;
        }
        Player online = Bukkit.getPlayer(player);
        return online != null && isModerator(online);
    }

    public boolean canSee(Player player, BedDefense defense) {
        return defense != null
                && (defense.published() || defense.isAuthor(player.getUniqueId()) || isModerator(player));
    }

    /** The defenses this player may pick or act on: what they can see. */
    public List<BedDefense> visibleTo(Player player) {
        if (isModerator(player)) {
            return store.forModeration();
        }
        return store.playableBy(player.getUniqueId());
    }

    /** Flags the next round as an editing round (fresh, or from one of the player's own). */
    public void requestEdit(UUID player, String sourceId) {
        pendingEdit.put(player, sourceId == null ? "" : sourceId);
        pendingPlay.remove(player);
    }

    /** Flags the next round as a playing round — leaving the editor. */
    public void requestPlay(UUID player) {
        pendingPlay.put(player, true);
        pendingEdit.remove(player);
    }

    /** The phase the player's next round opens in, without consuming the intent. */
    public Phase upcomingPhase(UUID player, BedDefenseState state) {
        if (pendingEdit.containsKey(player)) {
            return Phase.EDIT;
        }
        if (pendingPlay.containsKey(player)) {
            return Phase.PLAY;
        }
        if (state != null && state.phase() == Phase.EDIT) {
            return Phase.EDIT;
        }
        return Phase.PLAY;
    }

    /**
     * Joins (or re-joins) a map in bed defense practice. With nothing to
     * build — no defense chosen and none available — the player is put in
     * the editor instead, and told why.
     */
    public void join(Player player, ArenaTemplate template) {
        if (!supports(template)) {
            msg().send(player, "beddefense.not-a-map");
            return;
        }
        UUID id = player.getUniqueId();
        if (upcomingPhase(id, null) == Phase.PLAY) {
            dropVariantIfNothingEligible(player);
        }
        if (upcomingPhase(id, null) == Phase.PLAY && !pendingPlay.containsKey(id)) {
            PracticeSession current = plugin.sessions().get(id);
            BedDefenseState state = current != null ? BedDefenseMode.state(current) : null;
            if (state == null || state.phase() != Phase.EDIT) {
                if (roundDefense(id) == null) {
                    requestEdit(id, null);
                    msg().send(player, store.isEmpty()
                            ? "beddefense.forced-edit" : "beddefense.no-defense");
                }
            }
        }
        lastMap.put(id, template.name());
        plugin.sessions().join(player, template);
    }

    /**
     * Plays a defense: chosen for the rounds ahead, then the current bed
     * defense session restarts on it, or the player's last map is joined,
     * or — with no map to go back to — the map picker opens.
     */
    public void play(Player player, BedDefense defense) {
        UUID id = player.getUniqueId();
        BedDefenseSelection selection = rawSelection(id);
        if (!playable(selection, defense)) {
            msg().send(player, ineligibleKey(selection), "name", defense.name());
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        selectDefense(id, defense.id());
        clearRound(id);
        PracticeSession session = plugin.sessions().get(id);
        if (session != null && session.mode() instanceof BedDefenseMode) {
            BedDefenseState state = BedDefenseMode.state(session);
            requestPlay(id);
            if (state != null && state.phase() == Phase.PREVIEW) {
                exitPreview(player, session, state, false);
            }
            plugin.sessions().restart(player);
            return;
        }
        ArenaTemplate last = lastMap(id);
        if (last != null && plugin.templates().canUse(player, last)) {
            join(player, last);
            return;
        }
        new me.beekrbonkr.practicecore.gui.BedDefenseArenaMenu(plugin, player, null).open();
    }

    /**
     * {@link #play(Player, BedDefense)} with obsidian practice switched on or
     * off first. A defense obsidian practice cannot run on is refused before
     * anything changes.
     */
    public void play(Player player, BedDefense defense, boolean obsidian) {
        play(player, defense, obsidian ? BedDefenseSelection.Variant.OBSIDIAN
                : BedDefenseSelection.Variant.NORMAL);
    }

    /**
     * {@link #play(Player, BedDefense)} in one variant — plain, obsidian or
     * repair — switched to first. A defense the variant cannot run on is
     * refused before anything changes.
     */
    public void play(Player player, BedDefense defense, BedDefenseSelection.Variant variant) {
        UUID id = player.getUniqueId();
        BedDefenseSelection selection = rawSelection(id);
        BedDefenseSelection wanted = selection.withVariant(variant);
        if (!playable(wanted, defense)) {
            msg().send(player, ineligibleKey(wanted), "name", defense.name());
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        if (selection.variant() != variant) {
            saveSelection(id, wanted);
        }
        play(player, defense);
    }

    /**
     * Opens the editor — on a defense of the player's own, or fresh with
     * null — in the current session, their last map, or via the map picker.
     */
    public void edit(Player player, BedDefense source) {
        UUID id = player.getUniqueId();
        if (source != null && !source.isAuthor(id)) {
            msg().send(player, "beddefense.not-owner");
            return;
        }
        requestEdit(id, source == null ? null : source.id());
        PracticeSession session = plugin.sessions().get(id);
        if (session != null && session.mode() instanceof BedDefenseMode) {
            BedDefenseState state = BedDefenseMode.state(session);
            if (state != null && state.phase() == Phase.PREVIEW) {
                exitPreview(player, session, state, false);
            }
            plugin.sessions().restart(player);
            return;
        }
        ArenaTemplate last = lastMap(id);
        if (last != null && plugin.templates().canUse(player, last)) {
            join(player, last);
            return;
        }
        new me.beekrbonkr.practicecore.gui.BedDefenseArenaMenu(plugin, player, null).open();
    }

    /** Out of the editor and into a round — the just-saved defense, or whatever is chosen. */
    public void leaveEditor(Player player, PracticeSession session, BedDefenseState state) {
        UUID id = player.getUniqueId();
        clearRound(id);
        if (roundDefense(id) == null) {
            msg().send(player, "beddefense.no-defense");
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        requestPlay(id);
        msg().send(player, "beddefense.edit.exit");
        plugin.sessions().restart(player);
    }

    public ArenaTemplate lastMap(UUID player) {
        String name = lastMap.get(player);
        return name == null ? null : plugin.templates().get(name);
    }

    // --------------------------------------------------------------- rounds

    /**
     * The defense the player's upcoming round builds: their chosen one, or a
     * shuffle draw from favorites / the public gallery. Decided once and
     * held until the round is over, so the kit dealt before the round and
     * the targets built at READY agree.
     */
    public BedDefense roundDefense(UUID player) {
        BedDefense held = roundDefense.get(player);
        if (held != null && store.get(held.id()) != null) {
            return held;
        }
        BedDefenseSelection selection = selection(player);
        List<BedDefense> pool = switch (selection.shuffle()) {
            case FAVORITES -> favorites(player);
            case PUBLIC -> store.published();
            case OFF -> List.of();
        };
        BedDefense picked = null;
        if (!pool.isEmpty()) {
            List<BedDefense> candidates = new ArrayList<>(pool);
            // Obsidian practice draws only from what it can run on.
            candidates.removeIf(d -> !playable(selection, d));
            String previous = lastRound.get(player);
            if (candidates.size() > 1 && previous != null) {
                candidates.removeIf(d -> d.id().equals(previous));
            }
            picked = candidates.isEmpty() ? null
                    : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        if (picked == null) {
            picked = store.get(selection.defense());
            if (!visibleTo(player, picked) || !playable(selection, picked)) {
                picked = null;
            }
        }
        if (picked == null) {
            // Nothing chosen (or it was deleted, or has obsidian on the bed
            // in obsidian practice, or does not seal it for bed repair) —
            // the first playable one.
            List<BedDefense> playable = playableBy(player, selection);
            picked = playable.isEmpty() ? null : playable.get(0);
        }
        if (picked != null) {
            roundDefense.put(player, picked);
        }
        return picked;
    }

    /** Ends the current round's draw; the next kit/READY picks afresh. */
    public void clearRound(UUID player) {
        BedDefense held = roundDefense.remove(player);
        if (held != null) {
            lastRound.put(player, held.id());
        }
    }

    /**
     * Puts the arena instance in shape for the round: resolves the base and
     * bed, then builds whatever the phase needs. Runs at READY — after the
     * join teleport and after every reset.
     */
    public void rebuild(Player player, PracticeSession session, BedDefenseState state) {
        UUID id = player.getUniqueId();
        // Every road into a bed defense session ends here — the join command,
        // the Play menu, the default arena — so this is where the last map is
        // remembered for the chat and gallery shortcuts.
        lastMap.put(id, session.template().name());
        state.setData(RushMapData.parse(session.template()));
        RushMapData data = state.data();
        String teamName = plugin.stats().pref(id, "rush.team." + session.template().name(), null);
        RushMapData.TeamBase base = data.team(teamName);
        if ((base == null || !base.playable()) && !data.playableTeams().isEmpty()) {
            base = data.playableTeams().get(0);
        }
        state.setBase(base);
        state.targets().clear();
        state.generators().clear();
        state.resetAttempt();
        removeEntities(state);
        state.previewReplaced().clear();
        if (base == null) {
            plugin.getLogger().warning("Bed defense map '" + session.template().name()
                    + "' has no playable team — rounds cannot start.");
            return;
        }
        Location origin = session.origin();
        Location head = new Location(origin.getWorld(),
                origin.getBlockX() + base.bedHead().getBlockX(),
                origin.getBlockY() + base.bedHead().getBlockY(),
                origin.getBlockZ() + base.bedHead().getBlockZ());
        state.setFrame(new DefenseFrame(head, base.bedFacing()));
        replaceBed(state, base);

        // Phase transitions requested from a menu land here.
        String editSource = pendingEdit.remove(id);
        boolean play = pendingPlay.remove(id) != null;
        if (editSource != null) {
            state.setPhase(Phase.EDIT);
            state.editSequence().clear();
            state.setEditSourceId(null);
            state.setEditName(null);
            state.setEditPublished(false);
            BedDefense source = store.get(editSource);
            if (source != null && source.isAuthor(id)) {
                state.setEditSourceId(source.id());
                state.setEditName(source.name());
                state.setEditPublished(source.published());
                pendingLoad.put(id, source.id());
            }
        } else if (play || state.phase() != Phase.EDIT) {
            state.setPhase(Phase.PLAY);
        }
        state.setSelection(selection(id));
        BedDefenseSelection raw = rawSelection(id);
        if (raw.competitive() && raw.variant() == BedDefenseSelection.Variant.NORMAL
                && !state.selection().competitive() && state.phase() != Phase.EDIT) {
            msg().send(player, "beddefense.competitive-needs-shop");
        }
        if (state.phase() != Phase.EDIT && dropVariantIfNothingEligible(player)) {
            // Every defense left has obsidian on the bed already, or none
            // seals it (the one that qualified was deleted or hidden
            // mid-session). The kit already dealt was the variant's one, so
            // the round restarts as an ordinary one rather than falling
            // into the editor.
            state.setSelection(selection(id));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.sessions().get(id) == session) {
                    plugin.sessions().restart(player);
                }
            });
            return;
        }

        if (state.phase() == Phase.EDIT) {
            rebuildEditSequence(player, session, state);
            msg().send(player, "beddefense.edit.entered",
                    "radius", String.valueOf(plugin.pcConfig().bedDefenseEditRadius()));
            return;
        }

        BedDefense defense = roundDefense(id);
        state.setDefense(defense);
        if (defense != null && defense.markPlayed(id)) {
            // Starting a round is playing it: the base an auto-hide share is
            // measured against counts everyone who tried, not only finishers.
            store.save(defense);
        }
        if (defense == null) {
            // No defense left to build (deleted mid-session) — into the editor.
            requestEdit(id, null);
            msg().send(player, "beddefense.no-defense");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.sessions().get(id) == session) {
                    plugin.sessions().restart(player);
                }
            });
            return;
        }
        boolean obsidian = state.selection().obsidian();
        boolean repairing = state.selection().repair();
        state.obsidianTargets().clear();
        for (DefenseBlock block : defense.blocks()) {
            Location loc = state.frame().toWorld(block);
            if (!session.containsBlock(loc) || state.frame().isBed(loc)) {
                // A spot past the map's edge could never be built — it is
                // simply not part of the round on this map.
                continue;
            }
            if (obsidian && state.frame().isCoverSpot(loc)) {
                // Whatever the designer put on the bed gives way to obsidian.
                continue;
            }
            state.targets().add(new Target(block, loc));
        }
        if (obsidian) {
            for (Location loc : state.frame().coverSpots()) {
                if (session.containsBlock(loc)) {
                    Vector local = state.frame().toLocal(loc);
                    state.obsidianTargets().add(new Target(new DefenseBlock(local.getBlockX(),
                            local.getBlockY(), local.getBlockZ(), Material.OBSIDIAN,
                            Material.OBSIDIAN.createBlockData().getAsString()), loc));
                }
            }
        }
        carve(session, state);
        if (obsidian) {
            prebuild(player, session, state, defense);
            if (state.introduceObsidian()) {
                msg().send(player, "beddefense.obsidian.entered", "name", defense.name(),
                        "count", String.valueOf(state.obsidianTargets().size()));
            }
        } else if (repairing) {
            // The defense stands; the sky does the breaking. Nothing is
            // bought here either, and there is no preview to hint at.
            prebuild(player, session, state, defense);
            repair.begin(player, session, state);
        } else {
            // Nothing is bought in obsidian practice: no generators, no dealer.
            armGenerators(session, state, defense);
            for (RushMapData.Dealer dealer : data.dealers()) {
                plugin.rush().spawnDealer(new Location(origin.getWorld(),
                        origin.getX() + dealer.offset().getX(),
                        origin.getY() + dealer.offset().getY(),
                        origin.getZ() + dealer.offset().getZ(),
                        dealer.yaw(), 0));
            }
        }
        if (!repairing) {
            showHologram(player, state);
        }
        if (state.phase() == Phase.GUIDED) {
            // A reset never leaves a guide behind — guided is entered on purpose.
            state.setPhase(Phase.PLAY);
        }
    }

    /** The player's own bed must stand every round — the previous one may have broken it. */
    private void replaceBed(BedDefenseState state, RushMapData.TeamBase base) {
        String facing = base.bedFacing().name().toLowerCase(Locale.ROOT);
        Location head = state.frame().head();
        Location foot = state.frame().foot();
        Material material = base.bedMaterial() != null ? base.bedMaterial() : Material.RED_BED;
        try {
            foot.getBlock().setBlockData(Bukkit.createBlockData(material,
                    "[part=foot,facing=" + facing + "]"), false);
            head.getBlock().setBlockData(Bukkit.createBlockData(material,
                    "[part=head,facing=" + facing + "]"), false);
        } catch (IllegalArgumentException ignored) {
            // A bed material the server no longer knows — the map's own stands.
        }
    }

    /**
     * Cuts the defense's footprint out of the map: every target spot that
     * holds a map block is emptied (tracked, so the reset puts it back). A
     * defense designed on an open island then fits a bed tucked against a
     * wall — the wall simply gains a hole the shape of the defense.
     */
    private void carve(PracticeSession session, BedDefenseState state) {
        List<Target> spots = new ArrayList<>(state.targets());
        spots.addAll(state.obsidianTargets());
        for (Target target : spots) {
            Block block = target.loc().getBlock();
            if (!session.containsBlock(target.loc()) || state.frame().isBed(target.loc())) {
                continue;
            }
            if (!block.getType().isAir()) {
                session.tracker().recordPlace(block, block.getBlockData());
                block.setType(Material.AIR, false);
            }
        }
    }

    /**
     * Obsidian practice starts with the whole defense standing, the blocks
     * on the bed included: the round is breaking into it. Every block is
     * tracked like one the player placed, so it can be broken and the reset
     * takes it away again. Wool follows the player's color, as their kit
     * would.
     */
    private void prebuild(Player player, PracticeSession session, BedDefenseState state,
                          BedDefense defense) {
        Material wool = kitWool(player);
        for (DefenseBlock block : defense.blocks()) {
            Location loc = state.frame().toWorld(block);
            if (!session.containsBlock(loc) || state.frame().isBed(loc)) {
                continue;
            }
            Block at = loc.getBlock();
            session.tracker().recordPlace(at, at.getBlockData());
            at.setBlockData(state.frame().toWorld(block, wool), false);
        }
    }

    /**
     * Arms the player's own base generators — the iron/gold spawners closer
     * to their spawn than to any other team's — plus emeralds when the
     * defense needs obsidian, since that is the only way to buy it.
     *
     * <p>Emeralds drop at the player's own base rather than at the map's
     * emerald spawners. Those sit in the middle, and walking out to them is
     * a bridging run, which is not what this mode practices. Exactly one is
     * armed no matter how many the map has, so the rate stays the map's
     * rather than a multiple of it.
     */
    private void armGenerators(PracticeSession session, BedDefenseState state, BedDefense defense) {
        RushMapData data = state.data();
        Location origin = session.origin();
        Vector home = state.base().spawn();
        Location goldSpot = null;
        Location ironSpot = null;
        for (RushMapData.Generator generator : data.generators()) {
            Material drops;
            int interval;
            switch (generator.type()) {
                case "iron" -> {
                    drops = Material.IRON_INGOT;
                    interval = plugin.pcConfig().rushIronIntervalTicks();
                }
                case "gold" -> {
                    drops = Material.GOLD_INGOT;
                    interval = plugin.pcConfig().rushGoldIntervalTicks();
                }
                default -> {
                    continue; // emeralds are placed below; diamonds have no part here
                }
            }
            if (!nearestTeamIs(data, generator.offset(), home)) {
                continue;
            }
            Location spot = dropSpot(origin, generator.offset());
            if (drops == Material.GOLD_INGOT) {
                goldSpot = spot;
            } else {
                ironSpot = spot;
            }
            state.generators().add(new BedDefenseState.Generator(spot, drops,
                    generator.type(), interval));
        }
        armBaseEmeralds(state, defense, origin, home, goldSpot != null ? goldSpot : ironSpot);
    }

    /**
     * One emerald generator on the player's own base gold spawner (iron, or
     * the spawn itself, when the map gives the base neither). Armed only for
     * a defense that needs obsidian, and only on a map that has emerald
     * spawners of its own — a map without them never sold obsidian.
     */
    private void armBaseEmeralds(BedDefenseState state, BedDefense defense, Location origin,
                                 Vector home, Location baseSpot) {
        if (!plugin.pcConfig().bedDefenseEmeraldForObsidian()
                || !defense.containsKind(Material.OBSIDIAN)
                || state.data().generatorsOf("emerald").isEmpty()) {
            return;
        }
        Location spot = baseSpot != null ? baseSpot : dropSpot(origin, home);
        state.generators().add(new BedDefenseState.Generator(spot, Material.EMERALD,
                "emerald", plugin.pcConfig().rushEmeraldIntervalTicks()));
    }

    /** The block above a map offset, centered, in world coordinates. */
    private static Location dropSpot(Location origin, Vector offset) {
        return new Location(origin.getWorld(),
                origin.getBlockX() + offset.getBlockX() + 0.5,
                origin.getBlockY() + offset.getBlockY() + 1.0,
                origin.getBlockZ() + offset.getBlockZ() + 0.5);
    }

    private static boolean nearestTeamIs(RushMapData data, Vector generator, Vector home) {
        double mine = generator.distanceSquared(home);
        for (RushMapData.TeamBase team : data.playableTeams()) {
            if (team.spawn() != null && generator.distanceSquared(team.spawn()) < mine) {
                return false;
            }
        }
        return true;
    }

    /** A floating hint over the bed: how to get a preview. Gone in a few seconds, or when the player walks up. */
    private void showHologram(Player player, BedDefenseState state) {
        int ticks = plugin.pcConfig().bedDefenseHologramTicks();
        if (ticks <= 0 || msg().silenced("beddefense.hologram")) {
            return;
        }
        Location at = state.frame().head().add(0.5, 1.6, 0.5);
        Component text = msg().component("beddefense.hologram");
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
        });
        state.setHologram(display);
        state.setHologramTicks(ticks);
    }

    // ------------------------------------------------------------------ kits

    /**
     * The kit for the player's upcoming round. Competitive is a match
     * opening: sword, team-dyed leather, the bed defense item and the menu
     * item. Practice adds the defense's exact blocks, one water bucket per
     * water block. Obsidian practice is tools and eight obsidian; bed repair
     * is armor alone, the blocks arriving with the damage. The editor
     * carries full stacks of every allowed block.
     */
    public Map<Integer, ItemStack> kit(Player player, ArenaTemplate template, BedDefenseState state) {
        UUID id = player.getUniqueId();
        if (state != null && state.phase() == Phase.PREVIEW) {
            // The kit re-check after a spawn must not push blocks into the
            // preview hotbar: while previewing, the controls ARE the kit.
            Map<Integer, ItemStack> controls = new LinkedHashMap<>();
            for (String role : previewRoles(state)) {
                controls.put(plugin.pcConfig().bedDefensePreviewItemSlot(role), createItem(role));
            }
            return controls;
        }
        Phase phase = upcomingPhase(id, state);
        List<ItemStack> items = new ArrayList<>();
        boolean repairing = phase != Phase.EDIT && selection(id).repair();
        Material sword = plugin.pcConfig().rushStarterSword();
        if (sword != null && !repairing) {
            items.add(new ItemStack(sword));
        }
        if (repairing) {
            // Bed repair deals no blocks up front: every volley hands over
            // exactly what it took, and nothing else is needed.
            phase = Phase.PLAY;
        } else if (phase == Phase.EDIT) {
            for (Material kind : allowedKinds()) {
                items.add(new ItemStack(kind == Material.WHITE_WOOL ? kitWool(player) : kind, 64));
            }
            if (plugin.pcConfig().bedDefenseWaterBuckets()) {
                items.add(new ItemStack(Material.WATER_BUCKET));
                items.add(new ItemStack(Material.WATER_BUCKET));
            }
        } else if (selection(id).obsidian()) {
            // Break in, seal the bed, build back: tools and the eight
            // obsidian, nothing to buy.
            for (Material tool : obsidianTools()) {
                items.add(new ItemStack(tool));
            }
            items.add(new ItemStack(Material.OBSIDIAN, DefenseFrame.COVER.size()));
        } else if (!selection(id).competitive()) {
            BedDefense defense = roundDefense(id);
            if (defense != null) {
                for (Map.Entry<Material, Integer> entry : defense.kindCounts().entrySet()) {
                    Material kind = entry.getKey();
                    int count = entry.getValue();
                    if (kind == Material.WATER) {
                        if (plugin.pcConfig().bedDefenseWaterBuckets()) {
                            for (int i = 0; i < count; i++) {
                                items.add(new ItemStack(Material.WATER_BUCKET));
                            }
                        }
                        continue;
                    }
                    Material item = kind == Material.WHITE_WOOL ? kitWool(player) : kind;
                    while (count > 0) {
                        int stack = Math.min(count, item.getMaxStackSize());
                        items.add(new ItemStack(item, stack));
                        count -= stack;
                    }
                }
            }
        }
        Map<Integer, ItemStack> kit = new LinkedHashMap<>();
        // Hotbar fixtures first, so nothing else lands on them.
        kit.put(plugin.pcConfig().bedDefenseItemSlot(), createItem(ITEM_MENU));
        if (plugin.pcConfig().menuItemEnabled()) {
            kit.put(plugin.pcConfig().menuItemSlot(), plugin.menuItems().create());
        }
        // Remembered layout: each material goes where the player last kept it.
        Map<Integer, String> layout = plugin.stats().kitLayout(id, BedDefenseMode.ID);
        List<ItemStack> unplaced = new ArrayList<>();
        for (ItemStack item : items) {
            int slot = -1;
            for (Map.Entry<Integer, String> entry : layout.entrySet()) {
                if (!kit.containsKey(entry.getKey())
                        && entry.getValue().equalsIgnoreCase(BlockKinds.normalize(item.getType()).name())) {
                    slot = entry.getKey();
                    break;
                }
            }
            if (slot >= 0 && slot < 36) {
                kit.put(slot, item);
            } else {
                unplaced.add(item);
            }
        }
        for (ItemStack item : unplaced) {
            for (int slot = 0; slot < 36; slot++) {
                if (!kit.containsKey(slot)) {
                    kit.put(slot, item);
                    break;
                }
            }
        }
        if (phase != Phase.EDIT) {
            String team = teamFor(player, template);
            org.bukkit.Color color = team == null ? null
                    : DyeColors.parse(team, DyeColor.WHITE).getColor();
            kit.put(39, leather(Material.LEATHER_HELMET, color));
            kit.put(38, leather(Material.LEATHER_CHESTPLATE, color));
            kit.put(37, leather(Material.LEATHER_LEGGINGS, color));
            kit.put(36, leather(Material.LEATHER_BOOTS, color));
        }
        return kit;
    }

    private String teamFor(Player player, ArenaTemplate template) {
        String team = plugin.stats().pref(player.getUniqueId(),
                "rush.team." + template.name(), null);
        RushMapData data = RushMapData.parse(template);
        if (data.team(team) == null || !data.team(team).playable()) {
            return data.playableTeams().isEmpty() ? null : data.playableTeams().get(0).name();
        }
        return team;
    }

    private static ItemStack leather(Material piece, org.bukkit.Color color) {
        ItemStack stack = new ItemStack(piece);
        if (color != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The player's chosen wool color, white like the shop's default otherwise. */
    public Material kitWool(Player player) {
        DyeColor color = plugin.settings().woolColor(player.getUniqueId());
        return color == null ? Material.WHITE_WOOL : DyeColors.wool(color);
    }

    /**
     * Remembers where the player keeps each kind of block, so the next kit
     * lands the same way. Captured on every reset and at session end,
     * playing phases only — the preview hotbar is not a layout.
     */
    public void captureLayout(Player player, BedDefenseState state) {
        if (state.phase() == Phase.PREVIEW) {
            return;
        }
        Map<Integer, String> layout = new LinkedHashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || plugin.menuItems().isMenuItem(item)
                    || itemRole(item) != null) {
                continue;
            }
            String kind = BlockKinds.normalize(item.getType()).name();
            if (!layout.containsValue(kind)) {
                layout.put(slot, kind);
            }
        }
        if (!layout.isEmpty()) {
            plugin.stats().saveKitLayout(player.getUniqueId(), BedDefenseMode.ID, layout);
        }
    }

    // ------------------------------------------------------------ hotbar items

    public ItemStack createItem(String role) {
        var config = plugin.pcConfig();
        Material material = role.equals(ITEM_MENU)
                ? config.bedDefenseItemMaterial() : config.bedDefensePreviewItemMaterial(role);
        return ItemBuilder.of(material)
                .name(msg().name("gui.beddefense.items." + role + ".name"))
                .lore(msg().lore("gui.beddefense.items." + role + ".lore"))
                .hideAttributes()
                .edit(meta -> meta.getPersistentDataContainer()
                        .set(itemKey, PersistentDataType.STRING, role))
                .build();
    }

    /** The role of one of this mode's hotbar items, or null for anything else. */
    public String itemRole(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- preview

    /**
     * Dropping an item (or the menu button): a preview when nothing has been
     * built yet, otherwise guided mode — the attempt is kept and pointed
     * along instead of being thrown away.
     */
    public void previewOrGuide(Player player, PracticeSession session, BedDefenseState state) {
        switch (state.phase()) {
            case PLAY -> {
                if (state.repair()) {
                    // The defense is standing right there, and the sky
                    // would not wait for a flying preview to finish.
                    msg().actionBar(player, "beddefense.repair.no-preview");
                    return;
                }
                if (state.attemptInProgress(session.timerRunning())) {
                    enterGuided(player, session, state, true);
                } else {
                    enterPreview(player, session, state);
                }
            }
            case GUIDED -> exitGuided(player, session, state);
            case PREVIEW -> exitPreview(player, session, state, true);
            case EDIT -> msg().actionBar(player, "beddefense.preview.not-in-editor");
        }
    }

    /** The preview hotbar: guided building has no place in obsidian practice. */
    private List<String> previewRoles(BedDefenseState state) {
        return state != null && state.obsidian()
                ? List.of(ITEM_PREVIOUS, ITEM_PLAY, ITEM_NEXT, ITEM_EXIT)
                : List.of(ITEM_PREVIOUS, ITEM_PLAY, ITEM_NEXT, ITEM_GUIDED, ITEM_EXIT);
    }

    public void enterPreview(Player player, PracticeSession session, BedDefenseState state) {
        if (state.phase() != Phase.PLAY || state.defense() == null) {
            return;
        }
        if (state.repair()) {
            msg().actionBar(player, "beddefense.repair.no-preview");
            return;
        }
        // Stepping off the spawn started the clock, but with nothing built
        // there is no attempt to protect — the preview resets the clock.
        session.resetTimer();
        session.setState(SessionState.READY);
        removeHologram(state);
        state.setPhase(Phase.PREVIEW);
        state.stash(player.getInventory().getContents().clone(), null);
        player.getInventory().clear();
        var config = plugin.pcConfig();
        for (String role : previewRoles(state)) {
            player.getInventory().setItem(config.bedDefensePreviewItemSlot(role), createItem(role));
        }
        player.getInventory().setHeldItemSlot(config.bedDefensePreviewItemSlot(ITEM_PLAY));
        player.setAllowFlight(true);
        player.setFlying(true);
        state.setPreviewIndex(0);
        state.setPreviewPlaying(true);
        state.setPreviewCooldown(config.bedDefensePreviewStepTicks());
        msg().send(player, "beddefense.preview.entered", "name", state.defense().name());
        plugin.sounds().play(player, "beddefense.preview-enter");
    }

    /** Leaves the preview: blocks undone, kit back, feet on the ground. */
    public void exitPreview(Player player, PracticeSession session, BedDefenseState state,
                            boolean announce) {
        if (state.phase() != Phase.PREVIEW) {
            return;
        }
        undoPreview(state);
        state.setPhase(Phase.PLAY);
        restoreStash(player, state);
        player.setFlying(false);
        player.setAllowFlight(false);
        plugin.sessions().teleportInternal(player, session.spawn());
        if (announce) {
            msg().send(player, "beddefense.preview.exited");
        }
    }

    private void restoreStash(Player player, BedDefenseState state) {
        if (state.stashedInventory() != null) {
            player.getInventory().clear();
            player.getInventory().setContents(state.stashedInventory());
        }
        state.clearStash();
        player.updateInventory();
    }

    private void undoPreview(BedDefenseState state) {
        List<Map.Entry<Location, BlockData>> entries = new ArrayList<>(state.previewReplaced().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            entries.get(i).getKey().getBlock().setBlockData(entries.get(i).getValue(), false);
        }
        state.previewReplaced().clear();
        state.setPreviewIndex(0);
    }

    /** One step forward or back through the animation. */
    public void previewStep(Player player, BedDefenseState state, int delta) {
        List<Target> targets = state.previewTargets();
        int index = state.previewIndex();
        if (delta > 0) {
            if (index >= targets.size()) {
                state.setPreviewPlaying(false);
                msg().actionBar(player, "beddefense.preview.done");
                return;
            }
            Target target = targets.get(index);
            Block block = target.loc().getBlock();
            state.previewReplaced().putIfAbsent(target.loc(), block.getBlockData());
            block.setBlockData(state.frame().toWorld(target.block(), kitWool(player)), false);
            state.setPreviewIndex(index + 1);
            plugin.sounds().play(player, "beddefense.preview-step", target.loc());
            msg().actionBar(player, "beddefense.preview.step",
                    "step", String.valueOf(index + 1),
                    "total", String.valueOf(targets.size()),
                    "material", BlockKinds.pretty(target.block().kind()));
        } else {
            if (index <= 0) {
                msg().actionBar(player, "beddefense.preview.start");
                return;
            }
            Target target = targets.get(index - 1);
            BlockData replaced = state.previewReplaced().remove(target.loc());
            target.loc().getBlock().setBlockData(
                    replaced != null ? replaced : Material.AIR.createBlockData(), false);
            state.setPreviewIndex(index - 1);
            plugin.sounds().play(player, "beddefense.preview-step", target.loc());
            msg().actionBar(player, "beddefense.preview.step",
                    "step", String.valueOf(index - 1),
                    "total", String.valueOf(targets.size()),
                    "material", BlockKinds.pretty(target.block().kind()));
        }
    }

    public void previewToggle(Player player, BedDefenseState state) {
        if (state.previewIndex() >= state.previewTargets().size()) {
            // Play at the end starts over.
            undoPreview(state);
            state.setPreviewPlaying(true);
        } else {
            state.setPreviewPlaying(!state.previewPlaying());
        }
        state.setPreviewCooldown(0);
        refreshPreviewItems(player, state);
        msg().actionBar(player, state.previewPlaying()
                ? "beddefense.preview.playing" : "beddefense.preview.paused");
        plugin.sounds().play(player, "menu.click");
    }

    private void refreshPreviewItems(Player player, BedDefenseState state) {
        int slot = plugin.pcConfig().bedDefensePreviewItemSlot(ITEM_PLAY);
        ItemStack play = createItem(ITEM_PLAY);
        if (state.previewPlaying()) {
            play = ItemBuilder.of(plugin.pcConfig().bedDefensePreviewItemMaterial("pause"))
                    .name(msg().name("gui.beddefense.items.pause.name"))
                    .lore(msg().lore("gui.beddefense.items.pause.lore"))
                    .hideAttributes()
                    .edit(meta -> meta.getPersistentDataContainer()
                            .set(itemKey, PersistentDataType.STRING, ITEM_PLAY))
                    .build();
        }
        player.getInventory().setItem(slot, play);
    }

    // ----------------------------------------------------------------- guided

    /**
     * Guided building: the next block in the recorded order floats over its
     * spot, glowing and blinking, until it is placed. Untimed — the run is
     * off the books the moment guidance starts. Coming from a live attempt
     * the blocks already placed stay; from a preview the animation is undone
     * and the player builds from the start.
     */
    public void enterGuided(Player player, PracticeSession session, BedDefenseState state,
                            boolean fromAttempt) {
        if (state.defense() == null) {
            return;
        }
        if (state.obsidian()) {
            // The defense already stands; there is no order to be led through.
            msg().actionBar(player, "beddefense.guided.not-in-obsidian");
            return;
        }
        if (state.repair()) {
            msg().actionBar(player, "beddefense.repair.no-preview");
            return;
        }
        if (state.phase() == Phase.PREVIEW) {
            undoPreview(state);
            restoreStash(player, state);
            player.setFlying(false);
            player.setAllowFlight(false);
            plugin.sessions().teleportInternal(player, session.spawn());
        }
        removeHologram(state);
        state.setPhase(Phase.GUIDED);
        // The clock stops for good; READY keeps the board honest and the
        // finish untimed.
        session.resetTimer();
        session.setState(SessionState.READY);
        giveMissingBlocks(player, state);
        updateGuide(player, state);
        msg().send(player, fromAttempt ? "beddefense.guided.from-attempt" : "beddefense.guided.entered");
        plugin.sounds().play(player, "beddefense.guided-enter");
    }

    /** Back to a timed attempt: the arena resets so the next run starts clean. */
    public void exitGuided(Player player, PracticeSession session, BedDefenseState state) {
        if (state.phase() != Phase.GUIDED) {
            return;
        }
        removeGuide(state);
        state.setPhase(Phase.PLAY);
        msg().send(player, "beddefense.guided.exited");
        plugin.sessions().restart(player);
    }

    /** A competitive kit has no blocks; guidance hands the practice stacks over. */
    private void giveMissingBlocks(Player player, BedDefenseState state) {
        BedDefense defense = state.defense();
        for (Map.Entry<Material, Integer> entry : defense.kindCounts().entrySet()) {
            Material kind = entry.getKey();
            Material item = kind == Material.WATER ? Material.WATER_BUCKET
                    : kind == Material.WHITE_WOOL ? kitWool(player) : kind;
            if (kind == Material.WATER && !plugin.pcConfig().bedDefenseWaterBuckets()) {
                continue;
            }
            int have = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && BlockKinds.normalize(stack.getType()) == kind) {
                    have += stack.getAmount();
                }
            }
            int need = entry.getValue() - have;
            while (need > 0) {
                int amount = Math.min(need, item.getMaxStackSize());
                player.getInventory().addItem(new ItemStack(item, amount));
                need -= amount;
            }
        }
    }

    /** Points the guide at the next unbuilt block, or finishes when there is none. */
    public void updateGuide(Player player, BedDefenseState state) {
        Target next = state.nextTarget();
        if (next == null) {
            removeGuide(state);
            return;
        }
        BlockData shown = state.frame().toWorld(next.block(), kitWool(player));
        BlockDisplay guide = state.guide();
        Location at = next.loc().clone().add(0.15, 0.15, 0.15);
        if (guide == null || !guide.isValid()) {
            guide = at.getWorld().spawn(at, BlockDisplay.class, d -> {
                d.setBlock(shown);
                d.setGlowing(true);
                d.setGlowColorOverride(org.bukkit.Color.LIME);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0, 0, 0),
                        new org.joml.AxisAngle4f(0, 0, 0, 1),
                        new org.joml.Vector3f(0.7f, 0.7f, 0.7f),
                        new org.joml.AxisAngle4f(0, 0, 0, 1)));
            });
            state.setGuide(guide);
        } else {
            guide.teleport(at);
            guide.setBlock(shown);
        }
        state.setGuideShown(true);
        msg().actionBar(player, "beddefense.guided.next",
                "material", BlockKinds.pretty(next.block().kind()),
                "step", String.valueOf(state.satisfied() + 1),
                "total", String.valueOf(state.targets().size()));
    }

    private void removeGuide(BedDefenseState state) {
        if (state.guide() != null) {
            state.guide().remove();
            state.setGuide(null);
        }
    }

    private void removeHologram(BedDefenseState state) {
        if (state.hologram() != null) {
            state.hologram().remove();
            state.setHologram(null);
        }
        state.setHologramTicks(0);
    }

    /** Every display this state spawned. Safe to call twice. */
    public void removeEntities(BedDefenseState state) {
        removeGuide(state);
        removeHologram(state);
    }

    // ----------------------------------------------------------------- placing

    /**
     * A block is about to be placed in a play phase. Returns a messages.yml
     * key to refuse the placement with, or null to let it stand.
     */
    public String checkPlace(Player player, PracticeSession session, BedDefenseState state,
                             Block block, Material placedKind) {
        Phase phase = state.phase();
        if (phase == Phase.PREVIEW) {
            return "beddefense.preview.no-building";
        }
        if (state.frame() != null && state.frame().belowBed(block.getLocation())) {
            return "beddefense.below-bed"; // every phase: no digging under the bed
        }
        if (phase == Phase.EDIT) {
            return checkEditPlace(state, block, placedKind);
        }
        if (state.frame() != null && state.frame().isBed(block.getLocation())) {
            return "beddefense.edit.bed-protected";
        }
        return null;
    }

    /** A block landed (place or bucket) in a play phase: progress, guide, finish. */
    public void afterPlace(Player player, PracticeSession session, BedDefenseState state,
                           Location loc) {
        switch (state.phase()) {
            case PLAY -> {
                if (state.repair()) {
                    // Nothing to finish: the tick sees the defense standing
                    // again and counts the round.
                    return;
                }
                state.countPlaced();
                if (session.state() == SessionState.READY) {
                    // Built without ever leaving the spawn block: the first
                    // block is the latest the clock may start.
                    session.setState(SessionState.ACTIVE);
                    session.startTimer();
                }
                if (state.complete()) {
                    finishRound(player, session, state);
                }
            }
            case GUIDED -> {
                if (state.nextTarget() == null) {
                    removeGuide(state);
                    completeGuided(player, session, state);
                } else {
                    updateGuide(player, state);
                }
            }
            default -> {
            }
        }
    }

    /**
     * A block was broken in a play phase: the guide may need to move back,
     * and in obsidian practice breaking in is the attempt's first act — it
     * starts the clock the way a first block does elsewhere.
     */
    public void afterBreak(Player player, PracticeSession session, BedDefenseState state) {
        if (state.phase() == Phase.GUIDED) {
            Bukkit.getScheduler().runTask(plugin, () -> updateGuide(player, state));
            return;
        }
        if (state.phase() == Phase.PLAY && state.obsidian()) {
            state.countPlaced();
            if (session.state() == SessionState.READY) {
                session.setState(SessionState.ACTIVE);
                session.startTimer();
            }
        }
    }

    /**
     * The last block is in: pin the clock now and finish a tick later, the
     * same deferred pattern rush uses, so the recorded time is the moment
     * the defense stood complete.
     */
    private void finishRound(Player player, PracticeSession session, BedDefenseState state) {
        if (state.finishing() || state.defense() == null) {
            return;
        }
        state.setFinishing(true);
        session.freezeTimer();
        // The live instance, not the one this round was dealt: a reload in
        // between swaps them, and a stale save would undo likes since.
        BedDefense defense = store.get(state.defense().id());
        boolean cleared = false;
        if (defense != null) {
            UUID id = player.getUniqueId();
            defense.countCompletion(id);
            if (state.selection().competitive() && defense.isAuthor(id) && !defense.authorCleared()) {
                // The author has now built this exact shape for real — the
                // proof publishing asks for.
                defense.setClearedFingerprint(defense.fingerprint());
                cleared = true;
            }
            store.save(defense);
        }
        if (!state.selection().ranked()) {
            msg().actionBar(player, "beddefense.records-disabled");
        }
        if (cleared && !defense.published() && !defense.autoHidden()
                && plugin.pcConfig().bedDefenseRequireAuthorClear()) {
            msg().send(player, "beddefense.cleared", "name", defense.name(), "id", defense.id());
            msg().send(player, "beddefense.cleared-actions", "name", defense.name(), "id", defense.id());
            plugin.sounds().play(player, "beddefense.cleared");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                plugin.sessions().finish(player, session);
            }
        });
    }

    private void completeGuided(Player player, PracticeSession session, BedDefenseState state) {
        msg().send(player, "beddefense.guided.done", "name", state.defense().name());
        plugin.sounds().play(player, "run.finish");
        // Nothing placed in the tick before the reset may count as a run.
        state.setFinishing(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.sessions().get(session.playerId()) == session) {
                state.setPhase(Phase.PLAY);
                session.setState(SessionState.ACTIVE);
                plugin.sessions().completeUntimed(player, session);
            }
        });
    }

    // ------------------------------------------------------------------- edit

    /** Placement rules in the editor: inside the radius, an allowed block, never the bed. */
    private String checkEditPlace(BedDefenseState state, Block block, Material placedKind) {
        DefenseFrame frame = state.frame();
        if (frame == null) {
            return null;
        }
        if (frame.isBed(block.getLocation())) {
            return "beddefense.edit.bed-protected";
        }
        if (frame.distance(block.getLocation()) > plugin.pcConfig().bedDefenseEditRadius()) {
            return "beddefense.edit.out-of-radius";
        }
        if (!allowedKind(placedKind)) {
            return "beddefense.edit.not-allowed";
        }
        return null;
    }

    public boolean allowedKind(Material kind) {
        if (kind == Material.WATER) {
            return plugin.pcConfig().bedDefenseWaterBuckets();
        }
        return allowedKinds().contains(kind);
    }

    /** Records a placed (or poured) editor block in the sequence and refills the hand. */
    public void editPlaced(Player player, BedDefenseState state, Block block) {
        state.editSequence().put(block.getLocation(), block.getBlockData());
        refillHand(player);
    }

    private void refillHand(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.BUCKET) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
                return;
            }
            if (hand.getType().isAir() || plugin.menuItems().isMenuItem(hand)
                    || itemRole(hand) != null) {
                return;
            }
            if (hand.getType().isBlock() && hand.getAmount() < hand.getMaxStackSize()) {
                hand.setAmount(hand.getMaxStackSize());
            }
        });
    }

    /** True for a block of the current build — the only thing the editor lets you break. */
    public boolean isEditBlock(BedDefenseState state, Location loc) {
        return state.editSequence().containsKey(loc.getBlock().getLocation());
    }

    public void editBroken(BedDefenseState state, Location loc) {
        state.editSequence().remove(loc.getBlock().getLocation());
    }

    /**
     * Before the arena reverts under an editing player: the sequence is
     * refreshed from the world (what actually stands, in placement order),
     * so the reset can put the build straight back.
     */
    public void snapshotEdit(BedDefenseState state) {
        List<Location> gone = new ArrayList<>();
        for (Map.Entry<Location, BlockData> entry : state.editSequence().entrySet()) {
            Block block = entry.getKey().getBlock();
            if (BlockKinds.kindOf(block) == Material.AIR) {
                gone.add(entry.getKey());
            } else {
                entry.setValue(block.getBlockData());
            }
        }
        gone.forEach(state.editSequence()::remove);
    }

    /** Clears the build: the arena reverts, and nothing is put back. */
    public void editReset(Player player, BedDefenseState state) {
        state.editSequence().clear();
        msg().send(player, "beddefense.edit.reset");
        plugin.sounds().play(player, "menu.click");
        plugin.sessions().restart(player);
    }

    /**
     * Loads one of the player's own defenses into the editor. The arena
     * resets and the build is placed block by block in its saved order, so
     * the sequence is preserved; name and visibility come along for saving
     * back under the same id.
     */
    public void loadIntoEditor(Player player, BedDefenseState state, BedDefense source) {
        state.setEditSourceId(source.id());
        state.setEditName(source.name());
        state.setEditPublished(source.published());
        state.editSequence().clear();
        pendingLoad.put(player.getUniqueId(), source.id());
        msg().send(player, "beddefense.edit.loaded", "name", source.name());
        plugin.sessions().restart(player);
    }

    private final Map<UUID, String> pendingLoad = new HashMap<>();

    /**
     * Puts the build back after a reset in the editor — from a defense
     * waiting to be loaded, or from the sequence itself. Every block is
     * tracked so the next reset reverts it like a hand-placed one.
     */
    private void rebuildEditSequence(Player player, PracticeSession session, BedDefenseState state) {
        String load = pendingLoad.remove(session.playerId());
        BedDefense source = load != null ? store.get(load) : null;
        Material wool = kitWool(player);
        if (source != null) {
            state.editSequence().clear();
            for (DefenseBlock block : source.blocks()) {
                state.editSequence().put(state.frame().toWorld(block),
                        state.frame().toWorld(block, wool));
            }
        }
        for (Map.Entry<Location, BlockData> entry : List.copyOf(state.editSequence().entrySet())) {
            Location loc = entry.getKey();
            if (!session.containsBlock(loc) || state.frame().isBed(loc)) {
                state.editSequence().remove(loc);
                continue;
            }
            Block block = loc.getBlock();
            session.tracker().recordPlace(block, block.getBlockData());
            block.setBlockData(entry.getValue(), false);
        }
    }

    /** The build as defense blocks in the defense frame, placement order kept. */
    public List<DefenseBlock> editBlocks(BedDefenseState state) {
        List<DefenseBlock> blocks = new ArrayList<>();
        DefenseFrame frame = state.frame();
        for (Location loc : state.editSequence().keySet()) {
            Block block = loc.getBlock();
            Material kind = BlockKinds.kindOf(block);
            if (kind == Material.AIR) {
                continue; // broken by hand, or water that flowed away
            }
            Vector local = frame.toLocal(loc);
            BlockData data = frame.toLocal(block.getBlockData());
            blocks.add(new DefenseBlock(local.getBlockX(), local.getBlockY(), local.getBlockZ(),
                    kind, data.getAsString()));
        }
        return blocks;
    }

    /**
     * Saves the build. An identical defense the player can already see —
     * anyone's published one, or one of their own — refuses the save loudly
     * and offers to play, like or favorite the original instead; an
     * unpublished twin of someone else's is invisible to them and allowed.
     * A nameless build asks for a name first, then saves itself.
     */
    public void editSave(Player player, PracticeSession session, BedDefenseState state) {
        List<DefenseBlock> blocks = editBlocks(state);
        if (blocks.isEmpty()) {
            msg().send(player, "beddefense.edit.empty");
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        UUID id = player.getUniqueId();
        BedDefense existing = state.editSourceId() != null ? store.get(state.editSourceId()) : null;
        BedDefense duplicate = store.duplicateOf(blocks, id, existing != null ? existing.id() : null);
        if (duplicate != null) {
            refuseDuplicate(player, duplicate);
            return;
        }
        if (state.editName() == null || state.editName().isBlank()) {
            promptName(player, session, state, () -> editSave(player, session, state));
            return;
        }
        if (existing == null && store.ownedBy(id).size() >= plugin.pcConfig().bedDefenseMaxPerPlayer()) {
            msg().send(player, "beddefense.edit.limit",
                    "max", String.valueOf(plugin.pcConfig().bedDefenseMaxPerPlayer()));
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        BedDefense saved;
        boolean wantPublic = state.editPublished();
        String withdrawn = null;
        if (existing != null && existing.isAuthor(id) && existing.autoHidden() && wantPublic) {
            // Reports hid it; a reshape does not lift that, a moderator does.
            wantPublic = false;
            withdrawn = "beddefense.notice.saved-auto-hidden";
        }
        if (existing != null && existing.isAuthor(id)) {
            boolean wasPublic = existing.published();
            boolean reshaped = !BedDefense.fingerprintOf(blocks).equals(existing.fingerprint());
            existing.setName(state.editName());
            saved = store.reshape(existing, blocks);
            // A reshaped public defense is a new shape nobody has proven
            // buildable: it goes private until the author clears it again.
            // A rename alone changes nothing about the shape, so a defense a
            // moderator published stays public through it.
            if (wantPublic && reshaped && !canPublish(saved)) {
                saved.setPublished(false);
                withdrawn = wasPublic ? "beddefense.notice.private-changed"
                        : "beddefense.notice.saved-private";
            } else if (wantPublic && !reshaped) {
                saved.setPublished(wasPublic || canPublish(saved));
                if (!saved.published()) {
                    withdrawn = "beddefense.notice.saved-private";
                }
            } else {
                saved.setPublished(wantPublic);
            }
            store.save(saved);
        } else {
            saved = store.create(state.editName(), id, player.getName(), false, blocks);
            if (wantPublic && !canPublish(saved)) {
                withdrawn = "beddefense.notice.saved-private";
            } else if (wantPublic) {
                saved.setPublished(true);
                store.save(saved);
            }
        }
        msg().send(player, saved.published() ? "beddefense.edit.saved-public" : "beddefense.edit.saved",
                "name", saved.name(), "blocks", String.valueOf(blocks.size()));
        if (withdrawn != null) {
            msg().send(player, withdrawn, "name", saved.name(), "id", saved.id());
        }
        plugin.sounds().play(player, "beddefense.saved");
        // Straight into playing what was just designed.
        selectDefense(id, saved.id());
        clearRound(id);
        requestPlay(id);
        plugin.sessions().restart(player);
    }

    private void refuseDuplicate(Player player, BedDefense duplicate) {
        plugin.sounds().play(player, "beddefense.duplicate");
        msg().title(player, "beddefense.edit.duplicate-title", "beddefense.edit.duplicate-subtitle",
                "name", duplicate.name(), "author", duplicate.authorName());
        msg().send(player, "beddefense.edit.duplicate",
                "name", duplicate.name(), "author", duplicate.authorName(), "id", duplicate.id());
        msg().send(player, "beddefense.edit.duplicate-actions",
                "name", duplicate.name(), "author", duplicate.authorName(), "id", duplicate.id());
    }

    /** Asks for a name in an anvil; the callback runs once a valid one arrives. */
    public void promptName(Player player, PracticeSession session, BedDefenseState state,
                           Runnable then) {
        String max = String.valueOf(Math.min(plugin.pcConfig().bedDefenseNameMaxLength(),
                AnvilPrompts.MAX_LENGTH));
        plugin.prompts().prompt(player, msg().component("gui.beddefense.editor.name-prompt.title"),
                state.editName() == null ? "" : state.editName(),
                msg().lore("gui.beddefense.editor.name-prompt.lore", "max", max), answer -> {
            if (plugin.sessions().get(player.getUniqueId()) != session
                    || state.phase() != Phase.EDIT) {
                return;
            }
            String name = cleanName(answer);
            if (name == null) {
                msg().send(player, "beddefense.edit.name-invalid",
                        "max", String.valueOf(plugin.pcConfig().bedDefenseNameMaxLength()));
                plugin.sounds().play(player, "menu.deny");
                return;
            }
            state.setEditName(name);
            msg().send(player, "beddefense.edit.name-set", "name", name);
            plugin.sounds().play(player, "menu.click");
            if (then != null) {
                then.run();
            }
        });
    }

    /** Printable, trimmed, length-capped; null when nothing usable is left. */
    private String cleanName(String raw) {
        String name = raw.replaceAll("[\\p{Cntrl}§]", "").trim();
        int max = plugin.pcConfig().bedDefenseNameMaxLength();
        if (name.isEmpty() || name.length() > max) {
            return null;
        }
        return name;
    }

    // ----------------------------------------------------------- publishing

    /**
     * Whether a defense may go public right now: its author has finished
     * this exact shape in a competitive round — or the server switched that
     * gate off. It binds everyone: a moderator cannot publish an unproven
     * defense either.
     */
    public boolean canPublish(BedDefense defense) {
        return !plugin.pcConfig().bedDefenseRequireAuthorClear() || defense.authorCleared();
    }

    /**
     * Whether this player may publish this defense right now. On top of
     * {@link #canPublish}, an author is held back while reports have hidden
     * it — only a moderator lifts that.
     */
    public boolean canPublish(Player actor, BedDefense defense) {
        if (!canPublish(defense)) {
            return false;
        }
        return !defense.autoHidden() || (isModerator(actor) && !defense.isAuthor(actor.getUniqueId()));
    }

    /**
     * Flips the visibility of a saved defense (editor button, gallery action
     * and the chat command). Publishing needs the author's clear whoever
     * asks; an auto-hidden defense needs a moderator, whose publish also
     * closes its reports. A moderator may flip anyone's, and the author is
     * told, now or on their next login. Anyone else is refused. Refusals
     * say why in chat.
     *
     * @return true when the visibility actually changed
     */
    public boolean setPublished(Player actor, BedDefense defense, boolean published) {
        UUID id = actor.getUniqueId();
        boolean own = defense.isAuthor(id);
        if (!own && !isModerator(actor)) {
            msg().send(actor, "beddefense.not-owner");
            plugin.sounds().play(actor, "menu.deny");
            return false;
        }
        if (defense.published() == published) {
            msg().send(actor, published ? "beddefense.visibility.already-public"
                    : "beddefense.visibility.already-private", "name", defense.name());
            plugin.sounds().play(actor, "menu.deny");
            return false;
        }
        if (published && !canPublish(defense)) {
            if (own) {
                msg().send(actor, "beddefense.visibility.needs-clear", "name", defense.name(), "id", defense.id());
                msg().send(actor, "beddefense.visibility.needs-clear-actions",
                        "name", defense.name(), "id", defense.id());
            } else {
                msg().send(actor, "beddefense.visibility.moderator-needs-clear",
                        "name", defense.name(), "author", defense.authorName());
            }
            plugin.sounds().play(actor, "menu.deny");
            return false;
        }
        if (published && own && defense.autoHidden()) {
            msg().send(actor, "beddefense.visibility.auto-hidden", "name", defense.name());
            plugin.sounds().play(actor, "menu.deny");
            return false;
        }
        int closed = 0;
        if (published && defense.autoHidden()) {
            // A moderator putting it back is the review the hide waited for.
            closed = defense.reportCount();
            defense.clearReports();
            defense.markReportsSeen();
            defense.setAutoHidden(false);
        }
        defense.setPublished(published);
        store.save(defense);
        if (own) {
            msg().send(actor, published ? "beddefense.visibility.public" : "beddefense.visibility.private",
                    "name", defense.name());
        } else {
            msg().send(actor, published ? "beddefense.visibility.moderator-public"
                    : "beddefense.visibility.moderator-private",
                    "name", defense.name(), "author", defense.authorName());
            if (closed > 0) {
                msg().send(actor, "beddefense.visibility.moderator-restored",
                        "name", defense.name(), "count", String.valueOf(closed));
            }
            plugin.notices().notify(defense.author(), published
                            ? "beddefense.notice.public-by-moderator"
                            : "beddefense.notice.private-by-moderator",
                    "name", defense.name(), "id", defense.id(), "moderator", actor.getName());
        }
        plugin.sounds().play(actor, published ? "menu.toggle-on" : "menu.toggle-off");
        return true;
    }

    /**
     * Deletes a defense and every time recorded on it. A moderator deleting
     * someone else's tells the author, now or on their next login.
     */
    public void delete(Player actor, BedDefense defense) {
        store.delete(defense);
        for (String key : List.of(statsKey(defense.id()), practiceStatsKey(defense.id()),
                obsidianStatsKey(defense.id()), legacyStrictStatsKey(defense.id()))) {
            plugin.leaderboards().forget(key);
            plugin.stats().purgeTemplate(key, wiped -> { });
        }
        msg().send(actor, "beddefense.deleted", "name", defense.name());
        plugin.sounds().play(actor, "menu.click");
        if (!defense.isAuthor(actor.getUniqueId())) {
            plugin.notices().notify(defense.author(), "beddefense.notice.deleted-by-moderator",
                    "name", defense.name(), "moderator", actor.getName());
        }
    }

    // -------------------------------------------------------------- reports

    /**
     * Files (or updates) a report on a public defense and tells every
     * moderator online. Your own defense cannot be reported, and a private
     * one has nobody to report it.
     *
     * @return true when the report was recorded
     */
    public boolean report(Player reporter, BedDefense defense, String reason) {
        UUID id = reporter.getUniqueId();
        if (defense.isAuthor(id)) {
            msg().send(reporter, "beddefense.report.own");
            plugin.sounds().play(reporter, "menu.deny");
            return false;
        }
        if (!defense.published()) {
            msg().send(reporter, "beddefense.report.private");
            plugin.sounds().play(reporter, "menu.deny");
            return false;
        }
        String cleaned = cleanReason(reason);
        if (cleaned == null) {
            msg().send(reporter, "beddefense.report.reason-invalid",
                    "max", String.valueOf(plugin.pcConfig().bedDefenseReportReasonMaxLength()));
            plugin.sounds().play(reporter, "menu.deny");
            return false;
        }
        boolean fresh = defense.addReport(new BedDefense.Report(id, reporter.getName(), cleaned,
                System.currentTimeMillis()));
        boolean hidden = shouldAutoHide(defense);
        if (hidden) {
            defense.setPublished(false);
            defense.setAutoHidden(true);
        }
        store.save(defense);
        msg().send(reporter, fresh ? "beddefense.report.sent" : "beddefense.report.updated",
                "name", defense.name());
        plugin.sounds().play(reporter, "beddefense.report");
        String builders = String.valueOf(defense.buildersBesidesAuthor());
        String builderReports = String.valueOf(defense.reportsFromBuilders());
        if (hidden) {
            plugin.notices().notify(defense.author(), "beddefense.notice.auto-hidden",
                    "name", defense.name(), "id", defense.id(),
                    "reports", builderReports, "players", builders);
        }
        boolean announce = plugin.pcConfig().bedDefenseReportsNotifyModerators();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isModerator(online) || online.equals(reporter)) {
                continue;
            }
            if (announce) {
                msg().send(online, "beddefense.report.alert",
                        "player", reporter.getName(),
                        "name", defense.name(),
                        "id", defense.id(),
                        "author", defense.authorName(),
                        "reason", cleaned,
                        "count", String.valueOf(defense.reportCount()));
            }
            if (hidden) {
                // A defense going dark on its own is news whether or not
                // single reports are announced.
                msg().send(online, "beddefense.report.auto-hidden-alert",
                        "name", defense.name(), "id", defense.id(), "author", defense.authorName(),
                        "reports", builderReports, "players", builders);
            }
            if (announce || hidden) {
                msg().send(online, "beddefense.report.alert-actions",
                        "name", defense.name(), "id", defense.id());
            }
        }
        return true;
    }

    /**
     * Whether the builders' reports on a public defense have reached the
     * configured share: at least {@code min-reports} of them, and at least
     * {@code percent} of everyone but the author who has started a round on
     * it. Reports from players who never built it do not count.
     */
    private boolean shouldAutoHide(BedDefense defense) {
        int percent = plugin.pcConfig().bedDefenseAutoHidePercent();
        if (percent <= 0 || !defense.published()) {
            return false;
        }
        int reports = defense.reportsFromBuilders();
        int builders = defense.buildersBesidesAuthor();
        return reports >= plugin.pcConfig().bedDefenseAutoHideMinReports()
                && builders > 0
                && reports * 100L >= (long) percent * builders;
    }

    /**
     * A moderator has looked at this defense's reports (its review or
     * reports menu, or {@code info}): they stop counting as unseen.
     */
    public void markReportsSeen(BedDefense defense) {
        if (defense.markReportsSeen()) {
            store.save(defense);
        }
    }

    /** Lifts an automatic hide once no report is left, and tells the author. */
    private void liftAutoHideIfClear(Player moderator, BedDefense defense) {
        if (!defense.autoHidden() || defense.reportCount() > 0) {
            return;
        }
        defense.setAutoHidden(false);
        msg().send(moderator, "beddefense.report.auto-hide-lifted", "name", defense.name());
        plugin.notices().notify(defense.author(), "beddefense.notice.auto-hide-lifted",
                "name", defense.name(), "id", defense.id(), "moderator", moderator.getName());
    }

    // ------------------------------------------------------------ reminders

    /** Every moderator online hears about reports nobody has looked at (the interval task). */
    public void remindModerators() {
        List<BedDefense> unseen = store.unseenReported();
        if (unseen.isEmpty()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isModerator(online)) {
                remind(online, unseen);
            }
        }
    }

    /** A moderator who just joined is reminded a moment later, after the join chatter. */
    public void remindOnJoin(Player player) {
        if (!plugin.pcConfig().bedDefenseReportsRemindOnJoin() || !isModerator(player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                List<BedDefense> unseen = store.unseenReported();
                if (!unseen.isEmpty()) {
                    remind(player, unseen);
                }
            }
        }, 40L);
    }

    private static final int REMINDER_ENTRIES = 5;

    private void remind(Player moderator, List<BedDefense> unseen) {
        int total = 0;
        for (BedDefense defense : unseen) {
            total += defense.unseenReports();
        }
        msg().send(moderator, "beddefense.report.reminder",
                "count", String.valueOf(unseen.size()), "reports", String.valueOf(total));
        int shown = 0;
        for (BedDefense defense : unseen) {
            if (shown++ >= REMINDER_ENTRIES) {
                msg().send(moderator, "beddefense.report.reminder-more",
                        "count", String.valueOf(unseen.size() - REMINDER_ENTRIES));
                break;
            }
            BedDefense.Report latest = defense.reports().get(defense.reports().size() - 1);
            msg().send(moderator, "beddefense.report.reminder-entry",
                    msg().ref("state", defense.autoHidden()
                            ? "beddefense.report.reminder-hidden" : "beddefense.report.reminder-open"),
                    "id", defense.id(),
                    "name", defense.name(),
                    "author", defense.authorName(),
                    "count", String.valueOf(defense.reportCount()),
                    "unseen", String.valueOf(defense.unseenReports()),
                    "reporter", latest.reporterName(),
                    "reason", latest.reason());
        }
        msg().send(moderator, "beddefense.report.reminder-actions");
        plugin.sounds().play(moderator, "beddefense.report");
    }

    /** Asks for a reason in an anvil, then files the report. */
    public void promptReport(Player reporter, BedDefense defense) {
        String max = String.valueOf(Math.min(plugin.pcConfig().bedDefenseReportReasonMaxLength(),
                AnvilPrompts.MAX_LENGTH));
        plugin.prompts().prompt(reporter, msg().component("gui.beddefense.actions.report-prompt.title"),
                "", msg().lore("gui.beddefense.actions.report-prompt.lore",
                        "name", defense.name(), "max", max), answer -> {
            // The live instance: a reshape or reload while they typed swaps it.
            BedDefense live = store.get(defense.id());
            if (live == null) {
                msg().send(reporter, "beddefense.unknown", "id", defense.id());
                return;
            }
            report(reporter, live, answer);
        });
    }

    /** A moderator closes every report on a defense. @return how many were dismissed */
    public int dismissReports(Player moderator, BedDefense defense) {
        int count = defense.reportCount();
        if (count == 0) {
            msg().send(moderator, "beddefense.report.none", "name", defense.name());
            plugin.sounds().play(moderator, "menu.deny");
            return 0;
        }
        defense.clearReports();
        defense.markReportsSeen();
        msg().send(moderator, "beddefense.report.dismissed",
                "name", defense.name(), "count", String.valueOf(count));
        liftAutoHideIfClear(moderator, defense);
        store.save(defense);
        plugin.sounds().play(moderator, "menu.click");
        return count;
    }

    /** A moderator drops one player's report. */
    public boolean dismissReport(Player moderator, BedDefense defense, UUID reporter) {
        if (!defense.removeReport(reporter)) {
            return false;
        }
        defense.markReportsSeen();
        liftAutoHideIfClear(moderator, defense);
        store.save(defense);
        plugin.sounds().play(moderator, "menu.click");
        return true;
    }

    /** Printable, trimmed, length-capped; null when nothing usable is left. */
    private String cleanReason(String raw) {
        if (raw == null) {
            return null;
        }
        String reason = raw.replaceAll("[\\p{Cntrl}§]", "").trim();
        int max = plugin.pcConfig().bedDefenseReportReasonMaxLength();
        if (reason.isEmpty()) {
            return null;
        }
        return reason.length() > max ? reason.substring(0, max) : reason;
    }

    // ------------------------------------------------------------------ stats

    public static String statsKey(String defenseId) {
        return "beddefense#" + defenseId;
    }

    /**
     * Where a practice round's time goes. Practice hands the blocks over
     * instead of making them be bought, so its times are not comparable
     * with competitive ones and are kept on a key of their own — personal
     * bests the player can see, never a public ranking.
     */
    public static String practiceStatsKey(String defenseId) {
        return "beddefense#" + defenseId + "#practice";
    }

    /** True for a key holding practice times, which are never ranked. */
    public static boolean isPracticeStatsKey(String key) {
        return key != null && key.startsWith("beddefense#") && key.endsWith("#practice");
    }

    /**
     * Where an obsidian round's time goes: break in, seal the bed, build
     * back, with a fixed kit. Ranked, on a public board of its own per
     * defense, since the kit never varies and the times compare.
     */
    public static String obsidianStatsKey(String defenseId) {
        return "beddefense#" + defenseId + "#obsidian";
    }

    /** True for a key holding obsidian practice times. */
    public static boolean isObsidianStatsKey(String key) {
        return key != null && key.startsWith("beddefense#") && key.endsWith("#obsidian");
    }

    /**
     * Where a bed repair run's score goes: rounds survived, ranked highest
     * first on a public board of its own per defense.
     */
    public static String repairStatsKey(String defenseId) {
        return "beddefense#" + defenseId + "#repair";
    }

    /** True for a key holding bed repair scores. */
    public static boolean isRepairStatsKey(String key) {
        return key != null && key.startsWith("beddefense#") && key.endsWith("#repair");
    }

    /** The variant a board key belongs to. */
    public static BedDefenseSelection.Variant variantOfKey(String key) {
        return isRepairStatsKey(key) ? BedDefenseSelection.Variant.REPAIR
                : isObsidianStatsKey(key) ? BedDefenseSelection.Variant.OBSIDIAN
                : BedDefenseSelection.Variant.NORMAL;
    }

    /**
     * The board key strict-order rounds used to write to. Strict order was
     * removed, so nothing writes this any more; it is still recognized so
     * times set before the removal keep resolving to their defense instead
     * of showing as a dead key.
     */
    public static String legacyStrictStatsKey(String defenseId) {
        return "beddefense#" + defenseId + "#strict";
    }

    /** A board key resolved to its defense, or null for foreign keys. */
    public BedDefense resolveStatsKey(String key) {
        if (key == null || !key.startsWith("beddefense#")) {
            return null;
        }
        String rest = key.substring("beddefense#".length());
        for (String suffix : List.of("#practice", "#obsidian", "#repair", "#strict")) {
            if (rest.endsWith(suffix)) {
                rest = rest.substring(0, rest.length() - suffix.length());
                break;
            }
        }
        return store.get(rest);
    }

    /** "<name> (Bed Defense)" for boards and broadcasts. */
    public String displayFor(BedDefense defense) {
        return msg().raw("beddefense.board-name").replace("<name>", defense.name());
    }

    /** The same, named for the key's variant: practice, obsidian and repair say so. */
    public String displayForKey(String key, BedDefense defense) {
        String name = isPracticeStatsKey(key) ? "beddefense.board-name-practice"
                : isObsidianStatsKey(key) ? "beddefense.board-name-obsidian"
                : isRepairStatsKey(key) ? "beddefense.board-name-repair"
                : "beddefense.board-name";
        return msg().raw(name).replace("<name>", defense.name());
    }

    /** Every key a defense can hold times under, ranked or not. */
    public List<String> statsKeys(BedDefense defense) {
        return List.of(statsKey(defense.id()), practiceStatsKey(defense.id()),
                obsidianStatsKey(defense.id()), repairStatsKey(defense.id()));
    }

    /** The public boards a defense keeps: competitive, obsidian and repair. */
    public List<String> rankedStatsKeys(BedDefense defense) {
        return List.of(statsKey(defense.id()), obsidianStatsKey(defense.id()),
                repairStatsKey(defense.id()));
    }

    // ----------------------------------------------------------------- ticking

    public void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD, TICK_PERIOD);
        int minutes = plugin.pcConfig().bedDefenseReportsRemindMinutes();
        if (minutes > 0) {
            long period = minutes * 60L * 20L;
            reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::remindModerators, period, period);
        }
    }

    public void restartTask() {
        shutdown();
        allowedKinds = null; // the shop may have changed with the reload
        startTask();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
    }

    private void tickAll() {
        for (PracticeSession session : plugin.sessions().all()) {
            if (!(session.mode() instanceof BedDefenseMode)
                    || !(session.modeState() instanceof BedDefenseState state)) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null) {
                continue;
            }
            tickHologram(player, state);
            switch (state.phase()) {
                case PREVIEW -> tickPreview(player, state);
                case GUIDED -> tickGuide(state);
                case PLAY -> {
                    if (state.repair() && (session.state() == SessionState.READY
                            || session.state() == SessionState.ACTIVE)) {
                        repair.tick(player, session, state, TICK_PERIOD);
                    }
                }
                default -> {
                }
            }
            if (session.state() == SessionState.ACTIVE
                    || (state.phase() == Phase.GUIDED && session.state() == SessionState.READY)) {
                tickGenerators(state);
            }
        }
    }

    private void tickHologram(Player player, BedDefenseState state) {
        TextDisplay hologram = state.hologram();
        if (hologram == null) {
            return;
        }
        int left = state.hologramTicks() - TICK_PERIOD;
        state.setHologramTicks(left);
        double hide = plugin.pcConfig().bedDefenseHologramHideDistance();
        if (left <= 0 || !hologram.isValid() || !hologram.getWorld().equals(player.getWorld())
                || hologram.getLocation().distanceSquared(player.getLocation()) < hide * hide) {
            removeHologram(state);
        }
    }

    private void tickPreview(Player player, BedDefenseState state) {
        if (!state.previewPlaying()) {
            return;
        }
        int cooldown = state.previewCooldown() - TICK_PERIOD;
        if (cooldown > 0) {
            state.setPreviewCooldown(cooldown);
            return;
        }
        state.setPreviewCooldown(plugin.pcConfig().bedDefensePreviewStepTicks());
        previewStep(player, state, 1);
        if (state.previewIndex() >= state.previewTargets().size()) {
            state.setPreviewPlaying(false);
            refreshPreviewItems(player, state);
            msg().actionBar(player, "beddefense.preview.done");
        }
    }

    /** The guide blinks: shown for one beat, hidden for the next. */
    private void tickGuide(BedDefenseState state) {
        BlockDisplay guide = state.guide();
        if (guide == null || !guide.isValid()) {
            return;
        }
        int blink = state.guideBlink() + TICK_PERIOD;
        int period = plugin.pcConfig().bedDefenseGuideBlinkTicks();
        if (blink < period) {
            state.setGuideBlink(blink);
            return;
        }
        state.setGuideBlink(0);
        boolean shown = !state.guideShown();
        state.setGuideShown(shown);
        guide.setViewRange(shown ? 1.0f : 0.0f);
    }

    private void tickGenerators(BedDefenseState state) {
        for (BedDefenseState.Generator generator : state.generators()) {
            generator.countdown -= TICK_PERIOD;
            if (generator.countdown > 0) {
                continue;
            }
            generator.countdown = generator.intervalTicks;
            if (plugin.rush().nearbyDrops(generator.dropSpot, generator.drops)
                    < plugin.pcConfig().rushGeneratorItemCap()) {
                plugin.rush().dropTracked(generator.dropSpot, new ItemStack(generator.drops),
                        generator.type, false);
            }
        }
    }

    // ---------------------------------------------------------------- teardown

    /** Session end: entities gone, preview undone, phase intents forgotten. */
    public void cleanup(Player player, PracticeSession session, BedDefenseState state) {
        removeEntities(state);
        if (state.repair()) {
            // Leaving mid-run still counts the rounds survived so far.
            repair.record(player, session, state);
            repair.clearVolley(state);
        }
        if (state.phase() == Phase.PREVIEW) {
            undoPreview(state);
            if (player != null) {
                restoreStash(player, state);
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        if (player != null) {
            captureLayout(player, state);
        }
        UUID id = session.playerId();
        // A switch to another map tears the old session down after the new
        // one is registered — its intents (editor, play) belong to the new
        // round and must survive. Only a session that really ends forgets.
        PracticeSession now = plugin.sessions().get(id);
        if (now == null || now == session) {
            forget(id);
        }
    }

    public void forget(UUID player) {
        pendingEdit.remove(player);
        pendingPlay.remove(player);
        pendingLoad.remove(player);
        roundDefense.remove(player);
        lastRound.remove(player);
        lastMap.remove(player);
    }
}
