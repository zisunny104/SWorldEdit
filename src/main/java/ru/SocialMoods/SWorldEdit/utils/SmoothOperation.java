package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Height-map smoothing. Capture and planning are tick-batched; placement uses the clipboard pipeline. */
public final class SmoothOperation implements Runnable {
    private static final int RADIUS=5;
    private static final double[] WEIGHTS=gaussian();
    private final PlayerData player;
    private final Level level;
    private final int x,y,z,width,height,length,area,volume,batch,iterations,limit;
    private final Set<Integer> terrain;
    private final Consumer<Runnable> schedule;
    private final Block[] blocks;
    private final int[] tops,bottoms,obstructions;
    private double[] heights, next, horizontal;
    private final BlocksArray changes = new BlocksArray();
    private final CompletableFuture<Integer> future = new CompletableFuture<>();
    private int read, pass, filtered, planned;
    private boolean vertical;

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
        bottoms=new int[area];obstructions=new int[area];Arrays.fill(obstructions,-1);
        heights=new double[area];Arrays.fill(heights,-1);next=new double[area];horizontal=new double[area];
    }

    private static double[] gaussian(){
        double[] weights=new double[RADIUS*2+1];double total=0;
        for(int i=-RADIUS;i<=RADIUS;i++){weights[i+RADIUS]=Math.exp(-i*i/2.0);total+=weights[i+RADIUS];}
        for(int i=0;i<weights.length;i++)weights[i]/=total;
        return weights;
    }

    private boolean usable(int column){return tops[column]>=0 && obstructions[column]<tops[column];}

    private double filteredHeight(int column){
        int cx=column%width,cz=column/width;
        double sum=0,weight=0;
        for(int delta=-RADIUS;delta<=RADIUS;delta++){
            int axis=(vertical?cz:cx)+delta;
            // Extend the current edge sample, as in FAWE's boundary sampling.
            int neighbor=axis<0 || axis>=(vertical?length:width)?column:
                (vertical?cx+axis*width:axis+cz*width);
            if(!usable(neighbor))continue;
            double w=WEIGHTS[delta+RADIUS];
            sum+=(vertical?horizontal[neighbor]:heights[neighbor])*w;weight+=w;
        }
        return sum/weight;
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
                boolean natural=terrain.contains(block.getId());
                if(level.getBlockEntity(position)!=null || (block.getId()!=0 && !natural))obstructions[column]=h;
                if(natural){
                    if(h==0 || !terrain.contains(blocks[index-area].getId()) || obstructions[column]>=h-1)bottoms[column]=h;
                    tops[column]=h;heights[column]=h;
                }
            }
            while(read==volume && pass<iterations && budget-->0) {
                int c=filtered++;
                double value=usable(c)?filteredHeight(c):heights[c];
                if(vertical)next[c]=usable(c)?Math.max(bottoms[c],Math.min(height-1,Math.round(value))):value;
                else horizontal[c]=value;
                if(filtered==area){
                    filtered=0;
                    if(vertical){double[] swap=heights;heights=next;next=swap;pass++;}
                    vertical=!vertical;
                }
            }
            while(pass==iterations && planned<volume && budget-->0) {
                int index=planned++, c=index%area, h=index/area;
                if(!usable(c) || h<bottoms[c])continue;
                int target=(int)Math.round(heights[c]), top=tops[c];
                if(target==top)continue;
                Block desired=blocks[index];
                if(h>target && h<=top)desired=new BlockAir();
                else if(h==target)desired=blocks[c+top*area];
                else if(h<target) {
                    // Resample only the continuous terrain below this surface; preserve its material layers.
                    int source=bottoms[c]+(int)((long)(h-bottoms[c])*(top-bottoms[c])/(target-bottoms[c]));
                    desired=blocks[c+source*area];
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
