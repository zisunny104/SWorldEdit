package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.CommandSender;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;

@SuppressWarnings("unused")
public abstract class WECommand extends Command {
    public WECommand(String name, String description) {
        super(name, description);
        setPermission("we.command");
        commandParameters.clear();
        CommandParameter[] parameters = switch (name) {
            case "/help" -> new CommandParameter[]{CommandParameter.newType("page",true,CommandParamType.INT)};
            case "/set", "/walls" -> new CommandParameter[]{CommandParameter.newType("block",CommandParamType.STRING)};
            case "/replace" -> new CommandParameter[]{CommandParameter.newType("from",CommandParamType.STRING),CommandParameter.newType("to",CommandParamType.STRING)};
            case "/cyl", "/hcyl", "/sphere", "/hsphere" -> new CommandParameter[]{CommandParameter.newType("block",CommandParamType.STRING),CommandParameter.newType("radius",CommandParamType.INT)};
            case "/rotate" -> new CommandParameter[]{CommandParameter.newEnum("axis",new String[]{"y"}),CommandParameter.newType("degrees",CommandParamType.INT)};
            default -> new CommandParameter[0];
        };
        commandParameters.put("default",parameters);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof cn.nukkit.Player player)) {
            sender.sendMessage(Messages.text("text.worldedit.is.only.available.in.game"));
            return false;
        }

        if (!player.hasPermission("we.command")) {
            player.sendMessage(Messages.text("text.you.don.t.have.permission.to.use.worldedit"));
            return false;
        }

        var parameters = commandParameters.get("default");
        long required = java.util.Arrays.stream(parameters).filter(parameter -> !parameter.optional).count();
        if (args.length < required || args.length > parameters.length) {
            sender.sendMessage(Messages.text("text.command.usage", syntax()));
            return false;
        }

        try {
            PlayerData wePlayerData = SWorldEdit.get().getWEPlayer(player);
            if (wePlayerData == null) {
                player.sendMessage(Messages.text("text.failed.to.get.worldedit.player.data"));
                return false;
            }

            this.execute(player, wePlayerData, args);

        } catch (Exception e) {
            player.sendMessage(Messages.text("text.error.executing.command", e.getMessage()));
        }

        return true;
    }

    public String syntax() {
        StringBuilder usage = new StringBuilder("/").append(getName());
        for (var parameter : commandParameters.get("default")) {
            String label = parameter.enumData == null ? parameter.name : String.join("|",parameter.enumData.getValues());
            usage.append(' ').append(parameter.optional ? "[" : "<").append(label).append(parameter.optional ? "]" : ">");
        }
        return usage.toString();
    }

    public void checkForErrors(cn.nukkit.Player player, PlayerData dat) {
        if (dat == null) {
            player.sendMessage(Messages.text("text.player.data.not.found"));
            return;
        }

        if (!dat.hasSelection()) {
            player.sendMessage(Messages.text("text.you.need.to.select.both.positions.first.using"));
            return;
        }

        if (dat.getSelection().getPosOne() == null) {
            player.sendMessage(Messages.text("text.first.position.not.set.use.pos1.to.set"));
            return;
        }

        if (dat.getSelection().getPosTwo() == null) {
            player.sendMessage(Messages.text("text.second.position.not.set.use.pos2.to.set"));
        }
    }

    public abstract void execute(cn.nukkit.Player player, PlayerData wePlayerData, String[] args);
}