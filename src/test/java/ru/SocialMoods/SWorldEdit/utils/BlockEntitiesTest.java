package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockChest;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockEntitiesTest {
    private BlockChest chest() {
        BlockChest block=mock(BlockChest.class);
        when(block.clone()).thenReturn(block);
        return block;
    }
    private CompoundTag contents() {
        return new CompoundTag().putString("id","Chest").putInt("x",1).putInt("y",70).putInt("z",2)
                .putInt("pairx",2).putInt("pairz",2)
                .putList(new ListTag<CompoundTag>("Items").add(new CompoundTag().putByte("Count",12).putByte("Slot",0)))
                .putString("CustomName","Example");
    }
    private BlockEntitySpawnable validEntity() {
        BlockEntitySpawnable entity=mock(BlockEntitySpawnable.class);
        when(entity.isBlockEntityValid()).thenReturn(true);
        return entity;
    }
    @Test void captureFlushesRuntimeDataAndDeepCopiesNestedNbt() {
        Level level=mock(Level.class);Vector3 position=new Vector3(1,70,2);
        BlockEntity entity=mock(BlockEntity.class);entity.namedTag=contents();
        when(entity.isBlockEntityValid()).thenReturn(true);
        BlockChest block=chest();
        when(level.getBlockEntity(position)).thenReturn(entity);when(level.getBlock(position)).thenReturn(block);
        CompoundTag copy=BlockEntities.capture(level,position);
        verify(entity).saveNBT();
        copy.getList("Items",CompoundTag.class).get(0).putByte("Count",1);
        assertEquals(12,entity.namedTag.getList("Items",CompoundTag.class).get(0).getByte("Count"));
    }
    @Test void repeatPlacementRelocatesAndCopiesNbtWithoutReferencingTheSourceChest() {
        Level level=mock(Level.class);CompoundTag source=contents();List<CompoundTag> created=new ArrayList<>();
        BlockEntitySpawnable entity=validEntity();
        try(var factory=mockStatic(BlockEntity.class)) {
            factory.when(() -> BlockEntity.createBlockEntity(anyString(),nullable(FullChunk.class),any(CompoundTag.class)))
                    .thenAnswer(call -> { created.add(((CompoundTag)call.getArgument(2)).clone());return entity; });
            BlockEntities.place(level,new Vector3(100,71,200),chest(),source);
            BlockEntities.place(level,new Vector3(-100,72,-200),chest(),source);
        }
        assertEquals(2,created.size());assertEquals(100,created.get(0).getInt("x"));
        assertEquals(-100,created.get(1).getInt("x"));assertEquals(1,source.getInt("x"));
        assertFalse(created.get(0).contains("pairx"));assertTrue(source.contains("pairx"));
        assertEquals(12,created.get(1).getList("Items",CompoundTag.class).get(0).getByte("Count"));
        assertEquals("Example",created.get(1).getString("CustomName"));
        verify(entity,times(2)).setDirty();verify(entity,times(2)).spawnToAll();
    }
    @Test void absentNbtCreatesCleanUsableChest() {
        Level level=mock(Level.class);BlockEntitySpawnable entity=validEntity();
        try(var factory=mockStatic(BlockEntity.class)) {
            factory.when(() -> BlockEntity.createBlockEntity(anyString(),nullable(FullChunk.class),any(CompoundTag.class)))
                    .thenAnswer(call -> {
                        CompoundTag tag=call.getArgument(2);assertEquals("Chest",tag.getString("id"));
                        assertEquals(70,tag.getInt("y"));assertFalse(tag.contains("Items"));return entity;
                    });
            BlockEntities.place(level,new Vector3(4,70,8),chest(),null);
        }
        verify(entity).isBlockEntityValid();
    }
    @Test void incompatiblePayloadRetriesWithCleanNbtAndClosesPreviousEntity() {
        Level level=mock(Level.class);BlockEntity old=mock(BlockEntity.class);
        when(level.getBlockEntity(any(Vector3.class))).thenReturn(old);
        BlockEntitySpawnable clean=validEntity();List<CompoundTag> attempts=new ArrayList<>();
        try(var factory=mockStatic(BlockEntity.class)) {
            factory.when(() -> BlockEntity.createBlockEntity(anyString(),nullable(FullChunk.class),any(CompoundTag.class)))
                    .thenAnswer(call -> {
                        CompoundTag tag=call.getArgument(2);attempts.add(tag.clone());
                        return tag.contains("Items")?null:clean;
                    });
            BlockEntities.place(level,new Vector3(4,70,8),chest(),contents());
        }
        assertEquals(2,attempts.size());assertTrue(attempts.get(0).contains("Items"));
        assertFalse(attempts.get(1).contains("Items"));verify(old,atLeastOnce()).close();
        verify(old,never()).onBreak();verify(clean).isBlockEntityValid();
    }
    @Test void totalInitializationFailureCannotLeaveABrokenContainerBehind() {
        Level level=mock(Level.class);Block air=mock(Block.class);
        try(var factory=mockStatic(BlockEntity.class);var blocks=mockStatic(Block.class)) {
            blocks.when(() -> Block.get(0)).thenReturn(air);
            assertThrows(IllegalStateException.class,
                    () -> BlockEntities.place(level,new Vector3(4,70,8),chest(),contents()));
        }
        verify(level).setBlock(any(Vector3.class),same(air),eq(true),eq(false));
    }

    @Test void failedCreationRestoresAnOrdinaryDestinationBlock() {
        Level level=mock(Level.class);Block previous=mock(Block.class);
        when(previous.clone()).thenReturn(previous);when(level.getBlock(any(Vector3.class))).thenReturn(previous);
        try(var factory=mockStatic(BlockEntity.class)) {
            assertThrows(IllegalStateException.class,
                    () -> BlockEntities.place(level,new Vector3(4,70,8),chest(),contents()));
        }
        verify(level).setBlock(any(Vector3.class),same(previous),eq(true),eq(false));
    }

    @Test void staleSourceEntityOnOrdinaryBlockDoesNotBecomeAContainer() {
        Level level=mock(Level.class);Block ordinary=mock(Block.class);BlockEntity stale=mock(BlockEntity.class);
        when(level.getBlock(any(Vector3.class))).thenReturn(ordinary);
        when(level.getBlockEntity(any(Vector3.class))).thenReturn(stale);
        when(stale.isBlockEntityValid()).thenReturn(false);
        assertNull(BlockEntities.capture(level,new Vector3(1,70,2)));
        verify(stale,never()).saveNBT();
    }
}
