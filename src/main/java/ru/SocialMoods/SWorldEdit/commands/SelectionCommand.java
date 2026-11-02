package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.preview.Previews;
import ru.SocialMoods.SWorldEdit.utils.SelectionBounds;
import java.util.*;

public final class SelectionCommand extends WECommand {
    public enum Kind { SIZE,EXPAND,CONTRACT,SHIFT,OUTSET,INSET }
    private final Kind kind;
    private static final String[] DIRECTIONS={"north","south","east","west","up","down"};
    public SelectionCommand(Kind kind){
        super("/"+kind.name().toLowerCase(Locale.ROOT),Messages.text("selection."+kind.name().toLowerCase(Locale.ROOT)+".description"));
        this.kind=kind;
        var amount=CommandParameter.newType("amount",CommandParamType.INT);
        var reverse=CommandParameter.newType("reverse-amount",true,CommandParamType.INT);
        var direction=CommandParameter.newEnum("direction",true,DIRECTIONS);
        switch(kind){
            case SIZE -> commandParameters.put("default",new CommandParameter[]{CommandParameter.newEnum("clipboard",true,new String[]{"-c"})});
            case EXPAND,CONTRACT -> {
                commandParameters.put("default",new CommandParameter[]{amount,reverse,direction});
                commandParameters.put("directed",new CommandParameter[]{amount,CommandParameter.newEnum("direction",DIRECTIONS)});
                if(kind==Kind.EXPAND)commandParameters.put("vertical",new CommandParameter[]{CommandParameter.newEnum("extent",new String[]{"vert"})});
            }
            case SHIFT -> commandParameters.put("default",new CommandParameter[]{amount,direction});
            case OUTSET,INSET -> commandParameters.put("default",new CommandParameter[]{amount,CommandParameter.newEnum("axes",true,new String[]{"-h","-v"})});
        }
    }
    public record Arguments(int amount,int reverse,String direction,boolean horizontal,boolean vertical,boolean fullHeight,boolean clipboard){}
    @Override public String syntax(){String usage=super.syntax();return kind==Kind.EXPAND?usage+" | //expand vert":usage;}
    public static Arguments parse(Kind kind,String[] args){
        if(kind==Kind.SIZE){if(args.length>1 || (args.length==1&&!args[0].equals("-c")))throw new IllegalArgumentException("Usage: //size [-c]");return new Arguments(0,0,null,false,false,false,args.length==1);}
        if(kind==Kind.EXPAND && args.length==1 && args[0].equalsIgnoreCase("vert"))return new Arguments(0,0,null,false,true,true,false);
        int maximum=kind==Kind.EXPAND||kind==Kind.CONTRACT?3:2;
        if(args.length<1 || args.length>maximum)throw new IllegalArgumentException("Invalid selection arguments");
        int amount=Integer.parseInt(args[0]);if(amount<0)throw new IllegalArgumentException("Amount must be non-negative");
        if(kind==Kind.OUTSET || kind==Kind.INSET){
            String flag=args.length==2?args[1]:"";
            if(!Set.of("","-h","-v").contains(flag))throw new IllegalArgumentException("Use -h or -v");
            return new Arguments(amount,0,null,!flag.equals("-v"),!flag.equals("-h"),false,false);
        }
        int reverse=0;String direction=null;
        if(args.length>1){
            if(args[1].matches("[0-9]+")){
                if(kind==Kind.SHIFT)throw new IllegalArgumentException("Shift has no reverse amount");
                reverse=Integer.parseInt(args[1]);if(args.length==3)direction=args[2];
            }else {if(args.length==3)throw new IllegalArgumentException("Reverse amount must precede direction");direction=args[1];}
        }
        if(direction!=null)direction(direction,new Vector3(0,0,1));
        return new Arguments(amount,reverse,direction,false,false,false,false);
    }
    public static int[] direction(String name,Vector3 look){
        if(name==null){double x=Math.abs(look.x),y=Math.abs(look.y),z=Math.abs(look.z);return y>x&&y>z?new int[]{0,look.y<0?-1:1,0}:x>z?new int[]{look.x<0?-1:1,0,0}:new int[]{0,0,look.z<0?-1:1};}
        return switch(name.toLowerCase(Locale.ROOT)){
            case "north" -> new int[]{0,0,-1};case "south" -> new int[]{0,0,1};case "east" -> new int[]{1,0,0};
            case "west" -> new int[]{-1,0,0};case "up" -> new int[]{0,1,0};case "down" -> new int[]{0,-1,0};
            default -> throw new IllegalArgumentException("Unknown direction: "+name);
        };
    }
    private static SelectionBounds clipboard(PlayerData data){
        if(data.copiedBlocks==null || data.copiedBlocks.blocks.isEmpty())throw new IllegalArgumentException(Messages.text("selection.empty-clipboard"));
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(Block block:data.copiedBlocks.blocks){minX=Math.min(minX,block.getFloorX());minY=Math.min(minY,block.getFloorY());minZ=Math.min(minZ,block.getFloorZ());maxX=Math.max(maxX,block.getFloorX());maxY=Math.max(maxY,block.getFloorY());maxZ=Math.max(maxZ,block.getFloorZ());}
        return new SelectionBounds(minX,minY,minZ,maxX,maxY,maxZ);
    }
    @Override public void execute(Player player,PlayerData data,String[] args){
        if(data==null){player.sendMessage(Messages.text("text.player.data.not.found"));return;}
        try{
            var parsed=parse(kind,args);
            if(parsed.clipboard()){
                var b=clipboard(data);player.sendMessage(Messages.text("selection.clipboard-size",b.width(),b.height(),b.length(),b.volume(),data.copiedBlocks.blocks.size()));return;
            }
            if(!data.hasSelection()){player.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));return;}
            var selection=data.getSelection();var b=SelectionBounds.of(selection.getPosOne(),selection.getPosTwo());
            if(kind==Kind.SIZE){player.sendMessage(Messages.text("selection.size",b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ(),b.width(),b.height(),b.length(),b.volume()));return;}
            var level=selection.getPosOne().level;
            SelectionBounds changed=parsed.fullHeight()?b.vertical(level.getMinBlockY(),level.getMaxBlockY()):switch(kind){
                case OUTSET,INSET -> b.symmetric(parsed.amount(),kind==Kind.INSET,parsed.horizontal(),parsed.vertical());
                default -> b.directional(kind.name().toLowerCase(Locale.ROOT),parsed.amount(),parsed.reverse(),direction(parsed.direction(),player.getDirectionVector()));
            };
            changed.apply(selection);Previews.selection(player,selection);
            player.sendMessage(Messages.text("selection.updated",changed.width(),changed.height(),changed.length(),changed.volume()));
        }catch(RuntimeException e){player.sendMessage(Messages.text("selection.failed",e.getMessage()));}
    }
}
