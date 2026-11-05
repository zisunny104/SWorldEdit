package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.*;
import cn.nukkit.level.*;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Existing editing semantics, with bounded server-thread planning and disk-backed recovery. */
public final class WorldEdits {
    private final StreamingClipboard engine;
    private final int limit;
    public WorldEdits(StreamingClipboard engine,int limit){if(limit<1)throw new IllegalArgumentException("editing.max-blocks must be positive");this.engine=engine;this.limit=limit;}
    private long count(SelectionBounds bounds){
        if(bounds.volume().compareTo(java.math.BigInteger.valueOf(limit))>0)throw new IllegalArgumentException(Messages.text("editing.limit",bounds.volume(),limit));
        return bounds.volume().longValueExact();
    }
    private CompletableFuture<Integer> paint(PlayerData owner,Level level,SelectionBounds bounds,long scans,IntFunction<Vector3> points,Predicate<Vector3> filter,Block value){
        Block pattern=value.clone();
        return engine.edit(owner==null?new PlayerData(null):owner,level,bounds,scans,limit,new StreamingClipboard.Planner(){
            int state=-1;
            @Override public PagedClipboard.Cell cell(int index,PagedClipboard.Palette palette){
                Vector3 point=points.apply(index);if(!filter.test(point))return null;
                if(state<0)state=palette.capture(pattern);
                return new PagedClipboard.Cell(point.getFloorX(),point.getFloorY(),point.getFloorZ(),state,null,false);
            }
        },owner!=null);
    }
    public CompletableFuture<Integer> set(PlayerData owner,Position first,Position second,Block value){
        var bounds=SelectionBounds.of(first,second);
        return paint(owner,first.level,bounds,count(bounds),index->StreamingClipboard.scanPoint(bounds,index),point->true,value);
    }
    public CompletableFuture<Integer> replace(PlayerData owner,Position first,Position second,Block from,Block to){
        var bounds=SelectionBounds.of(first,second);Level level=first.level;
        int id=from==null?-1:from.getId(),meta=from==null?0:from.getDamage();
        return paint(owner,level,bounds,count(bounds),index->StreamingClipboard.scanPoint(bounds,index),point->{
            Block actual=level.getBlock(point);
            return actual instanceof BlockLightBlock || id<0 || actual.getId()==id && actual.getDamage()==meta;
        },to);
    }
    public CompletableFuture<Integer> walls(PlayerData owner,Position first,Position second,Block value){
        var bounds=SelectionBounds.of(first,second);long w=bounds.width(),l=bounds.length();
        long perimeter=w==1?l:l==1?w:2*w+2*l-4;
        long scans=Math.multiplyExact(perimeter,bounds.height());
        return paint(owner,first.level,bounds,scans,index->{
            long offset=index%perimeter;int y=Math.toIntExact(bounds.minY()+index/perimeter),x,z;
            if(w==1){x=bounds.minX();z=Math.toIntExact(bounds.minZ()+offset);}
            else if(l==1){x=Math.toIntExact(bounds.minX()+offset);z=bounds.minZ();}
            else if(offset<w){x=Math.toIntExact(bounds.minX()+offset);z=bounds.minZ();}
            else if((offset-=w)<w){x=Math.toIntExact(bounds.minX()+offset);z=bounds.maxZ();}
            else if((offset-=w)<l-2){x=bounds.minX();z=Math.toIntExact(bounds.minZ()+1+offset);}
            else{x=bounds.maxX();z=Math.toIntExact(bounds.minZ()+1+offset-(l-2));}
            return new Vector3(x,y,z);
        },point->true,value);
    }
    public CompletableFuture<Integer> geometry(PlayerData owner,Position center,int radius,Block value,boolean sphere,boolean hollow){
        if(radius<1)throw new IllegalArgumentException("Radius must be positive");
        int x=center.getFloorX(),y=center.getFloorY(),z=center.getFloorZ();
        var bounds=new SelectionBounds(Math.subtractExact(x,radius),sphere?Math.subtractExact(y,radius):y,Math.subtractExact(z,radius),
                Math.addExact(x,radius),sphere?Math.addExact(y,radius):y,Math.addExact(z,radius));
        long outer=(long)radius*radius,inner=(long)(radius-1)*(radius-1);
        return paint(owner,center.level,bounds,count(bounds),index->StreamingClipboard.scanPoint(bounds,index),point->{
            long dx=(long)point.getFloorX()-x,dy=(long)point.getFloorY()-y,dz=(long)point.getFloorZ()-z;
            long squared=dx*dx+dy*dy+dz*dz;
            return squared<=outer && (!hollow || squared>inner);
        },value);
    }
    public CompletableFuture<Integer> rotate(PlayerData owner,Position center,Selection selection,int degrees){
        var bounds=SelectionBounds.of(selection.getPosOne(),selection.getPosTwo());
        if(center.level!=selection.getPosOne().level)throw new IllegalArgumentException("Rotate center and selection must share a world");
        if(degrees<0 || degrees>360)throw new IllegalArgumentException("Rotation must be between 0 and 360 degrees");
        return engine.rotate(owner,center.level,bounds,center.getFloorX(),center.getFloorZ(),degrees%360,limit);
    }
}
