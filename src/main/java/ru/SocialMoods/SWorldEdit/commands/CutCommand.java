package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class CutCommand extends WECommand {
    public CutCommand() {
        super("/cut", Messages.text("text.delete.blocks.in.area"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            Selection sel = dat.getSelection();
            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));
                return;
            }

            Block airBlock = new BlockAir();

            WorldUtils.setAsync(dat, sel.getPosOne(), sel.getPosTwo(), airBlock)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(Messages.text("text.successfully.removed.blocks", blocksChanged));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.cutting.blocks", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.cut.command", e.getMessage()));
        }
    }
}