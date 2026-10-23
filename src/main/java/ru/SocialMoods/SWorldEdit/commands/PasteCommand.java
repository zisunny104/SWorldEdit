package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class PasteCommand extends WECommand {
    public PasteCommand() {
        super("/paste", Messages.text("text.paste.area"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            BlocksArray copy = dat.copiedBlocks;
            if (copy == null || copy.blocks.isEmpty()) {
                p.sendMessage(Messages.text("text.clipboard.is.empty.use.copy.first.to.copy"));
                return;
            }

            Position pastePosition = new Position(p.getX(), p.getY(), p.getZ(), p.getLevel());

            WorldUtils.pasteAsync(dat, pastePosition, copy)
                    .thenAccept(blocksPasted -> {
                        p.sendMessage(Messages.text("text.successfully.pasted.blocks", blocksPasted));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.pasting", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.paste.command", e.getMessage()));
        }
    }
}