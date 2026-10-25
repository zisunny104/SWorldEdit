package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class UndoCommand extends WECommand {
    public UndoCommand() {
        super("/undo", "Cancel action");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            if (dat.getUndoSteps() == null || dat.getUndoSteps().isEmpty()) {
                p.sendMessage("There's nothing to undo");
                return;
            }

            int step = dat.getUndoSteps().size() - 1;

            WorldUtils.undoAsync(dat, step)
                    .thenAccept(blocksUndone -> {
                        p.sendMessage(String.format("Successfully undone %1$s blocks", blocksUndone));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while undoing: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing undo command: %1$s", e.getMessage()));
        }
    }
}