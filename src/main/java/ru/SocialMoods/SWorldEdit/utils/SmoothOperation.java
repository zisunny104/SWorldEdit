package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Height-map smoothing. Capture and planning are tick-batched; placement uses the clipboard pipeline. */
public final class SmoothOperation implements Runnable {
    private static final int RADIUS=5;
    private static final double[] WEIGHTS=gaussian();
    private static final Set<Integer> SURFACE_COVER=Set.of(31,32,37,38,78,175);
    private final PlayerData player;
    private final Level level;
    private final int x,y,z,width,height,terrainHeight,length,area,volume,batch,iterations,limit;
    private final boolean underwater;
    private final long tickNanos;
    private final Set<Integer> terrain;
    private final Consumer<Runnable> schedule;
    private final Block[] blocks;
    private final int[] tops,bottoms,obstructions,covers,ceilings;
    private final int[] waterTops,waterFill,waterSources,waterQueue;
    private double[] heights, next, horizontal;
    private final BlocksArray changes = new BlocksArray();
    private final CompletableFuture<Integer> future = new CompletableFuture<>();
    private int read, analyzed, pass, filtered, waterSeeded,waterRead,waterWritten,planned;
    private boolean vertical;

    public SmoothOperation(PlayerData player, Position first, Position second, int iterations,
            int maxIterations, int limit, int batch, Set<Integer> terrain, Consumer<Runnable> schedule) {
        this(player,first,second,iterations,maxIterations,limit,batch,terrain,true,3,schedule);
    }

    public SmoothOperation(PlayerData player, Position first, Position second, int iterations,
            int maxIterations, int limit, int batch, Set<Integer> terrain, boolean underwater,int tickMillis,Consumer<Runnable> schedule) {
        if (first.level == null || first.level != second.level) throw new IllegalArgumentException("Select positions in the same world");
        if (iterations < 1 || iterations > maxIterations)
            throw new IllegalArgumentException(Messages.text("smooth.invalid-iterations",iterations,maxIterations));
        if (batch < 1 || limit < 1)
            throw new IllegalArgumentException(Messages.text("smooth.invalid-budget",limit,batch));
        if(tickMillis<1)throw new IllegalArgumentException(Messages.text("smooth.invalid-time-budget",tickMillis));
        if (terrain.isEmpty() || terrain.contains(0))
            throw new IllegalArgumentException(Messages.text("smooth.invalid-terrain"));
        if(!Collections.disjoint(terrain,Set.of(8,9,10,11)))
            throw new IllegalArgumentException(Messages.text("smooth.invalid-liquid-terrain"));
        this.player=player;this.level=first.level;this.iterations=iterations;this.limit=limit;this.batch=batch;
        this.terrain=Set.copyOf(terrain);this.schedule=schedule;
        this.underwater=underwater;this.tickNanos=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(tickMillis);
        x=Math.min(first.getFloorX(),second.getFloorX());y=Math.min(first.getFloorY(),second.getFloorY());z=Math.min(first.getFloorZ(),second.getFloorZ());
        width=Math.toIntExact((long)Math.max(first.getFloorX(),second.getFloorX())-x+1);
        terrainHeight=Math.toIntExact((long)Math.max(first.getFloorY(),second.getFloorY())-y+1);
        length=Math.toIntExact((long)Math.max(first.getFloorZ(),second.getFloorZ())-z+1);
        area=Math.multiplyExact(width,length);
        long selectedVolume=(long)area*terrainHeight;
        if(selectedVolume>limit) throw new IllegalArgumentException(Messages.text("smooth.selection-limit",selectedVolume,limit));
        if(y<level.getMinBlockY() || (long)y+terrainHeight-1>level.getMaxBlockY())
            throw new IllegalArgumentException(Messages.text("smooth.world-height",level.getMinBlockY(),level.getMaxBlockY()));
        // Two extra capture layers let a boundary-cut plant move as one unit. Terrain stays inside the selection.
        height=Math.toIntExact(Math.min((long)terrainHeight+2,(long)level.getMaxBlockY()-y+1));
        volume=Math.multiplyExact(area,height);
        blocks=new Block[volume];tops=new int[area];Arrays.fill(tops,-1);
        bottoms=new int[area];obstructions=new int[area];Arrays.fill(obstructions,-1);
        covers=new int[area];
        ceilings=new int[area];Arrays.fill(ceilings,height-1);
        waterTops=new int[area];Arrays.fill(waterTops,-1);
        waterFill=new int[area];Arrays.fill(waterFill,-1);
        waterSources=new int[area];Arrays.fill(waterSources,-1);waterQueue=new int[area];
        heights=new double[area];Arrays.fill(heights,-1);next=new double[area];horizontal=new double[area];
    }

