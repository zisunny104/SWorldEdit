package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import java.util.ArrayList;

public class BlocksArray implements AutoCloseable {

    public PagedClipboard paged;
    public cn.nukkit.level.Level undoLevel;
    public int applied = Integer.MAX_VALUE;
    public boolean isEmpty() { return size() == 0; }
    public int size() { return paged == null ? blocks.size() : Math.min(paged.count, applied); }
    public SelectionBounds pagedBounds() { return paged == null ? null : paged.bounds; }
    @Override public void close() {
        if (paged != null) try { paged.close(); }
        catch (java.io.IOException e) { BlockEntities.report("Cannot release clipboard store", e); }
    }

    public final ArrayList<Block> blocks = new ArrayList<>();
    private final java.util.BitSet knownLayers = new java.util.BitSet();
    private final java.util.Map<Integer, Block> secondaryLayers = new java.util.HashMap<>();

    /** Null means the layer was not recorded. Air explicitly clears the layer. */
    public Block secondary(int index) {
        if (!knownLayers.get(index)) return null;
        Block block = secondaryLayers.get(index);
        return block == null ? new cn.nukkit.block.BlockAir() : block.clone();
    }

    public void setSecondary(int index, Block block) {
        knownLayers.set(index, block != null);
        if (block == null || block.getId() == 0) secondaryLayers.remove(index);
        else secondaryLayers.put(index, BlockSnapshots.relative(block, 0, 0, 0, new cn.nukkit.math.Vector3()));
    }

    void copySecondaryFrom(BlocksArray source) {
        knownLayers.clear();
        knownLayers.or(source.knownLayers);
        secondaryLayers.clear();
        secondaryLayers.putAll(source.secondaryLayers);
    }

    boolean hasSecondary(int index) { return secondaryLayers.containsKey(index); }

    void applySecondary(int index, cn.nukkit.level.Level level, cn.nukkit.math.Vector3 point) {
        if (knownLayers.get(index)) BlockLayers.write(level, point, secondary(index));
    }
    public final java.util.Map<Integer, cn.nukkit.nbt.tag.CompoundTag> blockEntities = new java.util.HashMap<>();
    final java.util.Map<ChestPairs.Point, cn.nukkit.nbt.tag.CompoundTag> externalChestPairs = new java.util.HashMap<>();
}

