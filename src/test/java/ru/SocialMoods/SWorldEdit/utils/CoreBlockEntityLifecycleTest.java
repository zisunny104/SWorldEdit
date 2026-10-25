package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.Mockito.*;

class CoreBlockEntityLifecycleTest {
    @SuppressWarnings("unchecked")
    @Test void actualCoreFactoryRegistersUsableChestBeaconAndSign() throws Exception {
        try { Class.forName("cn.nukkit.blockentity.BlockEntityChest"); }
        catch(ClassNotFoundException e) { assumeTrue(false,"Uses Nukkit block entity classes"); return; }
        Block.class.getMethod("init").invoke(null);
        for(String type:List.of("Chest","Beacon","Sign")) {
            BlockEntity.class.getMethod("registerBlockEntity",String.class,Class.class).invoke(null,type,Class.forName("cn.nukkit.blockentity.BlockEntity"+type));
        }
        Map<String,Block> blocks=new HashMap<>(); Map<String,BlockEntity> entities=new HashMap<>();
        java.util.function.Function<Vector3,String> key=v->v.getFloorX()+","+v.getFloorY()+","+v.getFloorZ();
        Level level=mock(Level.class,c->{
            if(c.getMethod().getName().equals("getBlock") && c.getArguments().length==6)
                return blocks.get(key.apply(new Vector3((int)c.getArgument(1),(int)c.getArgument(2),(int)c.getArgument(3))));
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(c);
        }); Server server=mock(Server.class);
        LevelProvider provider=mock(LevelProvider.class);BaseFullChunk chunk=mock(BaseFullChunk.class);
        when(provider.getLevel()).thenReturn(level);when(chunk.getProvider()).thenReturn(provider);when(level.getServer()).thenReturn(server);
        when(level.getChunk(anyInt(),anyInt(),eq(true))).thenReturn(chunk);
        when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        when(level.getBlock(any(Vector3.class))).thenAnswer(c->{ Vector3 v=c.getArgument(0);return blocks.getOrDefault(key.apply(v),Block.get(0)); });
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(c->entities.get(key.apply(c.getArgument(0))));
        when(level.getBlockIdAt(any(cn.nukkit.level.format.FullChunk.class),anyInt(),anyInt(),anyInt()))
            .thenAnswer(c->blocks.get(key.apply(new Vector3((int)c.getArgument(1),(int)c.getArgument(2),(int)c.getArgument(3)))).getId());
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(c->{
            Vector3 v=c.getArgument(0);Block b=c.getArgument(1);b.setComponents(v.x,v.y,v.z);b.level=level;blocks.put(key.apply(v),b);return true;
        });
        doAnswer(c->{BlockEntity e=c.getArgument(0);entities.put(key.apply(e),e);return null;}).when(chunk).addBlockEntity(any());
        doAnswer(c->{BlockEntity e=c.getArgument(0);entities.remove(key.apply(e),e);return null;}).when(chunk).removeBlockEntity(any());
        for(int id:new int[]{54,138,63}) {
            Vector3 destination=new Vector3(id,70,5);Block b=Block.get(id);
            CompoundTag tag=new CompoundTag().putString("id",id==54?"Chest":id==138?"Beacon":"Sign")
                .putInt("x",0).putInt("y",70).putInt("z",0).putString("Text","Hello\n世界");
            BlockEntities.place(level,destination,b,tag);
            BlockEntity actual=entities.get(key.apply(destination));
            assertNotNull(actual,"Missing registered entity for "+b.getClass().getName());
            assertFalse(actual.closed);assertTrue(actual.isBlockEntityValid());
            assertEquals(id,actual.getFloorX());
            if(id==54) assertNotNull(actual.getClass().getMethod("getInventory").invoke(actual));
            if(id==63) assertEquals("Hello",((String[])actual.getClass().getMethod("getText").invoke(actual))[0]);
        }
    }
}
