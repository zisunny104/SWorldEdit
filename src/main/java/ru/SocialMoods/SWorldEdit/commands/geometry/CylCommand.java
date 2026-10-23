package ru.SocialMoods.SWorldEdit.commands.geometry;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.commands.WECommand;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class CylCommand extends WECommand {
    public CylCommand() {
        super("/cyl", Messages.text("text.make.cylinder"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            if (args.length != 2) {
                p.sendMessage(Messages.text("text.usage.cyl.block.id.data.radius"));
                p.sendMessage(Messages.text("text.example.cyl.1.5.cyl.stone.3"));
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(Messages.text("text.block.doesn.t.exist.or.is.invalid", args[0]));
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(args[1]);
                if (radius <= 0) {
                    p.sendMessage(Messages.text("text.radius.must.be.a.positive.number"));
                    return;
                }
                if (radius > 100) {
                    p.sendMessage(Messages.text("text.radius.is.too.large.maximum.100"));
                    return;
                }
            } catch (NumberFormatException e) {
                p.sendMessage(Messages.text("text.invalid.radius.please.use.a.number"));
                return;
            }

            Position center = p.getPosition();

            WorldUtils.cylAsync(dat, center, radius, block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(Messages.text("text.successfully.created.cylinder.with.blocks", blocksChanged));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.creating.cylinder", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.cylinder.command", e.getMessage()));
        }
    }
}