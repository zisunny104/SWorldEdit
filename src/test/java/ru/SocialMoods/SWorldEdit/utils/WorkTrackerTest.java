package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import org.junit.jupiter.api.*;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkTrackerTest {
    final Queue<Runnable> ticks=new ArrayDeque<>();
    final List<String> messages=new ArrayList<>();
    final AtomicLong clock=new AtomicLong();
    final Thread main=Thread.currentThread();
    final PlayerData owner=new PlayerData(mock(Player.class));
    final WorkTracker tracker=new WorkTracker(ticks::add,(player,message)->{assertSame(main,Thread.currentThread());messages.add(message);},5,clock::get);
    @AfterEach void close(){tracker.close();}
    void tickAt(int seconds){clock.set(TimeUnit.SECONDS.toNanos(seconds));ticks.remove().run();}

    @Test void startsImmediatelyAndHeartbeatsEvenWhileIoHasNotAdvanced(){
        owner.getWorkProgress().report("work.phase.io",40,100);
        tracker.watch(owner,"//paste",new CompletableFuture<Integer>());
        assertEquals(1,messages.size());assertTrue(messages.getFirst().contains("//paste"));
        tickAt(4);assertEquals(1,messages.size());
        tickAt(5);assertTrue(messages.getLast().contains("40/100"));
        tickAt(10);assertEquals(3,messages.size());
        tickAt(15);assertTrue(messages.getLast().contains(Messages.text("work.no-change",15)));
        owner.getWorkProgress().report("streaming.phase.1",60,100);
        tickAt(20);assertTrue(messages.getLast().contains("60/100"));assertFalse(messages.getLast().contains(Messages.text("work.no-change",20)));
    }

    @Test void backgroundCompletionAndCallbacksReturnToServerTick()throws Exception{
        var source=new CompletableFuture<Integer>();var result=tracker.watch(owner,"//schem",source);
        result.thenAccept(value->{assertSame(main,Thread.currentThread());assertEquals(42,value);});
        Thread worker=new Thread(()->source.complete(42));worker.start();worker.join(5000);assertFalse(worker.isAlive());
        assertFalse(result.isDone());assertTrue(tracker.busy(owner));
        tickAt(1);assertEquals(42,result.join());assertFalse(tracker.busy(owner));assertTrue(ticks.isEmpty());
    }

    @Test void failuresReleaseBusyStateAndStopHeartbeat(){
        var source=new CompletableFuture<Integer>();var result=tracker.watch(owner,"//smooth",source);
        source.completeExceptionally(new IllegalStateException("failed"));tickAt(1);
        assertThrows(CompletionException.class,result::join);assertFalse(tracker.busy(owner));assertTrue(ticks.isEmpty());
    }

    @Test void closeCompletesPendingObserversAndIgnoresLateWorkerCompletion(){
        var source=new CompletableFuture<Integer>();var result=tracker.watch(owner,"//copy",source);
        tracker.close();assertThrows(CancellationException.class,result::join);assertFalse(tracker.busy(owner));
        source.complete(100);tickAt(20);assertEquals(1,messages.size());assertTrue(ticks.isEmpty());
    }

    @Test void phasesWithoutTotalsDoNotInventPercentOrEta(){
        owner.getWorkProgress().report("streaming.queued",2,0);
        tracker.watch(owner,"//copy",new CompletableFuture<Integer>());tickAt(5);
        assertFalse(messages.getLast().contains("%"));assertFalse(messages.getLast().contains("ETA"));
        assertTrue(messages.getLast().contains("2"));
    }

    @Test void rejectsAnotherObserverWithoutLosingFirstJob(){
        var first=new CompletableFuture<Integer>();var observed=tracker.watch(owner,"//copy",first);
        var duplicate=tracker.watch(owner,"//paste",new CompletableFuture<Integer>());
        assertThrows(CompletionException.class,duplicate::join);assertTrue(tracker.busy(owner));
        first.complete(5);tickAt(1);assertEquals(5,observed.join());
    }

    @Test void unavailableMessageChannelDoesNotPreventWorkCompletion(){
        try(var broken=new WorkTracker(ticks::add,(player,message)->{throw new IllegalStateException("channel closed");},5,clock::get)){
            var source=new CompletableFuture<Integer>();var observed=broken.watch(owner,"//copy",source);
            source.complete(9);tickAt(1);assertEquals(9,observed.join());assertFalse(broken.busy(owner));
        }
    }

    @Test void cancellingActiveWorkKeepsItBusyUntilTheOperationAcknowledges() {
        var source = new CompletableFuture<Integer>();
        var observed = tracker.watch(owner, "//smooth", source);
        assertTrue(tracker.cancel(owner));
        assertTrue(owner.getWorkProgress().cancellationRequested());
        assertTrue(tracker.busy(owner));
        source.completeExceptionally(new CancellationException("cancelled"));
        tickAt(1);
        assertTrue(observed.isCompletedExceptionally());
        assertFalse(tracker.busy(owner));
        owner.getWorkProgress().resetCancellation();
        assertDoesNotThrow(owner.getWorkProgress()::checkCancellation);
    }

    @Test void delayedWorkWaitsForItsOwnerAndCanBeCancelledWithoutRunning() {
        int[] executed = {0};
        var source = new CompletableFuture<Integer>();
        tracker.watch(owner, "//set", source);
        tracker.delay(owner, "//smooth 20", 5, () -> true, () -> executed[0]++);
        tickAt(5);assertEquals(0, executed[0]);
        assertEquals(2, tracker.status(owner, false).size());
        source.complete(1);tickAt(6);assertEquals(1, executed[0]);
        tracker.delay(owner, "//set stone", 5, () -> true, () -> executed[0]++);
        assertTrue(tracker.cancel(owner));tickAt(20);assertEquals(1, executed[0]);
    }

    @Test void unavailableAndClosedSchedulesNeverExecuteAndStatusIsScoped() {
        int[] executed = {0};
        var other = new PlayerData(mock(Player.class));
        tracker.delay(other, "//cut", 5, () -> false, () -> executed[0]++);
        assertTrue(tracker.status(owner, false).isEmpty());
        assertEquals(1, tracker.status(owner, true).size());
        tickAt(1);assertEquals(0, executed[0]);assertTrue(tracker.status(owner, true).isEmpty());
        tracker.delay(owner, "//cut", 5, () -> true, () -> executed[0]++);
        tracker.close();tickAt(20);assertEquals(0, executed[0]);
        assertThrows(IllegalStateException.class, () -> tracker.delay(owner,"//cut",1,()->true,()->{}));
    }
}
