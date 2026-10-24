package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolInfoTest {
    @Test void bindingsArePerPlayerAndDoNotModifyItems() {
        Item item=mock(Item.class);when(item.getId()).thenReturn(280);
        var first=new ToolBindings();var second=new ToolBindings();
        first.bind(item,ToolBindings.Mode.INFO);
        assertEquals(ToolBindings.Mode.INFO,first.mode(item,false));assertEquals(ToolBindings.Mode.NONE,second.mode(item,false));
        first.bind(item,ToolBindings.Mode.NONE);assertEquals(ToolBindings.Mode.NONE,first.mode(item,true));
        first.restoreWand(item);assertEquals(ToolBindings.Mode.SELECTION,first.mode(item,true));
        first.bind(item,ToolBindings.Mode.INFO);first.clear();assertEquals(ToolBindings.Mode.NONE,first.mode(item,false));
        verify(item,never()).setCustomName(anyString());
        assertThrows(IllegalArgumentException.class,()->first.bind(null,ToolBindings.Mode.INFO));
    }
    @Test void suggestedInputsMustResolveBackToTheExactBlock() throws Exception {
        BlockResolutionTest.initializeBlocks();
        Block stone=Block.get(1,0);
        String input=BlockInfo.input(stone,"minecraft:stone");assertNotNull(input);
        Block result=Utils.fromString(input,null);assertEquals(stone.getId(),result.getId());assertEquals(stone.getDamage(),result.getDamage());
        Block variant=Block.get(1,1);String variantInput=BlockInfo.input(variant,"minecraft:stone");
        assertNotNull(variantInput);assertEquals(variant.getDamage(),Utils.fromString(variantInput,null).getDamage());
        Block unsupported=mock(Block.class);when(unsupported.getId()).thenReturn(Integer.MAX_VALUE);
        assertNull(BlockInfo.input(unsupported,"minecraft:stone"));
    }
}
