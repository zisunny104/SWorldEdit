package ru.SocialMoods.SWorldEdit.preview;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.BlockEntityDataPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.plugin.PluginBase;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;

/** Embedded adaptation of zisunny104/BoundingBoxAPI (ec56e27). No world writes or external plugin required. */
public final class BoundingBoxes implements AutoCloseable {
    public interface Tasks { int later(Runnable task,int ticks); void cancel(int id); }
    private static final class Box {
        final Level level; final int x,y,z; int task=-1;
        Box(Level level,int x,int y,int z) { this.level=level;this.x=x;this.y=y;this.z=z; }
        Vector3 position() { return new Vector3(x,y,z); }
    }
    private final Tasks tasks;
    private final Map<Player,LinkedHashMap<String,Box>> shown=new HashMap<>();
    private boolean closed;
    public BoundingBoxes(PluginBase plugin) {
        this(new Tasks() {
            public int later(Runnable task,int ticks) { return plugin.getServer().getScheduler().scheduleDelayedTask(plugin,task,ticks).getTaskId(); }
            public void cancel(int id) { plugin.getServer().getScheduler().cancelTask(id); }
        });
    }
    public BoundingBoxes(Tasks tasks) { this.tasks=Objects.requireNonNull(tasks); }

    /** Both corners are inclusive. Zero seconds means until explicitly cleared. Main thread only. */
    public void showBox(Player player,Level level,int x1,int y1,int z1,int x2,int y2,int z2,String tag,int seconds) {
        if(closed || player==null || !player.isOnline() || level==null || player.getLevel()!=level) return;
        int minX=Math.min(x1,x2),minY=Math.min(y1,y2),minZ=Math.min(z1,z2);
        int sx=Math.toIntExact(Math.abs((long)x1-x2)+1),sy=Math.toIntExact(Math.abs((long)y1-y2)+1),sz=Math.toIntExact(Math.abs((long)z1-z2)+1);
        clearBox(player,tag);
        var own=shown.computeIfAbsent(player,p->new LinkedHashMap<>());
        while(own.size()>=2) clearBox(player,own.keySet().iterator().next());
        // Keep a different layer from standalone BoundingBoxAPI, which reserves the lowest layer.
        int by=Math.addExact(level.getMinBlockY(),1),bx=player.getFloorX(),bz=player.getFloorZ();
        if(by>level.getMaxBlockY()) return;
        for(Box box:own.values()) if(box.x==bx && box.y==by && box.z==bz) { bx=Math.addExact(bx,1); break; }
        CompoundTag data=compound(bx,by,bz,Math.toIntExact((long)minX-bx),Math.toIntExact((long)minY-by),Math.toIntExact((long)minZ-bz),sx,sy,sz);
        BlockEntityDataPacket packet=new BlockEntityDataPacket();packet.x=bx;packet.y=by;packet.z=bz;
        try { packet.namedTag=NBTIO.write(data,ByteOrder.LITTLE_ENDIAN,true); }
        catch(IOException error) { throw new IllegalStateException("Cannot encode selection preview",error); }
        Box box=new Box(level,bx,by,bz);own.put(tag,box);
        try {
            send(player,level,box.position(),Block.get(BlockID.STRUCTURE_BLOCK));
            player.dataPacket(packet);
            if(seconds>0) box.task=tasks.later(()->{
                var current=shown.get(player);
                if(current!=null && current.get(tag)==box) { box.task=-1;clearBox(player,tag); }
            },(int)Math.min(Integer.MAX_VALUE,(long)seconds*20));
        } catch(RuntimeException | LinkageError error) { clearBox(player,tag);throw error; }
    }
    static CompoundTag compound(int x,int y,int z,int ox,int oy,int oz,int sx,int sy,int sz) {
        return new CompoundTag().putString("id","StructureBlock").putInt("x",x).putInt("y",y).putInt("z",z)
            .putInt("data",1).putString("structureName","sworldedit-preview").putString("dataField","")
            .putInt("xStructureOffset",ox).putInt("yStructureOffset",oy).putInt("zStructureOffset",oz)
            .putInt("xStructureSize",sx).putInt("yStructureSize",sy).putInt("zStructureSize",sz)
            .putByte("showBoundingBox",1).putByte("isPowered",0).putByte("ignoreEntities",0).putByte("includePlayers",0)
            .putByte("removeBlocks",0).putFloat("integrity",100f).putLong("seed",0).putByte("mirror",0).putByte("rotation",0)
            .putByte("animationMode",0).putFloat("animationSeconds",0f).putByte("redstoneSaveMode",0).putByte("isWaterlogged",0);
    }
    private static void send(Player player,Level level,Vector3 position,Block source) {
        Block block=source.clone();block.setComponents(position.x,position.y,position.z);block.level=level;
        level.sendBlocks(player,new Block[]{block},UpdateBlockPacket.FLAG_ALL_PRIORITY);
    }
    public boolean isMarker(Player player,Vector3 position) {
        var own=shown.get(player);if(own==null || position==null) return false;
        return own.values().stream().anyMatch(box->box.level==player.getLevel() && box.x==position.getFloorX() && box.y==position.getFloorY() && box.z==position.getFloorZ());
    }
    public void clearBox(Player player,String tag) {
        var own=shown.get(player);Box box=own==null?null:own.remove(tag);if(box==null)return;
        if(box.task>=0)tasks.cancel(box.task);
        if(!player.isOnline() || player.getLevel()!=box.level)return;
        // Force removal before restoring, even if the real block is itself a structure block.
        send(player,box.level,box.position(),Block.get(0));
        send(player,box.level,box.position(),box.level.getBlock(box.position()));
        var entity=box.level.getBlockEntity(box.position());
        if(entity instanceof BlockEntitySpawnable spawnable)spawnable.spawnTo(player);
    }
    public void clearAllBoxes(Player player) {
        var own=shown.get(player);if(own==null)return;
        for(String tag:new ArrayList<>(own.keySet()))clearBox(player,tag);
        shown.remove(player);
    }
    public List<String> boxTags(Player player) { var own=shown.get(player);return own==null?List.of():List.copyOf(own.keySet()); }
    @Override public void close() { closed=true;for(Player player:new ArrayList<>(shown.keySet()))clearAllBoxes(player); }
}
