package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;

@SuppressWarnings("unused")
public abstract class WECommand extends Command {
    public WECommand(String name, String description) {
        super(name, description);
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