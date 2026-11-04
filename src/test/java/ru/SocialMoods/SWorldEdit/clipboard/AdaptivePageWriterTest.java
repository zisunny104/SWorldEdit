package ru.SocialMoods.SWorldEdit.clipboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePageWriterTest {
    @TempDir Path directory;

    @Test void smallClipboardDoesNotTouchDisk() throws Exception {
        Path unused = directory.resolve("unused");
        try (var writer = new AdaptivePageWriter(unused, 32, 128, 1024, 0)) {
            writer.append(new byte[]{1});
            try (var store = writer.finish()) {
                assertInstanceOf(MemoryPageStore.class, store);
                assertFalse(Files.exists(unused));
            }
        }
    }

    @Test void spillsEarlierPagesInOrderAndSupportsRepeatedReads() throws Exception {
        try (var writer = new AdaptivePageWriter(directory, 32, 64, 1024, 0)) {
            writer.append(new byte[]{1});
            writer.append(new byte[]{2});
            writer.append(new byte[]{3});
            try (var store = writer.finish()) {
                assertInstanceOf(DiskPageStore.class, store);
                assertEquals(3, store.byteCount());
                for (int i = 0; i < 2; i++) {
                    try (var reader = store.reader()) {
                        for (int value = 1; value <= 3; value++) assertArrayEquals(new byte[]{(byte)value}, reader.next());
                        assertNull(reader.next());
                    }
                }
            }
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void failedSpillCannotPublishAndRemovesPartialFile() throws Exception {
        try (var writer = new AdaptivePageWriter(directory, 32, 64, 40, 0)) {
            writer.append(new byte[]{1});
            assertThrows(IOException.class, () -> writer.append(new byte[]{2}));
            assertThrows(IOException.class, writer::finish);
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
