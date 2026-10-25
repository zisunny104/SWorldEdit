package ru.SocialMoods.SWorldEdit.service.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.data.Schematic;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.ClipboardOperation;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SchematicServiceImpl implements SchematicService {
    private final SchematicRepository repository;
    private final int limit, batch;
    private final Consumer<Runnable> schedule;

    public SchematicServiceImpl(SchematicRepository repository) {
        this(repository, SWorldEdit.get().getConfig().getInt("clipboard.max-blocks", 100000),
                SWorldEdit.get().getConfig().getInt("clipboard.blocks-per-tick", 512),
                task -> SWorldEdit.get().getServer().getScheduler().scheduleDelayedTask(SWorldEdit.get(), task, 1));
    }

    public SchematicServiceImpl(SchematicRepository repository, int limit, int batch, Consumer<Runnable> schedule) {
        this.repository = repository; this.limit = limit; this.batch = batch; this.schedule = schedule;
    }

    @Override public CompletableFuture<Void> save(PlayerData dat, String name) {
        try {
            Position p1 = dat.getSelection().getPosOne(), p2 = dat.getSelection().getPosTwo();
            int width = Math.toIntExact(Math.abs((long)p1.getFloorX()-p2.getFloorX())+1);
            int height = Math.toIntExact(Math.abs((long)p1.getFloorY()-p2.getFloorY())+1);
            int length = Math.toIntExact(Math.abs((long)p1.getFloorZ()-p2.getFloorZ())+1);
            Schematic.volume(width, height, length);
            Position origin = new Position(Math.min(p1.getFloorX(),p2.getFloorX()),
                    Math.min(p1.getFloorY(),p2.getFloorY()), Math.min(p1.getFloorZ(),p2.getFloorZ()), p1.level);
            BlocksArray snapshot = new BlocksArray();
            return ClipboardOperation.copy(p1,p2,origin,snapshot,limit,batch,schedule).thenCompose(count ->
                CompletableFuture.runAsync(() -> {
                    int[] ids = new int[count], data = new int[count];
                    for (int i=0;i<count;i++) { ids[i]=snapshot.blocks.get(i).getId(); data[i]=snapshot.blocks.get(i).getDamage(); }
                    try { repository.save(new Schematic((short)width,(short)height,(short)length,ids,data,snapshot.blockEntities),name); }
                    catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
                }));
        } catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
    }

    @Override public CompletableFuture<Integer> load(PlayerData dat, Position center, String name) {
        Position destination = new Position(center.getFloorX(),center.getFloorY(),center.getFloorZ(),center.level);
        return CompletableFuture.supplyAsync(() -> {
            try { return repository.load(name); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).thenCompose(schematic -> {
            CompletableFuture<Integer> result = new CompletableFuture<>();
            try { schedule.accept(() -> {
                try {
                    if (schematic.getBlocks().length > limit) throw new IllegalArgumentException("Schematic exceeds configured limit");
                    BlocksArray clipboard = new BlocksArray();
                    for (int i=0;i<schematic.getBlocks().length;i++) {
                        int id=schematic.getBlocks()[i], meta=schematic.getData()[i];
                        Block block=Block.get(id,meta);
                        if(block.getId()!=id || block.getDamage()!=meta || block.getClass().getSimpleName().equals("BlockUnknown"))
                            throw new IllegalArgumentException("Core does not support schematic block " + id + ":" + meta);
                        block.setComponents(i % schematic.getWidth(), i / schematic.getWidth() / schematic.getLength(),
                                i / schematic.getWidth() % schematic.getLength());
                        clipboard.blocks.add(block);
                    }
                    schematic.getBlockEntities().forEach((index,tag)->clipboard.blockEntities.put(index,tag.clone()));
                    // Preserve schematic paste's existing skip-air behavior.
                    ClipboardOperation.paste(dat,destination,clipboard,true,limit,batch,schedule)
                            .whenComplete((count,error)-> { if(error!=null) result.completeExceptionally(error); else result.complete(count); });
                } catch(RuntimeException | LinkageError e) { result.completeExceptionally(e); }
            }); } catch(RuntimeException e) { result.completeExceptionally(e); }
            return result;
        });
    }
}
