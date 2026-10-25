package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class RotateCommand extends WECommand {
    public RotateCommand() {
        super("/rotate", "Rotate structure around axis");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            if (args.length != 2) {
                p.sendMessage("Usage: //rotate <axis> <degrees>");
                p.sendMessage("Example: //rotate y 90");
                return;
            }

            String axis = args[0].toLowerCase(java.util.Locale.ROOT);
            if (!axis.equals("y")) {
                p.sendMessage("Currently only Y axis rotation is supported");
                return;
            }

            int degrees;
            try {
                degrees = Integer.parseInt(args[1]);
                if (degrees < 0 || degrees > 360) {
                    p.sendMessage("Rotation degrees must be between 0 and 360");
                    return;
                }

                degrees = degrees % 360;
            } catch (NumberFormatException e) {
                p.sendMessage("Invalid degrees value. Please use a number");
                return;
            }

            Selection sel = dat.getSelection();
            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage("Please select area first using //pos1 and //pos2");
                return;
            }

            Position center = new Position(p.getX(), p.getY(), p.getZ(), p.getLevel());

            int finalDegrees = degrees;
            WorldUtils.rotateYAsync(dat, center, sel, degrees)
                    .thenAccept(blocksRotated -> {
                        p.sendMessage(String.format("Successfully rotated %1$s blocks around %2$s axis by %3$s degrees", blocksRotated, axis, finalDegrees));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while rotating blocks: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing rotate command: %1$s", e.getMessage()));
        }
    }
}