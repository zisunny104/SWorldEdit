package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.Mockito.*;

class ConnectionsTest {
    @Test void patchedNukkitRecomputesFenceAndPaneConnectionsAtDestination() throws Exception {
        boolean available;
        try { Class.forName("cn.nukkit.block.ConnectionBlock"); available=true; }
        catch (ClassNotFoundException e) { available=false; }
        assumeTrue(available, "Stored connection-state assertion applies to the patched Nukkit core");
        Block.class.getMethod("init").invoke(null);
        for (int id : new int[]{85,102}) {
            Level level=mock(Level.class);
            when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
            when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(java.util.Collections.emptyMap());
            Block connected=spy(Block.get(id,0));connected.level=level;connected.setComponents(0,70,0);
            doAnswer(call -> call.getArgument(0)==BlockFace.EAST?Block.get(1):Block.get(0))
                    .when(connected).getSide(any(BlockFace.class));
            when(level.getBlock(any(Vector3.class))).thenReturn(connected);
            Connections.refresh(level,new Vector3(1,70,0),2);
            int shift=(Integer)Class.forName("cn.nukkit.block.ConnectionBlock")
                    .getMethod("getConnectionShift").invoke(connected);
            assertEquals(2<<shift,connected.getDamage(),"East connection for block "+id);
            verify(level).setBlock(connected,connected,true,false);
        }
    }
}
