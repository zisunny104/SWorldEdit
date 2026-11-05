package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.CommandParamType;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.utils.SurfaceOperation;
import java.util.*;

public final class GreenCommand extends SurfaceCommand {
    public GreenCommand(){
        super("green","green",false);setAliases(new String[]{"/green"});
        var radius=CommandParameter.newType("radius",true,CommandParamType.FLOAT);
        var height=CommandParameter.newType("height",true,CommandParamType.INT);
        var force=CommandParameter.newEnum("coarse",new String[]{"-f"});
        commandParameters.put("default",new CommandParameter[]{radius,height,CommandParameter.newEnum("coarse",true,new String[]{"-f"})});
        commandParameters.put("force",new CommandParameter[]{force,radius,height});
        commandParameters.put("radius-force",new CommandParameter[]{CommandParameter.newType("radius",CommandParamType.FLOAT),force,height});
    }
    public record Arguments(double radius,int height,boolean coarse){}
    public static Arguments parse(String[] args,double defaultRadius,int defaultHeight){
        List<String> numbers=new ArrayList<>();boolean coarse=false;
        for(String arg:args){if(arg.equals("-f")){if(coarse)throw new IllegalArgumentException("Duplicate -f");coarse=true;}else numbers.add(arg);}
        if(numbers.size()>2 || args.length>3)throw new IllegalArgumentException("Usage: /green [radius] [height] [-f]");
        double radius=numbers.isEmpty()?defaultRadius:Double.parseDouble(numbers.get(0));
        int height=numbers.size()<2?defaultHeight:Integer.parseInt(numbers.get(1));
        if(!Double.isFinite(radius) || radius<1 || height<1)throw new IllegalArgumentException("Radius must be finite and >= 1; height must be positive");
        return new Arguments(radius,height,coarse);
    }
    @Override SurfaceOperation operation(Player player,PlayerData data,String[] args,SWorldEdit plugin){
        var center=player.getPosition();var level=center.getLevel();
        var a=parse(args,plugin.getConfig().getDouble("surface.default-radius",10),Math.toIntExact((long)level.getMaxBlockY()-level.getMinBlockY()+1));
        return SurfaceOperation.green(data,center,a.radius(),a.height(),a.coarse(),plugin.getConfig().getInt("surface.max-radius",128),
            plugin.getConfig().getInt("surface.max-blocks",100000),plugin.getConfig().getInt("surface.max-checks",1000000),
            plugin.getConfig().getInt("surface.blocks-per-tick",512),plugin.getWorldTasks()::schedule);
    }
}
