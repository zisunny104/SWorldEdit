package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class HelpCommand extends WECommand {
    public HelpCommand() {
        super("/help", "List of World Edit Commands");
    }

    @Override
    public void execute(cn.nukkit.Player sender, PlayerData dat, String[] args) {
        try {
            if (args.length == 0) {
                sender.sendMessage(this.getHelpPage(1));
            } else if (args.length == 1) {
                try {
                    int page = Integer.parseInt(args[0]);
                    if (page >= 1 && page <= 3) {
                        sender.sendMessage(this.getHelpPage(page));
                    } else {
                        sender.sendMessage("Invalid page number. Please use page 1, 2, or 3");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid page number. Please use //help <page_number>");
                }
            } else {
                sender.sendMessage("Usage: //help <page_number>");
            }
        } catch (Exception e) {
            sender.sendMessage(String.format("Error displaying help: %1$s", e.getMessage()));
        }
    }

    private String getHelpPage(int page) {
        StringBuilder help = new StringBuilder();

        switch (page) {
            case 1: {
                help.append("WorldEdit Help - Page 1/3:\n");
                help.append("  //pos1 (//1) - Set first position\n");
                help.append("  //pos2 (//2) - Set second position\n");
                help.append("  //set <block> - Set blocks in selected area\n");
                help.append("  //walls <block> - Create walls in selected area\n");
                help.append("  //replace <original> <target> - Replace blocks in area");
                break;
            }
            case 2: {
                help.append("WorldEdit Help - Page 2/3:\n");
                help.append("  //cyl <block> <radius> - Create cylinder\n");
                help.append("  //hcyl <block> <radius> - Create hollow cylinder\n");
                help.append("  //sphere <block> <radius> - Create sphere\n");
                help.append("  //hsphere <block> <radius> - Create hollow sphere\n");
                help.append("  //cut - Cut area (replace with air)");
                break;
            }
            case 3: {
                help.append("WorldEdit Help - Page 3/3:\n");
                help.append("  //copy - Copy selected area to clipboard\n");
                help.append("  //paste [-a] - Paste from clipboard\n  //stack <count> [direction] [-a] - Repeat selected area\n");
                help.append("  //rotate <y> <degrees> - Rotate the selected area around your position\n");
                help.append("  //wand - Get selection wand axe\n");
                help.append("  //desel - Clear current selection");
                help.append("  //undo - Undo the last operation\n");
                help.append("  //schem <save|paste> <name> - Save or paste a schematic\n");
                help.append("  /tool <info|none> - Bind or disable a right-click block query tool\n");
                break;
            }
        }

        return help.toString();
    }
}
