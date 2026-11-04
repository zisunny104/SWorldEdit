package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** One in-flight detached page per job. Callers yield the tick when tryAppend returns false. */
public final class PagePump implements AutoCloseable {
    private final PageStore.Writer writer;
    private final Executor executor;
    private final int maxPageBytes;
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private boolean finished, closed;

    public PagePump(PageStore.Writer writer, Executor executor, int maxPageBytes) {
        if (maxPageBytes < 1) throw new IllegalArgumentException("Invalid page byte budget");
        this.writer = java.util.Objects.requireNonNull(writer);
        this.executor = java.util.Objects.requireNonNull(executor);
        this.maxPageBytes = maxPageBytes;
    }

    public synchronized boolean tryAppend(byte[] page) {
        checkWritable();
        if (!pending.isDone()) return false;
        pending.join(); // Completed failures propagate; never enqueue more after an I/O error.
        if (page.length > maxPageBytes) throw new IllegalArgumentException("Clipboard page exceeds page byte limit");
        byte[] detached = page.clone();
        pending = CompletableFuture.runAsync(() -> {
            try { writer.append(detached); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        }, executor);
        return true;
    }

    /** Ordered after the final write, without blocking the calling server thread. */
    public synchronized CompletableFuture<PageStore> finish() {
        checkWritable();
        finished = true;
        CompletableFuture<PageStore> result = pending.thenApplyAsync(ignored -> {
            try { return writer.finish(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        }, executor);
        pending = result.thenApply(store -> null);
        return result;
    }

    private void checkWritable() {
        if (closed || finished) throw new IllegalStateException("Clipboard page pump is closed");
    }

    /** Returns immediately; cleanup runs after the accepted write has stopped. */
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        pending = pending.handleAsync((ignored, failure) -> {
            try { writer.close(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            return null;
        }, executor);
    }
}