    private static double[] gaussian(){
        double[] weights=new double[RADIUS*2+1];double total=0;
        for(int i=-RADIUS;i<=RADIUS;i++){weights[i+RADIUS]=Math.exp(-i*i/2.0);total+=weights[i+RADIUS];}
        for(int i=0;i<weights.length;i++)weights[i]/=total;
        return weights;
    }

    private boolean usable(int column){return tops[column]>=0 && obstructions[column]<tops[column];}

    private static boolean water(Block block){return block.getId()==8 || block.getId()==9;}

    /** Finish metadata for every column before any neighbor participates in the height filter. */
    private void analyze(int c){
        int top=tops[c];if(top<0)return;
        for(int h=top+1;h<height && surfaceCover(blocks[c+h*area]);h++)covers[c]++;
        if(covers[c]>0){
            Block firstCover=blocks[c+(top+1)*area],lastCover=blocks[c+(top+covers[c])*area];
            if(firstCover.getId()==175 && (firstCover.getDamage()&8)!=0 || lastCover.getId()==175 && (lastCover.getDamage()&8)==0)
                obstructions[c]=Math.max(obstructions[c],top);
        }
        for(int h=top+1;h<height;h++)if(naturalLeaves(blocks[c+h*area])){ceilings[c]=h-1;break;}
        if(!underwater)return;
        int end=top;
        for(int h=top+1;h<height;h++){
            Block block=blocks[c+h*area];
            if(!water(block))continue;
            // A hanging liquid sheet, waterfall or non-source flow has no reliable level to preserve.
            if(h!=end+1 || block.getDamage()!=0){obstructions[c]=Math.max(obstructions[c],h);return;}
            end=h;
        }
        if(end>top)waterTops[c]=end;
    }

    private void seedWater(int c){
        if(waterTops[c]<0)return;
        waterFill[c]=waterTops[c];waterSources[c]=c;
        if(heights[c]<waterTops[c])waterQueue[waterWritten++]=c;
    }

    private void spreadWater(int from,int to){
        int level=waterFill[from];
        if(waterTops[to]>=0){
            int common=Math.min(level,waterTops[to]);
            if(level!=waterTops[to] && (waterTops[from]<0 || heights[from]<common && heights[to]<common && (tops[from]>=common || tops[to]>=common)))
                throw new IllegalArgumentException(Messages.text("smooth.water-level-conflict",y+level,y+waterTops[to]));
            return;
        }
        if(!usable(to) || heights[to]>=level || heights[to]>=tops[to] || tops[to]<level)return;
        if(waterFill[to]>=0){
            if(waterFill[to]!=level)throw new IllegalArgumentException(Messages.text("smooth.water-level-conflict",y+waterFill[to],y+level));
            return;
        }
        if(level>ceilings[to])throw new IllegalArgumentException(Messages.text("smooth.water-protected",x+to%width,z+to/width));
        waterFill[to]=level;waterSources[to]=waterSources[from];waterQueue[waterWritten++]=to;
    }

