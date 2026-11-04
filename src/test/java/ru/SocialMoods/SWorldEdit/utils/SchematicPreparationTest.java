package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.level.*;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.data.Schematic;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.service.impl.SchematicServiceImpl;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchematicPreparationTest {
    @Test void nativeBlockPreparationYieldsBeforeWorldWrites()throws Exception{
        var repository=mock(SchematicRepository.class);int[] ids=new int[8];Arrays.fill(ids,1);
        when(repository.load("test")).thenReturn(new Schematic((short)8,(short)1,(short)1,ids,new int[8]));
        var level=mock(Level.class);var owner=new PlayerData(mock(Player.class));
        var queue=new LinkedBlockingQueue<Runnable>();
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getBlock(any(Vector3.class))).thenAnswer(call->new BlockAir());
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenReturn(true);
        var service=new SchematicServiceImpl(repository,100,2,queue::add);
        try(var factory=mockStatic(Block.class)){
            factory.when(()->Block.get(1,0)).thenAnswer(call->new BlockStone());
            var future=service.load(owner,new Position(0,70,0,level),"test");
            Runnable first=queue.poll(5,TimeUnit.SECONDS);assertNotNull(first);first.run();
            assertFalse(future.isDone());verify(level,never()).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
            int guard=1000;
            while(!future.isDone()){assertTrue(guard-->0);Runnable next=queue.poll(1,TimeUnit.SECONDS);assertNotNull(next);next.run();}
            assertEquals(8,future.join());verify(level,times(8)).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false));
        }
    }
}
