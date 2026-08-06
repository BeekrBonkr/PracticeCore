package me.beekrbonkr.practicecore.grid;

import java.util.UUID;

public final class Slot {

    private final int index;
    private final int gridX;
    private final int gridZ;
    private SlotState state = SlotState.FREE;
    private UUID occupant;

    Slot(int index, int gridX, int gridZ) {
        this.index = index;
        this.gridX = gridX;
        this.gridZ = gridZ;
    }

    public int index() {
        return index;
    }

    public int gridX() {
        return gridX;
    }

    public int gridZ() {
        return gridZ;
    }

    public SlotState state() {
        return state;
    }

    public UUID occupant() {
        return occupant;
    }

    void assign(UUID player) {
        this.state = SlotState.RESERVED;
        this.occupant = player;
    }

    public void occupy() {
        this.state = SlotState.OCCUPIED;
    }

    public void markDirty() {
        this.state = SlotState.DIRTY;
    }

    void free() {
        this.state = SlotState.FREE;
        this.occupant = null;
    }
}
