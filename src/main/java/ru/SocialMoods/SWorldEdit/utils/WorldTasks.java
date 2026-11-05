package ru.SocialMoods.SWorldEdit.utils;

import ru.SocialMoods.SWorldEdit.SWorldEdit;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Lifecycle gate for server-thread callbacks, including callbacks posted by file I/O. */
public final class WorldTasks implements AutoCloseable {
    private final Consumer<Runnable> nextTick;
    private final Set<CompletableFuture<?>> active=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public WorldTasks(Consumer<Runnable> nextTick){this.nextTick=nextTick;}
    public void schedule(Runnable task){
        if(closed)throw new CancellationException("WorldEdit is stopping");
        nextTick.accept(()->{if(!closed)task.run();});
    }
    public <T> CompletableFuture<T> watch(CompletableFuture<T> future){
        if(closed){future.completeExceptionally(new CancellationException("WorldEdit is stopping"));return future;}
        active.add(future);future.whenComplete((value,error)->active.remove(future));
        if(closed)future.completeExceptionally(new CancellationException("WorldEdit is stopping"));
        return future;
    }
    public static <T> CompletableFuture<T> track(CompletableFuture<T> future){
        var plugin=SWorldEdit.get();
        return plugin==null || plugin.getWorldTasks()==null?future:plugin.getWorldTasks().watch(future);
    }
    @Override public void close(){
        closed=true;
        active.forEach(future->future.completeExceptionally(new CancellationException("WorldEdit is stopping")));
        active.clear();
    }
}
