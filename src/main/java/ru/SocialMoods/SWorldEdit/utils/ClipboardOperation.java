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
        java.util.Map<Integer, cn.nukkit.nbt.tag.CompoundTag> tags = new java.util.HashMap<>();
        Runnable work = new Runnable() {
            int index;
            @Override public void run() {
                try {
                    int end = (int)Math.min((long)volume, (long)index + batch);
                    while (index < end) {
                        int x = index % width, z = index / width % length, y = index / width / length;
                        var tag = BlockEntities.capture(level, new Vector3(minX + x, minY + y, minZ + z));
                        if (tag != null) tags.put(index, tag);
                        snapshot.add(BlockSnapshots.relative(level, minX + x, minY + y, minZ + z, anchor));
                        index++;
                    }
                    if (index < volume) schedule.accept(this);
                    else {
                        output.blocks.clear();
                        output.blocks.addAll(snapshot);
                        output.blockEntities.clear();
                        output.blockEntities.putAll(tags);
                        future.complete(volume);
                    }
                } catch (RuntimeException | LinkageError e) { BlockEntities.report("Clipboard operation failed", e); future.completeExceptionally(e); }
            }
        };
        try { schedule.accept(work); } catch (RuntimeException | LinkageError e) { BlockEntities.report("Clipboard operation failed", e); future.completeExceptionally(e); }
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
        java.util.Map<Integer, cn.nukkit.nbt.tag.CompoundTag> tags = new java.util.HashMap<>();
        clipboard.blockEntities.forEach((index, tag) -> tags.put(index, tag.clone()));
        if (snapshot.size() > limit) throw new IllegalArgumentException("Clipboard exceeds configured limit");
        List<Vector3> targets = new ArrayList<>(snapshot.size());
        for (Block block : snapshot) {
            int x = Math.toIntExact((long)center.getFloorX() + block.getFloorX());
            int y = Math.toIntExact((long)center.getFloorY() + block.getFloorY());
            int z = Math.toIntExact((long)center.getFloorZ() + block.getFloorZ());
            if (y < level.getMinBlockY() || y > level.getMaxBlockY()) throw new IllegalArgumentException("Paste exceeds world height");
            targets.add(new Vector3(x, y, z));
        }
        List<ChestPairs.Link> pairs = ChestPairs.plan(tags, targets::get,
                recordUndo ? java.util.Map.of() : clipboard.externalChestPairs);
        BlocksArray undo = new BlocksArray();
        List<Block> before = new ArrayList<>(java.util.Collections.nCopies(snapshot.size(), null));
        java.util.Map<Integer,cn.nukkit.nbt.tag.CompoundTag> beforeTags = new java.util.HashMap<>();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Runnable work = new Runnable() {
            int prepared, index, pairIndex, refreshIndex, side, placed;
            boolean registered, validationQueued;
            @Override public void run() {
                try {
                    int budget = batch;
                    // Snapshot every destination before unpairing either half of any existing chest.
                    while (prepared < snapshot.size() && budget-- > 0) {
                        int cursor = prepared++;
                        if (skipAir && snapshot.get(cursor).getId() == 0) continue;
                        Vector3 target = targets.get(cursor);
                        var previous = BlockEntities.capture(level, target);
                        if (previous != null) {
                            beforeTags.put(cursor, previous);
                            if (recordUndo) ChestPairs.rememberPeer(level, previous, undo.externalChestPairs);
                        }
                        before.set(cursor, level.getBlock(target).clone());
                    }
                    while (prepared == snapshot.size() && index < snapshot.size() && budget-- > 0) {
                        Block block = snapshot.get(index);
                        Vector3 target = targets.get(index++);
                        if (skipAir && block.getId() == 0) continue;
                        if (!registered && recordUndo) { player.addUndoBlocks(undo); registered = true; }
                        var previous = beforeTags.get(index - 1);
                        if (previous != null) undo.blockEntities.put(undo.blocks.size(), previous);
                        undo.blocks.add(before.get(index - 1));
                        BlockEntities.place(level, target, block, tags.get(index - 1));
                        placed++;
                    }
                    while (index == snapshot.size() && pairIndex < pairs.size() && budget-- > 0) {
                        ChestPairs.apply(level, pairs.get(pairIndex++));
                    }
                    if (index == snapshot.size() && pairIndex == pairs.size() && !validationQueued) {
                        validationQueued = true; schedule.accept(this); return;
                    }
                    // Pair restoration and every placement precede connection refresh.
                    while (index == snapshot.size() && pairIndex == pairs.size() && refreshIndex < targets.size() && budget-- > 0) {
                        if (skipAir && snapshot.get(refreshIndex).getId() == 0) { refreshIndex++; side = 0; continue; }
                        if (side == 0) BlockEntities.verify(level, targets.get(refreshIndex), snapshot.get(refreshIndex), tags.get(refreshIndex));
                        Connections.refresh(level, targets.get(refreshIndex), side++);
                        if (side == 7) { side = 0; refreshIndex++; }
                    }
                    if (index == snapshot.size() && pairIndex == pairs.size() && refreshIndex == targets.size()) {
                        future.complete(placed);
                    }
                    else schedule.accept(this);
                } catch (RuntimeException | LinkageError e) { BlockEntities.report("Clipboard operation failed", e); future.completeExceptionally(e); }
            }
        };
        try { schedule.accept(work); } catch (RuntimeException | LinkageError e) { BlockEntities.report("Clipboard operation failed", e); future.completeExceptionally(e); }
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
