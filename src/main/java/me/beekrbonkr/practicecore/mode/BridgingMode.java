package me.beekrbonkr.practicecore.mode;

public final class BridgingMode implements Mode {

    public static final String ID = "bridging";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Bridging";
    }
}
