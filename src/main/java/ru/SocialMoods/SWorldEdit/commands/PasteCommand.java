package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.WorldUtils;

@SuppressWarnings("unused")
public class PasteCommand extends WECommand {
    public PasteCommand() {
        super("/paste", "Paste area");
        this.commandParameters.clear();
        this.commandParameters.put("default", new cn.nukkit.command.data.CommandParameter[]{
            cn.nukkit.command.data.CommandParameter.newEnum("ignoreAir", true, new String[]{"-a"})
        });
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (dat == null) {
                p.sendMessage("Player data not found");
                return;
            }

            if (args.length > 1 || (args.length == 1 && !args[0].equals("-a"))) {
                p.sendMessage("Usage: //paste [-a] (-a skips air)");
                return;
            }
            boolean skipAir = args.length == 1;
            BlocksArray copy = dat.copiedBlocks;
            if (copy == null || copy.blocks.isEmpty()) {
                p.sendMessage("Clipboard is empty. Use //copy first to copy blocks");
                return;
            }

            Position pastePosition = new Position(p.getX(), p.getY(), p.getZ(), p.getLevel());

            WorldUtils.pasteAsync(dat, pastePosition, copy, skipAir)
                    .thenAccept(blocksPasted -> {
                        p.sendMessage(String.format("Successfully pasted %1$s blocks", blocksPasted));
                    })
                    .exceptionally(throwable -> {
                        p.sendMessage(String.format("Error while pasting: %1$s", throwable.getMessage()));
                        return null;
                    });

        } catch (Exception e) {
            p.sendMessage(String.format("Error executing paste command: %1$s", e.getMessage()));
        }
    }
}