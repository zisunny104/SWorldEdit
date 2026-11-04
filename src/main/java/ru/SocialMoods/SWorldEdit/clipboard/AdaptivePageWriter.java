package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.IOException;
import java.nio.file.Path;

/** Worker-thread writer: small jobs remain in memory; larger jobs spill without rereading the world. */
public final class AdaptivePageWriter implements PageStore.Writer {
    private final Path directory;
    private final int maxPageBytes;
    private final long memoryBytes, diskBytes, reserveBytes;
    private PageStore.Writer writer;
    private long residentBytes;
    private boolean disk, closed;

    public AdaptivePageWriter(Path directory, int maxPageBytes, long memoryBytes,
                              long diskBytes, long reserveBytes) {
        if (maxPageBytes < 1 || memoryBytes < 0 || diskBytes < 32 || reserveBytes < 0)
            throw new IllegalArgumentException("Invalid clipboard storage budget");
        this.directory = java.util.Objects.requireNonNull(directory);
        this.maxPageBytes = maxPageBytes;
        this.memoryBytes = memoryBytes;
        this.diskBytes = diskBytes;
        this.reserveBytes = reserveBytes;
        writer = new MemoryPageStore.Writer(maxPageBytes, memoryBytes);
    }

    @Override public void append(byte[] page) throws IOException {
        checkOpen();
        if (page.length > maxPageBytes) throw new IOException("Clipboard page exceeds page byte limit");
        try {
            if (!disk && page.length + 32L > memoryBytes - residentBytes) spill();
            writer.append(page);
            if (!disk) residentBytes += page.length + 32L;
        } catch (IOException | RuntimeException e) {
            try { close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private void spill() throws IOException {
        // Open the disk file first: a space/permission failure does not publish partial content.
        var destination = DiskPageStore.writer(directory, maxPageBytes, diskBytes, reserveBytes);
        try (var source = writer.finish(); var reader = source.reader()) {
            byte[] page;
            while ((page = reader.next()) != null) destination.append(page);
        } catch (IOException | RuntimeException e) {
            try { destination.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        writer = destination;
        disk = true;
        residentBytes = 0;
    }

    @Override public PageStore finish() throws IOException {
        checkOpen();
        try {
            PageStore result = writer.finish();
            closed = true;
            return result;
        } catch (IOException | RuntimeException e) {
            try { close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("Clipboard writer is closed");
    }

    @Override public void close() throws IOException {
        if (!closed) { closed = true; writer.close(); }
    }
}
