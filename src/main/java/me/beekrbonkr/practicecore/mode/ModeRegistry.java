package me.beekrbonkr.practicecore.mode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ModeRegistry {

    private final Map<String, Mode> modes = new LinkedHashMap<>();

    public void register(Mode mode) {
        modes.put(mode.id(), mode);
    }

    public Optional<Mode> get(String id) {
        return Optional.ofNullable(modes.get(id));
    }

    /**
     * The mode a template runs under. Templates with unknown modes are
     * rejected at load, so this only falls back to bridging for defensive
     * completeness (e.g. a mode unregistered mid-session by a reload).
     */
    public Mode of(me.beekrbonkr.practicecore.template.ArenaTemplate template) {
        Mode mode = modes.get(template.mode());
        return mode != null ? mode : modes.get(BridgingMode.ID);
    }

    public Set<String> ids() {
        return modes.keySet();
    }
}
