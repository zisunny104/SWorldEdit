package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class HelpCommand extends WECommand {
    public HelpCommand() {
        super("/help", Messages.text("text.list.of.world.edit.commands"));
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
                        sender.sendMessage(Messages.text("text.invalid.page.number.please.use.page.1.2"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.text("text.invalid.page.number.please.use.help.page.number"));
                }
            } else {
                sender.sendMessage(Messages.text("text.usage.help.page.number"));
            }
        } catch (Exception e) {
            sender.sendMessage(Messages.text("text.error.displaying.help", e.getMessage()));
        }
    }

    private String getHelpPage(int page) {
        StringBuilder help = new StringBuilder();

        switch (page) {
            case 1: {
                help.append(Messages.text("text.worldedit.help.page.1.3"));
                help.append(Messages.text("text.pos1.1.set.first.position"));
                help.append(Messages.text("text.pos2.2.set.second.position"));
                help.append(Messages.text("text.set.block.set.blocks.in.selected.area"));
                help.append(Messages.text("text.walls.block.create.walls.in.selected.area"));
                help.append(Messages.text("text.replace.original.target.replace.blocks.in.area"));
                break;
            }
            case 2: {
                help.append(Messages.text("text.worldedit.help.page.2.3"));
                help.append(Messages.text("text.cyl.block.radius.create.cylinder"));
                help.append(Messages.text("text.hcyl.block.radius.create.hollow.cylinder"));
                help.append(Messages.text("text.sphere.block.radius.create.sphere"));
                help.append(Messages.text("text.hsphere.block.radius.create.hollow.sphere"));
                help.append(Messages.text("text.cut.cut.area.replace.with.air"));
                break;
            }
            case 3: {
                help.append(Messages.text("text.worldedit.help.page.3.3"));
                help.append(Messages.text("text.copy.copy.selected.area.to.clipboard"));
                help.append(Messages.text("text.paste.paste.from.clipboard"));
                help.append(Messages.text("text.rotate.axis.degree.rotate.clipboard.content"));
                help.append(Messages.text("text.wand.get.selection.wand.axe"));
                help.append(Messages.text("text.desel.clear.current.selection"));
                break;
            }
        }

        return help.toString();
    }
}