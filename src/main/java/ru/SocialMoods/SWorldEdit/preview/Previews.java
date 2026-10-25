package ru.SocialMoods.SWorldEdit.preview;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.utils.Selection;

/** Isolates optional presentation failures from successful world edits. */
public final class Previews {
    public static final String SELECTION="sworldedit:selection",PASTE="sworldedit:paste";
    private Previews() { }
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        public static Bounds include(Bounds old,Vector3 point) {
            int x=point.getFloorX(),y=point.getFloorY(),z=point.getFloorZ();
            return old==null?new Bounds(x,y,z,x,y,z):new Bounds(Math.min(old.minX,x),Math.min(old.minY,y),Math.min(old.minZ,z),Math.max(old.maxX,x),Math.max(old.maxY,y),Math.max(old.maxZ,z));
        }
    }
    private static void run(java.util.function.Consumer<SWorldEdit> work) {
        var plugin=SWorldEdit.get();if(plugin==null || plugin.getBoundingBoxes()==null)return;
        try { work.accept(plugin); }
        catch(RuntimeException | LinkageError error) { plugin.getLogger().warning("Bounding-box preview unavailable: "+error.getMessage()); }
    }
    public static void selection(Player player,Selection selection) {
        run(plugin->{
            if(selection==null || selection.getPosOne()==null || selection.getPosTwo()==null ||
                    selection.getPosOne().level!=selection.getPosTwo().level ||
                    !plugin.getConfig().getBoolean("bounding-boxes.selection-enabled",true)) {
                plugin.getBoundingBoxes().clearBox(player,SELECTION);return;
            }
            var a=selection.getPosOne();var b=selection.getPosTwo();
            plugin.getBoundingBoxes().clearBox(player,SELECTION);
            plugin.getBoundingBoxes().showBox(player,a.level,a.getFloorX(),a.getFloorY(),a.getFloorZ(),b.getFloorX(),b.getFloorY(),b.getFloorZ(),SELECTION,
                    Math.max(0,plugin.getConfig().getInt("bounding-boxes.selection-seconds",0)));
        });
    }
    public static void pasted(Player player,Level level,Bounds bounds) {
        run(plugin->{
            plugin.getBoundingBoxes().clearBox(player,PASTE);
            int seconds=plugin.getConfig().getInt("bounding-boxes.paste-seconds",10);
            if(bounds!=null && seconds>0 && plugin.getConfig().getBoolean("bounding-boxes.paste-enabled",true))
                plugin.getBoundingBoxes().showBox(player,level,bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ,PASTE,seconds);
        });
    }
    public static void clearPaste(Player player) { run(plugin->plugin.getBoundingBoxes().clearBox(player,PASTE)); }
    public static void clear(Player player) { run(plugin->plugin.getBoundingBoxes().clearAllBoxes(player)); }
    public static boolean isMarker(Player player,Vector3 position) {
        var plugin=SWorldEdit.get();return plugin!=null && plugin.getBoundingBoxes()!=null && plugin.getBoundingBoxes().isMarker(player,position);
    }
}
