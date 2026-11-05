package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.utils.SurfaceOperation;

public final class NaturalizeCommand extends SurfaceCommand {
    public NaturalizeCommand(){super("/naturalize","naturalize",true);}
    @Override SurfaceOperation operation(Player player,PlayerData data,String[] args,SWorldEdit plugin){
        if(args.length!=0)throw new IllegalArgumentException("Usage: //naturalize");
        return SurfaceOperation.naturalize(data,data.getSelection().getPosOne(),data.getSelection().getPosTwo(),
            plugin.getConfig().getInt("surface.max-blocks",100000),plugin.getConfig().getInt("surface.max-checks",1000000),
            plugin.getConfig().getInt("surface.blocks-per-tick",512),plugin.getWorldTasks()::schedule);
    }
}
