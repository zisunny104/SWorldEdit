package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.math.Vector3;
import lombok.extern.slf4j.Slf4j;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
@Slf4j
public class CopyCommand extends WECommand {
    public CopyCommand() {
        super("/copy", Messages.text("text.copy.area.to.clipboard"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            this.checkForErrors(p, dat);
            Selection sel = dat.getSelection();

            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));
                return;
            }

            dat.copiedBlocks = new BlocksArray();

            WorldUtils.copyAsync(sel.getPosOne(), sel.getPosTwo(), new Vector3(p.getX(), p.getY(), p.getZ()), dat.copiedBlocks)
                    .thenAccept(blocksCopied -> {
                        p.sendMessage(Messages.text("text.successfully.copied.blocks.to.clipboard", blocksCopied));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.copying", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.copy.command", e.getMessage()));
        }
    }
}