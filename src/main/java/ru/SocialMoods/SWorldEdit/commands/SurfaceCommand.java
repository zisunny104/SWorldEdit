package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.utils.SurfaceOperation;
import java.util.*;

abstract class SurfaceCommand extends WECommand {
    private final Set<UUID> busy=new HashSet<>();
    private final String kind;
    private final boolean selectionRequired;
    SurfaceCommand(String name,String kind,boolean selectionRequired){super(name,Messages.text(kind+".description"));this.kind=kind;this.selectionRequired=selectionRequired;}
    abstract SurfaceOperation operation(Player player,PlayerData data,String[] args,SWorldEdit plugin);
    @Override public final void execute(Player player,PlayerData data,String[] args){
        if(data==null){player.sendMessage(Messages.text("text.player.data.not.found"));return;}
        if(selectionRequired && !data.hasSelection()){
            player.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));return;
        }
        UUID id=player.getUniqueId();
        if(busy.contains(id)){player.sendMessage(Messages.text("surface.busy"));return;}
        try{
            SurfaceOperation work=operation(player,data,args,SWorldEdit.get());
            busy.add(id);
            work.start().whenComplete((count,error)->{
                busy.remove(id);
                player.sendMessage(error==null?Messages.text(kind+".success",count):Messages.text("surface.failed",error.getMessage()));
            });
        }catch(RuntimeException e){player.sendMessage(Messages.text("surface.failed",e.getMessage()));}
    }
}
