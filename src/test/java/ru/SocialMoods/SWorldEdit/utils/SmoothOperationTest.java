package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmoothOperationTest {
    final Level level=mock(Level.class);
    final PlayerData player=new PlayerData(mock(Player.class));
    final Map<String,Block> world=new HashMap<>();
    final ArrayDeque<Runnable> queue=new ArrayDeque<>();
    final List<Vector3> writes=new ArrayList<>();
    String key(Vector3 v){return v.getFloorX()+","+v.getFloorY()+","+v.getFloorZ();}
    Block block(int id,double x,double y,double z){
        Block b=mock(Block.class);when(b.getId()).thenReturn(id);
        doCallRealMethod().when(b).setComponents(anyDouble(),anyDouble(),anyDouble());
        doCallRealMethod().when(b).getFloorX();doCallRealMethod().when(b).getFloorY();doCallRealMethod().when(b).getFloorZ();
        b.setComponents(x,y,z);b.level=level;
        when(b.clone()).thenAnswer(c->block(id,b.x,b.y,b.z));return b;
    }
    @BeforeEach void setup(){
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        when(level.getBlock(any(Vector3.class))).thenAnswer(c->{Vector3 v=c.getArgument(0);return world.getOrDefault(key(v),block(0,v.x,v.y,v.z));});
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(c->{
            Vector3 v=c.getArgument(0);Block b=c.getArgument(1);writes.add(new Vector3(v.x,v.y,v.z));
            world.put(key(v),block(b.getId(),v.x,v.y,v.z));return true;
        });
    }
    void terrain(int center,int surrounding){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            int top=x==0&&z==0?center:surrounding;
            for(int h=0;h<=top;h++)world.put(x+","+(70+h)+","+z,block(h==top?2:3,x,70+h,z));
        }
    }
    SmoothOperation operation(int iterations,int batch){return new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,74,1,level),iterations,10,100, batch,Set.of(1,2,3),queue::add);}
    void finish(){int guard=10000;while(!queue.isEmpty()){assertTrue(guard-->0);queue.remove().run();}}
    int id(int y){return world.getOrDefault("0,"+y+",0",block(0,0,y,0)).getId();}

    @Test void lowersPeakInBatchesWithinSelectionAndUndo(){
        terrain(4,1);var result=operation(1,1).start();
        assertTrue(writes.isEmpty());queue.remove().run();assertTrue(writes.isEmpty());
        finish();assertEquals(3,result.join());assertEquals(2,id(72));assertEquals(0,id(73));assertEquals(0,id(74));
        assertTrue(writes.stream().allMatch(v->v.x==0&&v.z==0&&v.y>=70&&v.y<=74));
        assertEquals(1,player.undoSteps.size());assertNull(player.copiedBlocks);
        var undo=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(3,undo.join());
        assertEquals(2,id(74));assertEquals(3,id(72));assertTrue(player.undoSteps.isEmpty());
    }
    @Test void fillsValleyWithSubsurfaceAndKeepsGrassOnTop(){
        terrain(1,4);var result=operation(1,2).start();finish();assertEquals(3,result.join());
        assertEquals(3,id(71));assertEquals(3,id(72));assertEquals(2,id(73));assertEquals(0,id(74));
    }
    @Test void repeatedPassesUsePreviousPassAndFlatTerrainIsNoOp(){
        terrain(4,1);var result=operation(2,3).start();finish();assertEquals(4,result.join());assertEquals(2,id(71));
        world.clear();writes.clear();player.undoSteps.clear();terrain(2,2);
        result=operation(3,3).start();finish();assertEquals(0,result.join());assertTrue(writes.isEmpty());assertTrue(player.undoSteps.isEmpty());
    }
    @Test void edgePeaksParticipateWithoutWritingOutsideSelection(){
        terrain(1,1);
        for(int h=1;h<=4;h++)world.put("-1,"+(70+h)+",0",block(h==4?2:3,-1,70+h,0));
        var result=operation(1,2).start();finish();assertTrue(result.join()>0);
        assertEquals(0,world.get("-1,74,0").getId());
        assertTrue(writes.stream().allMatch(v->v.x>=-1&&v.x<=1&&v.z>=-1&&v.z<=1&&v.y>=70&&v.y<=74));
    }
    @Test void buriedBedrockAndOreDoNotExcludeSurfaceOrGetOverwritten(){
        for(int protectedId:List.of(7,14)){
            world.clear();writes.clear();player.undoSteps.clear();terrain(4,1);
            world.put("0,70,0",block(protectedId,0,70,0));
            var result=operation(1,2).start();finish();assertTrue(result.join()>0);
            assertEquals(protectedId,id(70));assertTrue(writes.stream().noneMatch(v->v.y==70));
        }
    }
    @Test void compressionResamplesSubsurfaceInsteadOfOnlyPaintingGrass(){
        // A tall thin column: two stone layers, two dirt layers, then grass.
        // Neighbors at height 1 bring its surface down to height 2.
        terrain(4,1);world.put("0,70,0",block(1,0,70,0));world.put("0,71,0",block(1,0,71,0));
        var result=operation(1,2).start();finish();assertTrue(result.join()>0);
        assertEquals(1,id(70));assertEquals(3,id(71));assertEquals(2,id(72));
    }
    @Test void skipsColumnsWithWaterStructuresOrBlockEntities(){
        for(int unsafe:List.of(9,54)){
            world.clear();terrain(4,1);world.put("0,74,0",block(unsafe,0,74,0));
            var result=operation(1,3).start();finish();assertEquals(0,result.join());
        }
        world.clear();terrain(4,1);
        when(level.getBlockEntity(argThat((Vector3 v)->v!=null&&v.x==0&&v.z==0&&v.y==74))).thenReturn(mock(BlockEntity.class));
        var result=operation(1,3).start();finish();assertEquals(0,result.join());assertTrue(writes.isEmpty());
    }
    @Test void rejectsInvalidIterationsVolumeAndWorldBeforeReads(){
        assertThrows(IllegalArgumentException.class,()->operation(0,1));
        assertThrows(IllegalArgumentException.class,()->operation(11,1));
        assertThrows(IllegalArgumentException.class,()->new SmoothOperation(player,new Position(0,70,0,level),new Position(100,74,100,level),1,10,100,1,Set.of(1),queue::add));
        assertThrows(IllegalArgumentException.class,()->new SmoothOperation(player,new Position(0,70,0,level),new Position(1,74,1,mock(Level.class)),1,10,100,1,Set.of(1),queue::add));
        verify(level,never()).getBlock(any(Vector3.class));assertTrue(writes.isEmpty());
    }
    @Test void readFailureDoesNotEditAndPartialWriteFailureRetainsUndo(){
        terrain(4,1);
        when(level.getBlock(any(Vector3.class))).thenThrow(new IllegalStateException("read failure"));
        var failed=operation(1,1).start();finish();assertTrue(failed.isCompletedExceptionally());assertTrue(writes.isEmpty());assertTrue(player.undoSteps.isEmpty());
        when(level.getBlock(any(Vector3.class))).thenAnswer(c->{Vector3 v=c.getArgument(0);return world.getOrDefault(key(v),block(0,v.x,v.y,v.z));});
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenReturn(true).thenThrow(new IllegalStateException("write failure"));
        failed=operation(1,1).start();finish();assertTrue(failed.isCompletedExceptionally());assertEquals(1,player.undoSteps.size());assertFalse(player.undoSteps.getFirst().blocks.isEmpty());
    }
}
