package ru.SocialMoods.SWorldEdit;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings("unused")
@Slf4j
public class Listener implements cn.nukkit.event.Listener {
    private static final String WAND_NAME = "WorldEdit Wand Axe";
    private final Function<cn.nukkit.Player, PlayerData> playerData;

    public Listener() {
        this(player -> SWorldEdit.get().getWEPlayer(player));
    }

    Listener(Function<cn.nukkit.Player, PlayerData> playerData) {
        this.playerData = Objects.requireNonNull(playerData);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("we.command") && isWandItem(event.getItem())) {
            // Breaking may arrive after the click, with a different predicted target.
            // Only the interaction event selects a point; a wand must never break a block.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        cn.nukkit.Player player = event.getPlayer();
        if (!player.hasPermission("we.command") || !isWandItem(event.getItem())) {
            return;
        }
        if (event.getAction() != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK &&
                event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Cancel before resolving plugin data, including when it is unavailable or fails.
        event.setCancelled(true);
        try {
            Block block = event.getBlock();
            if (block == null || block.getLevel() == null) {
                return;
            }
            if (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                // Cancelling start-break alone may leave a client-predicted hole until a later packet.
                block.getLevel().sendBlocks(player, new Block[]{block}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
                BlockEntity entity = block.getLevel().getBlockEntity(block);
                if (entity instanceof BlockEntitySpawnable) {
                    ((BlockEntitySpawnable) entity).spawnTo(player);
                }
            }
            PlayerData data = playerData.apply(player);
            if (data == null || data.getSelection() == null) {
                return;
            }

            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK:
                    data.getSelection().setPosTwo(block.getLocation().clone());
                    player.sendMessage("Selected second position at " +
                            block.getFloorX() + ", " + block.getFloorY() + ", " + block.getFloorZ());
                    break;
                case RIGHT_CLICK_BLOCK:
                    data.getSelection().setPosOne(block.getLocation().clone());
                    player.sendMessage("Selected first position at " +
                            block.getFloorX() + ", " + block.getFloorY() + ", " + block.getFloorZ());
                    break;
            }
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    private boolean isWandItem(Item item) {
        if (item == null || item.getId() != 271) return false;
        String customName = item.getCustomName();
        return customName != null && customName.equals(WAND_NAME);
    }
}
