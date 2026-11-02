package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.level.Position;
import java.math.BigInteger;

/** Inclusive cuboid bounds; all calculations finish before either selection point is changed. */
public record SelectionBounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public SelectionBounds {
        if(minX>maxX || minY>maxY || minZ>maxZ)throw new IllegalArgumentException("Selection would be empty or inverted");
    }
    public static SelectionBounds of(Position a,Position b){
        if(a==null || b==null || a.level==null || a.level!=b.level)throw new IllegalArgumentException("Select two positions in the same world");
        return new SelectionBounds(Math.min(a.getFloorX(),b.getFloorX()),Math.min(a.getFloorY(),b.getFloorY()),Math.min(a.getFloorZ(),b.getFloorZ()),
            Math.max(a.getFloorX(),b.getFloorX()),Math.max(a.getFloorY(),b.getFloorY()),Math.max(a.getFloorZ(),b.getFloorZ()));
    }
    public long width(){return (long)maxX-minX+1;}
    public long height(){return (long)maxY-minY+1;}
    public long length(){return (long)maxZ-minZ+1;}
    public BigInteger volume(){return BigInteger.valueOf(width()).multiply(BigInteger.valueOf(height())).multiply(BigInteger.valueOf(length()));}
    private static SelectionBounds create(long[] min,long[] max){return new SelectionBounds(Math.toIntExact(min[0]),Math.toIntExact(min[1]),Math.toIntExact(min[2]),Math.toIntExact(max[0]),Math.toIntExact(max[1]),Math.toIntExact(max[2]));}
    public SelectionBounds directional(String mode,int amount,int reverse,int[] direction){
        if(direction==null || direction.length!=3)throw new IllegalArgumentException("Use one axis direction");
        for(int d:direction)if(d < -1 || d>1)throw new IllegalArgumentException("Use one axis direction");
        if(amount<0 || reverse<0 || Math.abs(direction[0])+Math.abs(direction[1])+Math.abs(direction[2])!=1)
            throw new IllegalArgumentException("Use non-negative amounts and one axis direction");
        long[] min={minX,minY,minZ},max={maxX,maxY,maxZ};
        for(int i=0;i<3;i++){
            int d=direction[i];if(d==0)continue;
            switch(mode){
                case "expand" -> {min[i]-=d>0?reverse:amount;max[i]+=d>0?amount:reverse;}
                // FAWE contracts the trailing face towards the specified direction.
                case "contract" -> {min[i]+=d>0?amount:reverse;max[i]-=d>0?reverse:amount;}
                case "shift" -> {if(reverse!=0)throw new IllegalArgumentException("Shift has no reverse amount");min[i]+=(long)d*amount;max[i]+=(long)d*amount;}
                default -> throw new IllegalArgumentException("Unknown selection operation");
            }
        }
        return create(min,max);
    }
    public SelectionBounds symmetric(int amount,boolean inset,boolean horizontal,boolean vertical){
        if(amount<0 || (!horizontal&&!vertical))throw new IllegalArgumentException("Invalid selection amount or axes");
        long delta=inset?-(long)amount:amount;
        return create(new long[]{minX-(horizontal?delta:0),minY-(vertical?delta:0),minZ-(horizontal?delta:0)},
            new long[]{maxX+(horizontal?delta:0),maxY+(vertical?delta:0),maxZ+(horizontal?delta:0)});
    }
    public SelectionBounds vertical(int worldMin,int worldMax){return new SelectionBounds(minX,worldMin,minZ,maxX,worldMax,maxZ);}
    public void apply(Selection selection){
        Position a=selection.getPosOne(),b=selection.getPosTwo();
        of(a,b);
        if(minY<a.level.getMinBlockY() || maxY>a.level.getMaxBlockY())throw new IllegalArgumentException("Selection exceeds world height");
        Position nextA=new Position(a.getFloorX()<=b.getFloorX()?minX:maxX,a.getFloorY()<=b.getFloorY()?minY:maxY,a.getFloorZ()<=b.getFloorZ()?minZ:maxZ,a.level);
        Position nextB=new Position(a.getFloorX()<=b.getFloorX()?maxX:minX,a.getFloorY()<=b.getFloorY()?maxY:minY,a.getFloorZ()<=b.getFloorZ()?maxZ:minZ,a.level);
        selection.setPosOne(nextA);selection.setPosTwo(nextB);
    }
}
