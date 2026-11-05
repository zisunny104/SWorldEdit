package ru.SocialMoods.SWorldEdit.utils;

import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One lightweight server-tick monitor; completion callbacks and player messages run on that thread. */
public final class WorkTracker implements AutoCloseable {
    private final Consumer<Runnable> schedule;
    private final BiConsumer<PlayerData,String> send;
    private final LongSupplier clock;
    private final long interval;
    private final int maxJobs;
    private final Map<PlayerData,Tracked<?>> active = new IdentityHashMap<>();
    private final Queue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private boolean ticking;

    public WorkTracker(Consumer<Runnable> schedule,BiConsumer<PlayerData,String> send,int seconds,LongSupplier clock) {
        this(schedule,send,seconds,4,clock);
    }
    public WorkTracker(Consumer<Runnable> schedule,BiConsumer<PlayerData,String> send,int seconds,int maxJobs,LongSupplier clock) {
        if(seconds<1)throw new IllegalArgumentException("progress.interval-seconds must be positive");
        if(maxJobs<1)throw new IllegalArgumentException("editing.max-jobs must be positive");
        this.maxJobs=maxJobs;
        this.schedule=schedule;this.send=send;this.clock=clock;interval=TimeUnit.SECONDS.toNanos(seconds);
    }
    public boolean busy(PlayerData owner){return active.containsKey(owner);}
    public boolean full(){return active.size()>=maxJobs;}

    public <T> CompletableFuture<T> watch(PlayerData owner,String command,CompletableFuture<T> work){
        if(closed)return CompletableFuture.failedFuture(new CancellationException("Work tracker closed"));
        if(busy(owner))return CompletableFuture.failedFuture(new IllegalStateException(Messages.text("work.busy")));
        if(full())return CompletableFuture.failedFuture(new IllegalStateException(Messages.text("work.queue-full")));
        Tracked<T> tracked=new Tracked<>(owner,command,clock.getAsLong());active.put(owner,tracked);
        notify(owner,Messages.text("work.started",command));
        work.whenComplete((value,error)->{
            if(!closed)completed.add(()->{
                if(active.remove(owner,tracked)){
                    owner.getUndoHistory().completed(owner);
                    if(error==null)tracked.result.complete(value);
                    else tracked.result.completeExceptionally(error);
                }
            });
        });
        if(!ticking){ticking=true;scheduleNext();}
        return tracked.result;
    }

    private void scheduleNext(){
        try{schedule.accept(this::tick);}catch(RuntimeException e){close();throw e;}
    }
    private void tick(){
        if(closed)return;
        Runnable done;while((done=completed.poll())!=null)done.run();
        long now=clock.getAsLong();
        for(Tracked<?> tracked:List.copyOf(active.values())){
            WorkProgress.Snapshot state=tracked.owner.getWorkProgress().snapshot();
            if(!state.equals(tracked.lastState)){tracked.lastState=state;tracked.lastChange=now;}
            if(now-tracked.lastNotice<interval)continue;
            tracked.lastNotice=now;
            String stage=Messages.text(state.phase(),state.completed(),state.total());
            if(now-tracked.lastChange>=interval*3)stage+=" "+Messages.text("work.no-change",TimeUnit.NANOSECONDS.toSeconds(now-tracked.lastChange));
            long elapsed=TimeUnit.NANOSECONDS.toSeconds(now-tracked.started);
            if(state.total()>0){
                long doneCount=Math.min(state.completed(),state.total());
                long percent=(long)(100.0*doneCount/state.total());
                notify(tracked.owner,Messages.text("work.progress",tracked.command,stage,doneCount,state.total(),percent,elapsed));
            }else notify(tracked.owner,Messages.text("work.waiting",tracked.command,stage,elapsed));
        }
        if(active.isEmpty()){ticking=false;return;}
        scheduleNext();
    }

    private void notify(PlayerData owner,String message){
        try{send.accept(owner,message);}
        catch(RuntimeException|LinkageError error){BlockEntities.report("Cannot deliver WorldEdit progress message",error);}
    }

    @Override public void close(){
        if(closed)return;closed=true;
        List<Tracked<?>> jobs=List.copyOf(active.values());active.clear();completed.clear();
        jobs.forEach(job->job.result.completeExceptionally(new CancellationException(Messages.text("work.cancelled"))));
    }
    private static final class Tracked<T>{
        final PlayerData owner;final String command;final long started;
        final CompletableFuture<T> result=new CompletableFuture<>();long lastNotice,lastChange;
        WorkProgress.Snapshot lastState;
        Tracked(PlayerData owner,String command,long now){this.owner=owner;this.command=command;started=lastNotice=lastChange=now;lastState=owner.getWorkProgress().snapshot();}
    }
}
