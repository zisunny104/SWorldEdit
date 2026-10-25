package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class SetCommand extends WECommand {
    public SetCommand() {
        super("/set", Messages.text("text.set.blocks.in.selected.area"));
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

            if (args.length != 1) {
                p.sendMessage(Messages.text("text.usage.set.block.id.data"));
                p.sendMessage(Messages.text("text.example.set.1.set.1.2.set.stone"));
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(Messages.text("text.block.doesn.t.exist.or.is.invalid", args[0]));
                p.sendMessage(Messages.text("text.use.numeric.id.or.block.name"));
                return;
            }

            WorldUtils.setAsync(dat, sel.getPosOne(), sel.getPosTwo(), block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(Messages.text("text.successfully.changed.blocks.to", blocksChanged, block.getName()));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.setting.blocks", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.set.command", e.getMessage()));
        }
    }
}