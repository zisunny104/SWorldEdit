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

class SurfaceOperationTest {
    final Level level=mock(Level.class);
    final PlayerData player=new PlayerData(mock(Player.class));
    final Map<String,Block> world=new HashMap<>();
    final Map<String,BlockEntity> entities=new HashMap<>();
    final List<Vector3> writes=new ArrayList<>();
    final ArrayDeque<Runnable> queue=new ArrayDeque<>();
    String key(Vector3 v){return v.getFloorX()+","+v.getFloorY()+","+v.getFloorZ();}
    Block block(int id,int damage,double x,double y,double z){
        Block b=mock(Block.class);when(b.getId()).thenReturn(id);when(b.getDamage()).thenReturn(damage);
        when(b.canPassThrough()).thenReturn(id==0 || id==31 || id==8 || id==9);
        doCallRealMethod().when(b).setComponents(anyDouble(),anyDouble(),anyDouble());
        doCallRealMethod().when(b).getFloorX();doCallRealMethod().when(b).getFloorY();doCallRealMethod().when(b).getFloorZ();
        b.setComponents(x,y,z);b.level=level;when(b.clone()).thenAnswer(c->block(id,damage,b.x,b.y,b.z));return b;
    }
    void put(int id,int damage,int x,int y,int z){world.put(x+","+y+","+z,block(id,damage,x,y,z));}
    int id(int x,int y,int z){return world.getOrDefault(x+","+y+","+z,block(0,0,x,y,z)).getId();}
    @BeforeEach void setup(){
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        when(level.getBlock(any(Vector3.class))).thenAnswer(c->{Vector3 v=c.getArgument(0);return world.getOrDefault(key(v),block(0,0,v.x,v.y,v.z));});
        when(level.getBlockEntity(any(Vector3.class))).thenAnswer(c->entities.get(key(c.getArgument(0))));
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(c->{
            Vector3 v=c.getArgument(0);Block b=c.getArgument(1);writes.add(new Vector3(v.x,v.y,v.z));put(b.getId(),b.getDamage(),v.getFloorX(),v.getFloorY(),v.getFloorZ());return true;
        });
    }
    SurfaceOperation green(boolean coarse,int batch){return SurfaceOperation.green(player,new Position(0,71,0,level),1,3,coarse,128,100,1000,batch,queue::add);}
    SurfaceOperation naturalize(int min,int max,int limit){return SurfaceOperation.naturalize(player,new Position(0,min,0,level),new Position(0,max,0,level),limit,1000,1,queue::add);}
    void finish(){int guard=10000;while(!queue.isEmpty()){assertTrue(guard-->0);queue.remove().run();}}

