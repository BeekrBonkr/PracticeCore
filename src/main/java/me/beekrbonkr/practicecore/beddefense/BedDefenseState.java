package me.beekrbonkr.practicecore.beddefense;

import me.beekrbonkr.practicecore.rush.RushMapData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-session bed defense scratch state. Unlike rush's, it survives arena
 * resets: the phase a player is in (building, previewing, being guided,
 * editing) is theirs until they change it, so a reset rebuilds the round
 * inside the same object. Owned by {@link me.beekrbonkr.practicecore.mode.BedDefenseMode};
 * ticked by {@link BedDefenseService}.
 */
public final class BedDefenseState {

    public enum Phase {
        /** Timed building of the chosen defense. */
        PLAY,
        /** Watching the defense assemble itself, block by block. */
        PREVIEW,
        /** Building with the next block pointed out; untimed. */
        GUIDED,
        /** Designing a defense of your own. */
        EDIT
    }

    /** One block the round expects, resolved into the world. */
    public record Target(DefenseBlock block, Location loc) {
    }

    /** A base generator armed for this round. */
    public static final class Generator {
        final Location dropSpot;
        final Material drops;
        final String type;
        final int intervalTicks;
        int countdown;

        Generator(Location dropSpot, Material drops, String type, int intervalTicks) {
            this.dropSpot = dropSpot;
            this.drops = drops;
            this.type = type;
            this.intervalTicks = Math.max(1, intervalTicks);
            this.countdown = this.intervalTicks;
        }
    }

    private Phase phase = Phase.PLAY;
    private RushMapData data;
    private RushMapData.TeamBase base;
    private DefenseFrame frame;
    private BedDefenseSelection selection = BedDefenseSelection.defaults();
    /** The defense this round builds (or previews / guides); null while editing fresh. */
    private BedDefense defense;
    private final List<Target> targets = new ArrayList<>();
    private final List<Generator> generators = new ArrayList<>();
    /** Blocks the player placed this attempt — an attempt is "in progress" once this is > 0. */
    private int placedThisAttempt;
    /** Set once the finishing block landed, so a second event never re-finishes. */
    private boolean finishing;

    // ---- preview
    private ItemStack[] stashedInventory;
    private ItemStack[] stashedArmor;
    private int previewIndex;
    private boolean previewPlaying;
    private int previewCooldown;
    private final Map<Location, BlockData> previewReplaced = new LinkedHashMap<>();

    // ---- guided
    private BlockDisplay guide;
    private boolean guideShown;
    private int guideBlink;

    // ---- hologram
    private TextDisplay hologram;
    private int hologramTicks;

    // ---- edit
    private String editSourceId;
    private String editName;
    private boolean editPublished;
    /** The build so far: each block's position → what stands there, in placement order. */
    private final Map<Location, BlockData> editSequence = new LinkedHashMap<>();

    // ------------------------------------------------------------ accessors

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public RushMapData data() {
        return data;
    }

    public void setData(RushMapData data) {
        this.data = data;
    }

    public RushMapData.TeamBase base() {
        return base;
    }

    public void setBase(RushMapData.TeamBase base) {
        this.base = base;
    }

    public DefenseFrame frame() {
        return frame;
    }

    public void setFrame(DefenseFrame frame) {
        this.frame = frame;
    }

    public BedDefenseSelection selection() {
        return selection;
    }

    public void setSelection(BedDefenseSelection selection) {
        this.selection = selection;
    }

    public BedDefense defense() {
        return defense;
    }

    public void setDefense(BedDefense defense) {
        this.defense = defense;
    }

    public List<Target> targets() {
        return targets;
    }

    public List<Generator> generators() {
        return generators;
    }

    public int placedThisAttempt() {
        return placedThisAttempt;
    }

    public void countPlaced() {
        placedThisAttempt++;
    }

    public void resetAttempt() {
        placedThisAttempt = 0;
        finishing = false;
    }

