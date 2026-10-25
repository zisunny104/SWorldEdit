package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class DeselCommand extends WECommand {
    public DeselCommand() {
        super("/desel", "Cancel selection");
        super.setAliases(new String[]{"/;", "/sel", "/deselect"});
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            if (dat.getSelection() == null) {
                p.sendMessage("No selection to clear");
                return;
            }

            dat.getSelection().setPosOne(null);
            dat.getSelection().setPosTwo(null);
            p.sendMessage("Selection cleared successfully");

        } catch (Exception e) {
            p.sendMessage(String.format("Error clearing selection: %1$s", e.getMessage()));
        }
    }
}