package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.block.Block;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.Utils;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class WallsCommand extends WECommand {
    public WallsCommand() {
        super("/walls", "Build walls among area");
        super.setAliases(new String[]{"/wall"});
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
                p.sendMessage("Usage: //walls <block_id>[:data]");
                p.sendMessage("Example: //walls 1, //walls stone");
                return;
            }

            Block block = Utils.fromString(args[0], p);
            if (block == null) {
                p.sendMessage(String.format("Block '%1$s' doesn't exist or is invalid", args[0]));
                return;
            }

            WorldUtils.wallsAsync(sel.getPosOne(), sel.getPosTwo(), block)
                    .thenAccept(blocksChanged -> {
                        p.sendMessage(String.format("Successfully created walls with %1$s blocks", blocksChanged));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while creating walls: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing walls command: %1$s", e.getMessage()));
        }
    }
}