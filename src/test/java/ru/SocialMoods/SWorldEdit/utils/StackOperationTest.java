package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.commands.StackCommand;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StackOperationTest {
    private final Level level = mock(Level.class);
    private final PlayerData player = new PlayerData(mock(Player.class));
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<String> writes = new ArrayList<>();
    private Block block(int id) {
        Block b = mock(Block.class);
        when(b.getId()).thenReturn(id);
        when(b.clone()).thenAnswer(call -> block(id));
        return b;
    }
    private void prepare() {
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
            Vector3 v = call.getArgument(0);
            return block(v.getFloorX() == 0 ? 792 : 0);
        });
        when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(false)))
            .thenAnswer(call -> {
                Vector3 v = call.getArgument(0);
                Block b = call.getArgument(1);
                writes.add(v.getFloorX() + ":" + v.getFloorY() + ":" + v.getFloorZ() + "=" + b.getId());
                return true;
            });
    }
    @Test void repeatsSnapshotIncludingAirWithoutTouchingSourceAndRecordsOneUndo() {
        prepare();
        StackOperation op = new StackOperation(player, new Position(1,70,0,level),
            new Position(0,70,0,level), 2, 1,0,0, 100, 1, queue::add);
        var result = op.start();
        assertTrue(writes.isEmpty());
        queue.remove().run();
        assertTrue(writes.isEmpty()); // Source snapshot is finished before any writes.
        while (!queue.isEmpty()) queue.remove().run();
        assertEquals(4, result.join());
        assertEquals(List.of("2:70:0=792", "3:70:0=0", "4:70:0=792", "5:70:0=0"), writes);
        assertEquals(1, player.undoSteps.size());
        assertEquals(4, player.undoSteps.getFirst().blocks.size());
        assertNull(player.copiedBlocks);
    }
    @Test void rejectsBoundsAndExcessBeforeReadingWorld() {
        prepare();
        assertThrows(IllegalArgumentException.class, () -> new StackOperation(player,
            new Position(0,319,0,level), new Position(0,319,0,level), 1,0,1,0,100,1,queue::add));
        assertThrows(IllegalArgumentException.class, () -> new StackOperation(player,
            new Position(0,70,0,level), new Position(1,70,0,level), 100,1,0,0,100,1,queue::add));
        assertTrue(writes.isEmpty());
        verify(level, never()).getBlock(any(Vector3.class));
    }
    @Test void partialFailureStillHasUndoHistory() {
        prepare();
        when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(false)))
            .thenReturn(true).thenThrow(new IllegalStateException("test failure"));
        var result = new StackOperation(player, new Position(0,70,0,level),
            new Position(0,70,0,level), 2,0,0,-1,100,10,queue::add).start();
        while (!queue.isEmpty()) queue.remove().run();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, player.undoSteps.size());
        assertEquals(2, player.undoSteps.getFirst().blocks.size());
    }
    @Test void parsesDirectionsAndUsesDominantLookAxis() {
        assertArrayEquals(new int[]{0,0,-1}, StackCommand.direction("north", new Vector3()));
        assertArrayEquals(new int[]{0,-1,0}, StackCommand.direction(null, new Vector3(.1,-.9,.2)));
        assertArrayEquals(new int[]{1,0,0}, StackCommand.direction(null, new Vector3(.9,0,.2)));
        assertThrows(IllegalArgumentException.class, () -> StackCommand.direction("typo", new Vector3()));
    }
}
