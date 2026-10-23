package ru.SocialMoods.SWorldEdit;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unused")
@Slf4j
public class Listener implements cn.nukkit.event.Listener {
    private static final String WAND_NAME = "WorldEdit Wand Axe";

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        try {
            cn.nukkit.Player player = event.getPlayer();
            Block block = event.getBlock();
            Item item = player.getInventory().getItemInHand();

            if (!player.hasPermission("we.command")) {
                return;
            }

            if (item.getId() == 271 && isWandItem(item)) { // Wooden axe
                PlayerData data = SWorldEdit.get().getWEPlayer(player);
                if (data != null && data.getSelection() != null) {
                    data.getSelection().setPosTwo(block.getLocation().clone());
                    player.sendMessage(Messages.text("text.selected.second.position.at", block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                }
                event.setCancelled(true);
            }
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        try {
            cn.nukkit.Player player = event.getPlayer();
            Block block = event.getBlock();
            Item item = player.getInventory().getItemInHand();

            if (!player.hasPermission("we.command")) {
                return;
            }

            if (item.getId() == 271 && isWandItem(item)) {
                PlayerData data = SWorldEdit.get().getWEPlayer(player);
                if (data == null || data.getSelection() == null) {
                    return;
                }

                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK:
                        data.getSelection().setPosTwo(block.getLocation().clone());
                        player.sendMessage(Messages.text("text.selected.second.position.at", block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                        break;
                    case RIGHT_CLICK_BLOCK:
                        data.getSelection().setPosOne(block.getLocation().clone());
                        player.sendMessage(Messages.text("text.selected.first.position.at", block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                        break;
                }

                event.setCancelled(true);
            }
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    private boolean isWandItem(Item item) {
        if (item == null) return false;
        String customName = item.getCustomName();
        return customName != null && customName.equals(WAND_NAME);
    }
}