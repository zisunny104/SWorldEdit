package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.IntFunction;

/** Pair only mutually linked source halves; never infer pairs from destination adjacency. */
final class ChestPairs {
    record Point(int x, int y, int z) {
        static Point of(CompoundTag tag) { return new Point(tag.getInt("x"),tag.getInt("y"),tag.getInt("z")); }
        Vector3 vector() { return new Vector3(x,y,z); }
    }
    record Link(Vector3 first, Vector3 second) {
        Link shifted(long dx, long dy, long dz) {
            return new Link(shift(first,dx,dy,dz),shift(second,dx,dy,dz));
        }
        private static Vector3 shift(Vector3 v,long dx,long dy,long dz) {
            return new Vector3(Math.toIntExact(v.getFloorX()+dx),Math.toIntExact(v.getFloorY()+dy),Math.toIntExact(v.getFloorZ()+dz));
        }
    }
    private record Half(CompoundTag tag, Vector3 target) { }
    private ChestPairs() { }
    private static boolean chest(CompoundTag tag) {
        return tag != null && tag.getString("id").replace("minecraft:","").equalsIgnoreCase("Chest");
    }
    private static Point peer(CompoundTag tag) {
        if (!chest(tag) || !tag.contains("pairx") || !tag.contains("pairz")) return null;
        Point own=Point.of(tag);
        Point other=new Point(tag.getInt("pairx"),own.y,tag.getInt("pairz"));
        return Math.abs((long)own.x-other.x)+Math.abs((long)own.z-other.z)==1 ? other:null;
    }
    static void rememberPeer(Level level, CompoundTag tag, Map<Point,CompoundTag> external) {
        if (tag == null) return;
        Point other=peer(tag);
        if (other == null || external.containsKey(other)) return;
        CompoundTag otherTag=BlockEntities.capture(level,other.vector());
        if (otherTag != null && Point.of(tag).equals(peer(otherTag))) external.put(other,otherTag);
    }
    static List<Link> plan(Map<Integer,CompoundTag> tags, IntFunction<Vector3> target,
                           Map<Point,CompoundTag> external) {
        Map<Point,Half> halves=new HashMap<>();
        tags.forEach((index,tag) -> { if(chest(tag)) halves.put(Point.of(tag),new Half(tag,target.apply(index))); });
        external.forEach((point,tag) -> halves.putIfAbsent(point,new Half(tag,point.vector())));
        List<Link> links=new ArrayList<>();Set<Point> used=new HashSet<>();
        // Only links referenced by an edited half are eligible; external-only pairs are untouched.
        tags.forEach((index,tag) -> {
            Point other=peer(tag);
            if(other==null) return;
            Point own=Point.of(tag);Half pair=halves.get(other);
            if(pair==null || !own.equals(peer(pair.tag)) || used.contains(own) || used.contains(other)) return;
            Vector3 a=target.apply(index),b=pair.target;
            if(a.getFloorY()!=b.getFloorY() || Math.abs((long)a.getFloorX()-b.getFloorX())+
                    Math.abs((long)a.getFloorZ()-b.getFloorZ())!=1) return;
            used.add(own);used.add(other);links.add(new Link(a,b));
        });
        return links;
    }
    static void apply(Level level, Link link) {
        BlockEntity first=level.getBlockEntity(link.first),second=level.getBlockEntity(link.second);
        if(first==null || second==null || first.closed || second.closed ||
                !first.isBlockEntityValid() || !second.isBlockEntityValid())
            throw new IllegalStateException("Cannot restore double chest: a destination half is unavailable");
        try {
            if(first.getClass().getMethod("getPair").invoke(first)!=second ||
                    second.getClass().getMethod("getPair").invoke(second)!=first) {
                Method pairWith=Arrays.stream(first.getClass().getMethods())
                        .filter(m -> m.getName().equals("pairWith") && m.getParameterCount()==1 &&
                                m.getParameterTypes()[0].isInstance(second)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Core chest pairing API is unavailable"));
                if(!Boolean.TRUE.equals(pairWith.invoke(first,second)))
                    throw new IllegalStateException("Core rejected double chest pairing");
            }
            if(first.getClass().getMethod("getPair").invoke(first)!=second ||
                    second.getClass().getMethod("getPair").invoke(second)!=first)
                throw new IllegalStateException("Core did not establish a mutual double chest pair");
            first.setDirty();second.setDirty();
            if(first instanceof BlockEntitySpawnable) ((BlockEntitySpawnable)first).spawnToAll();
            if(second instanceof BlockEntitySpawnable) ((BlockEntitySpawnable)second).spawnToAll();
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot restore double chest pairing",e);
        }
    }
}
