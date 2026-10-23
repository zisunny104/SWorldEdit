package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.item.Item;
import ru.SocialMoods.SWorldEdit.PlayerData;

@SuppressWarnings("unused")
public class WandCommand extends WECommand {
    public WandCommand() {
        super("/wand", "Give wand axe");
    }

    @Override
    public void execute(cn.nukkit.Player p, PlayerData dat, String[] args) {
        try {
            if (p == null) {
                return;
            }

            Item axe = Item.get(271);
            axe.setCustomName("WorldEdit Wand Axe");

            p.getInventory().setItemInHand(axe);
            p.getInventory().sendHeldItem(p);

            p.sendMessage("You have received the WorldEdit wand axe");
            p.sendMessage("Right click: set position #1");
            p.sendMessage("Left click: set position #2");

        } catch (Exception e) {
            p.sendMessage("Error giving wand: " + e.getMessage());
        }
    }
}
