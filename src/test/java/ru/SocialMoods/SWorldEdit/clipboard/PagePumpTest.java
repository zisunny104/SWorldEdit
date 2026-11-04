package ru.SocialMoods.SWorldEdit.clipboard;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class PagePumpTest {
    @Test void backpressureOwnsOnlyOnePageAndFinishWaitsForIt() throws Exception {
        var queue = new ArrayDeque<Runnable>();
        var pump = new PagePump(new MemoryPageStore.Writer(32, 1024), queue::add, 32);
        byte[] page = {42};
        assertTrue(pump.tryAppend(page));
        page[0] = 0;
        assertFalse(pump.tryAppend(page));
        assertEquals(1, queue.size());
        var done = pump.finish();
        assertFalse(done.isDone());
        while (!queue.isEmpty()) queue.remove().run();
        try (var store = done.join(); var reader = store.reader()) {
            assertArrayEquals(new byte[]{42}, reader.next());
            assertNull(reader.next());
        }
        pump.close();
        while (!queue.isEmpty()) queue.remove().run();
    }

    @Test void failedWriteRejectsFollowingPagesAndCanBeCleanedUp() {
        var queue = new ArrayDeque<Runnable>();
        boolean[] closed = {false};
        var writer = new PageStore.Writer() {
            public void append(byte[] page) throws IOException { throw new IOException("disk full"); }
            public PageStore finish() { fail("Must not publish after failed write"); return null; }
            public void close() { closed[0] = true; }
        };
        var pump = new PagePump(writer, queue::add, 32);
        assertTrue(pump.tryAppend(new byte[]{1}));
        queue.remove().run();
        assertThrows(CompletionException.class, () -> pump.tryAppend(new byte[]{2}));
        assertTrue(pump.finish().isCompletedExceptionally());
        pump.close();
        while (!queue.isEmpty()) queue.remove().run();
        assertTrue(closed[0]);
    }

    @Test void memoryStoreBoundsEmptyPageOverheadAndDoesNotShareArrays() throws Exception {
        try (var writer = new MemoryPageStore.Writer(32, 64)) {
            byte[] page = {7};
            writer.append(page);
            page[0] = 99;
            assertThrows(IOException.class, () -> writer.append(new byte[0]));
            try (var store = writer.finish()) {
                var first = store.reader();
                assertEquals(7, first.next()[0]);
                store.close();
                assertNull(first.next());
                first.close();
                assertThrows(IOException.class, store::reader);
            }
        }
    }
}
