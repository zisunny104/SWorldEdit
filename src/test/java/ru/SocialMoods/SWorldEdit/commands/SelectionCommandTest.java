package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.utils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionCommandTest {
    @Test void parsesDirectionsReverseAmountVerticalAndAxisFlags(){
        var a=SelectionCommand.parse(SelectionCommand.Kind.EXPAND,new String[]{"3","2","north"});
        assertEquals(3,a.amount());assertEquals(2,a.reverse());assertEquals("north",a.direction());
        assertTrue(SelectionCommand.parse(SelectionCommand.Kind.EXPAND,new String[]{"vert"}).fullHeight());
        assertEquals("up",SelectionCommand.parse(SelectionCommand.Kind.CONTRACT,new String[]{"2","up"}).direction());
        a=SelectionCommand.parse(SelectionCommand.Kind.OUTSET,new String[]{"2","-h"});assertTrue(a.horizontal());assertFalse(a.vertical());
        a=SelectionCommand.parse(SelectionCommand.Kind.INSET,new String[]{"2","-v"});assertFalse(a.horizontal());assertTrue(a.vertical());
        assertTrue(SelectionCommand.parse(SelectionCommand.Kind.SIZE,new String[]{"-c"}).clipboard());
        assertArrayEquals(new int[]{0,-1,0},SelectionCommand.direction(null,new Vector3(0,-1,0)));
        for(String[] bad:new String[][]{{"-1"},{"3","-2"},{"1","sideways"},{"2","north","4"},{"9999999999"}})
            assertThrows(IllegalArgumentException.class,()->SelectionCommand.parse(SelectionCommand.Kind.EXPAND,bad));
        assertThrows(IllegalArgumentException.class,()->SelectionCommand.parse(SelectionCommand.Kind.SHIFT,new String[]{"2","1"}));
        assertThrows(IllegalArgumentException.class,()->SelectionCommand.parse(SelectionCommand.Kind.INSET,new String[]{"1","-a"}));
    }
    @Test void updatesSelectionWithoutWorldEditsClipboardOrHistoryChanges(){
        Player p=mock(Player.class);Level level=mock(Level.class);when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        PlayerData data=new PlayerData(p);data.getSelection().setPosOne(new Position(0,70,0,level));data.getSelection().setPosTwo(new Position(4,74,4,level));
        data.copiedBlocks=new BlocksArray();BlocksArray clipboard=data.copiedBlocks;
        new SelectionCommand(SelectionCommand.Kind.EXPAND).execute(p,data,new String[]{"3","east"});
        assertEquals(7,data.getSelection().getPosTwo().getFloorX());assertSame(clipboard,data.copiedBlocks);assertTrue(data.undoSteps.isEmpty());
        verify(level,never()).getBlock(any(Vector3.class));verify(level,never()).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
        Position first=data.getSelection().getPosOne(),second=data.getSelection().getPosTwo();
        new SelectionCommand(SelectionCommand.Kind.SHIFT).execute(p,data,new String[]{"500","up"});
        assertSame(first,data.getSelection().getPosOne());assertSame(second,data.getSelection().getPosTwo());
        new SelectionCommand(SelectionCommand.Kind.EXPAND).execute(p,data,new String[]{"vert"});
        assertEquals(-64,data.getSelection().getPosOne().getFloorY());assertEquals(319,data.getSelection().getPosTwo().getFloorY());
    }
    @Test void clipboardSizeNeedsNoSelectionOrWorldAccess(){
        Player p=mock(Player.class);PlayerData data=new PlayerData(p);data.copiedBlocks=new BlocksArray();
        for(int x:new int[]{-2,4}){Block b=mock(Block.class);when(b.getFloorX()).thenReturn(x);when(b.getFloorY()).thenReturn(0);when(b.getFloorZ()).thenReturn(0);data.copiedBlocks.blocks.add(b);}
        new SelectionCommand(SelectionCommand.Kind.SIZE).execute(p,data,new String[]{"-c"});
        verify(p).sendMessage(contains("7"));assertFalse(data.hasSelection());assertTrue(data.undoSteps.isEmpty());
    }
}
