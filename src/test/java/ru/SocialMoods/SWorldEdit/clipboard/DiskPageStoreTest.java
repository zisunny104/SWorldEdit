package ru.SocialMoods.SWorldEdit.clipboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DiskPageStoreTest {
    @TempDir Path directory;

    private Path onlyFile() throws IOException {
        try (var files = Files.list(directory)) { return files.findFirst().orElseThrow(); }
    }

    @Test void preservesPagesAndAllowsRepeatedStreamingReads() throws Exception {
        byte[] first = {0, 1, -1}, second = new byte[8192];
        new Random(1).nextBytes(second);
        try (var writer = DiskPageStore.writer(directory, 8192, 1_000_000, 0)) {
            writer.append(first);
            writer.append(second);
            try (var store = writer.finish()) {
                assertEquals(2, store.pageCount());
                assertEquals(8195, store.byteCount());
                for (int pass = 0; pass < 2; pass++) {
                    try (var reader = store.reader()) {
                        assertArrayEquals(first, reader.next());
                        assertArrayEquals(second, reader.next());
                        assertNull(reader.next());
                        assertNull(reader.next());
                    }
                }
            }
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void failedOrAbandonedWriterDoesNotPublish() throws Exception {
        try (var writer = DiskPageStore.writer(directory, 32, 100, 0)) {
            assertThrows(IOException.class, () -> writer.append(new byte[33]));
            writer.append(new byte[32]);
            assertThrows(IOException.class, () -> writer.append(new byte[32]));
            assertTrue(onlyFile().toString().endsWith(".partial"));
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void activeReadersPinFileAfterStoreIsReplaced() throws Exception {
        try (var writer = DiskPageStore.writer(directory, 32, 100, 0)) {
            writer.append(new byte[]{42});
            var store = writer.finish();
            var reader = store.reader();
            store.close();
            assertThrows(IOException.class, store::reader);
            assertTrue(Files.exists(onlyFile()));
            assertArrayEquals(new byte[]{42}, reader.next());
            assertNull(reader.next());
            reader.close();
            reader.close();
            store.close();
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void rejectsCorruptionAndTruncation() throws Exception {
        for (boolean truncate : new boolean[]{false, true}) {
            try (var writer = DiskPageStore.writer(directory, 32, 100, 0)) {
                writer.append(new byte[]{1, 2, 3});
                try (var store = writer.finish()) {
                    Path path = onlyFile();
                    byte[] data = Files.readAllBytes(path);
                    if (truncate) data = java.util.Arrays.copyOf(data, data.length - 1);
                    else data[20] ^= 1;
                    Files.write(path, data);
                    try (var reader = store.reader()) {
                        assertThrows(IOException.class, () -> { while (reader.next() != null) { } });
                    }
                }
            }
        }
    }

    @Test void rejectsOversizedLengthBeforeAllocation() throws Exception {
        try (var writer = DiskPageStore.writer(directory, 32, 100, 0)) {
            writer.append(new byte[]{1});
            try (var store = writer.finish()) {
                byte[] data = Files.readAllBytes(onlyFile());
                java.nio.ByteBuffer.wrap(data).putInt(12, Integer.MAX_VALUE);
                Files.write(onlyFile(), data);
                try (var reader = store.reader()) { assertThrows(IOException.class, reader::next); }
            }
        }
    }

    @Test void rejectsImpossibleReserveWithoutLeavingFiles() throws Exception {
        assertThrows(IOException.class, () -> DiskPageStore.writer(directory, 32, 100, Long.MAX_VALUE));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void manyPagesAreReadOneAtATime() throws Exception {
        byte[] page = new byte[4096];
        try (var writer = DiskPageStore.writer(directory, page.length, 10_000_000, 0)) {
            for (int i = 0; i < 1024; i++) { page[0] = (byte)i; writer.append(page); }
            try (var store = writer.finish(); var reader = store.reader()) {
                for (int i = 0; i < 1024; i++) assertEquals((byte)i, reader.next()[0]);
                assertNull(reader.next());
            }
        }
    }
}
