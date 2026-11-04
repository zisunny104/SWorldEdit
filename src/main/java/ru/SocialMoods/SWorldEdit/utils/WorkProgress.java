package ru.SocialMoods.SWorldEdit.utils;

/** Detached progress data: workers may publish it, but never send player messages. */
public final class WorkProgress {
    public record Snapshot(String phase, long completed, long total) { }
    private volatile Snapshot snapshot = new Snapshot("work.phase.running",0,0);
    public void report(String phase,long completed,long total) {
        snapshot = new Snapshot(phase,Math.max(0,completed),Math.max(0,total));
    }
    public Snapshot snapshot() { return snapshot; }
}
