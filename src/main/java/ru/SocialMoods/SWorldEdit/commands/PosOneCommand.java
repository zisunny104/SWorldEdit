package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class PosOneCommand extends WECommand {
    public PosOneCommand() {
        super("/pos1", Messages.text("text.set.first.position"));
        super.setAliases(new String[]{"/1"});
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

            dat.getSelection().setPosOne(player.getPosition().clone());
            ru.SocialMoods.SWorldEdit.preview.Previews.selection(player, dat.getSelection());
            player.sendMessage(Messages.text("text.first.position.set.to", player.getFloorX(), player.getFloorY(), player.getFloorZ()));

        } catch (Exception e) {
            player.sendMessage(Messages.text("text.error.setting.first.position", e.getMessage()));
        }
    }
}