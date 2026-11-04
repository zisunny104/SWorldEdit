package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Plans green/naturalize on the server thread in bounded batches, without touching the user's clipboard. */
public final class SurfaceOperation implements Runnable {
    private final PlayerData player;
    private final Level level;
    private final int minX,minY,minZ,width,length,maxY,columns,limit,batch;
    private final boolean green,coarse;
    private final double radiusSquared;
    private final int centerX,centerZ;
    private final Consumer<Runnable> schedule;
    private final BlocksArray changes=new BlocksArray();
    private final CompletableFuture<Integer> result=new CompletableFuture<>();
    private int column,y,groundY;
    private boolean initialized,foundGround;

    private SurfaceOperation(PlayerData player,Level level,int minX,int minY,int minZ,int width,int length,int maxY,
            int limit,int maxChecks,int batch,boolean green,boolean coarse,double radius,int centerX,int centerZ,Consumer<Runnable> schedule){
        if(level==null || limit<1 || maxChecks<1 || batch<1 || width<1 || length<1 || minY>maxY)
            throw new IllegalArgumentException("Invalid surface operation limits or bounds");
        this.columns=Math.multiplyExact(width,length);
        long checks=(long)columns*((long)maxY-minY+1+(green || maxY==level.getMaxBlockY()?0:1));
        if(checks>maxChecks || minY<level.getMinBlockY() || maxY>level.getMaxBlockY())
            throw new IllegalArgumentException("Surface scan exceeds configured checks or world height");
        this.player=player;this.level=level;this.minX=minX;this.minY=minY;this.minZ=minZ;this.width=width;this.length=length;this.maxY=maxY;
        this.limit=limit;this.batch=batch;this.green=green;this.coarse=coarse;this.radiusSquared=radius*radius;
        this.centerX=centerX;this.centerZ=centerZ;this.schedule=schedule;
    }

    public static SurfaceOperation green(PlayerData player,Position center,double radius,int height,boolean coarse,
            int maxRadius,int limit,int maxChecks,int batch,Consumer<Runnable> schedule){
        if(center.level==null || !Double.isFinite(radius) || radius<1 || radius>maxRadius || height<1)
            throw new IllegalArgumentException("Invalid green radius or height");
        int extent=(int)Math.ceil(radius),cx=center.getFloorX(),cz=center.getFloorZ();
        int minX=Math.toIntExact((long)cx-extent),minZ=Math.toIntExact((long)cz-extent);
        Math.toIntExact((long)cx+extent);Math.toIntExact((long)cz+extent);
        int size=Math.addExact(Math.multiplyExact(extent,2),1);
        Level level=center.level;
        int minY=(int)Math.max(level.getMinBlockY(),(long)center.getFloorY()-height);
        int maxY=(int)Math.min(level.getMaxBlockY(),(long)center.getFloorY()+height);
        return new SurfaceOperation(player,level,minX,minY,minZ,size,size,maxY,limit,maxChecks,batch,true,coarse,radius,cx,cz,schedule);
    }

    public static SurfaceOperation naturalize(PlayerData player,Position first,Position second,int limit,int maxChecks,int batch,Consumer<Runnable> schedule){
        if(first.level==null || first.level!=second.level)throw new IllegalArgumentException("Select positions in the same world");
        int minX=Math.min(first.getFloorX(),second.getFloorX()),minY=Math.min(first.getFloorY(),second.getFloorY()),minZ=Math.min(first.getFloorZ(),second.getFloorZ());
        int width=Math.toIntExact((long)Math.max(first.getFloorX(),second.getFloorX())-minX+1);
        int length=Math.toIntExact((long)Math.max(first.getFloorZ(),second.getFloorZ())-minZ+1);
        return new SurfaceOperation(player,first.level,minX,minY,minZ,width,length,Math.max(first.getFloorY(),second.getFloorY()),limit,maxChecks,batch,false,false,0,0,0,schedule);
    }

    public CompletableFuture<Integer> start(){
        try{schedule.accept(this);}catch(RuntimeException|LinkageError e){result.completeExceptionally(e);}
        return result;
    }

    private static boolean ground(Block block){return block.getDamage()==0 && (block.getId()==1 || block.getId()==2 || block.getId()==3);}
    private void nextColumn(){column++;initialized=false;foundGround=false;}
    private void add(Vector3 position,Block before,Block after){
        if(before.getId()==after.getId() && before.getDamage()==after.getDamage())return;
        if(changes.blocks.size()>=limit)throw new IllegalArgumentException("Surface changes exceed configured block limit");
        after.setComponents(position.x-minX,position.y-minY,position.z-minZ);changes.blocks.add(after);
    }

    @Override public void run(){
        if(result.isDone())return;
        try{
            int budget=batch;
            long deadline=TickBudget.deadline("surface.tick-millis");
            player.getWorkProgress().report("work.phase.surface",column,columns);
            while(column<columns && budget-->0 && System.nanoTime()<deadline){
                int x=minX+column%width,z=minZ+column/width;
                if(!initialized){
                    initialized=true;y=maxY;
                    if(green){
                        double dx=(long)x-centerX,dz=(long)z-centerZ;
                        if(dx*dx+dz*dz>radiusSquared){nextColumn();continue;}
                    }else if(maxY<level.getMaxBlockY()){
                        // Do not naturalize a cross-section whose terrain continues above the selection.
                        if(ground(level.getBlock(new Vector3(x,maxY+1,z))))nextColumn();
                        continue;
                    }
                }
                Vector3 position=new Vector3(x,y,z);
                Block block=level.getBlock(position);
                if(green){
                    if(level.getBlockEntity(position)!=null){nextColumn();continue;}
                    if(block.getId()==3 && (block.getDamage()==0 || (coarse && block.getDamage()==1))){
                        add(position,block,new BlockGrass());nextColumn();continue;
                    }
                    int id=block.getId();
                    if((id>=8 && id<=11) || !block.canPassThrough()){nextColumn();continue;}
                }else if(ground(block)){
                    if(!foundGround){foundGround=true;groundY=y;}
                    if(level.getBlockEntity(position)==null){
                        int depth=groundY-y;
                        add(position,block,depth==0?new BlockGrass():depth<=3?new BlockDirt():new BlockStone());
                    }
                }
                if(y==minY)nextColumn();else y--;
            }
            if(column==columns){
                if(changes.blocks.isEmpty()){result.complete(0);return;}
                ClipboardOperation.paste(player,new Position(minX,minY,minZ,level),changes,false,limit,batch,schedule)
                    .whenComplete((count,error)->{if(error==null)result.complete(count);else result.completeExceptionally(error);});
            }else schedule.accept(this);
        }catch(RuntimeException|LinkageError e){result.completeExceptionally(e);}
    }
}
