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
    private final List<Delayed> delayed = new ArrayList<>();
    private long nextId;
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

    public boolean cancel(PlayerData owner) {
        boolean removed = delayed.removeIf(job -> job.owner == owner);
        if (busy(owner)) {
            owner.getWorkProgress().requestCancellation();
            return true;
        }
        return removed;
    }

    public List<String> status(PlayerData owner, boolean everyone) {
        List<String> lines = new ArrayList<>();
        long now = clock.getAsLong();
        for (Tracked<?> job : active.values()) {
            if (!everyone && job.owner != owner) continue;
            WorkProgress.Snapshot state = job.owner.getWorkProgress().snapshot();
            String stage = Messages.text(state.phase(), state.completed(), state.total());
            long elapsed = TimeUnit.NANOSECONDS.toSeconds(now - job.started);
            String line = state.total() > 0
                    ? Messages.text("work.progress", job.command, stage, state.completed(), state.total(),
                        (long)(100.0 * Math.min(state.completed(), state.total()) / state.total()), elapsed)
                    : Messages.text("work.waiting", job.command, stage, elapsed);
            lines.add(everyone ? job.owner.getPlayer().getName() + " " + line : line);
        }
        for (Delayed job : delayed) {
            if (!everyone && job.owner != owner) continue;
            long seconds = Math.max(0, TimeUnit.NANOSECONDS.toSeconds(job.due - now));
            String line = Messages.text("work.scheduled-status", job.id, job.command, seconds);
            lines.add(everyone ? job.owner.getPlayer().getName() + " " + line : line);
        }
        return lines;
    }

    public long delay(PlayerData owner, String command, int seconds, BooleanSupplier valid, Runnable action) {
        if (closed) throw new IllegalStateException(Messages.text("work.cancelled"));
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException(Messages.text("work.delay-range"));
        if (delayed.size() >= maxJobs) throw new IllegalStateException(Messages.text("work.queue-full"));
        long id = ++nextId;
        delayed.add(new Delayed(id, owner, command, clock.getAsLong() + TimeUnit.SECONDS.toNanos(seconds), valid, action));
        if (!ticking) { ticking = true; scheduleNext(); }
        return id;
    }

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
                    if (error == null) notify(owner, Messages.text("work.finished", command,
                            TimeUnit.NANOSECONDS.toSeconds(clock.getAsLong() - tracked.started)));
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
        for (Delayed job : List.copyOf(delayed)) {
            if (!job.valid.getAsBoolean()) {
                delayed.remove(job);
                notify(job.owner, Messages.text("work.schedule-unavailable", job.id));
            } else if (now >= job.due && !busy(job.owner) && !full()) {
                delayed.remove(job);
                try { job.action.run(); }
                catch (RuntimeException error) { notify(job.owner, Messages.text("work.schedule-failed", job.id, error.getMessage())); }
            }
        }
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
        if(active.isEmpty() && delayed.isEmpty()){ticking=false;return;}
        scheduleNext();
    }

    private void notify(PlayerData owner,String message){
        try{send.accept(owner,message);}
        catch(RuntimeException|LinkageError error){BlockEntities.report("Cannot deliver WorldEdit progress message",error);}
    }

    @Override public void close(){
        if(closed)return;closed=true;
        List<Tracked<?>> jobs=List.copyOf(active.values());active.clear();completed.clear();delayed.clear();
        jobs.forEach(job->job.result.completeExceptionally(new CancellationException(Messages.text("work.cancelled"))));
    }
    private record Delayed(long id, PlayerData owner, String command, long due, BooleanSupplier valid, Runnable action) { }
    private static final class Tracked<T>{
        final PlayerData owner;final String command;final long started;
        final CompletableFuture<T> result=new CompletableFuture<>();long lastNotice,lastChange;
        WorkProgress.Snapshot lastState;
        Tracked(PlayerData owner,String command,long now){this.owner=owner;this.command=command;started=lastNotice=lastChange=now;lastState=owner.getWorkProgress().snapshot();}
    }
}
