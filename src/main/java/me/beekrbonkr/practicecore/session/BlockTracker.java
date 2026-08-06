package me.beekrbonkr.practicecore.session;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records every block the player places so a reset can revert exactly those
 * positions to the schematic's original data — roughly a thousand times
 * cheaper than re-pasting the schematic each run.
 */
public final class BlockTracker {

    private final Map<Location, BlockData> placed = new LinkedHashMap<>();

    public void recordPlace(Block block, BlockData replaced) {
        placed.putIfAbsent(block.getLocation(), replaced);
    }

    public boolean isTracked(Location location) {
        return placed.containsKey(location);
    }

    public int count() {
        return placed.size();
    }

    public void revertAll() {
        for (Map.Entry<Location, BlockData> entry : placed.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue(), false);
        }
        placed.clear();
    }
}
