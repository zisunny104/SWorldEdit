package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.*;
import java.nio.file.*;
import java.util.zip.CRC32;

/** Session-scoped spool; one page is resident per reader, with no in-memory page index. */
public final class DiskPageStore implements PageStore {
    private static final int MAGIC = 0x53574550;
    private static final int FORMAT = 1;
    private static final long HEADER_BYTES = 12, FOOTER_BYTES = 20;
    private final Path path;
    private final int maxPageBytes;
    private final long pages, bytes;
    private int readers;
    private boolean closed;

    private DiskPageStore(Path path, int maxPageBytes, long pages, long bytes) {
        this.path = path;
        this.maxPageBytes = maxPageBytes;
        this.pages = pages;
        this.bytes = bytes;
    }

    /** A writer must be closed on failure; only finish() publishes a readable store. */
    public static Writer writer(Path directory, int maxPageBytes, long maxFileBytes,
                                long reserveBytes) throws IOException {
        return new Writer(directory, maxPageBytes, maxFileBytes, reserveBytes);
    }

    @Override public long pageCount() { return pages; }
    @Override public long byteCount() { return bytes; }

    @Override public synchronized Reader reader() throws IOException {
        if (closed) throw new IOException("Clipboard store is closed");
        DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT || input.readInt() != maxPageBytes)
                throw new IOException("Invalid clipboard page header");
        } catch (IOException e) { input.close(); throw e; }
        readers++;
        return new Reader() {
            long count, payload;
            boolean ended, released;
            @Override public byte[] next() throws IOException {
                if (released) throw new IOException("Clipboard reader is closed");
                if (ended) return null;
                try {
                    int size = input.readInt();
                    if (size == -1) {
                        if (input.readLong() != count || input.readLong() != payload
                                || count != pages || payload != bytes || input.read() != -1)
                            throw new IOException("Invalid clipboard completion marker");
                        ended = true;
                        return null;
                    }
                    if (size < 0 || size > maxPageBytes || count >= pages || size > bytes - payload)
                        throw new IOException("Invalid clipboard page length");
                    int expected = input.readInt();
                    byte[] page = new byte[size];
                    input.readFully(page);
                    if (checksum(page) != expected) throw new IOException("Clipboard page checksum mismatch");
                    count++;
                    payload += size;
                    return page;
                } catch (IOException e) {
                    try { close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                    throw e;
                }
            }
            @Override public void close() throws IOException {
                synchronized (DiskPageStore.this) {
                    if (released) return;
                    released = true;
                    try { input.close(); }
                    finally { readers--; deleteIfReleased(); }
                }
            }
        };
    }

    /** Existing readers pin the file until their operation has released it. */
    @Override public synchronized void close() throws IOException {
        closed = true;
        deleteIfReleased();
    }

    private void deleteIfReleased() throws IOException {
        if (closed && readers == 0) Files.deleteIfExists(path);
    }

    private static int checksum(byte[] page) {
        CRC32 crc = new CRC32();
        crc.update(page);
        return (int) crc.getValue();
    }

    public static final class Writer implements PageStore.Writer {
        private final Path partial;
        private final int maxPageBytes;
        private final long maxFileBytes, reserveBytes;
        private final FileOutputStream file;
        private final DataOutputStream output;
        private long pages, bytes, fileBytes = HEADER_BYTES;
        private boolean closed, published;

        private Writer(Path directory, int maxPageBytes, long maxFileBytes, long reserveBytes) throws IOException {
            if (maxPageBytes < 1 || maxFileBytes < HEADER_BYTES + FOOTER_BYTES || reserveBytes < 0)
                throw new IllegalArgumentException("Invalid clipboard disk budget");
            this.maxPageBytes = maxPageBytes;
            this.maxFileBytes = maxFileBytes;
            this.reserveBytes = reserveBytes;
            Files.createDirectories(directory);
            if (reserveBytes > Long.MAX_VALUE - HEADER_BYTES - FOOTER_BYTES
                    || Files.getFileStore(directory).getUsableSpace() < reserveBytes + HEADER_BYTES + FOOTER_BYTES)
                throw new IOException("Insufficient clipboard disk space");
            partial = Files.createTempFile(directory, "swe-clipboard-", ".partial");
            FileOutputStream opened = null;
            try {
                opened = new FileOutputStream(partial.toFile());
                file = opened;
                output = new DataOutputStream(new BufferedOutputStream(file));
                output.writeInt(MAGIC);
                output.writeInt(FORMAT);
                output.writeInt(maxPageBytes);
            } catch (IOException e) {
                if (opened != null) opened.close();
                Files.deleteIfExists(partial);
                throw e;
            }
        }

        /** Synchronous by design: a bounded worker queue supplies detached pages. */
        public void append(byte[] page) throws IOException {
            if (closed) throw new IOException("Clipboard writer is closed");
            if (page.length > maxPageBytes) throw new IOException("Clipboard page exceeds page byte limit");
            long additional = 8L + page.length;
            if (additional > maxFileBytes - fileBytes - FOOTER_BYTES)
                throw new IOException("Clipboard exceeds disk byte limit " + maxFileBytes);
            long available = Files.getFileStore(partial).getUsableSpace();
            if (available < reserveBytes || additional + FOOTER_BYTES > available - reserveBytes)
                throw new IOException("Insufficient clipboard disk space");
            output.writeInt(page.length);
            output.writeInt(checksum(page));
            output.write(page);
            output.flush();
            pages++;
            bytes += page.length;
            fileBytes += additional;
        }

        public DiskPageStore finish() throws IOException {
            if (closed) throw new IOException("Clipboard writer is closed");
            long available = Files.getFileStore(partial).getUsableSpace();
            if (available < reserveBytes || FOOTER_BYTES > available - reserveBytes)
                throw new IOException("Insufficient clipboard disk space");
            output.writeInt(-1);
            output.writeLong(pages);
            output.writeLong(bytes);
            output.flush();
            file.getFD().sync();
            output.close();
            closed = true;
            Path ready = partial.resolveSibling(partial.getFileName().toString().replace(".partial", ".ready"));
            // Unsupported atomic rename is an error: never publish a partly copied clipboard.
            Files.move(partial, ready, StandardCopyOption.ATOMIC_MOVE);
            published = true;
            return new DiskPageStore(ready, maxPageBytes, pages, bytes);
        }

        @Override public void close() throws IOException {
            try { if (!closed) { closed = true; output.close(); } }
            finally { if (!published) Files.deleteIfExists(partial); }
        }
    }
}
