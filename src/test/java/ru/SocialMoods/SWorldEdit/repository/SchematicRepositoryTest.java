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
    private void write(String name, CompoundTag tag) throws Exception {
        try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(dir.resolve(name + ".schem")))) {
            NBTIO.write(tag, stream);
        }
    }
}
