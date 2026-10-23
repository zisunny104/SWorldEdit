package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class ReplaceCommand extends WECommand {
    public ReplaceCommand() {
        super("/replace", Messages.text("text.replace.blocks.in.selected.area"));
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

            if (args.length != 2) {
                p.sendMessage(Messages.text("text.usage.replace.original.block.replacement.block"));
                return;
            }

            Block originalBlock = Utils.fromString(args[0], p);
            Block replacementBlock = Utils.fromString(args[1], p);

            if (originalBlock == null) {
                p.sendMessage(Messages.text("text.original.block.doesn.t.exist.or.is.invalid", args[0]));
                return;
            }

            if (replacementBlock == null) {
                p.sendMessage(Messages.text("text.replacement.block.doesn.t.exist.or.is.invalid", args[1]));
                return;
            }

            WorldUtils.replaceAsync(dat, sel.getPosOne(), sel.getPosTwo(), originalBlock, replacementBlock)
                    .thenAccept(blocksReplaced -> {
                        p.sendMessage(Messages.text("text.successfully.replaced.blocks", blocksReplaced));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.replacing.blocks", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.replace.command", e.getMessage()));
        }
    }
}