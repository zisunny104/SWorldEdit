package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class UndoCommand extends WECommand {
    public UndoCommand() {
        super("/undo", Messages.text("text.cancel.action"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            if (dat.getUndoSteps() == null || dat.getUndoSteps().isEmpty()) {
                p.sendMessage(Messages.text("text.there.s.nothing.to.undo"));
                return;
            }

            int step = dat.getUndoSteps().size() - 1;

            WorldUtils.undoAsync(dat, step)
                    .thenAccept(blocksUndone -> {
                        p.sendMessage(Messages.text("text.successfully.undone.blocks", blocksUndone));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.undoing", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.undo.command", e.getMessage()));
        }
    }
}