package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.Mockito.*;

class CoreChestPairTest {
    @SuppressWarnings("unchecked")
    @Test void nukkitPairApiBuildsOneShared54SlotInventory() throws Exception {
        Class<? extends BlockEntity> type;
        try { type=(Class<? extends BlockEntity>)Class.forName("cn.nukkit.blockentity.BlockEntityChest"); }
        catch(ClassNotFoundException e) { assumeTrue(false,"This integration assertion uses the Nukkit chest API");return; }
        Block.class.getMethod("init").invoke(null);
        BlockEntity first=mock(type,CALLS_REAL_METHODS),second=mock(type,CALLS_REAL_METHODS);
        Map<Integer,BlockEntity> tiles=Map.of(10,first,11,second);
        Level level=mock(Level.class,call->{
            if(call.getMethod().getName().startsWith("getBlockEntity")) {
                for(Object argument:call.getArguments())if(argument instanceof Vector3 v)return tiles.get(v.getFloorX());
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        for(BlockEntity entity:new BlockEntity[]{first,second}) {
            int x=entity==first?10:11;
            entity.level=level;entity.x=x;entity.y=70;entity.z=0;
            entity.namedTag=new CompoundTag().putString("id","Chest").putInt("x",x).putInt("y",70).putInt("z",0);
            doReturn(true).when(entity).isBlockEntityValid();
            doNothing().when((BlockEntitySpawnable)entity).spawnToAll();
            doReturn(Block.get(54)).when(entity);type.getMethod("getBlock").invoke(entity);
            Object inventory=Class.forName("cn.nukkit.inventory.ChestInventory").getConstructor(type).newInstance(entity);
            var field=type.getDeclaredField("inventory");field.setAccessible(true);field.set(entity,inventory);
        }
        ChestPairs.apply(level,new ChestPairs.Link(new Vector3(10,70,0),new Vector3(11,70,0)));
        assertSame(second,type.getMethod("getPair").invoke(first));
        assertSame(first,type.getMethod("getPair").invoke(second));
        Object inventory=type.getMethod("getInventory").invoke(first);
        assertSame(inventory,type.getMethod("getInventory").invoke(second));
        assertEquals(54,inventory.getClass().getMethod("getSize").invoke(inventory));
        assertEquals(11,first.namedTag.getInt("pairx"));assertEquals(10,second.namedTag.getInt("pairx"));
        verify(first).setDirty();verify(second).setDirty();
    }
}
