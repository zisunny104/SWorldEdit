package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class DeselCommand extends WECommand {
    public DeselCommand() {
        super("/desel", Messages.text("text.cancel.selection"));
        super.setAliases(new String[]{"/;", "/sel", "/deselect"});
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            if (dat.getSelection() == null) {
                p.sendMessage(Messages.text("text.no.selection.to.clear"));
                return;
            }

            dat.getSelection().setPosOne(null);
            dat.getSelection().setPosTwo(null);
            ru.SocialMoods.SWorldEdit.preview.Previews.selection(p, dat.getSelection());
            p.sendMessage(Messages.text("text.selection.cleared.successfully"));

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.clearing.selection", e.getMessage()));
        }
    }
}