    public boolean finishing() {
        return finishing;
    }

    public void setFinishing(boolean finishing) {
        this.finishing = finishing;
    }

    /**
     * An attempt is in progress once a block is down. A running clock alone
     * does not count: stepping off the spawn starts it, and a preview that
     * refused every player who had taken a step would never be seen.
     */
    public boolean attemptInProgress(boolean timerRunning) {
        return placedThisAttempt > 0;
    }

    // ---- preview

    public ItemStack[] stashedInventory() {
        return stashedInventory;
    }

    public ItemStack[] stashedArmor() {
        return stashedArmor;
    }

    /** {@code armor} is unused now that the whole inventory is stashed in one array. */
    public void stash(ItemStack[] inventory, ItemStack[] armor) {
        this.stashedInventory = inventory;
        this.stashedArmor = armor;
    }

    public void clearStash() {
        stashedInventory = null;
        stashedArmor = null;
    }

    public int previewIndex() {
        return previewIndex;
    }

    public void setPreviewIndex(int previewIndex) {
        this.previewIndex = previewIndex;
    }

    public boolean previewPlaying() {
        return previewPlaying;
    }

    public void setPreviewPlaying(boolean previewPlaying) {
        this.previewPlaying = previewPlaying;
    }

    public int previewCooldown() {
        return previewCooldown;
    }

    public void setPreviewCooldown(int previewCooldown) {
        this.previewCooldown = previewCooldown;
    }

    public Map<Location, BlockData> previewReplaced() {
        return previewReplaced;
    }

    // ---- guided

    public BlockDisplay guide() {
        return guide;
    }

    public void setGuide(BlockDisplay guide) {
        this.guide = guide;
    }

    public boolean guideShown() {
        return guideShown;
    }

    public void setGuideShown(boolean guideShown) {
        this.guideShown = guideShown;
    }

    public int guideBlink() {
        return guideBlink;
    }

    public void setGuideBlink(int guideBlink) {
        this.guideBlink = guideBlink;
    }

    // ---- hologram

    public TextDisplay hologram() {
        return hologram;
    }

    public void setHologram(TextDisplay hologram) {
        this.hologram = hologram;
    }

    public int hologramTicks() {
        return hologramTicks;
    }

    public void setHologramTicks(int hologramTicks) {
        this.hologramTicks = hologramTicks;
    }

    // ---- edit

    public String editSourceId() {
        return editSourceId;
    }

    public void setEditSourceId(String editSourceId) {
        this.editSourceId = editSourceId;
    }

    public String editName() {
        return editName;
    }

    public void setEditName(String editName) {
        this.editName = editName;
    }

    public boolean editPublished() {
        return editPublished;
    }

    public void setEditPublished(boolean editPublished) {
        this.editPublished = editPublished;
    }

    public Map<Location, BlockData> editSequence() {
        return editSequence;
    }

    /** True for a block of the current build — what the editor lets you break. */
    public boolean isEditBlock(Location loc) {
        return editSequence.containsKey(loc.getBlock().getLocation());
    }

    // -------------------------------------------------------------- progress

    /** How many of the round's targets stand satisfied right now. */
    public int satisfied() {
        int count = 0;
        for (Target target : targets) {
            if (isSatisfied(target)) {
                count++;
            }
        }
        return count;
    }

    public boolean isSatisfied(Target target) {
        return BlockKinds.kindOf(target.loc().getBlock()) == target.block().kind();
    }

    /** The first target in placement order that is not yet built, or null when done. */
    public Target nextTarget() {
        for (Target target : targets) {
            if (!isSatisfied(target)) {
                return target;
            }
        }
        return null;
    }

    public Target targetAt(Location loc) {
        for (Target target : targets) {
            if (target.loc().getBlockX() == loc.getBlockX()
                    && target.loc().getBlockY() == loc.getBlockY()
                    && target.loc().getBlockZ() == loc.getBlockZ()) {
                return target;
            }
        }
        return null;
    }
}
