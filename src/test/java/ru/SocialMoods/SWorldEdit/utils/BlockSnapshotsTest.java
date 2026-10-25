package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockSnapshotsTest {
    private Block block() {
        Block block = mock(Block.class);
        doCallRealMethod().when(block).setComponents(anyDouble(), anyDouble(), anyDouble());
        return block;
    }
    @Test void placementCannotChangeClipboardEvenWhenCoreMutatesItsArgument() {
        Level level = mock(Level.class);
        Block clipboard = block();
        Block detached = block();
        clipboard.setComponents(1, 2, 3);
        when(clipboard.clone()).thenReturn(detached);
        doAnswer(call -> {
            Block placed = call.getArgument(1);
            placed.setComponents(100, 70, 200);
            return true;
        }).when(level).setBlock(any(Vector3.class), any(Block.class), eq(true), eq(false));
        BlockSnapshots.place(level, new Vector3(100, 70, 200), clipboard, false);
        BlockSnapshots.place(level, new Vector3(200, 70, 300), clipboard, false);
        assertEquals(1, clipboard.x);
        assertEquals(2, clipboard.y);
        assertEquals(3, clipboard.z);
        verify(level, times(2)).setBlock(any(Vector3.class), same(detached), eq(true), eq(false));
        verify(clipboard, times(2)).clone();
    }

    @Test void copyingUsesDetachedCoreBlockInsteadOfReconstructingIdAndData() {
        Level level = mock(Level.class);
        Block source = block();
        Block detached = block();
        source.setComponents(-17, 70, 40);
        when(level.getBlock(any(Vector3.class))).thenReturn(source);
        when(source.clone()).thenReturn(detached);
        Block result = BlockSnapshots.relative(level, -17, 70, 40, new Vector3(-20, 65, 30));
        assertSame(detached, result);
        assertEquals(3, result.x);
        assertEquals(5, result.y);
        assertEquals(10, result.z);
        assertEquals(-17, source.x);
        verify(source, never()).getDamage();
    }
}

