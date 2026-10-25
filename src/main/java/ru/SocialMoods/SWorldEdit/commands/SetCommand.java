package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.block.Block;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class SetCommand extends WECommand {
    public SetCommand() {
        super("/set", "Set blocks in selected area");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            Selection sel = dat.getSelection();
            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage("Please select area first using //pos1 and //pos2");
                return;
            }

            if (args.length != 1) {
                p.sendMessage("Usage: //set <block_id>[:data]");
                p.sendMessage("Example: //set 1, //set 1:2, //set stone");
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(String.format("Block '%1$s' doesn't exist or is invalid", args[0]));
                p.sendMessage("Use numeric ID or block name");
                return;
            }

            WorldUtils.setAsync(dat, sel.getPosOne(), sel.getPosTwo(), block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(String.format("Successfully changed %1$s blocks to %2$s", blocksChanged, block.getName()));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while setting blocks: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing set command: %1$s", e.getMessage()));
        }
    }
}