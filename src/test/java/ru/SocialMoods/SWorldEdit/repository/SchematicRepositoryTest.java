package ru.SocialMoods.SWorldEdit.repository;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.SocialMoods.SWorldEdit.data.Schematic;
import ru.SocialMoods.SWorldEdit.repository.impl.SchematicRepositoryImpl;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SchematicRepositoryTest {
    @TempDir Path dir;
    @Test void highIdsAndMetadataSurviveDiskRoundTrip() throws Exception {
        SchematicRepositoryImpl repository = new SchematicRepositoryImpl(dir.toFile());
        int[] ids = {0, 255, 256, 792, 10001};
        int[] metadata = {0, 1, 255, 256, 65535};
        repository.save(new Schematic((short)5, (short)1, (short)1, ids, metadata), "modern");
        Schematic loaded = repository.load("modern");
        assertArrayEquals(ids, loaded.getBlocks());
        assertArrayEquals(metadata, loaded.getData());
    }
    @Test void legacyBytesRemainUnsigned() throws Exception {
        write("legacy", new CompoundTag().putShort("Width", 2).putShort("Height", 1)
                .putShort("Length", 1).putByteArray("Blocks", new byte[]{1, (byte)255})
                .putByteArray("Data", new byte[]{0, (byte)128}));
        Schematic loaded = new SchematicRepositoryImpl(dir.toFile()).load("legacy");
        assertArrayEquals(new int[]{1, 255}, loaded.getBlocks());
        assertArrayEquals(new int[]{0, 128}, loaded.getData());
    }
    @Test void malformedArraysAreRejectedBeforePlacement() throws Exception {
        write("broken", new CompoundTag().putShort("Width", 2).putShort("Height", 1)
                .putShort("Length", 1).putIntArray("BlockIds", new int[]{792})
                .putIntArray("BlockData", new int[]{0}));
        assertThrows(java.io.IOException.class,
                () -> new SchematicRepositoryImpl(dir.toFile()).load("broken"));
        assertThrows(IllegalArgumentException.class, () -> Schematic.volume(32768, 1, 1));
        assertThrows(ArithmeticException.class, () -> Schematic.volume(32767, 32767, 32767));
    }
    @Test void nestedNbtAndChestLinksSurviveDiskRoundTripWithoutSharing() throws Exception {
        var items = new cn.nukkit.nbt.tag.ListTag<CompoundTag>("Items");
        items.add(new CompoundTag().putByte("Slot",0).putString("Name","minecraft:diamond").putByte("Count",3));
        CompoundTag first = new CompoundTag().putString("id","Chest").putInt("x",100).putInt("y",70)
                .putInt("z",200).putInt("pairx",101).putInt("pairz",200).putList(items);
        CompoundTag second = new CompoundTag().putString("id","Chest").putInt("x",101).putInt("y",70)
                .putInt("z",200).putInt("pairx",100).putInt("pairz",200);
        CompoundTag sign = new CompoundTag().putString("id","Sign").putString("Text","§aHello\n世界")
                .putBoolean("IsGlowing",true).putCompound("FrontText",new CompoundTag().putString("Text","nested"));
        Schematic source=new Schematic((short)3,(short)1,(short)1,new int[]{54,54,63},new int[3],
                java.util.Map.of(0,first,1,second,2,sign));
        first.putInt("pairx",999); items.get(0).putByte("Count",99);
        var repository=new SchematicRepositoryImpl(dir.toFile()); repository.save(source,"nbt");
        Schematic loaded=repository.load("nbt");
        assertEquals(101,loaded.getBlockEntities().get(0).getInt("pairx"));
        assertEquals(3,loaded.getBlockEntities().get(0).getList("Items",CompoundTag.class).get(0).getByte("Count"));
        assertEquals(100,loaded.getBlockEntities().get(1).getInt("pairx"));
        assertEquals("§aHello\n世界",loaded.getBlockEntities().get(2).getString("Text"));
        assertTrue(loaded.getBlockEntities().get(2).getBoolean("IsGlowing"));
        assertEquals("nested",loaded.getBlockEntities().get(2).getCompound("FrontText").getString("Text"));
        assertTrue(new Schematic((short)1,(short)1,(short)1,new int[]{1},new int[]{0}).getBlockEntities().isEmpty());
    }
    @Test void invalidAndDuplicateEntityEntriesAreRejected() throws Exception {
        for(int index:new int[]{-1,1}) {
            var entries=new cn.nukkit.nbt.tag.ListTag<CompoundTag>("BlockEntities");
            entries.add(new CompoundTag().putInt("Index",index).putCompound("Data",new CompoundTag().putString("id","Chest")));
            write("badnbt",new CompoundTag().putShort("Width",1).putShort("Height",1).putShort("Length",1)
                    .putIntArray("BlockIds",new int[]{54}).putIntArray("BlockData",new int[]{0}).putList(entries));
            assertThrows(java.io.IOException.class,()->new SchematicRepositoryImpl(dir.toFile()).load("badnbt"));
        }
        var entries=new cn.nukkit.nbt.tag.ListTag<CompoundTag>("BlockEntities");
        for(int i=0;i<2;i++) entries.add(new CompoundTag().putInt("Index",0)
                .putCompound("Data",new CompoundTag().putString("id","Chest")));
        write("duplicate",new CompoundTag().putShort("Width",1).putShort("Height",1).putShort("Length",1)
                .putIntArray("BlockIds",new int[]{54}).putIntArray("BlockData",new int[]{0}).putList(entries));
        assertThrows(java.io.IOException.class,()->new SchematicRepositoryImpl(dir.toFile()).load("duplicate"));
    }
    private void write(String name, CompoundTag tag) throws Exception {
        try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(dir.resolve(name + ".schem")))) {
            NBTIO.write(tag, stream);
        }
    }
}
