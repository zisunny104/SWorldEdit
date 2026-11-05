package ru.SocialMoods.SWorldEdit.utils;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import cn.nukkit.block.BlockAir;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class UndoHistoryTest {
    BlocksArray record(int cells){BlocksArray result=new BlocksArray();for(int i=0;i<cells;i++)result.blocks.add(new BlockAir());return result;}
    @Test void globalMemoryAndPerPlayerStepsEvictOldInactiveRecordsOnly(){
        List<BlocksArray> released=new ArrayList<>();var history=new UndoHistory(2,4,released::add);
        var a=new PlayerData(null,history);var b=new PlayerData(null,history);
        var first=record(3);a.addUndoBlocks(first);history.completed(a);
        var second=record(3);b.addUndoBlocks(second);assertTrue(a.undoSteps.isEmpty());assertTrue(released.contains(first));
        history.completed(b);a.addUndoBlocks(record(1));history.completed(a);a.addUndoBlocks(record(1));history.completed(a);a.addUndoBlocks(record(1));history.completed(a);
        assertEquals(2,a.undoSteps.size());history.close();assertTrue(a.undoSteps.isEmpty());assertTrue(b.undoSteps.isEmpty());
    }
    @Test void activePartialUndoIsPinnedAndOversizedRecordsAreRejectedBeforeEditing(){
        List<BlocksArray> released=new ArrayList<>();var history=new UndoHistory(2,3,released::add);
        var a=new PlayerData(null,history);var b=new PlayerData(null,history);
        var active=record(3);a.addUndoBlocks(active);b.addUndoBlocks(record(3));history.completed(b);
        assertTrue(a.undoSteps.contains(active));assertFalse(released.contains(active));
        assertThrows(IllegalArgumentException.class,()->history.checkMemoryRecord(4));history.close();
    }
}
