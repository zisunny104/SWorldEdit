package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import java.util.ArrayList;

public class BlocksArray {

    public final ArrayList<Block> blocks = new ArrayList<>();
    public final java.util.Map<Integer, cn.nukkit.nbt.tag.CompoundTag> blockEntities = new java.util.HashMap<>();
    final java.util.Map<ChestPairs.Point, cn.nukkit.nbt.tag.CompoundTag> externalChestPairs = new java.util.HashMap<>();
}

