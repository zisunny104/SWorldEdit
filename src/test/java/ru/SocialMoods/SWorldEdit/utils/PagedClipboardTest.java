package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.BlockUnknown;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PagedClipboardTest {
    @Test void keepsNativeExtendedMetadataAndDetachedNbt()throws Exception{
        var palette=new PagedClipboard.Palette(16);
        var nativeBlock=new BlockUnknown(1000,4095);
        int state=palette.capture(nativeBlock);
        var tag=new CompoundTag().putString("id","Sign").putString("Text","第一行\n第二行");
        var cells=PagedClipboard.decode(PagedClipboard.encode(List.of(new PagedClipboard.Cell(-5,7,3,state,tag,false))),16);
        var restored=palette.block(cells.getFirst());
        assertEquals(1000,restored.getId());assertEquals(4095,restored.getDamage());assertEquals(-5,restored.getFloorX());
        assertNull(restored.level);assertEquals("第一行\n第二行",cells.getFirst().tag().getString("Text"));
        cells.getFirst().tag().putString("Text","changed");assertEquals("第一行\n第二行",tag.getString("Text"));
        nativeBlock.setDamage(1);assertEquals(4095,palette.block(cells.getFirst()).getDamage());
    }
}
