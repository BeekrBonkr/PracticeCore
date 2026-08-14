package me.beekrbonkr.practicecore.pvpbot;

import org.bukkit.Location;
import org.bukkit.entity.Husk;

/**
 * Per-session PvP spar scratch state, owned by PvpBotMode and ticked by
 * {@link PvpBotService}. Holds the bot entity, the session's loaded settings,
 * the running stats the sidebar shows, and the AI's working memory.
 */
public final class BotFight {

    // ------------------------------------------------------------- identity
    public Husk bot;
    /** The floating name + health bar riding the bot. */
    public org.bukkit.entity.TextDisplay nameTag;
    public BotSettings settings;
    public Location playerSpawn;
    public Location botSpawn;

    // ---------------------------------------------------------------- stats
    public int hitsLanded;
    public int hitsTaken;
    public int combo;
    public int longestCombo;
    public int kills;
    public int deaths;

    // ----------------------------------------------------------- AI memory
    /** Settings GUI open — the bot stands still and holds fire. */
    public boolean paused;
    /** Ticks until the next swing is allowed. */
    public int attackCooldown;
    /** Ticks left in the post-stock grace where the bot stays passive. */
    public int graceTicks;
    /** +1 / -1: which side the bot is strafing toward when aimed at dead-on. */
    public int strafeSign = 1;
    /** Ticks until the strafe side may flip again. */
    public int strafeFlipTicks;
    /** Countdown of a jump-crit in progress; strikes when it reaches zero. */
    public int critTicks = -1;
    /** The very next bot hit carries the crit bonus (consumed by the listener). */
    public boolean critBonusNextHit;
    /** Ticks left holding the 1.8 sword block; damage taken is halved. */
    public int blockTicks;
    public int rodCooldown;
    public int bowCooldown;
    /** Ticks until the bot's held item reverts to its sword (rod/bow flourish). */
    public int heldRevertTicks;
    /** Recent hits taken in a short window — triggers defensive blocking. */
    public int recentHitsTaken;
    public int recentHitsWindow;
    /** Last bot position, to spot being stuck against a wall and hop. */
    public double lastX;
    public double lastZ;
    /** Ticks until the approach path is recomputed — A* every tick is waste. */
    public int repathTicks;
    /** Ticks until the nametag health bar refreshes. */
    public int nameTicks;
    /** Ticks the bot rides incoming knockback without fighting it — combos. */
    public int hitstunTicks;

    public boolean blocking() {
        return blockTicks > 0;
    }

    public void countHitLanded() {
        hitsLanded++;
        combo++;
        longestCombo = Math.max(longestCombo, combo);
    }

    public void countHitTaken() {
        hitsTaken++;
        combo = 0;
        recentHitsTaken++;
        recentHitsWindow = 30;
    }

    /** Fresh stock: full AI reset, stats keep counting. */
    public void resetStock() {
        combo = 0;
        hitstunTicks = 0;
        attackCooldown = 20;
        graceTicks = 20;
        critTicks = -1;
        critBonusNextHit = false;
        blockTicks = 0;
        rodCooldown = 0;
        bowCooldown = 0;
        heldRevertTicks = 0;
        recentHitsTaken = 0;
        recentHitsWindow = 0;
    }
}
