package me.beekrbonkr.practicecore.beddefense;

import org.bukkit.Material;

/**
 * One block of a saved bed defense, in the defense's own frame: offsets from
 * the bed's head block with the bed facing north (see {@link DefenseFrame}).
 * {@code kind} is the normalized material the block is judged by;
 * {@code data} is the exact block state the author placed, kept so previews
 * and guides can show a ladder the right way round.
 */
public record DefenseBlock(int x, int y, int z, Material kind, String data) {

    /** "x,y,z,KIND" — the line this block contributes to a fingerprint. */
    public String fingerprintLine() {
        return x + "," + y + "," + z + "," + kind.name();
    }
}
