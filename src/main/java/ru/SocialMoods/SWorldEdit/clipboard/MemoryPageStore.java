package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Small-clipboard implementation of the same detached-page contract. */
public final class MemoryPageStore implements PageStore {
    private List<byte[]> pages;
    private final long bytes;
    private final long count;

    private MemoryPageStore(List<byte[]> pages, long bytes) {
        this.pages = List.copyOf(pages);
        this.bytes = bytes;
        this.count = pages.size();
    }

    @Override public long pageCount() { return count; }
    @Override public long byteCount() { return bytes; }
    @Override public synchronized Reader reader() throws IOException {
        if (pages == null) throw new IOException("Clipboard store is closed");
        return new Reader() {
            List<byte[]> pinned = pages;
            int index;
            @Override public byte[] next() throws IOException {
                if (pinned == null) throw new IOException("Clipboard reader is closed");
                return index == pinned.size() ? null : pinned.get(index++).clone();
            }
            @Override public void close() { pinned = null; }
        };
    }
    @Override public synchronized void close() {
        pages = null;
    }

    public static final class Writer implements PageStore.Writer {
        private final int maxPageBytes;
        private final long maxBytes;
        private List<byte[]> pages = new ArrayList<>();
        private long bytes;

        public Writer(int maxPageBytes, long maxBytes) {
            if (maxPageBytes < 1 || maxBytes < 0) throw new IllegalArgumentException("Invalid clipboard memory budget");
            this.maxPageBytes = maxPageBytes;
            this.maxBytes = maxBytes;
        }
        public void append(byte[] page) throws IOException {
            if (pages == null) throw new IOException("Clipboard writer is closed");
            if (page.length > maxPageBytes || page.length > maxBytes - bytes)
                throw new IOException("Clipboard exceeds memory byte limit " + maxBytes);
            // Also budget per-page reference/array overhead, including zero-length pages.
            if (32L > maxBytes - bytes - page.length)
                throw new IOException("Clipboard exceeds memory byte limit " + maxBytes);
            pages.add(page.clone());
            bytes += page.length + 32L;
        }
        public MemoryPageStore finish() throws IOException {
            if (pages == null) throw new IOException("Clipboard writer is closed");
            long payload = bytes - pages.size() * 32L;
            MemoryPageStore result = new MemoryPageStore(pages, payload);
            pages = null;
            return result;
        }
        @Override public void close() { pages = null; }
    }
}
