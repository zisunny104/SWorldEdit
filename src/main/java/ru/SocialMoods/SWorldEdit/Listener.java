package ru.SocialMoods.SWorldEdit;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.types.PlayerActionType;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
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
    private record Point(int x, int y, int z) { }
    private final Map<cn.nukkit.Player, Set<Point>> pendingStarts = new WeakHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPacket(DataPacketReceiveEvent event) {
        boolean auth = event.getPacket() instanceof PlayerAuthInputPacket;
        boolean legacy = event.getPacket() instanceof PlayerActionPacket;
        if (!auth && !legacy) return;
        cn.nukkit.Player player = event.getPlayer();
        var inventory = player.getInventory();
        if (inventory == null || !player.hasPermission("we.command") || !isWandItem(inventory.getItemInHand())) {
            pendingStarts.remove(player);
            return;
        }
        Set<Point> starts = new HashSet<>();
        if (auth) {
            PlayerAuthInputPacket packet = (PlayerAuthInputPacket) event.getPacket();
            for (var action : packet.getBlockActionData().values()) {
                if (action.getAction() == PlayerActionType.START_DESTROY_BLOCK) {
                    var position = action.getPosition();
                    starts.add(new Point(position.x, position.y, position.z));
                }
            }
        } else {
            PlayerActionPacket packet = (PlayerActionPacket) event.getPacket();
            if (packet.action == PlayerActionPacket.ACTION_START_BREAK) {
                starts.add(new Point(packet.x, packet.y, packet.z));
            }
        }
        // The core can emit multiple interactions for one input and for held-button continues.
        // Permit each explicit start once; continued/predicted destruction cannot change selection.
        pendingStarts.put(player, starts);
    }

    private boolean acceptStart(cn.nukkit.Player player, Block block) {
        Set<Point> starts = pendingStarts.get(player);
        // Older cores/plugins may emit interactions without either observed input packet.
        return starts == null || starts.remove(new Point(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
    }

    private static boolean samePosition(cn.nukkit.level.Position position, Block block) {
        return position != null && position.level == block.getLevel() &&
                position.getFloorX() == block.getFloorX() && position.getFloorY() == block.getFloorY() &&
                position.getFloorZ() == block.getFloorZ();
    }

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
            if (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK && !acceptStart(player, block)) {
                return;
            }
            PlayerData data = playerData.apply(player);
            if (data == null || data.getSelection() == null) {
                return;
            }

            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK:
                    if (samePosition(data.getSelection().getPosTwo(), block)) return;
                    data.getSelection().setPosTwo(block.getLocation().clone());
                    player.sendMessage(String.format("Selected second position at %1$s, %2$s, %3$s", block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                    break;
                case RIGHT_CLICK_BLOCK:
                    if (samePosition(data.getSelection().getPosOne(), block)) return;
                    data.getSelection().setPosOne(block.getLocation().clone());
                    player.sendMessage(String.format("Selected first position at %1$s, %2$s, %3$s", block.getFloorX(), block.getFloorY(), block.getFloorZ()));
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


