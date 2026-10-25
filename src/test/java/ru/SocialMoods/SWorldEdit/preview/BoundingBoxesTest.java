package ru.SocialMoods.SWorldEdit.preview;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.BlockEntityDataPacket;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteOrder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundingBoxesTest {
    private final Player player=mock(Player.class);
    private final Level level=mock(Level.class);
    private final Map<Integer,Runnable> pending=new HashMap<>();
    private final List<BlockEntityDataPacket> packets=new ArrayList<>();
    private int nextId,lastDelay;
    private final BoundingBoxes boxes=new BoundingBoxes(new BoundingBoxes.Tasks(){
        public int later(Runnable work,int ticks){lastDelay=ticks;pending.put(++nextId,work);return nextId;}
        public void cancel(int id){pending.remove(id);}
    });
    @BeforeAll static void blocks() throws Exception {
        try {Block.class.getMethod("init").invoke(null);}
        catch(NoSuchMethodException e){Object registry=Class.forName("cn.nukkit.registry.Registries").getField("BLOCK").get(null);registry.getClass().getMethod("init").invoke(registry);}
    }
    @BeforeEach void prepare(){
        when(player.isOnline()).thenReturn(true);when(player.getLevel()).thenReturn(level);
        when(player.getFloorX()).thenReturn(12);when(player.getFloorZ()).thenReturn(20);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(7));
        when(player.dataPacket(any(DataPacket.class))).thenAnswer(call->{if(call.getArgument(0) instanceof BlockEntityDataPacket packet)packets.add(packet);return true;});
    }
    private void show(String tag,int seconds){boxes.showBox(player,level,4,70,9,2,72,5,tag,seconds);}
    @Test void embedsInclusiveBoundsAndExpiresWithoutWorldWrites() throws Exception {
        show(Previews.PASTE,10);assertEquals(200,lastDelay);assertEquals(List.of(Previews.PASTE),boxes.boxTags(player));
        CompoundTag tag=NBTIO.read(packets.getFirst().namedTag,ByteOrder.LITTLE_ENDIAN,true);
        assertEquals(3,tag.getInt("xStructureSize"));assertEquals(3,tag.getInt("yStructureSize"));assertEquals(5,tag.getInt("zStructureSize"));
        assertEquals(2,tag.getInt("x")+tag.getInt("xStructureOffset"));assertEquals(70,tag.getInt("y")+tag.getInt("yStructureOffset"));
        assertTrue(tag.getBoolean("showBoundingBox"));
        assertTrue(boxes.isMarker(player,new Vector3(12,-63,20)));
        pending.remove(nextId).run();assertTrue(boxes.boxTags(player).isEmpty());
        verify(level,never()).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
    }
    @Test void staleExpiryCannotRemoveReplacementAndSelectionUsesDifferentMarker(){
        show(Previews.SELECTION,0);show(Previews.PASTE,10);
        assertEquals(2,boxes.boxTags(player).size());assertEquals(12,packets.get(0).x);assertEquals(13,packets.get(1).x);
        Runnable old=pending.get(nextId);show(Previews.PASTE,15);old.run();
        assertTrue(boxes.boxTags(player).contains(Previews.PASTE));assertEquals(300,lastDelay);
        boxes.close();assertTrue(boxes.boxTags(player).isEmpty());assertTrue(pending.isEmpty());
    }
    @Test void wrongWorldAndOfflinePlayersReceiveNoBoxAndClearingNeverRestoresInNewWorld(){
        when(player.isOnline()).thenReturn(false);show(Previews.PASTE,10);assertTrue(packets.isEmpty());
        when(player.isOnline()).thenReturn(true);show(Previews.PASTE,10);
        clearInvocations(level);when(player.getLevel()).thenReturn(mock(Level.class));boxes.clearAllBoxes(player);
        verify(level,never()).sendBlocks(eq(player),any(Vector3[].class),anyInt());assertTrue(pending.isEmpty());
    }
    @Test void affectedPasteBoundsSupportNegativeCoordinatesAndSingleBlocks(){
        var bounds=Previews.Bounds.include(null,new Vector3(-5,70,4));
        bounds=Previews.Bounds.include(bounds,new Vector3(-2,68,1));
        assertEquals(new Previews.Bounds(-5,68,1,-2,70,4),bounds);
        assertEquals(new Previews.Bounds(1,2,3,1,2,3),Previews.Bounds.include(null,new Vector3(1,2,3)));
    }
}
