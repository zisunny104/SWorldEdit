package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class WallsCommand extends WECommand {
    public WallsCommand() {
        super("/walls", Messages.text("text.build.walls.among.area"));
        super.setAliases(new String[]{"/wall"});
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
                p.sendMessage(Messages.text("text.usage.walls.block.id.data"));
                p.sendMessage(Messages.text("text.example.walls.1.walls.stone"));
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(Messages.text("text.block.doesn.t.exist.or.is.invalid", args[0]));
                return;
            }

            WorldUtils.wallsAsync(sel.getPosOne(), sel.getPosTwo(), block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(Messages.text("text.successfully.created.walls.with.blocks", blocksChanged));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.creating.walls", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.walls.command", e.getMessage()));
        }
    }
}