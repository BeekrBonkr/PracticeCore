package me.beekrbonkr.practicecore.rush;

import me.beekrbonkr.practicecore.template.ArenaTemplate;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rush layout of one arena, read from arena.yml {@code settings.rush}:
 * team bases (spawn + bed), item generators and shop dealer spots, all as
 * offsets from the paste origin. Written by the MBedwars importer or the
 * setup wizard's rush subcommands; parsed fresh per session.
 *
 * <pre>
 * rush:
 *   source: lobby-map          # optional MBedwars arena it was imported from
 *   teams:
 *     RED:
 *       spawn: {x: 1.5, y: 2.0, z: 3.5, yaw: 90.0, pitch: 0.0}
 *       bed: {x: 4, y: 1, z: 5, facing: NORTH, material: RED_BED}
 *   generators:
 *     - {type: iron, x: 2, y: 1, z: 3}
 *   dealers:
 *     - {x: 5.5, y: 2.0, z: 3.5, yaw: 180.0}
 * </pre>
 */
public final class RushMapData {

    /** One team's base. {@code bedHead} is null while the bed is unset. */
    public record TeamBase(String name, Vector spawn, float yaw, float pitch,
                           Vector bedHead, BlockFace bedFacing, Material bedMaterial) {

        public boolean playable() {
            return spawn != null && bedHead != null;
        }
    }

    /** {@code type} is a generator id: iron, gold, diamond or emerald. */
    public record Generator(String type, Vector offset) {
    }

    public record Dealer(Vector offset, float yaw) {
    }

    private final Map<String, TeamBase> teams = new LinkedHashMap<>();
    private final List<Generator> generators = new ArrayList<>();
    private final List<Dealer> dealers = new ArrayList<>();
    private final String source;

    private RushMapData(String source) {
        this.source = source;
    }

    public static RushMapData parse(ArenaTemplate template) {
        return parse(template.settingsSection().getConfigurationSection("rush"));
    }

    /** Parses an unsaved settings map — the setup wizard's live state. */
    public static RushMapData parseSettings(Map<String, Object> settings) {
        org.bukkit.configuration.MemoryConfiguration holder =
                new org.bukkit.configuration.MemoryConfiguration();
        return parse(holder.createSection("settings", settings).getConfigurationSection("rush"));
    }

