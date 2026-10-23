package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class PosTwoCommand extends WECommand {
    public PosTwoCommand() {
        super("/pos2", Messages.text("text.set.second.position"));
        super.setAliases(new String[]{"/2"});
    }

    @Override
    public void execute(cn.nukkit.Player player, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                player.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            if (dat.getSelection() == null) {
                player.sendMessage(Messages.text("text.selection.data.not.found"));
                return;
            }

            dat.getSelection().setPosTwo(player.getPosition().clone());
            player.sendMessage(Messages.text("text.second.position.set.to", player.getFloorX(), player.getFloorY(), player.getFloorZ()));

        } catch (Exception e) {
            player.sendMessage(Messages.text("text.error.setting.second.position", e.getMessage()));
        }
    }
}