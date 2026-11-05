package ru.SocialMoods.SWorldEdit.utils;

import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.*;
import java.util.function.Consumer;

/** Main-thread retention policy. Active recovery records stay pinned until their edit ends. */
public final class UndoHistory implements AutoCloseable {
    private record Entry(PlayerData owner,BlocksArray data){}
    private final int maxSteps;
    private final long maxMemoryBlocks;
    private final Consumer<BlocksArray> release;
    private final List<Entry> entries=new ArrayList<>();
    private final Set<BlocksArray> pinned=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;
    public UndoHistory(int maxSteps,long maxMemoryBlocks,Consumer<BlocksArray> release){
        if(maxSteps<1 || maxMemoryBlocks<1)throw new IllegalArgumentException("Invalid history limits");
        this.maxSteps=maxSteps;this.maxMemoryBlocks=maxMemoryBlocks;this.release=release;
    }
    public void checkMemoryRecord(long count){
        if(count>maxMemoryBlocks)throw new IllegalArgumentException("Recovery needs "+count+" in-memory blocks; history.max-memory-blocks="+maxMemoryBlocks);
    }
    public void add(PlayerData owner,BlocksArray data){
        if(closed)throw new IllegalStateException("Undo history is closed");
        sweep();owner.undoSteps.add(data);entries.add(new Entry(owner,data));pinned.add(data);trim(data);
    }
    public void completed(PlayerData owner){
        BlocksArray newest=null;
        for(Entry entry:entries)if(entry.owner==owner){pinned.remove(entry.data);newest=entry.data;}
        sweep();trim(newest);
    }
    private void sweep(){entries.removeIf(entry->{boolean removed=!entry.owner.undoSteps.contains(entry.data);if(removed)pinned.remove(entry.data);return removed;});}
    private long memory(){long count=0;for(Entry entry:entries)if(entry.data.paged==null)count+=entry.data.blocks.size();return count;}
    private void remove(Entry entry){entries.remove(entry);entry.owner.undoSteps.remove(entry.data);pinned.remove(entry.data);release.accept(entry.data);}
    private void trim(BlocksArray newest){
        for(Entry entry:List.copyOf(entries))if(entry.owner.undoSteps.size()>maxSteps && !pinned.contains(entry.data) && entry.data!=newest)remove(entry);
        while(memory()>maxMemoryBlocks){
            Entry oldest=entries.stream().filter(entry->entry.data.paged==null && entry.data!=newest && !pinned.contains(entry.data)).findFirst().orElse(null);
            if(oldest==null)break; // Temporary excess is bounded by admitted jobs; never discard a live recovery record.
            remove(oldest);
        }
    }
    @Override public void close(){
        if(closed)return;closed=true;
        for(Entry entry:List.copyOf(entries))remove(entry);
    }
}
