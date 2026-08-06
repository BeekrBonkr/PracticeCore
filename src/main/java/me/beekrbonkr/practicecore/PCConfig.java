package me.beekrbonkr.practicecore;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml. */
public final class PCConfig {

    public enum TimerStartMode { MOVE, FIRST_BLOCK }

    private final String worldName;
    private final int gridSpacing;
    private final int baseY;
    private final int maxSchematicSize;
    private final TimerStartMode timerStartMode;
    private final int scoreboardTicks;
    private final int failYOffset;
    private final boolean allowPearls;
    private final boolean allowBuckets;

    public PCConfig(FileConfiguration cfg) {
        this.worldName = cfg.getString("world.name", "practice_world");
        this.gridSpacing = cfg.getInt("grid.spacing", 1000);
        this.baseY = cfg.getInt("grid.base-y", 100);
        this.maxSchematicSize = cfg.getInt("grid.max-schematic-size", 800);
        TimerStartMode mode;
        try {
            mode = TimerStartMode.valueOf(cfg.getString("timer.start-mode", "MOVE").toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = TimerStartMode.MOVE;
        }
        this.timerStartMode = mode;
        this.scoreboardTicks = Math.max(1, cfg.getInt("scoreboard.update-ticks", 2));
        this.failYOffset = cfg.getInt("session.fail-y-offset", 0);
        this.allowPearls = cfg.getBoolean("session.allow-pearls", false);
        this.allowBuckets = cfg.getBoolean("session.allow-buckets", false);
    }

    public String worldName() {
        return worldName;
    }

    public int gridSpacing() {
        return gridSpacing;
    }

    public int baseY() {
        return baseY;
    }

    public int maxSchematicSize() {
        return maxSchematicSize;
    }

    public TimerStartMode timerStartMode() {
        return timerStartMode;
    }

    public int scoreboardTicks() {
        return scoreboardTicks;
    }

    public int failYOffset() {
        return failYOffset;
    }

    public boolean allowPearls() {
        return allowPearls;
    }

    public boolean allowBuckets() {
        return allowBuckets;
    }
}
