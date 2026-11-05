package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import java.util.concurrent.CompletableFuture;

/** Future-based API; world access is always scheduled on the server thread. */
public final class WorldUtils {
    private WorldUtils(){}
    private static WorldEdits edits(){return SWorldEdit.get().getWorldEdits();}
    public static CompletableFuture<Integer> setAsync(PlayerData owner,Position first,Position second,Block value){return edits().set(owner,first,second,value);}
    public static CompletableFuture<Integer> replaceAsync(PlayerData owner,Position first,Position second,Block from,Block to){return edits().replace(owner,first,second,from,to);}
    public static CompletableFuture<Integer> wallsAsync(PlayerData owner,Position first,Position second,Block value){return edits().walls(owner,first,second,value);}
    @Deprecated public static CompletableFuture<Integer> wallsAsync(Position first,Position second,Block value){return wallsAsync(null,first,second,value);}
    public static CompletableFuture<Integer> cylAsync(PlayerData owner,Position center,int radius,Block value){return edits().geometry(owner,center,radius,value,false,false);}
    public static CompletableFuture<Integer> hcylAsync(PlayerData owner,Position center,int radius,Block value){return edits().geometry(owner,center,radius,value,false,true);}
    public static CompletableFuture<Integer> sphereAsync(PlayerData owner,Position center,int radius,Block value){return edits().geometry(owner,center,radius,value,true,false);}
    public static CompletableFuture<Integer> hsphereAsync(PlayerData owner,Position center,int radius,Block value){return edits().geometry(owner,center,radius,value,true,true);}
    public static CompletableFuture<Integer> rotateYAsync(PlayerData owner,Position center,Selection selection,int degrees){return edits().rotate(owner,center,selection,degrees);}
    public static CompletableFuture<Integer> copyAsync(Position first,Position second,Vector3 origin,BlocksArray output){return copyAsync(null,first,second,origin,output);}
    public static CompletableFuture<Integer> copyAsync(PlayerData owner,Position first,Position second,Vector3 origin,BlocksArray output){
        var plugin=SWorldEdit.get();
        if(owner!=null && plugin.getConfig().getBoolean("clipboard.streaming.enabled",true))return plugin.getStreamingClipboard().copy(owner,first,second,origin,output);
        return ClipboardOperation.copy(owner,first,second,origin,output,plugin.getConfig().getInt("clipboard.max-blocks",100000),
                plugin.getConfig().getInt("clipboard.blocks-per-tick",512),plugin.getWorldTasks()::schedule);
    }
    public static CompletableFuture<Integer> pasteAsync(PlayerData owner,Position center,BlocksArray source){return pasteAsync(owner,center,source,false);}
    public static CompletableFuture<Integer> pasteAsync(PlayerData owner,Position center,BlocksArray source,boolean skipAir){
        var plugin=SWorldEdit.get();
        if(source.paged!=null)return plugin.getStreamingClipboard().paste(owner,center,source,skipAir);
        return ClipboardOperation.paste(owner,center,source,skipAir,plugin.getConfig().getInt("clipboard.max-blocks",100000),
                plugin.getConfig().getInt("clipboard.blocks-per-tick",512),plugin.getWorldTasks()::schedule);
    }
    public static CompletableFuture<Integer> undoAsync(PlayerData owner,int step){
        var plugin=SWorldEdit.get();
        if(step>=0 && step<owner.undoSteps.size() && owner.undoSteps.get(step).paged!=null)return plugin.getStreamingClipboard().undo(owner,step);
        return ClipboardOperation.undo(owner,step,plugin.getConfig().getInt("clipboard.blocks-per-tick",512),plugin.getWorldTasks()::schedule);
    }
}
