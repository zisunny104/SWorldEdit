package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class PosTwoCommand extends WECommand {
    public PosTwoCommand() {
        super("/pos2", "Set second position");
        super.setAliases(new String[]{"/2"});
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

            dat.getSelection().setPosTwo(player.getPosition().clone());
            ru.SocialMoods.SWorldEdit.preview.Previews.selection(player, dat.getSelection());
            player.sendMessage(String.format("Second position set to (%1$s, %2$s, %3$s)", player.getFloorX(), player.getFloorY(), player.getFloorZ()));

        } catch (Exception e) {
            player.sendMessage(String.format("Error setting second position: %1$s", e.getMessage()));
        }
    }
}