    private static RushMapData parse(ConfigurationSection rush) {
        RushMapData data = new RushMapData(rush == null ? null : rush.getString("source"));
        if (rush == null) {
            return data;
        }
        ConfigurationSection teams = rush.getConfigurationSection("teams");
        if (teams != null) {
            for (String name : teams.getKeys(false)) {
                ConfigurationSection team = teams.getConfigurationSection(name);
                if (team == null) {
                    continue;
                }
                Vector spawn = null;
                float yaw = 0;
                float pitch = 0;
                ConfigurationSection s = team.getConfigurationSection("spawn");
                if (s != null) {
                    spawn = new Vector(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                    yaw = (float) s.getDouble("yaw");
                    pitch = (float) s.getDouble("pitch");
                }
                Vector bedHead = null;
                BlockFace facing = BlockFace.NORTH;
                Material material = Material.RED_BED;
                ConfigurationSection b = team.getConfigurationSection("bed");
                if (b != null) {
                    bedHead = new Vector(b.getInt("x"), b.getInt("y"), b.getInt("z"));
                    try {
                        BlockFace parsed = BlockFace.valueOf(b.getString("facing", "NORTH")
                                .toUpperCase(Locale.ROOT));
                        // Cardinal faces only — a bed can't face NORTH_EAST,
                        // and createBlockData would throw mid-rebuild on it.
                        switch (parsed) {
                            case NORTH, SOUTH, EAST, WEST -> facing = parsed;
                            default -> { }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                    Material parsed = Material.matchMaterial(b.getString("material", "RED_BED"));
                    if (parsed != null && Tag.BEDS.isTagged(parsed)) {
                        material = parsed;
                    }
                }
                data.teams.put(name.toUpperCase(Locale.ROOT),
                        new TeamBase(name.toUpperCase(Locale.ROOT), spawn, yaw, pitch,
                                bedHead, facing, material));
            }
        }
        for (Map<?, ?> entry : rush.getMapList("generators")) {
            Object type = entry.get("type");
            if (type == null) {
                continue;
            }
            data.generators.add(new Generator(
                    String.valueOf(type).toLowerCase(Locale.ROOT),
                    new Vector(number(entry.get("x")), number(entry.get("y")), number(entry.get("z")))));
        }
        for (Map<?, ?> entry : rush.getMapList("dealers")) {
            data.dealers.add(new Dealer(
                    new Vector(number(entry.get("x")), number(entry.get("y")), number(entry.get("z"))),
                    (float) number(entry.get("yaw"))));
        }
        return data;
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    // -------------------------------------------------------------- writing

    /**
     * The {@code settings.rush} subtree as plain maps, the shape
     * {@link ArenaTemplate#save()} persists. Mutating helpers below edit this
     * structure in place inside a template's settings.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> rushSection(Map<String, Object> settings) {
        return (Map<String, Object>) settings
                .computeIfAbsent("rush", k -> new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> teamSection(Map<String, Object> settings, String team) {
        Map<String, Object> teams = (Map<String, Object>) rushSection(settings)
                .computeIfAbsent("teams", k -> new LinkedHashMap<String, Object>());
        return (Map<String, Object>) teams
                .computeIfAbsent(team.toUpperCase(Locale.ROOT), k -> new LinkedHashMap<String, Object>());
    }

    public static void writeTeamSpawn(Map<String, Object> settings, String team,
                                      Vector offset, float yaw, float pitch) {
        Map<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("x", offset.getX());
        spawn.put("y", offset.getY());
        spawn.put("z", offset.getZ());
        spawn.put("yaw", yaw);
        spawn.put("pitch", pitch);
        teamSection(settings, team).put("spawn", spawn);
    }

    public static void writeTeamBed(Map<String, Object> settings, String team,
                                    Vector headOffset, BlockFace facing, Material material) {
        Map<String, Object> bed = new LinkedHashMap<>();
        bed.put("x", headOffset.getBlockX());
        bed.put("y", headOffset.getBlockY());
        bed.put("z", headOffset.getBlockZ());
        bed.put("facing", facing.name());
        bed.put("material", material.name());
        teamSection(settings, team).put("bed", bed);
    }

    @SuppressWarnings("unchecked")
    public static void addGenerator(Map<String, Object> settings, String type, Vector offset) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) rushSection(settings)
                .computeIfAbsent("generators", k -> new ArrayList<Map<String, Object>>());
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("type", type.toLowerCase(Locale.ROOT));
        gen.put("x", offset.getBlockX());
        gen.put("y", offset.getBlockY());
        gen.put("z", offset.getBlockZ());
        list.add(gen);
    }

    @SuppressWarnings("unchecked")
    public static void addDealer(Map<String, Object> settings, Vector offset, float yaw) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) rushSection(settings)
                .computeIfAbsent("dealers", k -> new ArrayList<Map<String, Object>>());
        Map<String, Object> dealer = new LinkedHashMap<>();
        dealer.put("x", offset.getX());
        dealer.put("y", offset.getY());
        dealer.put("z", offset.getZ());
        dealer.put("yaw", yaw);
        list.add(dealer);
    }

    public static void writeSource(Map<String, Object> settings, String source) {
        rushSection(settings).put("source", source);
    }

    // -------------------------------------------------------------- lookups

    public Map<String, TeamBase> teams() {
        return Map.copyOf(teams);
    }

    /** Teams with both a spawn and a bed, in configured order. */
    public List<TeamBase> playableTeams() {
        return teams.values().stream().filter(TeamBase::playable).toList();
    }

    public TeamBase team(String name) {
        return name == null ? null : teams.get(name.toUpperCase(Locale.ROOT));
    }

    public List<Generator> generators() {
        return List.copyOf(generators);
    }

    public List<Generator> generatorsOf(String type) {
        return generators.stream().filter(g -> g.type().equals(type)).toList();
    }

    public List<Dealer> dealers() {
        return List.copyOf(dealers);
    }

    public String source() {
        return source;
    }

    /** A rush session needs at least one base to spawn at and attack. */
    public boolean playable() {
        return !playableTeams().isEmpty();
    }
}
