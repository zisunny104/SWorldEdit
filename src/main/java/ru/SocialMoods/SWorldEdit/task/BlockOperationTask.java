package ru.SocialMoods.SWorldEdit.task;

import cn.nukkit.scheduler.AsyncTask;

public class BlockOperationTask extends AsyncTask {
    private final Runnable operation;
    private volatile boolean completed = false;
    private volatile Throwable exception = null;

    public BlockOperationTask(Runnable operation) {
        this.operation = operation;
    }

    @Override
    public void onRun() {
        try {
            operation.run();
        } catch (Exception | LinkageError e) {
            this.exception = e;
        } finally {
            synchronized (this) {
                this.completed = true;
                this.notifyAll();
            }
        }
    }

    public void waitForCompletion() throws InterruptedException {
        synchronized (this) {
            while (!completed) {
                this.wait();
            }
        }

        if (exception != null) {
            throw new RuntimeException("Block operation failed", exception);
        }
    }
}
