package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.utils.ToolBindings;

public final class ToolCommand extends WECommand {
    public ToolCommand() {
        super("tool",Messages.text("tool.description"));
        setAliases(new String[]{"/tool"});
    }
    @Override public void execute(Player player, PlayerData data, String[] args) {
        if(args.length != 1 || !(args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("none"))) {
            player.sendMessage(Messages.text("text.command.usage",syntax())); return;
        }
        var item=player.getInventory().getItemInHand();
        if(item == null || item.getId()==0) { player.sendMessage(Messages.text("tool.hold-item")); return; }
        boolean info=args[0].equalsIgnoreCase("info");
        data.getTools().bind(item,info ? ToolBindings.Mode.INFO : ToolBindings.Mode.NONE);
        player.sendMessage(Messages.text(info ? "tool.info.bound" : "tool.none",item.getName()));
    }
}
