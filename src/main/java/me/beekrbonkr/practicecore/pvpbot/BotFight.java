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
    /** The floating name + health bar trailing the bot. */
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
    /** Ticks left of a defensive crouch; incoming knockback is scaled down. */
    public int crouchTicks;
    /** Ticks before the bot may crouch again. */
    public int crouchCooldown;
    /** Ticks left of an s-tap: the backward tap that eats incoming knockback. */
    public int stapTicks;
    /** Ticks before the bot may s-tap again. */
    public int stapCooldown;
    /** Ticks the bot chases its own knockback to keep a combo alive. */
    public int comboFollowTicks;
    /** +1 / -1: which way around the player the strafe is currently carrying it. */
    public int orbitSense;
    /** How long the strafe has been carrying it the same way around. */
    public int orbitTicks;
    /** Ticks left of a deliberate cut back the other way through the crosshair. */
    public int cutbackTicks;

    // ------------------------------------------------------- respawn timers
    /** Ticks the player stays "dead" (blind, untouchable) before respawning. */
    public int playerRespawnTicks;
    /** Ticks the bot stays despawned before its body comes back. */
    public int botRespawnTicks;
    /** Where the body waits out the hold — the spawn teleport is the respawn. */
    public Location deathAnchor;

    public boolean playerDead() {
        return playerRespawnTicks > 0;
    }

    public boolean botDead() {
        return botRespawnTicks > 0;
    }

    public boolean crouching() {
        return crouchTicks > 0;
    }

    /** Mid-s-tap: the hit that lands right now carries less knockback. */
    public boolean stapping() {
        return stapTicks > 0;
    }

    // ------------------------------------------------------------- AFK watch
    /** How long the player has been completely still, capped at the AFK limit. */
    public int afkTicks;
    /** The player's position and aim last tick, for the stillness check. */
    public Location afkLoc;

    /** The player stepped away — the bot stands down and waits. */
    public boolean afk(int limit) {
        return afkTicks >= limit;
    }

    /** Any player activity: ends an AFK hold, with a beat of mercy on the wake. */
    public void wake(int wakeGrace, int limit) {
        if (afk(limit)) {
            graceTicks = Math.max(graceTicks, wakeGrace);
        }
        afkTicks = 0;
    }

    // -------------------------------------------- the cerebral layer's memory
    /** Fight-level intent: circle and trade. */
    public static final int NEUTRAL = 0;
    /** Press a won exchange: tighter spacing, harder shoves. */
    public static final int PRESSURE = 1;
    /** Badly losing: kite, rod, block until the picture changes. */
    public static final int RESET = 2;
    public int stance = NEUTRAL;
    /** Ticks until the stance is reviewed again. */
    public int stanceTicks;
    /** Where the bot last registered the player — stale by its reaction time. */
    public Location seenLoc;
    /** The player's motion at that registration, for dead-reckoning. */
    public org.bukkit.util.Vector seenVel;
    /** Ticks until the next perception refresh. */
    public int seenIn;
    /** Decaying count of the player's hops — jumpy players get crit-fished. */
    public double jumpHabit;
    /** Decaying count of out-of-range swings — spam gets whiff-punished. */
    public double whiffHabit;
    /** The player's on-ground state last tick, to spot the moment they hop. */
    public boolean playerWasOnGround = true;
    /** Ticks of kiting left before a RESET must wheel around and attack. */
    public int resetBudget;
    /** Ticks before the bot may turn tail into another RESET. */
    public int resetLockout;
    /** Ticks left of the lunge that punishes a whiffed swing. */
    public int punishTicks;
    /** Ticks left of the backpedal feint; a charging player eats the counter. */
    public int feintTicks;
    /** Ticks before another feint may start. */
    public int feintCooldown;

    public boolean blocking() {
        return blockTicks > 0;
    }

    public void countHitLanded() {
        hitsLanded++;
        combo++;
        longestCombo = Math.max(longestCombo, combo);
    }

    public void countHitTaken(int window) {
        hitsTaken++;
        combo = 0;
        recentHitsTaken++;
        recentHitsWindow = window;
    }

    /** Fresh stock: full AI reset, stats keep counting. */
    public void resetStock(int graceTicks, int feintCooldown) {
        combo = 0;
        hitstunTicks = 0;
        crouchTicks = 0;
        crouchCooldown = 0;
        stapTicks = 0;
        stapCooldown = 0;
        comboFollowTicks = 0;
        orbitSense = 0;
        orbitTicks = 0;
        cutbackTicks = 0;
        attackCooldown = graceTicks;
        this.graceTicks = graceTicks;
        critTicks = -1;
        critBonusNextHit = false;
        blockTicks = 0;
        rodCooldown = 0;
        bowCooldown = 0;
        heldRevertTicks = 0;
        recentHitsTaken = 0;
        recentHitsWindow = 0;
        stance = NEUTRAL;
        stanceTicks = 0;
        resetBudget = 0;
        resetLockout = 0;
        seenLoc = null;
        seenVel = null;
        seenIn = 0;
        punishTicks = 0;
        feintTicks = 0;
        this.feintCooldown = feintCooldown;
        playerWasOnGround = true;
        afkTicks = 0;
        afkLoc = null;
        // jumpHabit / whiffHabit deliberately survive the stock: habits are
        // session-long reads on the player, not per-life state.
    }
}
