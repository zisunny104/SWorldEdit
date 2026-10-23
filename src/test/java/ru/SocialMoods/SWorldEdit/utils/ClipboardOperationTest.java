package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClipboardOperationTest {
    private final Level level = mock(Level.class);
    private final Map<String, Block> world = new HashMap<>();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final PlayerData player = new PlayerData(mock(Player.class));
    private String key(Vector3 v) { return v.getFloorX()+","+v.getFloorY()+","+v.getFloorZ(); }
    private Block block(int id, double x, double y, double z) {
        Block b = mock(Block.class);
        when(b.getId()).thenReturn(id);
        doCallRealMethod().when(b).setComponents(anyDouble(), anyDouble(), anyDouble());
        doCallRealMethod().when(b).getFloorX();
        doCallRealMethod().when(b).getFloorY();
        doCallRealMethod().when(b).getFloorZ();
        b.setComponents(x,y,z); b.level = level;
        when(b.clone()).thenAnswer(call -> block(id,b.x,b.y,b.z));
        return b;
    }
    @BeforeEach void prepare() {
        when(level.getMinBlockY()).thenReturn(-64); when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
            Vector3 v = call.getArgument(0);
            return world.getOrDefault(key(v), block(0,v.x,v.y,v.z));
        });
        when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(false))).thenAnswer(call -> {
            Vector3 v = call.getArgument(0); Block b = call.getArgument(1);
            b.setComponents(v.x,v.y,v.z); b.level=level;
            world.put(key(v),b); return true;
        });
    }
    private void finish() { int ticks=0; while (!queue.isEmpty()) { assertTrue(ticks++<1000); queue.remove().run(); } }
    @Test void copiesAirAndCanPasteTwiceWithoutChangingClipboardThenUndo() {
        world.put("0,70,0",block(792,0,70,0));
        BlocksArray copy = new BlocksArray();
        var copied = ClipboardOperation.copy(new Position(0,70,0,level),new Position(1,70,0,level),
                new Vector3(0,70,0),copy,100,1,queue::add);
        assertTrue(copy.blocks.isEmpty()); finish(); assertEquals(2,copied.join());
        assertEquals(0,copy.blocks.get(1).getId());
        for (int x : new int[]{10,20}) {
            world.put((x+1)+",70,0",block(1,x+1,70,0));
            var pasted=ClipboardOperation.paste(player,new Position(x,70,0,level),copy,false,100,1,queue::add);
            finish(); assertEquals(2,pasted.join());
            assertEquals(792,world.get(x+",70,0").getId());
            assertEquals(0,world.get((x+1)+",70,0").getId());
        }
        assertEquals(0,copy.blocks.get(0).x); assertEquals(1,copy.blocks.get(1).x);
        assertEquals(2,player.undoSteps.size());
        var undo=ClipboardOperation.undo(player,1,1,queue::add); finish();
        assertEquals(2,undo.join()); assertEquals(1,world.get("21,70,0").getId());
        assertEquals(792,world.get("10,70,0").getId()); assertEquals(1,player.undoSteps.size());
    }
    @Test void airCanBeSkippedAndFailedCopyDoesNotPublishPartialClipboard() {
        BlocksArray copy=new BlocksArray(); copy.blocks.add(block(0,0,0,0));
        world.put("10,70,0",block(1,10,70,0));
        var paste=ClipboardOperation.paste(player,new Position(10,70,0,level),copy,true,100,2,queue::add);
        finish(); assertEquals(0,paste.join()); assertEquals(1,world.get("10,70,0").getId());
        when(level.getBlock(any(Vector3.class))).thenThrow(new IllegalStateException("read failed"));
        var failed=ClipboardOperation.copy(new Position(0,70,0,level),new Position(0,70,0,level),
                new Vector3(),copy,100,2,queue::add);
        finish(); assertTrue(failed.isCompletedExceptionally()); assertEquals(1,copy.blocks.size());
    }
    @Test void refreshesOutsideFenceOnlyAfterAllBlocksWerePlaced() {
        BlockFence boundary=mock(BlockFence.class);
        world.put("9,70,0",boundary);
        doAnswer(call -> { assertTrue(world.containsKey("11,70,0")); return 0; })
                .when(boundary).onUpdate(Level.BLOCK_UPDATE_NORMAL);
        BlocksArray copy=new BlocksArray();copy.blocks.add(block(1,0,0,0));copy.blocks.add(block(1,1,0,0));
        var result=ClipboardOperation.paste(player,new Position(10,70,0,level),copy,false,100,1,queue::add);
        finish();assertEquals(2,result.join());verify(boundary).onUpdate(Level.BLOCK_UPDATE_NORMAL);
    }
}

