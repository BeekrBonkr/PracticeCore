package me.beekrbonkr.practicecore.config;

import java.util.List;

/**
 * Outcome of {@code /practice reload}.
 *
 * @param ok           true when the new settings are live
 * @param needsConfirm true when the reload stopped because it would end live
 *                     sessions and the admin has not said yes yet
 * @param notes        what to show the admin, in order
 */
public record ReloadResult(boolean ok, boolean needsConfirm, List<String> notes) {

    public static ReloadResult failed(List<String> notes) {
        return new ReloadResult(false, false, notes);
    }

    public static ReloadResult confirm(List<String> notes) {
        return new ReloadResult(false, true, notes);
    }

    public static ReloadResult ok(List<String> notes) {
        return new ReloadResult(true, false, notes);
    }
}
