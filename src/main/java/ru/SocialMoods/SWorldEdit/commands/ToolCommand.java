package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.ToolBindings;

public final class ToolCommand extends WECommand {
    public ToolCommand() {
        super("tool","Bind a read-only block query tool");
        setAliases(new String[]{"/tool"});
    }
    @Override public void execute(Player player, PlayerData data, String[] args) {
        if(args.length != 1 || !(args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("none"))) {
            player.sendMessage(String.format("Usage: %1$s",syntax())); return;
        }
        var item=player.getInventory().getItemInHand();
        if(item == null || item.getId()==0) { player.sendMessage("Hold an item before binding a tool."); return; }
        boolean info=args[0].equalsIgnoreCase("info");
        data.getTools().bind(item,info ? ToolBindings.Mode.INFO : ToolBindings.Mode.NONE);
        player.sendMessage((info ? String.format("Info tool bound to %1$s; right-click a block. Use /tool none to unbind.",item.getName()) : String.format("Tool disabled for %1$s; use //wand to restore the selection wand.",item.getName())));
    }
}
