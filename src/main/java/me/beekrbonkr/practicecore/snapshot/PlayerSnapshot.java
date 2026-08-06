package me.beekrbonkr.practicecore.snapshot;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete pre-practice player state. Persisted to disk the moment a session
 * starts so a server crash can never cost the player their inventory: on next
 * login an orphaned snapshot is simply restored.
 */
public final class PlayerSnapshot {

    private final ItemStack[] inventory; // all 41 slots: storage, armor, off-hand
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final GameMode gameMode;
    private final int level;
    private final float exp;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int fireTicks;
    private final int remainingAir;
    private final boolean allowFlight;
    private final boolean flying;
    private final List<PotionEffect> effects;

    private PlayerSnapshot(ItemStack[] inventory, String worldName, double x, double y, double z,
                           float yaw, float pitch, GameMode gameMode, int level, float exp,
                           double health, int foodLevel, float saturation, float exhaustion,
                           int fireTicks, int remainingAir, boolean allowFlight, boolean flying,
                           List<PotionEffect> effects) {
        this.inventory = inventory;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.level = level;
        this.exp = exp;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.fireTicks = fireTicks;
        this.remainingAir = remainingAir;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = effects;
    }

    public static PlayerSnapshot capture(Player player) {
        Location loc = player.getLocation();
        return new PlayerSnapshot(
                player.getInventory().getContents().clone(),
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                player.getGameMode(),
                player.getLevel(), player.getExp(),
                player.getHealth(),
                player.getFoodLevel(), player.getSaturation(), player.getExhaustion(),
                player.getFireTicks(), player.getRemainingAir(),
                player.getAllowFlight(), player.isFlying(),
                new ArrayList<>(player.getActivePotionEffects()));
    }

    /**
     * Restores everything. When {@code restoreLocation} is false (leave-by-
     * teleport: another plugin's destination wins) the player stays where the
     * teleport put them. Teleports synchronously — valid during quit and
     * shutdown, the two moments this must never fail.
     */
    public void apply(Player player, boolean restoreLocation) {
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }
        if (restoreLocation) {
            player.teleport(restoreLocation());
        }
        player.getInventory().setContents(inventory);
        player.setGameMode(gameMode);
        player.setLevel(level);
        player.setExp(exp);
        player.setHealth(Math.min(health, maxHealth(player)));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setFireTicks(fireTicks);
        player.setRemainingAir(remainingAir);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        player.addPotionEffects(effects);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
    }

    private Location restoreLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Original world gone (e.g. renamed) — fall back to main spawn.
            world = Bukkit.getWorlds().get(0);
            return world.getSpawnLocation();
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static double maxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    public void serialize(ConfigurationSection yml) {
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null) {
                yml.set("inventory." + i, inventory[i]);
            }
        }
        yml.set("inventory-size", inventory.length);
        yml.set("world", worldName);
        yml.set("x", x);
        yml.set("y", y);
        yml.set("z", z);
        yml.set("yaw", yaw);
        yml.set("pitch", pitch);
        yml.set("gamemode", gameMode.name());
        yml.set("level", level);
        yml.set("exp", exp);
        yml.set("health", health);
        yml.set("food", foodLevel);
        yml.set("saturation", saturation);
        yml.set("exhaustion", exhaustion);
        yml.set("fire-ticks", fireTicks);
        yml.set("remaining-air", remainingAir);
        yml.set("allow-flight", allowFlight);
        yml.set("flying", flying);
        yml.set("effects", effects);
    }

    @SuppressWarnings("unchecked")
    public static PlayerSnapshot deserialize(ConfigurationSection yml) {
        int size = yml.getInt("inventory-size", 41);
        ItemStack[] inventory = new ItemStack[size];
        ConfigurationSection inv = yml.getConfigurationSection("inventory");
        if (inv != null) {
            for (String key : inv.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < size) {
                        inventory[slot] = inv.getItemStack(key);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(yml.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gameMode = GameMode.SURVIVAL;
        }
        List<PotionEffect> effects = (List<PotionEffect>) (List<?>) yml.getList("effects", List.of());
        return new PlayerSnapshot(
                inventory,
                yml.getString("world", Bukkit.getWorlds().get(0).getName()),
                yml.getDouble("x"), yml.getDouble("y"), yml.getDouble("z"),
                (float) yml.getDouble("yaw"), (float) yml.getDouble("pitch"),
                gameMode,
                yml.getInt("level"), (float) yml.getDouble("exp"),
                yml.getDouble("health", 20.0),
                yml.getInt("food", 20), (float) yml.getDouble("saturation", 5.0),
                (float) yml.getDouble("exhaustion"),
                yml.getInt("fire-ticks"), yml.getInt("remaining-air", 300),
                yml.getBoolean("allow-flight"), yml.getBoolean("flying"),
                new ArrayList<>(effects));
    }
}
