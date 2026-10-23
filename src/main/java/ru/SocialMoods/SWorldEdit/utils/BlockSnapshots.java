package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;

/** Detached blocks prevent core placement methods from mutating clipboard coordinates. */
final class BlockSnapshots {
    private BlockSnapshots() { }

    static Block relative(Level level, int x, int y, int z, Vector3 origin) {
        Block block = level.getBlock(new Vector3(x, y, z)).clone();
        block.setComponents(x - origin.getFloorX(), y - origin.getFloorY(), z - origin.getFloorZ());
        return block;
    }

    static void place(Level level, Vector3 target, Block snapshot, boolean update) {
        level.setBlock(target, snapshot.clone(), true, update);
    }
}
