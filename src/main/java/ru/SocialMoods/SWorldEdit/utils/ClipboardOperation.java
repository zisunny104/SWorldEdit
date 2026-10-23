package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Tick-batched clipboard work. The caller schedules each batch on the server thread. */
public final class ClipboardOperation {
    private ClipboardOperation() { }

    public static CompletableFuture<Integer> copy(Position first, Position second, Vector3 origin,
            BlocksArray output, int limit, int batch, Consumer<Runnable> schedule) {
        if (first.level == null || first.level != second.level) throw new IllegalArgumentException("Select positions in the same world");
        Level level = first.level;
        int minX = Math.min(first.getFloorX(), second.getFloorX()), minY = Math.min(first.getFloorY(), second.getFloorY()), minZ = Math.min(first.getFloorZ(), second.getFloorZ());
        int width = Math.toIntExact((long)Math.max(first.getFloorX(), second.getFloorX()) - minX + 1);
        int height = Math.toIntExact((long)Math.max(first.getFloorY(), second.getFloorY()) - minY + 1);
        int length = Math.toIntExact((long)Math.max(first.getFloorZ(), second.getFloorZ()) - minZ + 1);
        int volume = Math.multiplyExact(Math.multiplyExact(width, height), length);
        if (volume > limit || batch < 1) throw new IllegalArgumentException("Clipboard exceeds configured limit");
        if (minY < level.getMinBlockY() || (long)minY + height - 1 > level.getMaxBlockY()) throw new IllegalArgumentException("Selection exceeds world height");
        Vector3 anchor = new Vector3(origin.getFloorX(), origin.getFloorY(), origin.getFloorZ());
        CompletableFuture<Integer> future = new CompletableFuture<>();
        List<Block> snapshot = new ArrayList<>();
        Runnable work = new Runnable() {
            int index;
            @Override public void run() {
                try {
                    int end = (int)Math.min((long)volume, (long)index + batch);
                    while (index < end) {
                        int x = index % width, z = index / width % length, y = index / width / length;
                        snapshot.add(BlockSnapshots.relative(level, minX + x, minY + y, minZ + z, anchor));
                        index++;
                    }
                    if (index < volume) schedule.accept(this);
                    else {
                        output.blocks.clear();
                        output.blocks.addAll(snapshot);
                        future.complete(volume);
                    }
                } catch (RuntimeException e) { future.completeExceptionally(e); }
            }
        };
        try { schedule.accept(work); } catch (RuntimeException e) { future.completeExceptionally(e); }
        return future;
    }

    public static CompletableFuture<Integer> paste(PlayerData player, Position center, BlocksArray clipboard,
            boolean skipAir, int limit, int batch, Consumer<Runnable> schedule) {
        return paste(player, center, clipboard, skipAir, limit, batch, schedule, true);
    }

    private static CompletableFuture<Integer> paste(PlayerData player, Position center, BlocksArray clipboard,
            boolean skipAir, int limit, int batch, Consumer<Runnable> schedule, boolean recordUndo) {
        if (center.level == null || batch < 1) throw new IllegalArgumentException("Invalid paste destination or batch size");
        Level level = center.level;
        List<Block> snapshot = List.copyOf(clipboard.blocks);
        if (snapshot.size() > limit) throw new IllegalArgumentException("Clipboard exceeds configured limit");
        List<Vector3> targets = new ArrayList<>(snapshot.size());
        for (Block block : snapshot) {
            int x = Math.toIntExact((long)center.getFloorX() + block.getFloorX());
            int y = Math.toIntExact((long)center.getFloorY() + block.getFloorY());
            int z = Math.toIntExact((long)center.getFloorZ() + block.getFloorZ());
            if (y < level.getMinBlockY() || y > level.getMaxBlockY()) throw new IllegalArgumentException("Paste exceeds world height");
            targets.add(new Vector3(x, y, z));
        }
        BlocksArray undo = new BlocksArray();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Runnable work = new Runnable() {
            int index, refreshIndex, side, placed;
            boolean registered;
            @Override public void run() {
                try {
                    int budget = batch;
                    while (index < snapshot.size() && budget-- > 0) {
                        Block block = snapshot.get(index);
                        Vector3 target = targets.get(index++);
                        if (skipAir && block.getId() == 0) continue;
                        if (!registered && recordUndo) { player.addUndoBlocks(undo); registered = true; }
                        undo.blocks.add(level.getBlock(target).clone());
                        BlockSnapshots.place(level, target, block, false);
                        placed++;
                    }
                    // All placements are complete before deriving connections from destination neighbours.
                    while (index == snapshot.size() && refreshIndex < targets.size() && budget-- > 0) {
                        if (skipAir && snapshot.get(refreshIndex).getId() == 0) { refreshIndex++; side = 0; continue; }
                        Connections.refresh(level, targets.get(refreshIndex), side++);
                        if (side == 7) { side = 0; refreshIndex++; }
                    }
                    if (index == snapshot.size() && refreshIndex == targets.size()) future.complete(placed);
                    else schedule.accept(this);
                } catch (RuntimeException e) { future.completeExceptionally(e); }
            }
        };
        try { schedule.accept(work); } catch (RuntimeException e) { future.completeExceptionally(e); }
        return future;
    }

    public static CompletableFuture<Integer> undo(PlayerData player, int step, int batch, Consumer<Runnable> schedule) {
        if (step < 0 || step >= player.undoSteps.size()) return CompletableFuture.completedFuture(0);
        BlocksArray original = player.undoSteps.get(step);
        if (original.blocks.isEmpty()) { player.undoSteps.remove(original); return CompletableFuture.completedFuture(0); }
        Level level = original.blocks.get(0).level;
        return paste(player, new Position(0, 0, 0, level), original, false, Integer.MAX_VALUE, batch, schedule, false)
                .thenApply(restored -> { player.undoSteps.remove(original); return restored; });
    }
}
