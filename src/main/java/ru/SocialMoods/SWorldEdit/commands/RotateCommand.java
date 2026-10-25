package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class RotateCommand extends WECommand {
    public RotateCommand() {
        super("/rotate", Messages.text("text.rotate.structure.around.axis"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage(Messages.text("text.player.data.not.found"));
                return;
            }

            if (args.length != 2) {
                p.sendMessage(Messages.text("text.usage.rotate.axis.degrees"));
                p.sendMessage(Messages.text("text.example.rotate.y.90"));
                return;
            }

            String axis = args[0].toLowerCase(java.util.Locale.ROOT);
            if (!axis.equals("y")) {
                p.sendMessage(Messages.text("text.currently.only.y.axis.rotation.is.supported"));
                return;
            }

            int degrees;
            try {
                degrees = Integer.parseInt(args[1]);
                if (degrees < 0 || degrees > 360) {
                    p.sendMessage(Messages.text("text.rotation.degrees.must.be.between.0.and.360"));
                    return;
                }

                degrees = degrees % 360;
            } catch (NumberFormatException e) {
                p.sendMessage(Messages.text("text.invalid.degrees.value.please.use.a.number"));
                return;
            }

            Selection sel = dat.getSelection();
            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));
                return;
            }

            Position center = new Position(p.getX(), p.getY(), p.getZ(), p.getLevel());

            int finalDegrees = degrees;
            WorldUtils.rotateYAsync(dat, center, sel, degrees)
                    .thenAccept(blocksRotated -> {
                        p.sendMessage(Messages.text("text.successfully.rotated.blocks.around.axis.by.degrees", blocksRotated, axis, finalDegrees));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(Messages.text("text.error.while.rotating.blocks", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.executing.rotate.command", e.getMessage()));
        }
    }
}