package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.CommandParamType;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.utils.SmoothOperation;
import java.util.*;

public final class SmoothCommand extends WECommand {
    private final Set<UUID> busy=new HashSet<>();
    public SmoothCommand(){
        super("/smooth",Messages.text("smooth.description"));
        commandParameters.put("default",new CommandParameter[]{CommandParameter.newType("iterations",true,CommandParamType.INT)});
    }
    @Override public void execute(Player player,PlayerData data,String[] args){
        if(data==null || !data.hasSelection()){player.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));return;}
        UUID id=player.getUniqueId();
        if(busy.contains(id)){player.sendMessage(Messages.text("smooth.busy"));return;}
        try {
            if(args.length>1)throw new IllegalArgumentException("Usage: //smooth [iterations]");
            int iterations=args.length==0?1:Integer.parseInt(args[0]);
            SWorldEdit plugin=SWorldEdit.get();
            List<Integer> configured=plugin.getConfig().getIntegerList("smooth.terrain-ids");
            Set<Integer> terrain=new HashSet<>(configured.isEmpty()?List.of(1,2,3,12,13,24,48,80,82,110,159,172):configured);
            SmoothOperation work=new SmoothOperation(data,data.getSelection().getPosOne(),data.getSelection().getPosTwo(),iterations,
                plugin.getConfig().getInt("smooth.max-iterations",10),plugin.getConfig().getInt("smooth.max-blocks",100000),
                plugin.getConfig().getInt("smooth.blocks-per-tick",512),terrain,
                task->plugin.getServer().getScheduler().scheduleDelayedTask(plugin,task,1));
            busy.add(id);
            work.start().whenComplete((count,error)->{
                busy.remove(id);
                player.sendMessage(error==null?Messages.text("smooth.success",count):Messages.text("smooth.failed",error.getMessage()));
            });
        }catch(RuntimeException e){player.sendMessage(Messages.text("smooth.failed",e.getMessage()));}
    }
}
