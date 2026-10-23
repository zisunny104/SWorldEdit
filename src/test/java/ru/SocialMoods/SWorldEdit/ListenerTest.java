package ru.SocialMoods.SWorldEdit;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.BlockFace;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.utils.Selection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListenerTest {
    private Player player;
    private Item wand;
    private Block block;
    private Level level;
    private PlayerData data;
    private Selection selection;
    private Listener listener;

    @BeforeEach void prepare() {
        player = mock(Player.class);
        when(player.hasPermission("we.command")).thenReturn(true);
        wand = mock(Item.class);
        when(wand.getId()).thenReturn(271);
        when(wand.getCustomName()).thenReturn("WorldEdit Wand Axe");
        block = mock(Block.class);
        level = mock(Level.class);
        when(block.getLevel()).thenReturn(level);
        when(block.getLocation()).thenReturn(new Location(2, 70, 4, level));
        when(block.getFloorX()).thenReturn(2);
        when(block.getFloorY()).thenReturn(70);
        when(block.getFloorZ()).thenReturn(4);
        data = mock(PlayerData.class);
        selection = new Selection();
        when(data.getSelection()).thenReturn(selection);
        listener = new Listener(p -> data);
    }

    private PlayerInteractEvent click(PlayerInteractEvent.Action action) {
        return new PlayerInteractEvent(player, wand, block, BlockFace.UP, action);
    }

    @Test void laterBreakPacketCannotReplaceTheClickedPosition() {
        PlayerInteractEvent click = click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
        listener.onInteract(click);
        assertTrue(click.isCancelled());
        assertEquals(2, selection.getPosTwo().getFloorX());
        var selected = selection.getPosTwo();
        BlockBreakEvent later = mock(BlockBreakEvent.class);
        when(later.getPlayer()).thenReturn(player);
        when(later.getItem()).thenReturn(wand);
        listener.onBlockBreak(later);
        verify(later).setCancelled(true);
        verify(later, never()).getBlock();
        assertSame(selected, selection.getPosTwo());
        verify(player, times(1)).sendMessage(anyString());
    }

    @Test void rightClickKeepsTheExistingFirstPositionMapping() {
        PlayerInteractEvent event = click(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        assertEquals(2, selection.getPosOne().getFloorX());
        assertNull(selection.getPosTwo());
        verify(player, never()).getInventory();
    }

    @Test void leftClickRestoresThePredictedBlockAndItsBlockEntity() {
        BlockEntitySpawnable entity = mock(BlockEntitySpawnable.class);
        when(level.getBlockEntity(block)).thenReturn(entity);
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        verify(level).sendBlocks(eq(player), any(Block[].class), eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
        verify(entity).spawnTo(player);
        assertEquals(2, selection.getPosTwo().getFloorX());
    }

    @Test void unavailableSelectionDataStillCancelsTheInteractionAndBreak() {
        listener = new Listener(p -> null);
        PlayerInteractEvent event = click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        assertNull(selection.getPosTwo());
        BlockBreakEvent broken = mock(BlockBreakEvent.class);
        when(broken.getPlayer()).thenReturn(player);
        when(broken.getItem()).thenReturn(wand);
        listener.onBlockBreak(broken);
        verify(broken).setCancelled(true);
    }

    @Test void dataFailureCannotAllowTheWandToStartBreaking() {
        listener = new Listener(p -> { throw new IllegalStateException("missing data"); });
        PlayerInteractEvent event = click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        assertNull(selection.getPosTwo());
    }

    @Test void airClicksDoNotWriteOrCancelASelection() {
        PlayerInteractEvent event = click(PlayerInteractEvent.Action.LEFT_CLICK_AIR);
        listener.onInteract(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(data);
    }

    @Test void ordinaryAxesAndPlayersWithoutPermissionAreUnaffected() {
        when(wand.getCustomName()).thenReturn("");
        PlayerInteractEvent ordinary = click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
        listener.onInteract(ordinary);
        assertFalse(ordinary.isCancelled());
        when(wand.getCustomName()).thenReturn("WorldEdit Wand Axe");
        when(player.hasPermission("we.command")).thenReturn(false);
        PlayerInteractEvent denied = click(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK);
        listener.onInteract(denied);
        assertFalse(denied.isCancelled());
        verifyNoInteractions(data);
    }

    private void input(cn.nukkit.network.protocol.types.PlayerActionType action, int x) {
        var inventory = mock(cn.nukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInHand()).thenReturn(wand);
        var packet = mock(cn.nukkit.network.protocol.PlayerAuthInputPacket.class);
        var data = new cn.nukkit.network.protocol.types.PlayerBlockActionData(action,
                new cn.nukkit.math.BlockVector3(x, 70, 4), 1);
        when(packet.getBlockActionData()).thenReturn(java.util.Map.of(action, data));
        listener.onPacket(new cn.nukkit.event.server.DataPacketReceiveEvent(player, packet));
    }

    @Test void authInputStartCanOnlySelectOnceAndContinueCannotSelectBehindIt() {
        input(cn.nukkit.network.protocol.types.PlayerActionType.START_DESTROY_BLOCK, 2);
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        var original = selection.getPosTwo();
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        assertSame(original, selection.getPosTwo());
        when(block.getFloorX()).thenReturn(3);
        when(block.getLocation()).thenReturn(new Location(3, 70, 4, level));
        input(cn.nukkit.network.protocol.types.PlayerActionType.CONTINUE_DESTROY_BLOCK, 3);
        PlayerInteractEvent continued = click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
        listener.onInteract(continued);
        assertTrue(continued.isCancelled());
        assertSame(original, selection.getPosTwo());
        verify(player, times(1)).sendMessage(anyString());
        // A separate explicit start is allowed immediately, without a time-based cooldown.
        input(cn.nukkit.network.protocol.types.PlayerActionType.START_DESTROY_BLOCK, 3);
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        assertEquals(3, selection.getPosTwo().getFloorX());
        verify(player, times(2)).sendMessage(anyString());
    }

    @Test void repeatedSamePointDoesNotSpamMessagesOnEitherButton() {
        listener.onInteract(click(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK));
        var first = selection.getPosOne();
        listener.onInteract(click(PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK));
        assertSame(first, selection.getPosOne());
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        var second = selection.getPosTwo();
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        assertSame(second, selection.getPosTwo());
        verify(player, times(2)).sendMessage(anyString());
    }

    @Test void predictedDestructionCannotSelectEvenWhenTargetChanges() {
        input(cn.nukkit.network.protocol.types.PlayerActionType.PREDICT_DESTROY_BLOCK, 2);
        listener.onInteract(click(PlayerInteractEvent.Action.LEFT_CLICK_BLOCK));
        assertNull(selection.getPosTwo());
        verify(player, never()).sendMessage(anyString());
        verify(level).sendBlocks(eq(player), any(Block[].class), eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
    }
}
