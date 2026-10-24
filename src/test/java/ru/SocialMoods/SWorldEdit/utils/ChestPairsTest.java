package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChestPairsTest {
    private CompoundTag half(int x,int peer) {
        return new CompoundTag().putString("id","Chest").putInt("x",x).putInt("y",70).putInt("z",0)
                .putInt("pairx",peer).putInt("pairz",0);
    }
    @Test void remapsOnlyMutualIncludedHalvesAndCanShiftEachStackCopy() {
        var plan=ChestPairs.plan(Map.of(0,half(0,1),1,half(1,0)),
                i -> new Vector3(10+i,80,20),Map.of());
        assertEquals(1,plan.size());
        assertEquals(10,Math.min(plan.get(0).first().x,plan.get(0).second().x));
        assertEquals(11,Math.max(plan.get(0).first().x,plan.get(0).second().x));
        var shifted=plan.get(0).shifted(4,0,0);
        assertEquals(14,Math.min(shifted.first().x,shifted.second().x));
    }
    @Test void partialOrCorruptPairsCannotLinkToSourceOrUnrelatedNeighbours() {
        assertTrue(ChestPairs.plan(Map.of(0,half(0,1)),i->new Vector3(10,70,0),Map.of()).isEmpty());
        assertTrue(ChestPairs.plan(Map.of(0,half(0,1),1,half(1,9)),i->new Vector3(10+i,70,0),Map.of()).isEmpty());
        assertTrue(ChestPairs.plan(Map.of(0,half(0,2),1,half(2,0)),i->new Vector3(10+i,70,0),Map.of()).isEmpty());
    }
    @Test void explicitUndoPeerMayReconnectButExternalOnlyPairsAreNeverModified() {
        var plan=ChestPairs.plan(Map.of(0,half(10,11)),i->new Vector3(10,70,0),
                Map.of(new ChestPairs.Point(11,70,0),half(11,10)));
        assertEquals(1,plan.size());
        assertTrue(ChestPairs.plan(Map.of(),i->new Vector3(),
                Map.of(new ChestPairs.Point(10,70,0),half(10,11),new ChestPairs.Point(11,70,0),half(11,10))).isEmpty());
    }
}
