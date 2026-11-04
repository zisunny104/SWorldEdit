package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.clipboard.*;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.preview.Previews;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Server-thread world access; a single bounded worker owns all spool I/O and compression. */
public final class StreamingClipboard implements AutoCloseable {
    public record Settings(Path cache, int maxBlocks, int batch, int pageCells, int maxStates,
                           long memoryBytes, long diskBytes, long reserveBytes,
                           long tickNanos, int maxJobs, int maxHistory, int maxStores) {
        public Settings {
            if (maxBlocks < 1 || batch < 1 || pageCells < 1 || pageCells > 4096 || maxStates < 1
                    || memoryBytes < 0 || diskBytes < 32 || reserveBytes < 0 || tickNanos < 1
                    || maxJobs < 1 || maxJobs > 8 || maxHistory < 1 || maxStores < 2)
                throw new IllegalArgumentException("Invalid clipboard.streaming settings");
        }
    }
    private final Settings settings;
    private final Path session;
    private final Consumer<Runnable> schedule;
    private final Predicate<Level> available;
    private final ExecutorService io;
    private final CompletableFuture<ClipboardCache> cache;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Set<PageStore> stores = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile boolean closed;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private boolean ticking;

    public StreamingClipboard(Settings settings, Consumer<Runnable> schedule, Predicate<Level> available) {
        this.settings = settings; this.schedule = schedule; this.available = available;
        session = settings.cache.resolve("session-" + UUID.randomUUID());
        io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "SWorldEdit-clipboard-io"); thread.setDaemon(true); return thread;
        });
        cache = CompletableFuture.supplyAsync(() -> {
            try { return ClipboardCache.open(settings.cache, session, message -> BlockEntities.report(message, new IOException(message))); }
            catch (IOException e) { throw new CompletionException(e); }
        }, io);
    }

    public CompletableFuture<Integer> copy(PlayerData owner, Position first, Position second, Vector3 origin, BlocksArray output) {
        if (first.level == null || first.level != second.level) throw new IllegalArgumentException("Select positions in the same world");
        SelectionBounds bounds = SelectionBounds.of(first, second);
        long volume;
        try { volume = bounds.volume().longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException(Messages.text("streaming.limit", bounds.volume(), settings.maxBlocks)); }
        if (volume > settings.maxBlocks) throw new IllegalArgumentException(Messages.text("streaming.limit", volume, settings.maxBlocks));
        checkHeight(first.level, bounds);
        int ox = origin.getFloorX(), oy = origin.getFloorY(), oz = origin.getFloorZ();
        SelectionBounds relative = new SelectionBounds(Math.subtractExact(bounds.minX(), ox), Math.subtractExact(bounds.minY(), oy),
                Math.subtractExact(bounds.minZ(), oz), Math.subtractExact(bounds.maxX(), ox), Math.subtractExact(bounds.maxY(), oy), Math.subtractExact(bounds.maxZ(), oz));
        return enqueue(new Copy(owner, first.level, bounds, relative, ox, oy, oz, (int)volume, output));
    }

    public CompletableFuture<Integer> paste(PlayerData owner, Position center, BlocksArray source, boolean skipAir) {
        return paste(owner, center, source, skipAir, true);
    }

    private CompletableFuture<Integer> paste(PlayerData owner, Position center, BlocksArray source, boolean skipAir, boolean undo) {
        if (source.paged == null) throw new IllegalArgumentException("Not a paged clipboard");
        if (center.level == null) throw new IllegalArgumentException("Invalid paste world");
        SelectionBounds b = source.paged.bounds;
        int x = center.getFloorX(), y = center.getFloorY(), z = center.getFloorZ();
        SelectionBounds target = new SelectionBounds(Math.addExact(b.minX(), x), Math.addExact(b.minY(), y), Math.addExact(b.minZ(), z),
                Math.addExact(b.maxX(), x), Math.addExact(b.maxY(), y), Math.addExact(b.maxZ(), z));
        checkHeight(center.level, target);
        return enqueue(new Paste(owner, center.level, source, x, y, z, skipAir, undo));
    }

    public CompletableFuture<Integer> undo(PlayerData owner, int step) {
        if (step < 0 || step >= owner.undoSteps.size()) return CompletableFuture.completedFuture(0);
        BlocksArray source = owner.undoSteps.get(step);
        return paste(owner, new Position(0, 0, 0, source.undoLevel), source, false, false).thenApply(count -> {
            owner.undoSteps.remove(source); release(source); return count;
        });
    }

    private void checkHeight(Level level, SelectionBounds bounds) {
        if (bounds.minY() < level.getMinBlockY() || bounds.maxY() > level.getMaxBlockY())
            throw new IllegalArgumentException(Messages.text("smooth.world-height", level.getMinBlockY(), level.getMaxBlockY()));
    }

    private CompletableFuture<Integer> enqueue(Job job) {
        if (closed) throw new IllegalStateException("Clipboard service is closed");
        if (jobs.stream().anyMatch(existing -> existing.owner == job.owner)) throw new IllegalStateException(Messages.text("streaming.busy"));
        boolean allocates = job instanceof Copy || job instanceof Paste paste && paste.recordUndo;
        boolean replaces = job instanceof Copy && job.owner.copiedBlocks != null && job.owner.copiedBlocks.paged != null;
        if (jobs.size() >= settings.maxJobs || allocates && !replaces && stores.size() >= settings.maxStores)
            throw new IllegalStateException(Messages.text("streaming.queue-full"));
        jobs.add(job);
        job.progress("streaming.queued",jobs.size(),0);
        if (!ticking) { ticking = true; schedule.accept(this::tick); }
        return job.result;
    }

    private void tick() {
        if (closed) return;
        Job job = jobs.peek();
        if (job == null) { ticking = false; return; }
        try {
            if (job.cancelled || !available.test(job.level)) throw new CancellationException(Messages.text("streaming.cancelled"));
            int position=0;
            for(Job queued:jobs){if(position++>0)queued.progress("streaming.queued",position,0);}
            if (!cache.isDone()) { job.progress("work.phase.cache",0,0);schedule.accept(this::tick);return; }
            cache.join();
            job.publish();
            job.step(System.nanoTime() + settings.tickNanos);
        } catch (Exception | LinkageError e) { job.fail(e); }
        if (job.result.isDone()) { jobs.remove(job); job.cleanup(); }
        try { schedule.accept(this::tick); }
        catch (RuntimeException e) { close(); throw e; }
    }

    public void cancel(PlayerData owner) { jobs.stream().filter(job -> job.owner == owner).forEach(job -> job.cancelled = true); }
    public CompletableFuture<Void> termination() { return termination; }

    public void release(BlocksArray clipboard) {
        if (clipboard == null || clipboard.paged == null) return;
        PageStore store = clipboard.paged.store;
        stores.remove(store);
        io.execute(() -> safeClose(store));
    }

    private static void safeClose(AutoCloseable resource) {
        if (resource != null) try { resource.close(); }
        catch (Exception e) { BlockEntities.report("Clipboard cleanup failed", e); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Job job : jobs) { job.cancelled = true; job.fail(new CancellationException(Messages.text("streaming.cancelled"))); job.cleanup(); }
        jobs.clear();
        List<PageStore> retained = List.copyOf(stores);
        io.execute(() -> retained.forEach(StreamingClipboard::safeClose));
        stores.clear();
        io.execute(() -> {
            try { cache.join().close(); termination.complete(null); }
            catch (Exception e) { BlockEntities.report("Clipboard cache cleanup failed", e); termination.completeExceptionally(e); }
        });
        io.shutdown(); // Drain accepted I/O and cleanup; do not block the server thread waiting for disk.
    }

    private abstract class Job {
        final PlayerData owner;
        final Level level;
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        CompletableFuture<?> pending;
        Consumer<Object> continuation;
        boolean cancelled;
        PageStore.Writer writer;
        PageStore.Reader reader;

        Job(PlayerData owner, Level level) { this.owner = owner; this.level = level; }
        abstract void step(long deadline) throws Exception;
        abstract void publish();

        @SuppressWarnings("unchecked")
        <T> void submit(Callable<T> action, Consumer<T> after) {
            continuation = value -> after.accept((T)value);
            pending = CompletableFuture.supplyAsync(() -> {
                try { return action.call(); }
                catch (Exception e) { throw new CompletionException(e); }
            }, io);
        }

        boolean ready() {
            if (pending == null) return true;
            if (!pending.isDone()) return false;
            Object value = pending.join();
            Consumer<Object> after = continuation;
            pending = null; continuation = null;
            after.accept(value);
            return pending == null;
        }

        PageStore.Writer newWriter() {
            return new AdaptivePageWriter(session, PagedClipboard.PAGE_BYTES, settings.memoryBytes, settings.diskBytes, settings.reserveBytes);
        }

        void flush(List<PagedClipboard.Cell> page) {
            List<PagedClipboard.Cell> detached = List.copyOf(page);
            page.clear();
            submit(() -> { writer.append(PagedClipboard.encode(detached)); return null; }, ignored -> { });
        }

        void progress(String stage, int count, int total) {
            owner.getWorkProgress().report(stage,count,total);
        }

        void fail(Throwable failure) {
            if (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
            result.completeExceptionally(failure);
        }

        void cleanup() {
            // A finish may have produced a store which the server thread never got to publish.
            if (pending != null) pending.whenComplete((value, error) -> { if (value instanceof PageStore store) safeClose(store); });
            io.execute(() -> { safeClose(reader); safeClose(writer); });
        }
    }

    private final class Copy extends Job {
        final SelectionBounds absolute, relative;
        final int ox, oy, oz, total;
        final BlocksArray output;
        final PagedClipboard.Palette palette = new PagedClipboard.Palette(settings.maxStates);
        final List<PagedClipboard.Cell> page = new ArrayList<>();
        int index;
        boolean finishing;

        Copy(PlayerData owner, Level level, SelectionBounds absolute, SelectionBounds relative, int ox, int oy, int oz, int total, BlocksArray output) {
            super(owner, level); this.absolute = absolute; this.relative = relative; this.ox = ox; this.oy = oy; this.oz = oz; this.total = total; this.output = output;
            writer = newWriter();
        }

        @Override void step(long deadline) throws Exception {
            if (!ready() || finishing) return;
            publish();
            int budget = settings.batch;
            while (index < total && budget-- > 0 && System.nanoTime() < deadline) {
                int x = (int)(absolute.minX() + index % absolute.width());
                int z = (int)(absolute.minZ() + index / absolute.width() % absolute.length());
                int y = (int)(absolute.minY() + index / absolute.width() / absolute.length());
                Vector3 point = new Vector3(x, y, z);
                var block = level.getBlock(point);
                CompoundTag tag = BlockEntities.capture(level, point, block);
                page.add(new PagedClipboard.Cell(Math.subtractExact(x, ox), Math.subtractExact(y, oy), Math.subtractExact(z, oz),
                        palette.capture(block), tag, validPair(level, tag, peer -> contains(absolute, peer))));
                index++;
                if (page.size() == settings.pageCells) { flush(page); return; }
            }
            publish();
            if (index == total) {
                if (!page.isEmpty()) { flush(page); return; }
                finishing = true;
                submit(writer::finish, store -> {
                    stores.add(store);
                    output.paged = new PagedClipboard(store, palette, relative, total);
                    result.complete(total);
                });
            }
        }
        @Override void publish(){progress(finishing?"work.phase.finalize":pending!=null&&!pending.isDone()?"work.phase.io":"streaming.copy",index,total);}
    }

    private final class Paste extends Job {
        final BlocksArray source;
        final int ox, oy, oz, visible;
        final boolean skipAir, recordUndo;
        final BlocksArray undo = new BlocksArray();
        final PagedClipboard.Palette beforePalette = new PagedClipboard.Palette(settings.maxStates);
        final List<PagedClipboard.Cell> before = new ArrayList<>();
        List<PagedClipboard.Cell> page;
        int cursor, seen, eligible, attempted, phase;
        boolean eof, registered;
        SelectionBounds undoBounds;
        Previews.Bounds affected;

        Paste(PlayerData owner, Level level, BlocksArray source, int ox, int oy, int oz, boolean skipAir, boolean recordUndo) {
            super(owner, level); this.source = source; this.ox = ox; this.oy = oy; this.oz = oz; this.skipAir = skipAir; this.recordUndo = recordUndo;
            visible = source.size();
            if (recordUndo) writer = newWriter();
        }

        Vector3 target(PagedClipboard.Cell cell) {
            return new Vector3(Math.addExact(cell.x(), ox), Math.addExact(cell.y(), oy), Math.addExact(cell.z(), oz));
        }

        void nextPage() {
            submit(() -> {
                if (reader == null) reader = source.paged.store.reader();
                byte[] bytes = reader.next();
                return bytes == null ? null : PagedClipboard.decode(bytes, settings.pageCells);
            }, cells -> { page = cells; cursor = 0; eof = cells == null; });
        }

        void restart(int nextPhase) {
            submit(() -> { safeClose(reader); reader = null; return null; }, ignored -> {
                phase = nextPhase; page = null; cursor = 0; seen = 0; eof = false;
            });
        }

        @Override void step(long deadline) throws Exception {
            if (!ready()) return;
            if (phase == 4) return;
            publish();
            if (page == null && !eof) { nextPage(); return; }
            int budget = settings.batch;
            while (page != null && cursor < page.size() && budget-- > 0 && System.nanoTime() < deadline) {
                PagedClipboard.Cell cell = page.get(cursor++);
                seen++;
                if (seen > source.paged.count) throw new IOException("Clipboard contains more records than its metadata");
                if (seen > visible) continue;
                int blockId = source.paged.palette.blockId(cell); // Validate state without cloning during read-only passes.
                if (skipAir && blockId == 0) continue;
                Vector3 point = target(cell);
                if (phase == 0) {
                    eligible++;
                    if (recordUndo) {
                        var old = level.getBlock(point);
                        CompoundTag tag = BlockEntities.capture(level, point, old);
                        before.add(new PagedClipboard.Cell(point.getFloorX(), point.getFloorY(), point.getFloorZ(), beforePalette.capture(old), tag,
                                validPair(level, tag, peer -> true)));
                        undoBounds = include(undoBounds, point);
                        if (before.size() == settings.pageCells) { flush(before); return; }
                    }
                } else if (phase == 1) {
                    if (recordUndo && !registered) {
                        undo.applied = 0; undo.undoLevel = level; owner.addUndoBlocks(undo); registered = true;
                        while (owner.undoSteps.size() > settings.maxHistory) release(owner.undoSteps.remove(0));
                    }
                    attempted++;
                    if (recordUndo) undo.applied = attempted; // Include a cell even if its placement fails halfway.
                    BlockEntities.place(level, point, source.paged.palette.block(cell), cell.tag());
                    affected = Previews.Bounds.include(affected, point);
                } else if (phase == 2 && cell.pair()) {
                    var tag = cell.tag();
                    Vector3 other = new Vector3(Math.toIntExact((long)point.getFloorX() + tag.getInt("pairx") - tag.getInt("x")),
                            point.getFloorY(), Math.toIntExact((long)point.getFloorZ() + tag.getInt("pairz") - tag.getInt("z")));
                    ChestPairs.apply(level, new ChestPairs.Link(point, other));
                } else if (phase == 3) {
                    BlockEntities.verify(level, point, source.paged.palette.block(cell), cell.tag());
                    for (int side = 0; side < 7; side++) Connections.refresh(level, point, side);
                    budget -= 7;
                }
            }
            publish();
            if (page != null && cursor == page.size()) { page = null; return; }
            if (!eof) return;
            if (seen != source.paged.count) throw new IOException("Clipboard record count does not match metadata");
            if (phase == 0) {
                if (!before.isEmpty()) { flush(before); return; }
                if (eligible == 0) { result.complete(0); return; }
                if (recordUndo) {
                    phase = 4;
                    submit(writer::finish, store -> {
                        stores.add(store); undo.paged = new PagedClipboard(store, beforePalette, undoBounds, eligible);
                        restart(1);
                    });
                } else restart(1);
            } else if (phase < 3) restart(phase + 1);
            else {
                if (recordUndo && attempted > 0) Previews.pasted(owner.getPlayer(), level, affected);
                else if (!recordUndo) Previews.clearPaste(owner.getPlayer());
                result.complete(attempted);
            }
        }

        @Override void cleanup() {
            super.cleanup();
            if (!registered && undo.paged != null) release(undo);
        }
        @Override void publish(){progress(phase==4?"work.phase.finalize":pending!=null&&!pending.isDone()?"work.phase.io":"streaming.phase."+phase,Math.min(seen,visible),visible);}
    }

    private static boolean contains(SelectionBounds bounds, ChestPairs.Point point) {
        return point.x() >= bounds.minX() && point.x() <= bounds.maxX() && point.y() >= bounds.minY() && point.y() <= bounds.maxY()
                && point.z() >= bounds.minZ() && point.z() <= bounds.maxZ();
    }

    private static boolean validPair(Level level, CompoundTag tag, Predicate<ChestPairs.Point> included) {
        ChestPairs.Point peer = ChestPairs.peer(tag);
        if (peer == null || !included.test(peer)) return false;
        CompoundTag other = BlockEntities.capture(level, peer.vector());
        return other != null && ChestPairs.Point.of(tag).equals(ChestPairs.peer(other));
    }

    private static SelectionBounds include(SelectionBounds bounds, Vector3 point) {
        int x = point.getFloorX(), y = point.getFloorY(), z = point.getFloorZ();
        return bounds == null ? new SelectionBounds(x, y, z, x, y, z) : new SelectionBounds(Math.min(x, bounds.minX()), Math.min(y, bounds.minY()),
                Math.min(z, bounds.minZ()), Math.max(x, bounds.maxX()), Math.max(y, bounds.maxY()), Math.max(z, bounds.maxZ()));
    }
}
