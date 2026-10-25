package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Height-map smoothing. Capture and planning are tick-batched; placement uses the clipboard pipeline. */
public final class SmoothOperation implements Runnable {
    private final PlayerData player;
    private final Level level;
    private final int x,y,z,width,height,length,area,volume,batch,iterations,limit;
    private final Set<Integer> terrain;
    private final Consumer<Runnable> schedule;
    private final Block[] blocks;
    private final int[] tops;
    private final boolean[] locked;
    private double[] heights, next;
    private final BlocksArray changes = new BlocksArray();
    private final CompletableFuture<Integer> future = new CompletableFuture<>();
    private int read, pass, filtered, planned;

    public SmoothOperation(PlayerData player, Position first, Position second, int iterations,
            int maxIterations, int limit, int batch, Set<Integer> terrain, Consumer<Runnable> schedule) {
        if (first.level == null || first.level != second.level) throw new IllegalArgumentException("Select positions in the same world");
        if (iterations < 1 || iterations > maxIterations || batch < 1 || limit < 1 || terrain.isEmpty() || terrain.contains(0))
            throw new IllegalArgumentException("Invalid smoothing iterations, limits or terrain IDs");
        this.player=player;this.level=first.level;this.iterations=iterations;this.limit=limit;this.batch=batch;
        this.terrain=Set.copyOf(terrain);this.schedule=schedule;
        x=Math.min(first.getFloorX(),second.getFloorX());y=Math.min(first.getFloorY(),second.getFloorY());z=Math.min(first.getFloorZ(),second.getFloorZ());
        width=Math.toIntExact((long)Math.max(first.getFloorX(),second.getFloorX())-x+1);
        height=Math.toIntExact((long)Math.max(first.getFloorY(),second.getFloorY())-y+1);
        length=Math.toIntExact((long)Math.max(first.getFloorZ(),second.getFloorZ())-z+1);
        area=Math.multiplyExact(width,length);volume=Math.multiplyExact(area,height);
        if(volume>limit || y<level.getMinBlockY() || (long)y+height-1>level.getMaxBlockY())
            throw new IllegalArgumentException("Selection exceeds smoothing limits or world height");
        blocks=new Block[volume];tops=new int[area];Arrays.fill(tops,-1);
        locked=new boolean[area];heights=new double[area];Arrays.fill(heights,-1);next=new double[area];
    }

    public CompletableFuture<Integer> start() {
        try { schedule.accept(this); } catch(RuntimeException|LinkageError e) { future.completeExceptionally(e); }
        return future;
    }

    private Vector3 position(int index) { return new Vector3(x+index%width,y+index/area,z+index/width%length); }

    @Override public void run() {
        if(future.isDone())return;
        try {
            int budget=batch;
            while(read<volume && budget-->0) {
                int index=read++, column=index%area, h=index/area;
                Vector3 position=position(index);
                Block block=level.getBlock(position).clone();blocks[index]=block;
                if(level.getBlockEntity(position)!=null || (block.getId()!=0 && !terrain.contains(block.getId())))locked[column]=true;
                if(terrain.contains(block.getId())){tops[column]=h;heights[column]=h;}
            }
            while(read==volume && pass<iterations && budget-->0) {
                int c=filtered++, cx=c%width, cz=c/width;
                if(locked[c] || tops[c]<0 || cx==0 || cz==0 || cx==width-1 || cz==length-1)next[c]=heights[c];
                else {
                    double sum=0,weight=0;
                    for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
                        int neighbor=c+dx+dz*width;
                        if(locked[neighbor] || tops[neighbor]<0)continue;
                        int w=(dx==0?2:1)*(dz==0?2:1);
                        sum+=heights[neighbor]*w;weight+=w;
                    }
                    next[c]=sum/weight;
                }
                if(filtered==area){double[] swap=heights;heights=next;next=swap;filtered=0;pass++;}
            }
            while(pass==iterations && planned<volume && budget-->0) {
                int index=planned++, c=index%area, h=index/area;
                if(locked[c] || tops[c]<0)continue;
                int target=(int)Math.round(heights[c]), top=tops[c];
                if(target==top)continue;
                Block desired=blocks[index];
                if(h>target && h<=top)desired=Block.get(0);
                else if(h==target)desired=blocks[c+top*area];
                else if(target>top && h>=top && h<target) {
                    // Preserve the surface material, using the old subsurface to fill raised ground.
                    desired=top>0 && terrain.contains(blocks[c+(top-1)*area].getId()) ? blocks[c+(top-1)*area] : blocks[c+top*area];
                    if(desired.getId()==2 || desired.getId()==110)desired=Block.get(3);
                }
                Block original=blocks[index];
                if(desired.getId()==original.getId() && desired.getDamage()==original.getDamage())continue;
                Block detached=desired.clone();detached.setComponents(index%width,h,index/width%length);
                changes.blocks.add(detached);
            }
            if(planned==volume) {
                if(changes.blocks.isEmpty()){future.complete(0);return;}
                ClipboardOperation.paste(player,new Position(x,y,z,level),changes,false,limit,batch,schedule)
                    .whenComplete((count,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(count);});
            } else schedule.accept(this);
        } catch(RuntimeException|LinkageError e){future.completeExceptionally(e);}
    }
}
