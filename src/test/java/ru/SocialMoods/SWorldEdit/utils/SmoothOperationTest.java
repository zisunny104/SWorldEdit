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
        when(b.clone()).thenAnswer(c->{int meta=b.getDamage();Block copy=block(id,b.x,b.y,b.z);when(copy.getDamage()).thenReturn(meta);return copy;});return b;
    }
    @BeforeEach void setup(){
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
        when(level.getBlock(any(Vector3.class))).thenAnswer(c->{Vector3 v=c.getArgument(0);return world.getOrDefault(key(v),block(0,v.x,v.y,v.z));});
        when(level.setBlock(any(Vector3.class),any(Block.class),eq(true),eq(false))).thenAnswer(c->{
            Vector3 v=c.getArgument(0);Block b=c.getArgument(1);writes.add(new Vector3(v.x,v.y,v.z));
            int meta=b.getDamage();Block copy=block(b.getId(),v.x,v.y,v.z);when(copy.getDamage()).thenReturn(meta);world.put(key(v),copy);return true;
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
    void waterTo(int top,int waterId){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int h=0;h<=top;h++){
            String position=x+","+(70+h)+","+z;
            if(!world.containsKey(position))world.put(position,block(waterId,x,70+h,z));
        }
    }
    SmoothOperation wetOperation(int iterations){return new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,76,1,level),iterations,10,100,2,Set.of(1,2,3),queue::add);}

    @Test void smoothsSubmergedPeakAndUndoRestoresBedAndWater(){
        for(int waterId:List.of(8,9)){
            world.clear();writes.clear();player.undoSteps.clear();terrain(4,1);waterTo(6,waterId);
            var result=wetOperation(1).start();finish();assertEquals(3,result.join());
            assertEquals(2,id(72));assertEquals(waterId,id(73));assertEquals(waterId,id(74));
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)assertEquals(waterId,world.get(x+",76,"+z).getId());
            assertEquals(0,id(77));assertTrue(writes.stream().allMatch(v->v.y<=76));
            var undo=ClipboardOperation.undo(player,0,2,queue::add);finish();assertEquals(3,undo.join());
            assertEquals(2,id(74));assertEquals(3,id(73));assertEquals(waterId,id(75));
        }
    }

    @Test void raisesSubmergedValleyByReplacingWaterWithBedMaterial(){
        terrain(1,4);waterTo(6,9);
        var result=wetOperation(1).start();finish();assertEquals(3,result.join());
        assertEquals(3,id(71));assertEquals(3,id(72));assertEquals(2,id(73));
        for(int h=74;h<=76;h++)assertEquals(9,id(h));
    }

    @Test void loweredShoreGetsAdjacentWaterAndRemovesSubmergedLandPlantsWithUndo(){
        terrain(4,1);waterTo(3,9);
        world.put("0,75,0",block(175,0,75,0));world.put("0,76,0",block(175,0,76,0));when(world.get("0,76,0").getDamage()).thenReturn(8);
        var result=operation(1,2).start();finish();assertTrue(result.join()>0);
        assertEquals(2,id(72));assertEquals(9,id(73));assertEquals(0,id(74));assertEquals(0,id(75));assertEquals(0,id(76));
        var undo=ClipboardOperation.undo(player,0,2,queue::add);finish();undo.join();
        assertEquals(2,id(74));assertEquals(175,id(75));assertEquals(175,id(76));assertEquals(8,world.get("0,76,0").getDamage());
    }

    @Test void doesNotFloodUnchangedDryTerrainWithASelectionsHighestWater(){
        terrain(2,2);world.put("-1,73,-1",block(9,-1,73,-1));world.put("-1,74,-1",block(9,-1,74,-1));
        var result=operation(1,2).start();finish();assertEquals(0,result.join());
        assertEquals(0,id(73));assertEquals(0,id(74));assertTrue(writes.isEmpty());
    }

    @Test void refusesToJoinDifferentWaterLevelsBeforeAnyWrites(){
        for(int x=-4;x<=4;x++){
            int top=x==0?4:0;
            for(int h=0;h<=top;h++)world.put(x+","+(70+h)+",0",block(h==top?2:3,x,70+h,0));
            if(x!=0)for(int h=1;h<=(x<0?2:3);h++)world.put(x+","+(70+h)+",0",block(9,x,70+h,0));
        }
        var work=new SmoothOperation(player,new Position(-4,70,0,level),new Position(4,74,0,level),2,10,100,2,Set.of(1,2,3),queue::add);
        var result=work.start();finish();
        var failure=assertThrows(java.util.concurrent.CompletionException.class,result::join);
        assertTrue(failure.getCause().getMessage().contains("Y="));assertTrue(writes.isEmpty());assertTrue(player.undoSteps.isEmpty());
    }

    @Test void keepsDifferentPondsAtTheirOwnLevelsWhenBankRemainsClosed(){
        for(int x=-5;x<=5;x++){
            int top=Math.abs(x)<=2?6:0;
            for(int h=0;h<=top;h++)world.put(x+","+(70+h)+",0",block(h==top?2:3,x,70+h,0));
            if(top==0)for(int h=1;h<=(x<0?2:3);h++)world.put(x+","+(70+h)+",0",block(9,x,70+h,0));
        }
        var work=new SmoothOperation(player,new Position(-5,70,0,level),new Position(5,76,0,level),1,10,200,2,Set.of(1,2,3),queue::add);
        var result=work.start();finish();assertTrue(result.join()>0);
        assertEquals(9,world.get("-5,72,0").getId());assertEquals(9,world.get("5,73,0").getId());
        assertFalse(world.containsKey("-5,73,0"));assertEquals(3,id(74));
    }

    @Test void waterAboveSelectionIsPreservedAndCaveBelowBedStaysDry(){
        terrain(4,1);world.put("0,71,0",block(0,0,71,0));waterTo(8,9);
        var result=operation(1,2).start();finish();assertTrue(result.join()>0);
        assertEquals(0,id(71));assertEquals(2,id(72));assertEquals(9,id(73));assertEquals(9,id(74));
        for(int h=75;h<=78;h++)assertEquals(9,id(h));
        assertTrue(writes.stream().allMatch(v->v.y<=74));
    }

    @Test void flowingOrHangingWaterIsProtectedAndUnderwaterModeCanBeDisabled(){
        terrain(4,1);Block flow=block(8,0,75,0);when(flow.getDamage()).thenReturn(8);world.put("0,75,0",flow);
        var result=wetOperation(1).start();finish();assertEquals(0,result.join());
        assertEquals(2,id(74));assertSame(flow,world.get("0,75,0"));
        world.clear();terrain(4,1);world.put("0,76,0",block(9,0,76,0));
        result=wetOperation(1).start();finish();assertEquals(0,result.join());assertEquals(2,id(74));
        world.clear();terrain(4,1);waterTo(6,9);
        var disabled=new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,76,1,level),1,10,100,2,Set.of(1,2,3),false,3,queue::add);
        result=disabled.start();finish();assertEquals(0,result.join());assertEquals(2,id(74));
    }

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
    @Test void grassAndSnowMoveWithSmoothedSurfaceAndCanBeUndone(){
        for(int cover:List.of(31,78)){
            world.clear();writes.clear();player.undoSteps.clear();terrain(4,1);
            Block cap=block(cover,0,75,0);when(cap.getDamage()).thenReturn(3);world.put("0,75,0",cap);
            var work=new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,76,1,level),
                    1,10,100,2,Set.of(1,2,3),queue::add);
            var result=work.start();finish();assertTrue(result.join()>0);
            assertEquals(2,id(72));assertEquals(cover,id(73));assertEquals(3,world.get("0,73,0").getDamage());
            assertEquals(0,id(75));
            var undo=ClipboardOperation.undo(player,0,2,queue::add);finish();undo.join();
            assertEquals(2,id(74));assertEquals(cover,id(75));assertEquals(3,world.get("0,75,0").getDamage());
        }
    }
    @Test void movesBothHalvesOfTallPlant(){
        terrain(4,1);
        world.put("0,75,0",block(175,0,75,0));world.put("0,76,0",block(175,0,76,0));
        when(world.get("0,76,0").getDamage()).thenReturn(8);
        // Both plant halves are outside the selected terrain height; capture and relocate them together.
        var work=new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,74,1,level),
                1,10,100,2,Set.of(1,2,3),queue::add);
        var result=work.start();finish();assertTrue(result.join()>0);
        assertEquals(175,id(73));assertEquals(175,id(74));assertEquals(8,world.get("0,74,0").getDamage());
        assertEquals(0,id(75));assertEquals(0,id(76));
    }
    @Test void naturalCanopyDoesNotExcludeGroundButPersistentLeavesDo(){
        for(boolean persistent:List.of(false,true)){
            world.clear();writes.clear();player.undoSteps.clear();terrain(4,1);
            var leaves=new cn.nukkit.block.BlockLeaves();leaves.setPersistent(persistent);leaves.setComponents(0,75,0);leaves.level=level;
            world.put("0,75,0",leaves);
            var work=new SmoothOperation(player,new Position(-1,70,-1,level),new Position(1,76,1,level),
                    1,10,100,2,Set.of(1,2,3),queue::add);
            var result=work.start();finish();
            if(persistent){assertEquals(0,result.join());assertEquals(2,id(74));}
            else {assertTrue(result.join()>0);assertEquals(2,id(72));}
            assertSame(leaves,world.get("0,75,0"));
            assertTrue(writes.stream().noneMatch(v->v.y==75));
        }
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
    @Test void skipsColumnsWithLavaStructuresOrBlockEntities(){
        for(int unsafe:List.of(10,11,54)){
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
        assertThrows(IllegalArgumentException.class,()->new SmoothOperation(player,new Position(0,70,0,level),new Position(1,74,1,level),1,10,100,1,Set.of(1,9),queue::add));
        assertThrows(IllegalArgumentException.class,()->new SmoothOperation(player,new Position(0,70,0,level),new Position(1,74,1,level),1,10,100,1,Set.of(1),true,0,queue::add));
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
