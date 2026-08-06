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

    public Set<String> ids() {
        return modes.keySet();
    }
}
