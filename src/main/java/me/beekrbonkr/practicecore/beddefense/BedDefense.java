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
 *
 * <p>Two moderation facts ride along. The <b>cleared fingerprint</b> is the
 * shape the author has finished in a competitive round — publishing needs
 * it, and because it names the exact shape, reshaping the defense silently
 * withdraws it. <b>Reports</b> are what other players flagged it for, one
 * per reporter, kept until a moderator dismisses them; {@code reportsSeen}
 * marks the last time a moderator looked at them, so anything newer is
 * "unseen" and worth a reminder. A defense enough of its builders have
 * reported is <b>auto-hidden</b>: private, and off limits to the author's
 * own publish button until a moderator has dealt with the reports.
 */
public final class BedDefense {

    /** One player's report on a defense. A second report from the same player replaces it. */
    public record Report(UUID reporter, String reporterName, String reason, long when) {
    }

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
    /** Players who have built it (started a round on it) at least once. */
    private final Set<UUID> played = new LinkedHashSet<>();
    private int completions;
    /** The shape the author completed competitively, or null until they have. */
    private String clearedFingerprint;
    private final List<Report> reports = new ArrayList<>();
    /** When a moderator last looked at its reports (0 = never). */
    private long reportsSeen;
    /** Hidden by its own builders' reports, awaiting a moderator. */
    private boolean autoHidden;

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

    /** Records that a player has started building it. @return true the first time */
    public boolean markPlayed(UUID player) {
        return played.add(player);
    }

    /** How many players other than the author have built it — the base a report share is measured against. */
    public int buildersBesidesAuthor() {
        return played.contains(author) ? played.size() - 1 : played.size();
    }

    public boolean isAuthor(UUID player) {
        return author.equals(player);
    }

    // ---------------------------------------------------------- moderation

    public String clearedFingerprint() {
        return clearedFingerprint;
    }

    public void setClearedFingerprint(String clearedFingerprint) {
        this.clearedFingerprint = clearedFingerprint;
    }

    /**
     * Whether the author has finished this exact shape in a competitive
     * round. A reshaped defense keeps the old fingerprint on record, so this
     * turns false the moment the blocks change and true again once the new
     * version has been built for real.
     */
    public boolean authorCleared() {
        return clearedFingerprint != null && clearedFingerprint.equals(fingerprint);
    }

    /** Every open report, oldest first. */
    public List<Report> reports() {
        return Collections.unmodifiableList(reports);
    }

    public int reportCount() {
        return reports.size();
    }

    public Report reportBy(UUID player) {
        for (Report report : reports) {
            if (report.reporter().equals(player)) {
                return report;
            }
        }
        return null;
    }

    /** Files a report, replacing an earlier one from the same player. @return true when it is new */
    public boolean addReport(Report report) {
        boolean replaced = reports.removeIf(r -> r.reporter().equals(report.reporter()));
        reports.add(report);
        return !replaced;
    }

    public boolean removeReport(UUID reporter) {
        return reports.removeIf(r -> r.reporter().equals(reporter));
    }

    public void clearReports() {
        reports.clear();
    }

    /** Reports from players who have actually built this defense. */
    public int reportsFromBuilders() {
        int count = 0;
        for (Report report : reports) {
            if (played.contains(report.reporter())) {
                count++;
            }
        }
        return count;
    }

    /** When a moderator last looked at the reports, or 0. */
    public long reportsSeen() {
        return reportsSeen;
    }

    public void setReportsSeen(long reportsSeen) {
        this.reportsSeen = Math.max(0, reportsSeen);
    }

    /** Reports filed since a moderator last looked. */
    public int unseenReports() {
        int count = 0;
        for (Report report : reports) {
            if (report.when() > reportsSeen) {
                count++;
            }
        }
        return count;
    }

    /** A moderator has looked at every report there is right now. @return true when any was unseen */
    public boolean markReportsSeen() {
        boolean changed = unseenReports() > 0;
        reportsSeen = Math.max(System.currentTimeMillis(), latestReport());
        return changed;
    }

    public boolean autoHidden() {
        return autoHidden;
    }

    public void setAutoHidden(boolean autoHidden) {
        this.autoHidden = autoHidden;
    }

    /** When the newest report came in, or 0 with none. */
    public long latestReport() {
        long latest = 0;
        for (Report report : reports) {
            latest = Math.max(latest, report.when());
        }
        return latest;
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

    /**
     * Whether obsidian practice can run on this defense: the round is
     * putting the eight obsidian on the bed yourself, so a defense that
     * already has obsidian on any of those spots has nothing to practice.
     */
    public boolean obsidianEligible() {
        for (DefenseBlock block : blocks) {
            if (block.kind() == Material.OBSIDIAN
                    && DefenseFrame.isCover(new org.bukkit.util.Vector(block.x(), block.y(), block.z()))) {
                return false;
            }
        }
        return true;
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
