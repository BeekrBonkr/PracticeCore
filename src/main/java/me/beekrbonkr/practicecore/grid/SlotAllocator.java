package me.beekrbonkr.practicecore.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hands out grid slots. Main-thread only: Bukkit join/command handling is
 * single-threaded, so a plain free-list is race-free. Lowest free index is
 * reused first. DIRTY slots (cleanup in flight) are never assigned; the
 * grid simply grows instead.
 */
public final class SlotAllocator {

    private final List<Slot> slots = new ArrayList<>();

    public Slot acquire(UUID player) {
        for (Slot slot : slots) {
            if (slot.state() == SlotState.FREE) {
                slot.assign(player);
                return slot;
            }
        }
        int[] pos = SpiralGrid.at(slots.size());
        Slot slot = new Slot(slots.size(), pos[0], pos[1]);
        slots.add(slot);
        slot.assign(player);
        return slot;
    }

    public void release(Slot slot) {
        slot.free();
    }

    /**
     * Discards the whole grid. Only valid once every session and the setup
     * wizard have ended — the practice world is being rebuilt, so no pasted
     * arena that a slot could describe still exists. Slots handed out before
     * the reset simply become garbage; freeing one later is a no-op on an
     * object nothing reads.
     */
    public void reset() {
        slots.clear();
    }

    public int size() {
        return slots.size();
    }
}
