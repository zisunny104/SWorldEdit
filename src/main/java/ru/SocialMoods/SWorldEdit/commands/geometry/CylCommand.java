package ru.SocialMoods.SWorldEdit.commands.geometry;

import cn.nukkit.block.Block;
import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.commands.WECommand;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class CylCommand extends WECommand {
    public CylCommand() {
        super("/cyl", "Make cylinder");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            if (args.length != 2) {
                p.sendMessage("Usage: //cyl <block_id>[:data] <radius>");
                p.sendMessage("Example: //cyl 1 5, //cyl stone 3");
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(String.format("Block '%1$s' doesn't exist or is invalid", args[0]));
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(args[1]);
                if (radius <= 0) {
                    p.sendMessage("Radius must be a positive number");
                    return;
                }
                if (radius > 100) {
                    p.sendMessage("Radius is too large (maximum 100)");
                    return;
                }
            } catch (NumberFormatException e) {
                p.sendMessage("Invalid radius. Please use a number");
                return;
            }

            Position center = p.getPosition();

            WorldUtils.cylAsync(dat, center, radius, block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(String.format("Successfully created cylinder with %1$s blocks", blocksChanged));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while creating cylinder: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing cylinder command: %1$s", e.getMessage()));
        }
    }
}