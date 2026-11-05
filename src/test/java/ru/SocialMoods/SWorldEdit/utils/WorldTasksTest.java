package ru.SocialMoods.SWorldEdit.utils;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class WorldTasksTest {
    @Test void shutdownCancelsFuturesAndGatesAlreadyQueuedOrLateCallbacks(){
        Queue<Runnable> ticks=new ArrayDeque<>();WorldTasks scope=new WorldTasks(ticks::add);int[] writes={0};
        var future=scope.watch(new CompletableFuture<Integer>());scope.schedule(()->writes[0]++);scope.close();
        assertThrows(CancellationException.class,future::join);ticks.remove().run();assertEquals(0,writes[0]);
        assertThrows(CancellationException.class,()->scope.schedule(()->writes[0]++));
        assertTrue(scope.watch(new CompletableFuture<Integer>()).isCompletedExceptionally());
    }
}
