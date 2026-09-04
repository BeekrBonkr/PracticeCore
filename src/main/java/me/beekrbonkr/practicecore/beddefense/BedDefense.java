package me.beekrbonkr.practicecore.beddefense;

import org.bukkit.Material;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One saved bed defense: the blocks around a bed in the order they were
 * placed, who designed it, whether it is published, and the community
 * numbers the gallery sorts by. Blocks are immutable once saved; the stats
 * and visibility change in place and are written back by
 * {@link DefenseStore}.
 */
public final class BedDefense {

    private final String id;
    private String name;
    private final UUID author;
    private String authorName;
    private final long created;
    private boolean published;
    private final List<DefenseBlock> blocks;
    private final String fingerprint;
    /** Players who liked it — one like each, toggled. */
    private final Set<UUID> likes = new LinkedHashSet<>();
    /** Players who finished building it at least once. */
    private final Set<UUID> played = new LinkedHashSet<>();
    private int completions;

    public BedDefense(String id, String name, UUID author, String authorName, long created,
                      boolean published, List<DefenseBlock> blocks) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.authorName = authorName;
        this.created = created;
        this.published = published;
        this.blocks = List.copyOf(blocks);
        this.fingerprint = fingerprintOf(this.blocks);
    }

    /**
     * The shape's identity: every block's position and kind, sorted, hashed.
     * Order of placement is deliberately left out — two defenses that play
     * identically are the same defense, however they were built.
     */
    public static String fingerprintOf(List<DefenseBlock> blocks) {
        List<String> lines = new ArrayList<>(blocks.size());
        for (DefenseBlock block : blocks) {
            lines.add(block.fingerprintLine());
        }
        Collections.sort(lines);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(String.join("\n", lines).hashCode());
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID author() {
        return author;
    }

    public String authorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public long created() {
        return created;
    }

    public boolean published() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public List<DefenseBlock> blocks() {
        return blocks;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Set<UUID> likes() {
        return likes;
    }

    public int likeCount() {
        return likes.size();
    }

    public boolean likedBy(UUID player) {
        return likes.contains(player);
    }

    public Set<UUID> played() {
        return played;
    }

    public int uniquePlayers() {
        return played.size();
    }

    public int completions() {
        return completions;
    }

    public void setCompletions(int completions) {
        this.completions = Math.max(0, completions);
    }

    public void countCompletion(UUID player) {
        played.add(player);
        completions++;
    }

    public boolean isAuthor(UUID player) {
        return author.equals(player);
    }

    /** The shape's most exposed material — what a rusher meets — as an icon. */
    public Material icon() {
        Map<Material, Integer> counts = kindCounts();
        Material best = null;
        int most = 0;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            if (entry.getKey() == Material.WATER || entry.getKey() == Material.LADDER) {
                continue;
            }
            if (entry.getValue() > most) {
                most = entry.getValue();
                best = entry.getKey();
            }
        }
        return best != null ? best : Material.RED_BED;
    }

    /** How many blocks of each kind the defense needs, in first-placed order. */
    public Map<Material, Integer> kindCounts() {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (DefenseBlock block : blocks) {
            counts.merge(block.kind(), 1, Integer::sum);
        }
        return counts;
    }

    public boolean containsKind(Material kind) {
        for (DefenseBlock block : blocks) {
            if (block.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** How far out, up or down from the bed the defense reaches. */
    public int reach() {
        int reach = 0;
        for (DefenseBlock block : blocks) {
            reach = Math.max(reach, Math.max(Math.abs(block.x()),
                    Math.max(Math.abs(block.y()), Math.abs(block.z()))));
        }
        return reach;
    }
}
