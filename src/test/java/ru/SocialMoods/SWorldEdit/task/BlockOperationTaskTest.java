package ru.SocialMoods.SWorldEdit.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockOperationTaskTest {
    @Test void linkageFailureCannotBeReportedAsSuccess(){
        var failure=new NoSuchMethodError("core mismatch");
        var task=new BlockOperationTask(()->{throw failure;});task.onRun();
        var error=assertThrows(RuntimeException.class,task::waitForCompletion);assertSame(failure,error.getCause());
    }
}
