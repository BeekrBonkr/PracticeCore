package me.beekrbonkr.practicecore.rushbot;

import me.beekrbonkr.practicecore.pvpbot.BotSettings;
import org.bukkit.Location;
import org.bukkit.entity.Husk;
import org.bukkit.entity.TextDisplay;

/**
 * One defender bot guarding an enemy rush base. Owned and ticked by
 * {@link RushBotService}; the list lives in the session's RushState so the
 * arena reset can rebuild the whole squad from the selection.
 */
public final class RushBot {

    private final String team;
    /** The guard spot the bot idles at and returns to when it loses interest. */
    private final Location post;
    /** The resolved difficulty (built from the chosen preset's tiers). */
    private final BotSettings settings;

    /** The live body; null while the bot is down. */
    public Husk entity;
    /** The floating team + health line over its head. */
    public TextDisplay tag;
    /** Ticks until a downed bot comes back — only while its bed stands. */
    public int respawnTicks;
    /** Eliminated for good: it died after its bed was broken. */
    public boolean out;

    // ---------------------------------------------------------- brain scratch
    /** Ticks of active hostility left; refreshed by sight, damage and alerts. */
    public int aggroTicks;
    public int attackCooldown;
    public int repathTicks;
    public int strafeSign = 1;
    public int strafeFlipTicks;
    /** Ticks riding incoming knockback instead of steering — combos work. */
    public int hitstunTicks;
    /** Ticks until the overhead tag text refreshes. */
    public int nameTicks;
    /** Last position, to spot being stuck against a wall and hop. */
    public double lastX;
    public double lastZ;

    public RushBot(String team, Location post, BotSettings settings) {
        this.team = team;
        this.post = post;
        this.settings = settings;
    }

    public String team() {
        return team;
    }

    public Location post() {
        return post;
    }

    public BotSettings settings() {
        return settings;
    }

    public Husk entity() {
        return entity;
    }

    public boolean alive() {
        return entity != null && entity.isValid();
    }
}
