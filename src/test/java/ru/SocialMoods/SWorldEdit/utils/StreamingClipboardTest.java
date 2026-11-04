package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.*;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.clipboard.DiskPageStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingClipboardTest {
    @TempDir Path directory;
    final Level level=mock(Level.class,withSettings().stubOnly());
    final PlayerData owner=new PlayerData(mock(Player.class));
    final Queue<Runnable> ticks=new ArrayDeque<>();
    final Map<String,Block> world=new HashMap<>();
    final Map<String,BlockEntity> tiles=new HashMap<>();
    final Thread server=Thread.currentThread();
    StreamingClipboard engine;
    int writes;
    int failAt=Integer.MAX_VALUE;
    String key(Vector3 point){return point.getFloorX()+","+point.getFloorY()+","+point.getFloorZ();}

    @BeforeEach void setup(){
        engine=new StreamingClipboard(new StreamingClipboard.Settings(directory,2000000,512,128,8192,0,64L*1024*1024,0,
                TimeUnit.MILLISECONDS.toNanos(3),4,10,128),ticks::add,ignored->true);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        doAnswer(call->{
            assertSame(server,Thread.currentThread());Vector3 point=call.getArgument(0);
            Block block=world.getOrDefault(key(point),new BlockAir()).clone();block.level=level;block.setComponents(point.x,point.y,point.z);return block;
        }).when(level).getBlock(any(Vector3.class));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{assertSame(server,Thread.currentThread());return tiles.get(key(call.getArgument(0)));});
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(call->{
            assertSame(server,Thread.currentThread());if(++writes==failAt)throw new IllegalStateException("injected write failure");
            Vector3 point=call.getArgument(0);Block block=((Block)call.getArgument(1)).clone();block.level=level;block.setComponents(point.x,point.y,point.z);world.put(key(point),block);return true;
        });
    }

    @AfterEach void close()throws Exception{engine.close();engine.termination().get(10,TimeUnit.SECONDS);}

    int finish(CompletableFuture<Integer> future)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);int turns=0;
        while(!future.isDone()){
            assertTrue(System.nanoTime()<end,"Operation stalled");
            Runnable tick=ticks.poll();assertNotNull(tick);tick.run();
            if(++turns%20==0)Thread.sleep(1);
        }
        // Remove the completed job before immediately submitting another job for the same owner.
        return future.join();
    }

    BlocksArray copy(int end)throws Exception{
        BlocksArray result=new BlocksArray();
        assertEquals(end+1,finish(engine.copy(owner,new Position(0,70,0,level),new Position(end,70,0,level),new Vector3(0,70,0),result)));
        owner.copiedBlocks=result;return result;
    }

    @Test void diskCopyRepeatsPasteSkipsAirAndRestoresUndo()throws Exception{
        world.put("0,70,0",new BlockStone());world.put("2,70,0",new BlockDirt());
        BlocksArray clipboard=copy(2);assertInstanceOf(DiskPageStore.class,clipboard.paged.store);
        assertEquals(new SelectionBounds(0,0,0,2,0,0),clipboard.pagedBounds());
        world.put("11,70,0",new BlockStone());
        assertEquals(2,finish(engine.paste(owner,new Position(10,70,0,level),clipboard,true)));
        assertEquals(1,world.get("11,70,0").getId());assertEquals(3,world.get("12,70,0").getId());
        assertEquals(3,finish(engine.paste(owner,new Position(20,70,0,level),clipboard,false)));
        assertEquals(0,world.get("21,70,0").getId());assertEquals(2,owner.undoSteps.size());
        assertEquals(3,finish(engine.undo(owner,1)));assertEquals(0,world.get("20,70,0").getId());
        assertEquals(2,finish(engine.undo(owner,0)));assertEquals(0,world.get("10,70,0").getId());
        assertEquals(1,world.get("11,70,0").getId());assertTrue(owner.undoSteps.isEmpty());
    }

    @Test void partialPasteFailureKeepsAttemptedPrefixUndo()throws Exception{
        for(int x=0;x<5;x++)world.put(x+",70,0",new BlockStone());
        BlocksArray clipboard=copy(4);failAt=3;
        var future=engine.paste(owner,new Position(10,70,0,level),clipboard,false);
        assertThrows(CompletionException.class,()->finish(future));
        assertEquals(1,owner.undoSteps.size());assertEquals(3,owner.undoSteps.getFirst().size());
        failAt=Integer.MAX_VALUE;assertEquals(3,finish(engine.undo(owner,0)));
        for(int x=10;x<15;x++)assertEquals(0,world.getOrDefault(x+",70,0",new BlockAir()).getId());
    }

    @Test void detectsCorruptSourceBeforeAnyWorldEdits()throws Exception{
        world.put("0,70,0",new BlockStone());BlocksArray clipboard=copy(0);
        Path ready;
        try(var files=Files.walk(directory)){ready=files.filter(path->path.toString().endsWith(".ready")).findFirst().orElseThrow();}
        byte[] bytes=Files.readAllBytes(ready);bytes[20]^=1;Files.write(ready,bytes);
        var future=engine.paste(owner,new Position(10,70,0,level),clipboard,false);
        assertThrows(CompletionException.class,()->finish(future));assertEquals(0,writes);assertTrue(owner.undoSteps.isEmpty());
    }

    @Test void rejectsOverlappingJobsForSamePlayerAndCancelsWithoutPublishing()throws Exception{
        BlocksArray output=new BlocksArray();
        var future=engine.copy(owner,new Position(0,70,0,level),new Position(100,70,0,level),new Vector3(),output);
        assertThrows(IllegalStateException.class,()->engine.copy(owner,new Position(0,70,0,level),new Position(1,70,0,level),new Vector3(),new BlocksArray()));
        engine.cancel(owner);assertThrows(CancellationException.class,()->finish(future));assertNull(output.paged);assertEquals(0,writes);
    }
    @Test void shutdownDuringCopyClosesSpoolAndNeverWritesWorld()throws Exception{
        var future=engine.copy(owner,new Position(0,70,0,level),new Position(99999,70,0,level),new Vector3(),new BlocksArray());
        for(int i=0;i<20;i++){ticks.remove().run();Thread.sleep(1);}
        assertFalse(future.isDone());
        engine.close();assertTrue(future.isCompletedExceptionally());engine.termination().get(10,TimeUnit.SECONDS);
        assertEquals(0,writes);
        try(var paths=Files.walk(directory)){assertFalse(paths.anyMatch(path->path.toString().endsWith(".partial")||path.toString().endsWith(".ready")));}
    }

    @Test void diskQuotaFailureDoesNotPublishOrReplaceOldClipboard()throws Exception{
        engine.close();engine.termination().get(10,TimeUnit.SECONDS);ticks.clear();
        engine=new StreamingClipboard(new StreamingClipboard.Settings(directory,2000000,512,128,8192,0,40,0,
                TimeUnit.MILLISECONDS.toNanos(3),4,10,128),ticks::add,ignored->true);
        BlocksArray old=new BlocksArray();old.blocks.add(new BlockStone());owner.copiedBlocks=old;
        BlocksArray output=new BlocksArray();
        var future=engine.copy(owner,new Position(0,70,0,level),new Position(10,70,0,level),new Vector3(),output);
        assertThrows(CompletionException.class,()->finish(future));assertNull(output.paged);assertSame(old,owner.copiedBlocks);assertEquals(0,writes);
    }

    @Test void streamsChestPairsHalfChestsAndSignTextWithoutLosingNbt()throws Exception{
        Map<BlockEntity,BlockEntity> links=new HashMap<>();
        java.util.function.Function<CompoundTag,BlockEntity> tile=tag->{
            var entity=mock(cn.nukkit.blockentity.BlockEntitySpawnable.class,withSettings().extraInterfaces(ClipboardOperationTest.PairedChest.class));
            entity.namedTag=tag.clone();when(entity.isBlockEntityValid()).thenReturn(true);
            var api=(ClipboardOperationTest.PairedChest)entity;
            when(api.getPair()).thenAnswer(call->links.get(entity));
            when(api.pairWith(any(BlockEntity.class))).thenAnswer(call->{
                BlockEntity other=call.getArgument(0);if(links.containsKey(entity)||links.containsKey(other))return false;
                links.put(entity,other);links.put(other,entity);
                entity.namedTag.putInt("pairx",other.namedTag.getInt("x")).putInt("pairz",other.namedTag.getInt("z"));
                other.namedTag.putInt("pairx",entity.namedTag.getInt("x")).putInt("pairz",entity.namedTag.getInt("z"));return true;
            });
            when(api.unpair()).thenAnswer(call->{
                var other=links.remove(entity);entity.namedTag.remove("pairx").remove("pairz");
                if(other!=null){links.remove(other);other.namedTag.remove("pairx").remove("pairz");}return other!=null;
            });
            doAnswer(call->{tiles.remove(entity.namedTag.getInt("x")+",70,0");return null;}).when(entity).close();return entity;
        };
        for(int x:new int[]{0,1,10,11}){
            var tag=new CompoundTag().putString("id","Chest").putInt("x",x).putInt("y",70).putInt("z",0)
                    .putInt("pairx",x%2==0?x+1:x-1).putInt("pairz",0)
                    .putList(new ListTag<CompoundTag>("Items").add(new CompoundTag().putByte("Count",x<2?12+x:x-7)));
            tiles.put(x+",70,0",tile.apply(tag));world.put(x+",70,0",new BlockChest());
        }
        for(int x:new int[]{0,10}){links.put(tiles.get(x+",70,0"),tiles.get((x+1)+",70,0"));links.put(tiles.get((x+1)+",70,0"),tiles.get(x+",70,0"));}
        var sign=new CompoundTag().putString("id","Sign").putInt("x",2).putInt("y",70).putInt("z",0).putString("Text","告示牌\n第二行");
        world.put("2,70,0",new BlockSignPost());tiles.put("2,70,0",tile.apply(sign));
        try(var factory=mockStatic(BlockEntity.class)){
            factory.when(()->BlockEntity.createBlockEntity(anyString(),nullable(cn.nukkit.level.format.FullChunk.class),any(CompoundTag.class))).thenAnswer(call->{
                CompoundTag tag=call.getArgument(2);BlockEntity created=tile.apply(tag);tiles.put(tag.getInt("x")+",70,0",created);return created;
            });
            BlocksArray full=copy(2);
            assertEquals(3,finish(engine.paste(owner,new Position(10,70,0,level),full,false)));
            assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            assertEquals(12,tiles.get("10,70,0").namedTag.getList("Items",CompoundTag.class).get(0).getByte("Count"));
            assertEquals("告示牌\n第二行",tiles.get("12,70,0").namedTag.getString("Text"));
            assertEquals(3,finish(engine.undo(owner,0)));
            assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            assertEquals(3,tiles.get("10,70,0").namedTag.getList("Items",CompoundTag.class).get(0).getByte("Count"));
            BlocksArray half=copy(0);
            assertEquals(1,finish(engine.paste(owner,new Position(10,70,0,level),half,false)));
            assertNull(links.get(tiles.get("10,70,0")));
            tiles.get("11,70,0").namedTag.getList("Items",CompoundTag.class).get(0).putByte("Count",30);
            assertEquals(1,finish(engine.undo(owner,0)));
            assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            assertEquals(30,tiles.get("11,70,0").namedTag.getList("Items",CompoundTag.class).get(0).getByte("Count"));
        }
    }

    @Test void capturesOverAMillionBlocksWithoutPerBlockRetainedObjects()throws Exception{
        engine.close();engine.termination().get(10,TimeUnit.SECONDS);ticks.clear();
        engine=new StreamingClipboard(new StreamingClipboard.Settings(directory,2000000,512,2048,8192,0,64L*1024*1024,0,
                TimeUnit.MILLISECONDS.toNanos(3),4,10,128),ticks::add,ignored->true);
        doAnswer(call->{
            assertSame(server,Thread.currentThread());Vector3 point=call.getArgument(0);
            Block block=new BlockStone();block.setComponents(point.x,point.y,point.z);block.level=level;return block;
        }).when(level).getBlock(any(Vector3.class));
        long started=System.nanoTime();System.gc();long initial=used();
        BlocksArray output=new BlocksArray();
        assertEquals(1048576,finish(engine.copy(owner,new Position(0,70,0,level),new Position(1023,70,1023,level),new Vector3(0,70,0),output)));
        System.gc();long retained=used()-initial;
        assertEquals(1048576,output.size());assertTrue(output.blocks.isEmpty());assertTrue(output.blockEntities.isEmpty());
        assertInstanceOf(DiskPageStore.class,output.paged.store);
        assertTrue(retained<64L*1024*1024,"A uniform million-block clipboard should not retain per-block objects");
        System.out.println("MILLION_COPY blocks=1048576 retainedHeapDelta="+retained+" diskPayload="+output.paged.store.byteCount()+" elapsedMs="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
    }

    private long used(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
}
