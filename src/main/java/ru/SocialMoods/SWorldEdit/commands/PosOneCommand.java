package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class PosOneCommand extends WECommand {
    public PosOneCommand() {
        super("/pos1", "Set first position");
        super.setAliases(new String[]{"/1"});
    }

    @Override
    public void execute(cn.nukkit.Player player, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                player.sendMessage("Player data not found");
                return;
            }

            if (dat.getSelection() == null) {
                player.sendMessage("Selection data not found");
                return;
            }

            dat.getSelection().setPosOne(player.getPosition().clone());
            player.sendMessage(String.format("First position set to (%1$s, %2$s, %3$s)", player.getFloorX(), player.getFloorY(), player.getFloorZ()));

        } catch (Exception e) {
            player.sendMessage(String.format("Error setting first position: %1$s", e.getMessage()));
        }
    }
}