    @Test void greenUsesCylinderSkipsCoarseAndKeepsClipboardWithOneUndo(){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)put(3,0,x,70,z);
        put(3,1,1,70,0);put(31,0,0,71,0);
        player.copiedBlocks=new BlocksArray();BlocksArray clipboard=player.copiedBlocks;
        var result=green(false,1).start();queue.remove().run();assertTrue(writes.isEmpty());finish();
        assertEquals(4,result.join());assertEquals(2,id(0,70,0));assertEquals(3,id(1,70,0));assertEquals(3,id(1,70,1));assertEquals(31,id(0,71,0));
        assertEquals(1,player.undoSteps.size());assertSame(clipboard,player.copiedBlocks);
        var undo=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(4,undo.join());assertEquals(3,id(0,70,0));assertTrue(player.undoSteps.isEmpty());
    }
    @Test void greenForceIncludesOnlyCoarseDirtAndNeverChangesHeight(){
        put(3,1,0,70,0);put(3,2,1,70,0);
        var result=green(true,2).start();finish();assertEquals(1,result.join());assertEquals(2,id(0,70,0));assertEquals(3,id(1,70,0));
        assertTrue(writes.stream().allMatch(v->v.y==70));
    }
    @Test void greenStopsAtRoofLiquidAndBlockEntityWithoutGreeningBuriedDirt(){
        put(3,0,0,70,0);put(1,0,0,72,0);
        put(3,0,1,70,0);put(9,0,1,71,0);
        put(3,0,-1,70,0);entities.put("-1,70,0",mock(BlockEntity.class));
        var result=green(true,2).start();finish();assertEquals(0,result.join());assertTrue(writes.isEmpty());assertTrue(player.undoSteps.isEmpty());
    }
    @Test void greenRespectsHeightAndNegativeWorldCoordinates(){
        put(3,0,-10,-64,-10);put(3,0,-10,-60,-10);
        var result=SurfaceOperation.green(player,new Position(-10,-63,-10,level),1,1,false,128,100,1000,2,queue::add).start();
        finish();assertEquals(1,result.join());assertEquals(2,id(-10,-64,-10));assertEquals(3,id(-10,-60,-10));
    }
    @Test void naturalizeMakesGrassThreeDirtThenStoneAndUndo(){
        for(int y=65;y<=70;y++)put(1,0,0,y,0);
        var result=naturalize(65,72,100).start();queue.remove().run();assertTrue(writes.isEmpty());finish();assertEquals(4,result.join());
        assertEquals(2,id(0,70,0));for(int y=67;y<=69;y++)assertEquals(3,id(0,y,0));for(int y=65;y<=66;y++)assertEquals(1,id(0,y,0));
        assertEquals(1,player.undoSteps.size());var undo=ClipboardOperation.undo(player,0,1,queue::add);finish();assertEquals(4,undo.join());
        for(int y=65;y<=70;y++)assertEquals(1,id(0,y,0));
    }
    @Test void naturalizePreservesOresVariantsAirAndBlockEntities(){
        for(int y=65;y<=70;y++)put(1,0,0,y,0);
        put(14,0,0,69,0);put(1,1,0,68,0);put(0,0,0,67,0);put(3,1,0,66,0);
        entities.put("0,65,0",mock(BlockEntity.class));
        var result=naturalize(65,72,100).start();finish();assertEquals(1,result.join());
        assertEquals(2,id(0,70,0));assertEquals(14,id(0,69,0));assertEquals(1,world.get("0,68,0").getDamage());
        assertEquals(0,id(0,67,0));assertEquals(1,world.get("0,66,0").getDamage());assertEquals(1,id(0,65,0));
    }
    @Test void naturalizeRejectsUndergroundCrossSectionAndKeepsSelectionBounds(){
        for(int y=65;y<=71;y++)put(1,0,0,y,0);
        var result=naturalize(65,70,100).start();finish();assertEquals(0,result.join());assertTrue(writes.isEmpty());
        world.remove("0,71,0");result=naturalize(68,72,100).start();finish();assertEquals(3,result.join());
        assertEquals(1,id(0,67,0));assertTrue(writes.stream().allMatch(v->v.y>=68 && v.y<=72 && v.x==0 && v.z==0));
    }
    @Test void zeroChangesAndExceededChangeLimitDoNotCreateUndoOrWrite(){
        put(2,0,0,70,0);for(int y=67;y<=69;y++)put(3,0,0,y,0);put(1,0,0,66,0);
        var result=naturalize(66,72,100).start();finish();assertEquals(0,result.join());assertTrue(player.undoSteps.isEmpty());
        world.clear();for(int y=66;y<=70;y++)put(1,0,0,y,0);
        result=naturalize(66,72,1).start();finish();assertTrue(result.isCompletedExceptionally());assertTrue(writes.isEmpty());assertTrue(player.undoSteps.isEmpty());
    }
    @Test void invalidBoundsRadiusAndScanLimitFailBeforeReads(){
        for(double radius:List.of(Double.NaN,Double.POSITIVE_INFINITY,0.0,129.0))assertThrows(IllegalArgumentException.class,()->SurfaceOperation.green(player,new Position(0,70,0,level),radius,10,false,128,100,1000,1,queue::add));
        assertThrows(IllegalArgumentException.class,()->SurfaceOperation.green(player,new Position(0,70,0,level),10,100,false,128,100,1000,1,queue::add));
        assertThrows(IllegalArgumentException.class,()->SurfaceOperation.naturalize(player,new Position(0,0,0,level),new Position(0,0,0,mock(Level.class)),100,1000,1,queue::add));
        assertDoesNotThrow(()->SurfaceOperation.green(player,new Position(0,70,0,level),10,384,false,128,100000,1000000,512,queue::add));
        verify(level,never()).getBlock(any(Vector3.class));
    }
    @Test void partialWriteFailureRetainsUndoAndPlanningFailureDoesNotWrite(){
        for(int y=65;y<=70;y++)put(1,0,0,y,0);
        when(level.getBlock(any(Vector3.class))).thenThrow(new IllegalStateException("read failure"));
        var result=naturalize(65,72,100).start();finish();assertTrue(result.isCompletedExceptionally());assertTrue(player.undoSteps.isEmpty());
        doAnswer(c->{Vector3 v=c.getArgument(0);return world.getOrDefault(key(v),block(0,0,v.x,v.y,v.z));}).when(level).getBlock(any(Vector3.class));
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenReturn(true).thenThrow(new IllegalStateException("write failure"));
        result=naturalize(65,72,100).start();finish();assertTrue(result.isCompletedExceptionally());assertEquals(1,player.undoSteps.size());assertFalse(player.undoSteps.getFirst().blocks.isEmpty());
    }
}
