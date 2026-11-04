package ru.SocialMoods.SWorldEdit.utils;

import ru.SocialMoods.SWorldEdit.SWorldEdit;
import java.util.concurrent.TimeUnit;

/** Soft time budget: a single core call cannot be preempted. */
public final class TickBudget {
    private TickBudget() { }
    public static long deadline(String setting) {
        var plugin=SWorldEdit.get();
        int millis=plugin==null?3:plugin.getConfig().getInt(setting,3);
        if(millis<1)throw new IllegalArgumentException(setting+" must be positive");
        return System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
