package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionBoundsTest {
    final SelectionBounds b=new SelectionBounds(-10,10,-10,10,20,10);
    @Test void expandsAndContractsInFaweDirectionRatherThanSwappingFaces(){
        assertEquals(new SelectionBounds(-12,10,-10,13,20,10),b.directional("expand",3,2,new int[]{1,0,0}));
        assertEquals(new SelectionBounds(-10,13,-10,10,18,10),b.directional("contract",3,2,new int[]{0,1,0}));
        assertEquals(new SelectionBounds(-10,10,-8,10,20,7),b.directional("contract",3,2,new int[]{0,0,-1}));
        assertEquals(b.width(),b.directional("shift",4,0,new int[]{-1,0,0}).width());
    }
    @Test void symmetricEditsRespectAxesAndRejectCollapsingSelection(){
        assertEquals(new SelectionBounds(-12,10,-12,12,20,12),b.symmetric(2,false,true,false));
        assertEquals(new SelectionBounds(-10,12,-10,10,18,10),b.symmetric(2,true,false,true));
        assertThrows(IllegalArgumentException.class,()->b.symmetric(6,true,true,true));
        assertThrows(IllegalArgumentException.class,()->b.directional("contract",22,0,new int[]{1,0,0}));
        assertThrows(ArithmeticException.class,()->new SelectionBounds(0,0,0,Integer.MAX_VALUE,0,0).directional("shift",1,0,new int[]{1,0,0}));
    }
    @Test void volumeIsInclusiveAndCannotOverflowLong(){
        assertEquals(BigInteger.ONE,new SelectionBounds(0,0,0,0,0,0).volume());
        var huge=new SelectionBounds(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertEquals(BigInteger.ONE.shiftLeft(96),huge.volume());
    }
    @Test void atomicApplicationPreservesReversedCornerRolesAndRejectsWorldOverflow(){
        Level level=mock(Level.class);when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        Selection s=new Selection();s.setPosOne(new Position(10,20,10,level));s.setPosTwo(new Position(-10,10,-10,level));
        b.directional("expand",3,0,new int[]{1,0,0}).apply(s);
        assertEquals(13,s.getPosOne().getFloorX());assertEquals(-10,s.getPosTwo().getFloorX());
        Position first=s.getPosOne(),second=s.getPosTwo();
        assertThrows(IllegalArgumentException.class,()->b.vertical(-65,319).apply(s));
        assertSame(first,s.getPosOne());assertSame(second,s.getPosTwo());
        assertThrows(IllegalArgumentException.class,()->SelectionBounds.of(first,new Position(0,0,0,mock(Level.class))));
    }
}
