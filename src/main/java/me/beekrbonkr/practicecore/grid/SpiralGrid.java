package me.beekrbonkr.practicecore.grid;

/**
 * Maps slot indices onto a square spiral around the origin:
 * 0 → (0,0), 1 → (1,0), 2 → (1,1), 3 → (0,1), 4 → (-1,1), …
 * Low indices stay near the origin, so reclaimed slots keep the active
 * chunk footprint compact.
 */
public final class SpiralGrid {

    private SpiralGrid() {
    }

    public static int[] at(int index) {
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int segmentLength = 1;
        int segmentPassed = 0;
        int segmentsDone = 0;
        for (int i = 0; i < index; i++) {
            x += dx;
            z += dz;
            if (++segmentPassed == segmentLength) {
                segmentPassed = 0;
                int t = dx;
                dx = -dz;
                dz = t;
                if (++segmentsDone % 2 == 0) {
                    segmentLength++;
                }
            }
        }
        return new int[] {x, z};
    }
}
