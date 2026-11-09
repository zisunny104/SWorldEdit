package ru.SocialMoods.SWorldEdit.utils;

/** Detached progress data: workers may publish it, but never send player messages. */
public final class WorkProgress {
    public record Snapshot(String phase, long completed, long total) { }
    private volatile Snapshot snapshot = new Snapshot("work.phase.running",0,0);
    private volatile boolean cancellationRequested;
    public void resetCancellation() { cancellationRequested = false; }
    public void requestCancellation() { cancellationRequested = true; }
    public boolean cancellationRequested() { return cancellationRequested; }
    public void checkCancellation() {
        if (cancellationRequested) throw new java.util.concurrent.CancellationException(
                ru.SocialMoods.SWorldEdit.i18n.Messages.text("work.user-cancelled"));
    }
    public void report(String phase,long completed,long total) {
        snapshot = new Snapshot(phase,Math.max(0,completed),Math.max(0,total));
    }
    public Snapshot snapshot() { return snapshot; }
}