    private void floodWater(int c){
        int cx=c%width,cz=c/width;
        if(cx>0)spreadWater(c,c-1);if(cx+1<width)spreadWater(c,c+1);
        if(cz>0)spreadWater(c,c-width);if(cz+1<length)spreadWater(c,c+width);
    }

    private Block fillWater(int c,Block original){
        if(water(original))return original;
        int source=waterSources[c];
        return blocks[source+waterTops[source]*area];
    }

    private static boolean surfaceCover(Block block) {
        if(SURFACE_COVER.contains(block.getId()))return true;
        for(Class<?> c=block.getClass();c!=null;c=c.getSuperclass())
            if(Set.of("BlockFlower","BlockTallGrass","BlockDoublePlant","BlockDeadBush","BlockSnowLayer").contains(c.getSimpleName()))return true;
        return false;
    }

    private static boolean naturalLeaves(Block block) {
        boolean leaves=block.getId()==18 || block.getId()==161;
        for(Class<?> c=block.getClass();c!=null&&!leaves;c=c.getSuperclass())leaves=c.getSimpleName().contains("Leaves");
        if(!leaves)return false;
        // Use the running core's semantics instead of assuming a persistence metadata bit.
        try{return Boolean.FALSE.equals(block.getClass().getMethod("isPersistent").invoke(block));}
        catch(ReflectiveOperationException e){return false;}
    }

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
            long deadline=System.nanoTime()+tickNanos;
            while(read<volume && budget-->0 && System.nanoTime()<deadline) {
                int index=read++, column=index%area, h=index/area;
                Vector3 position=position(index);
                Block block=level.getBlock(position).clone();blocks[index]=block;
                boolean natural=h<terrainHeight && terrain.contains(block.getId());
                if(level.getBlockEntity(position)!=null || (block.getId()!=0 && !natural && !surfaceCover(block) && !naturalLeaves(block) && !(underwater&&water(block))))obstructions[column]=h;
                if(natural){
                    if(h==0 || !terrain.contains(blocks[index-area].getId()) || obstructions[column]>=h-1)bottoms[column]=h;
                    tops[column]=h;heights[column]=h;
                }
            }
            while(read==volume && analyzed<area && budget-->0 && System.nanoTime()<deadline)analyze(analyzed++);
            while(analyzed==area && pass<iterations && budget-->0 && System.nanoTime()<deadline) {
                int c=filtered++;
                double value=usable(c)?filteredHeight(c):heights[c];
                if(vertical)next[c]=usable(c)?Math.max(bottoms[c],Math.min(Math.min(terrainHeight-1,ceilings[c]-covers[c]),Math.round(value))):value;
                else horizontal[c]=value;
                if(filtered==area){
                    filtered=0;
                    if(vertical){double[] swap=heights;heights=next;next=swap;pass++;}
                    vertical=!vertical;
                }
            }
            while(pass==iterations && waterSeeded<area && budget-->0 && System.nanoTime()<deadline)seedWater(waterSeeded++);
            while(waterSeeded==area && waterRead<waterWritten && budget-->0 && System.nanoTime()<deadline)floodWater(waterQueue[waterRead++]);
            while(waterSeeded==area && waterRead==waterWritten && planned<volume && budget-->0 && System.nanoTime()<deadline) {
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
                if(waterFill[c]>target){
                    // Only restore water inside the chosen terrain range; existing water above it is untouched.
                    if(h>target && h<=waterFill[c] && h<terrainHeight)desired=fillWater(c,original);
                    // Land plants/snow do not get transplanted underwater. Their removal is included in Undo.
                    else if(h>top && h<=top+covers[c])desired=new BlockAir();
                }else{
                    // Move the existing surface cover with its ground, including snow thickness and plant metadata.
                    if(h>target && h<=target+covers[c])desired=blocks[c+(top+h-target)*area];
                    else if(h>target && h>top && h<=top+covers[c])desired=new BlockAir();
                }
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
