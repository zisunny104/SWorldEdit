package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockThin;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.UpdateBlockPacket;

/** Refresh only connection-dependent blocks, without triggering unrelated growth/physics. */
final class Connections {
    private static final int[][] OFFSETS = {{0,0,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
    private Connections() { }

    static void refresh(Level level, Vector3 changed, int side) {
        int[] offset = OFFSETS[side];
        Vector3 position = new Vector3(changed.getFloorX() + offset[0], changed.getFloorY() + offset[1], changed.getFloorZ() + offset[2]);
        if (position.y < level.getMinBlockY() || position.y > level.getMaxBlockY()) return;
        Block block = level.getBlock(position);
        if (!isConnectedBlock(block)) return;
        block.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        // A core may recalculate the state without sending it when the stored state is unchanged.
        Block actual = level.getBlock(position);
        Player[] viewers = level.getChunkPlayers(position.getFloorX() >> 4, position.getFloorZ() >> 4)
                .values().toArray(new Player[0]);
        if (viewers.length != 0) level.sendBlocks(viewers, new Block[]{actual}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
    }

    private static boolean isConnectedBlock(Block block) {
        if (block instanceof BlockFence || block instanceof BlockThin) return true;
        // Wall base classes differ between Nukkit forks; no fork-specific linkage is required.
        for (Class<?> type = block.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("cn.nukkit.block.BlockWall") ||
                    type.getName().equals("cn.nukkit.block.BlockCobbleWall")) return true;
        }
        return false;
    }
}
