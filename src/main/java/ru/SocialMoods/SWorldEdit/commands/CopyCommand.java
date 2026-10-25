package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.math.Vector3;
import lombok.extern.slf4j.Slf4j;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
@Slf4j
public class CopyCommand extends WECommand {
    public CopyCommand() {
        super("/copy", "Copy area to clipboard");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            this.checkForErrors(p, dat);
            Selection sel = dat.getSelection();

            if (sel == null || sel.getPosOne() == null || sel.getPosTwo() == null) {
                p.sendMessage("Please select area first using //pos1 and //pos2");
                return;
            }

            BlocksArray snapshot = new BlocksArray();

            WorldUtils.copyAsync(sel.getPosOne(), sel.getPosTwo(), new Vector3(p.getX(), p.getY(), p.getZ()), snapshot)
                    .thenAccept(blocksCopied -> {
                        dat.copiedBlocks = snapshot;
                        p.sendMessage(String.format("Successfully copied %1$s blocks to clipboard", blocksCopied));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while copying: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing copy command: %1$s", e.getMessage()));
        }
    }
}