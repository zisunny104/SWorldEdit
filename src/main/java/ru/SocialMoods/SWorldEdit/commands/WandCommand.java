package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.item.Item;
import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class WandCommand extends WECommand {
    public WandCommand() {
        super("/wand", Messages.text("text.give.wand.axe"));
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (p == null) {
                return;
            }

            Item axe = Item.get(271);
            axe.setCustomName("WorldEdit Wand Axe");

            if (dat != null) dat.getTools().restoreWand(axe);
            p.getInventory().setItemInHand(axe);
            p.getInventory().sendHeldItem(p);

            p.sendMessage(Messages.text("text.you.have.received.the.worldedit.wand.axe"));
            p.sendMessage(Messages.text("text.right.click.set.position.1"));
            p.sendMessage(Messages.text("text.left.click.set.position.2"));

        } catch (Exception e) {
            p.sendMessage(Messages.text("text.error.giving.wand", e.getMessage()));
        }
    }
}
