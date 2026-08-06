package me.beekrbonkr.practicecore.util;

public final class TimeFormat {

    private TimeFormat() {
    }

    /**
     * Live scoreboard display. The server ticks at 20 Hz, so tenths are the
     * finest resolution that reads honestly mid-run.
     */
    public static String tenths(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long tenths = (millis % 1000) / 100;
        return "%d:%02d.%d".formatted(minutes, seconds, tenths);
    }

    /** Exact final time, shown on finish. */
    public static String precise(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return "%d:%02d.%03d".formatted(minutes, seconds, ms);
    }
}
