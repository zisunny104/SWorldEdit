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
    public final java.util.Map<Integer, cn.nukkit.nbt.tag.CompoundTag> blockEntities = new java.util.HashMap<>();
    final java.util.Map<ChestPairs.Point, cn.nukkit.nbt.tag.CompoundTag> externalChestPairs = new java.util.HashMap<>();
}

