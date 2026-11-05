package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.level.*;
import cn.nukkit.math.Vector3;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEditsTest {
    @TempDir Path directory;
    final Level level=mock(Level.class,withSettings().stubOnly());
    final Map<String,Block> world=new HashMap<>();
    final Queue<Runnable> ticks=new ArrayDeque<>();
    final PlayerData owner=new PlayerData(mock(Player.class));
    final Thread server=Thread.currentThread();
    StreamingClipboard engine;WorldEdits edits;int writes,failAt=Integer.MAX_VALUE;
    String key(Vector3 v){return v.getFloorX()+","+v.getFloorY()+","+v.getFloorZ();}
    @BeforeEach void setup(){
        engine=new StreamingClipboard(new StreamingClipboard.Settings(directory,10000,2,2,8192,0,10000000,0,TimeUnit.MILLISECONDS.toNanos(3),4,10,128),ticks::add,ignored->true);
        edits=new WorldEdits(engine,10000);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        when(level.getBlock(any(Vector3.class))).thenAnswer(call->{
            assertSame(server,Thread.currentThread());Vector3 point=call.getArgument(0);
            Block block=world.getOrDefault(key(point),new BlockAir()).clone();block.setComponents(point.x,point.y,point.z);block.level=level;return block;
        });
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{assertSame(server,Thread.currentThread());return null;});
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(call->{
            assertSame(server,Thread.currentThread());if(++writes==failAt)throw new IllegalStateException("injected failure");
            Vector3 point=call.getArgument(0);Block block=((Block)call.getArgument(1)).clone();block.setComponents(point.x,point.y,point.z);block.level=level;world.put(key(point),block);return true;
        });
    }
    @AfterEach void close()throws Exception{engine.close();engine.termination().get(10,TimeUnit.SECONDS);}
    int finish(CompletableFuture<Integer> future)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);int turns=0;
        while(!future.isDone()){assertTrue(System.nanoTime()<deadline);Runnable task=ticks.poll();assertNotNull(task);task.run();if(++turns%20==0)Thread.sleep(1);}
        return future.join();
    }
    Position at(int x,int y,int z){return new Position(x,y,z,level);}
    int id(int x,int z){return world.getOrDefault(x+",70,"+z,new BlockAir()).getId();}

    @Test void setSnapshotsArgumentsAndUndoRestoresEveryDestination()throws Exception{
        Position first=at(0,70,0),second=at(2,70,0);world.put("1,70,0",new BlockDirt());
        var work=edits.set(owner,first,second,new BlockStone());assertEquals(0,writes);first.setComponents(100,100,100);
        assertEquals(3,finish(work));for(int x=0;x<=2;x++)assertEquals(1,id(x,0));
        assertEquals(3,finish(engine.undo(owner,0)));assertEquals(0,id(0,0));assertEquals(3,id(1,0));assertEquals(0,id(2,0));
    }
    @Test void replaceHonorsMetadataAndLightBlockException()throws Exception{
        world.put("0,70,0",new BlockDirt(1));world.put("1,70,0",new BlockDirt());world.put("2,70,0",new BlockLightBlock());
        assertEquals(2,finish(edits.replace(owner,at(0,70,0),at(2,70,0),new BlockDirt(1),new BlockStone())));
        assertEquals(1,id(0,0));assertEquals(3,id(1,0));assertEquals(1,id(2,0));
        assertEquals(2,finish(engine.undo(owner,0)));assertEquals(1,world.get("0,70,0").getDamage());assertInstanceOf(BlockLightBlock.class,world.get("2,70,0"));
    }
    @Test void wallsAvoidDuplicateCornersAndHandleThinSelections()throws Exception{
        assertEquals(16,finish(edits.walls(owner,at(0,70,0),at(2,71,2),new BlockStone())));assertEquals(0,id(1,1));
        assertEquals(16,finish(engine.undo(owner,0)));
        assertEquals(6,finish(edits.walls(owner,at(0,70,0),at(0,71,2),new BlockStone())));assertEquals(6,finish(engine.undo(owner,0)));
        assertEquals(2,finish(edits.walls(owner,at(0,70,0),at(0,71,0),new BlockStone())));
    }
    @Test void geometryKeepsLegacyDiskAndSphereShapes()throws Exception{
        assertEquals(5,finish(edits.geometry(owner,at(0,70,0),1,new BlockStone(),false,false)));assertEquals(5,finish(engine.undo(owner,0)));
        assertEquals(4,finish(edits.geometry(owner,at(0,70,0),1,new BlockStone(),false,true)));assertEquals(0,id(0,0));assertEquals(4,finish(engine.undo(owner,0)));
        assertEquals(7,finish(edits.geometry(owner,at(0,70,0),1,new BlockStone(),true,false)));assertEquals(7,finish(engine.undo(owner,0)));
        assertEquals(6,finish(edits.geometry(owner,at(0,70,0),1,new BlockStone(),true,true)));assertEquals(0,id(0,0));
    }
    @Test void rotationCapturesAllSourcesBeforeClearingOverlapsAndRestoresOutsideDestination()throws Exception{
        world.put("1,70,0",new BlockStone());world.put("0,70,1",new BlockDirt());world.put("-1,70,0",new BlockGlass());
        Selection selected=new Selection();selected.setPosOne(at(0,70,0));selected.setPosTwo(at(1,70,1));
        var work=edits.rotate(owner,at(0,70,0),selected,90);selected.setPosOne(at(500,70,500));
        assertEquals(2,finish(work));assertEquals(1,id(0,1));assertEquals(3,id(-1,0));assertEquals(0,id(1,0));
        assertEquals(4,finish(engine.undo(owner,0)));assertEquals(1,id(1,0));assertEquals(3,id(0,1));assertEquals(20,id(-1,0));
    }
    @Test void partialRotationKeepsRecoveryForClearedAndWrittenCells()throws Exception{
        world.put("1,70,0",new BlockStone());world.put("0,70,1",new BlockDirt());world.put("-1,70,0",new BlockGlass());
        Selection selected=new Selection();selected.setPosOne(at(0,70,0));selected.setPosTwo(at(1,70,1));failAt=3;
        var work=edits.rotate(owner,at(0,70,0),selected,90);assertThrows(CompletionException.class,()->finish(work));
        assertEquals(1,owner.undoSteps.size());failAt=Integer.MAX_VALUE;finish(engine.undo(owner,0));
        assertEquals(1,id(1,0));assertEquals(3,id(0,1));assertEquals(20,id(-1,0));
    }
    @Test void boundsLimitsAndShutdownDoNotLeaveBackgroundWorldWrites()throws Exception{
        assertThrows(IllegalArgumentException.class,()->edits.set(owner,at(0,70,0),at(100,100,100),new BlockStone()));
        assertThrows(IllegalArgumentException.class,()->edits.geometry(owner,at(0,319,0),2,new BlockStone(),true,false));
        var work=edits.set(owner,at(0,70,0),at(99,70,0),new BlockStone());engine.close();
        var failure=assertThrows(CompletionException.class,work::join);assertInstanceOf(CancellationException.class,failure.getCause());
        while(!ticks.isEmpty())ticks.remove().run();assertEquals(0,writes);
    }

    @Test void shutdownAfterPartialEditStopsAllRemainingWrites()throws Exception{
        var work=edits.set(owner,at(0,70,0),at(19,70,0),new BlockStone());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(writes==0){assertTrue(System.nanoTime()<deadline);ticks.remove().run();Thread.sleep(1);}
        int before=writes;assertTrue(before<20);assertEquals(1,owner.undoSteps.size());
        engine.close();assertTrue(work.isCompletedExceptionally());
        while(!ticks.isEmpty())ticks.remove().run();assertEquals(before,writes);
    }

    @Test void ownerlessCompatibilityWallsCanBeRepeatedWithoutRetainedHistory()throws Exception{
        assertEquals(2,finish(edits.walls(null,at(0,70,0),at(0,71,0),new BlockStone())));
        assertEquals(2,finish(edits.walls(null,at(0,70,0),at(0,71,0),new BlockDirt())));
        assertTrue(owner.undoSteps.isEmpty());assertEquals(3,id(0,0));
    }

    @Test void rotationMovesSignNbtAndUndoRestoresOriginalText()throws Exception{
        Map<String,BlockEntity> tiles=new HashMap<>();
        java.util.function.Function<CompoundTag,BlockEntity> tile=tag->{
            var entity=mock(cn.nukkit.blockentity.BlockEntitySpawnable.class);entity.namedTag=tag.clone();
            when(entity.isBlockEntityValid()).thenReturn(true);
            doAnswer(call->{tiles.remove(key(new Vector3(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"))));return null;}).when(entity).close();
            return entity;
        };
        var tag=new CompoundTag().putString("id","Sign").putInt("x",1).putInt("y",70).putInt("z",0).putString("Text","告示牌\n第二行");
        world.put("1,70,0",new BlockSignPost());tiles.put("1,70,0",tile.apply(tag));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{assertSame(server,Thread.currentThread());return tiles.get(key(call.getArgument(0)));});
        try(var factory=mockStatic(BlockEntity.class)){
            factory.when(()->BlockEntity.createBlockEntity(anyString(),nullable(cn.nukkit.level.format.FullChunk.class),any(CompoundTag.class))).thenAnswer(call->{
                assertSame(server,Thread.currentThread());CompoundTag data=call.getArgument(2);var created=tile.apply(data);
                tiles.put(key(new Vector3(data.getInt("x"),data.getInt("y"),data.getInt("z"))),created);return created;
            });
            Selection selected=new Selection();selected.setPosOne(at(1,70,0));selected.setPosTwo(at(1,70,0));
            assertEquals(1,finish(edits.rotate(owner,at(0,70,0),selected,90)));
            assertFalse(tiles.containsKey("1,70,0"));assertEquals("告示牌\n第二行",tiles.get("0,70,1").namedTag.getString("Text"));
            assertEquals(1,tiles.get("0,70,1").namedTag.getInt("z"));
            assertEquals(2,finish(engine.undo(owner,0)));assertFalse(tiles.containsKey("0,70,1"));
            assertEquals("告示牌\n第二行",tiles.get("1,70,0").namedTag.getString("Text"));
        }
    }
}
