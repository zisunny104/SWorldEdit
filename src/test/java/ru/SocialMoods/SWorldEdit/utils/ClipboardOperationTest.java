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
    private final java.util.concurrent.LinkedBlockingQueue<Runnable> queue = new java.util.concurrent.LinkedBlockingQueue<>();
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
    private void finishUntil(java.util.concurrent.CompletableFuture<?> future) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(!future.isDone()) {
            assertTrue(System.nanoTime()<deadline,"Asynchronous operation did not complete");
            Runnable task=queue.poll(20,java.util.concurrent.TimeUnit.MILLISECONDS);
            if(task!=null) task.run();
        }
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

    @Test void copyPasteStackAndUndoCarryDetachedInventoryNbt() {
        var tiles = new HashMap<String,cn.nukkit.blockentity.BlockEntity>();
        java.util.function.Function<cn.nukkit.nbt.tag.CompoundTag,cn.nukkit.blockentity.BlockEntitySpawnable> tile = tag -> {
            var entity=mock(cn.nukkit.blockentity.BlockEntitySpawnable.class);
            entity.namedTag=tag.clone();when(entity.isBlockEntityValid()).thenReturn(true);
            String position=tag.getInt("x")+","+tag.getInt("y")+","+tag.getInt("z");
            doAnswer(call -> { tiles.remove(position);return null; }).when(entity).close();
            return entity;
        };
        var source=new cn.nukkit.nbt.tag.CompoundTag().putString("id","Chest").putInt("x",0).putInt("y",70).putInt("z",0)
                .putList(new cn.nukkit.nbt.tag.ListTag<cn.nukkit.nbt.tag.CompoundTag>("Items")
                    .add(new cn.nukkit.nbt.tag.CompoundTag().putByte("Count",12)));
        var previous=source.clone().putInt("x",10);
        previous.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).putByte("Count",3);
        tiles.put("0,70,0",tile.apply(source));tiles.put("10,70,0",tile.apply(previous));
        world.put("0,70,0",block(54,0,70,0));world.put("10,70,0",block(54,10,70,0));
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> tiles.get(key(call.getArgument(0))));
        try(var factory=mockStatic(cn.nukkit.blockentity.BlockEntity.class)) {
            factory.when(() -> cn.nukkit.blockentity.BlockEntity.createBlockEntity(anyString(),
                    nullable(cn.nukkit.level.format.FullChunk.class),any(cn.nukkit.nbt.tag.CompoundTag.class)))
                    .thenAnswer(call -> {
                        cn.nukkit.nbt.tag.CompoundTag tag=call.getArgument(2);var entity=tile.apply(tag);
                        tiles.put(tag.getInt("x")+","+tag.getInt("y")+","+tag.getInt("z"),entity);return entity;
                    });
            BlocksArray copy=new BlocksArray();
            var captured=ClipboardOperation.copy(new Position(0,70,0,level),new Position(0,70,0,level),
                    new Vector3(0,70,0),copy,100,1,queue::add);finish();assertEquals(1,captured.join());
            var pasted=ClipboardOperation.paste(player,new Position(10,70,0,level),copy,false,100,1,queue::add);
            finish();assertEquals(1,pasted.join());
            assertEquals(12,tiles.get("10,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            var undone=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(1,undone.join());
            assertEquals(3,tiles.get("10,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            var stacked=new StackOperation(player,new Position(0,70,0,level),new Position(0,70,0,level),
                    2,1,0,0,100,1,queue::add).start();finish();assertEquals(2,stacked.join());
            assertEquals(12,tiles.get("2,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            tiles.get("1,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).putByte("Count",1);
            assertEquals(12,copy.blockEntities.get(0).getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            assertEquals(12,tiles.get("0,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            assertEquals(12,tiles.get("2,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
        }
    }

    public interface PairedChest {
        cn.nukkit.blockentity.BlockEntity getPair();
        boolean pairWith(cn.nukkit.blockentity.BlockEntity other);
        boolean unpair();
    }

    @Test void doubleChestsSurvivePasteUndoPartialOverwriteAndSeparateStackCopies(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var tiles=new HashMap<String,cn.nukkit.blockentity.BlockEntity>();
        var links=new HashMap<cn.nukkit.blockentity.BlockEntity,cn.nukkit.blockentity.BlockEntity>();
        java.util.function.Function<cn.nukkit.nbt.tag.CompoundTag,cn.nukkit.blockentity.BlockEntitySpawnable> tile=tag -> {
            var entity=mock(cn.nukkit.blockentity.BlockEntitySpawnable.class,withSettings().extraInterfaces(PairedChest.class));
            entity.namedTag=tag.clone();when(entity.isBlockEntityValid()).thenReturn(true);
            PairedChest api=(PairedChest)entity;
            when(api.getPair()).thenAnswer(call->links.get(entity));
            when(api.pairWith(any(cn.nukkit.blockentity.BlockEntity.class))).thenAnswer(call->{
                cn.nukkit.blockentity.BlockEntity other=call.getArgument(0);
                if(links.containsKey(entity)||links.containsKey(other))return false;
                links.put(entity,other);links.put(other,entity);
                entity.namedTag.putInt("pairx",other.namedTag.getInt("x")).putInt("pairz",other.namedTag.getInt("z"));
                other.namedTag.putInt("pairx",entity.namedTag.getInt("x")).putInt("pairz",entity.namedTag.getInt("z"));
                return true;
            });
            when(api.unpair()).thenAnswer(call->{
                var other=links.remove(entity);entity.namedTag.remove("pairx").remove("pairz");
                if(other!=null){links.remove(other);other.namedTag.remove("pairx").remove("pairz");}return other!=null;
            });
            doAnswer(call->{tiles.remove(entity.namedTag.getInt("x")+",70,0");return null;}).when(entity).close();
            return entity;
        };
        for(int x:new int[]{0,1,10,11}) {
            var tag=new cn.nukkit.nbt.tag.CompoundTag().putString("id","Chest").putInt("x",x).putInt("y",70).putInt("z",0)
                .putInt("pairx",x%2==0?x+1:x-1).putInt("pairz",0)
                .putList(new cn.nukkit.nbt.tag.ListTag<cn.nukkit.nbt.tag.CompoundTag>("Items")
                    .add(new cn.nukkit.nbt.tag.CompoundTag().putByte("Count",x<2?12+x:x-7)));
            tiles.put(x+",70,0",tile.apply(tag));world.put(x+",70,0",block(54,x,70,0));
        }
        for(int x:new int[]{0,10}) {links.put(tiles.get(x+",70,0"),tiles.get((x+1)+",70,0"));links.put(tiles.get((x+1)+",70,0"),tiles.get(x+",70,0"));}
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->tiles.get(key(call.getArgument(0))));
        try(var factory=mockStatic(cn.nukkit.blockentity.BlockEntity.class)) {
            factory.when(()->cn.nukkit.blockentity.BlockEntity.createBlockEntity(anyString(),
                nullable(cn.nukkit.level.format.FullChunk.class),any(cn.nukkit.nbt.tag.CompoundTag.class))).thenAnswer(call->{
                    cn.nukkit.nbt.tag.CompoundTag tag=call.getArgument(2);var created=tile.apply(tag);
                    tiles.put(tag.getInt("x")+",70,0",created);return created;
                });
            BlocksArray copy=new BlocksArray();
            var captured=ClipboardOperation.copy(new Position(0,70,0,level),new Position(1,70,0,level),
                    new Vector3(0,70,0),copy,100,1,queue::add);finish();assertEquals(2,captured.join());
            var pasted=ClipboardOperation.paste(player,new Position(10,70,0,level),copy,false,100,1,queue::add);
            finish();assertEquals(2,pasted.join());assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            // Disk save/load must use the same NBT, pairing and undo pipeline.
            player.getSelection().setPosOne(new Position(0,70,0,level));
            player.getSelection().setPosTwo(new Position(1,70,0,level));
            var repository=new ru.SocialMoods.SWorldEdit.repository.impl.SchematicRepositoryImpl(dir.toFile());
            var service=new ru.SocialMoods.SWorldEdit.service.impl.SchematicServiceImpl(repository,100,1,queue::add);
            var saved=service.save(player,"paired");
            finishUntil(saved); saved.join();
            assertEquals(2,repository.load("paired").getBlockEntities().size());
            try(var registry=mockStatic(Block.class)) {
                registry.when(()->Block.get(54,0)).thenAnswer(call->block(54,0,0,0));
                var loaded=service.load(player,new Position(30,70,0,level),"paired");
                finishUntil(loaded); assertEquals(2,loaded.join());
                assertSame(tiles.get("31,70,0"),links.get(tiles.get("30,70,0")));
                assertEquals(12,tiles.get("30,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            }

            var diskUndo=ClipboardOperation.undo(player,1,1,queue::add);finish();assertEquals(2,diskUndo.join());
            assertNull(tiles.get("30,70,0"));
            var undone=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(2,undone.join());
            assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            assertEquals(3,tiles.get("10,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            assertEquals(4,tiles.get("11,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            BlocksArray half=new BlocksArray();half.blocks.add(copy.blocks.get(0));half.blockEntities.put(0,copy.blockEntities.get(0));
            var partial=ClipboardOperation.paste(player,new Position(10,70,0,level),half,false,100,1,queue::add);
            finish();assertEquals(1,partial.join());assertNull(links.get(tiles.get("10,70,0")));
            tiles.get("11,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).putByte("Count",30);
            var restore=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(1,restore.join());
            assertSame(tiles.get("11,70,0"),links.get(tiles.get("10,70,0")));
            assertEquals(30,tiles.get("11,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
            var stacked=new StackOperation(player,new Position(0,70,0,level),new Position(1,70,0,level),
                    2,1,0,0,100,1,queue::add).start();finish();assertEquals(4,stacked.join());
            assertSame(tiles.get("3,70,0"),links.get(tiles.get("2,70,0")));
            assertSame(tiles.get("5,70,0"),links.get(tiles.get("4,70,0")));
            assertSame(tiles.get("1,70,0"),links.get(tiles.get("0,70,0")));
            assertEquals(13,tiles.get("5,70,0").namedTag.getList("Items",cn.nukkit.nbt.tag.CompoundTag.class).get(0).getByte("Count"));
        }
    }
}
