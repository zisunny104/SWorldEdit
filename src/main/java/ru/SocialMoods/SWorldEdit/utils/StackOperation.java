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

/** All world access runs on the server thread via the supplied next-tick scheduler. */
public final class StackOperation implements Runnable {
    private final Level level;
    private final PlayerData player;
    private final Consumer<Runnable> schedule;
    private final int minX, minY, minZ, width, height, length, volume, count, dx, dy, dz;
    private final int batchSize;
    private final List<Block> source = new ArrayList<>();
    private final BlocksArray undo = new BlocksArray();
    private final CompletableFuture<Integer> future = new CompletableFuture<>();
    private int read, written, refreshed, side;
    private boolean registeredUndo;

    public StackOperation(PlayerData player, Position first, Position second, int count,
                          int dx, int dy, int dz, int maxBlocks, int batchSize, Consumer<Runnable> schedule) {
        if (first.level == null || first.level != second.level) throw new IllegalArgumentException("Select two positions in the same world");
        if (count < 1 || batchSize < 1 || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)
            throw new IllegalArgumentException("Invalid stack count or direction");
        this.player = player;
        this.level = first.level;
        this.schedule = schedule;
        this.count = count;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.batchSize = batchSize;
        minX = Math.min(first.getFloorX(), second.getFloorX());
        minY = Math.min(first.getFloorY(), second.getFloorY());
        minZ = Math.min(first.getFloorZ(), second.getFloorZ());
        width = Math.addExact(Math.subtractExact(Math.max(first.getFloorX(), second.getFloorX()), minX), 1);
        height = Math.addExact(Math.subtractExact(Math.max(first.getFloorY(), second.getFloorY()), minY), 1);
        length = Math.addExact(Math.subtractExact(Math.max(first.getFloorZ(), second.getFloorZ()), minZ), 1);
        volume = Math.multiplyExact(Math.multiplyExact(width, height), length);
        if ((long)volume * count > maxBlocks) throw new IllegalArgumentException("Stack exceeds block limit: " + maxBlocks);
        // Validate every far edge before touching the world, including integer overflow.
        coordinate(minX, width, dx, count, width - 1);
        coordinate(minZ, length, dz, count, length - 1);
        int lowY = coordinate(minY, height, dy, count, 0);
        int highY = coordinate(minY, height, dy, count, height - 1);
        if (Math.min(minY, lowY) < level.getMinBlockY() ||
                Math.max(minY + height - 1, highY) > level.getMaxBlockY())
            throw new IllegalArgumentException("Stack exceeds world height");
    }

    private static int coordinate(int min, int size, int direction, int copy, int offset) {
        return Math.toIntExact((long)min + (long)size * direction * copy + offset);
    }

    public CompletableFuture<Integer> start() {
        try { schedule.accept(this); } catch (RuntimeException e) { future.completeExceptionally(e); }
        return future;
    }

    @Override public void run() {
        if (future.isDone()) return;
        try {
            int budget = batchSize;
            while (read < volume && budget-- > 0) {
                int x = read % width;
                int z = read / width % length;
                int y = read / width / length;
                source.add(level.getBlock(new Vector3(minX + x, minY + y, minZ + z)).clone());
                read++;
            }
            while (read == volume && written < volume * count && budget-- > 0) {
                int index = written % volume;
                int copy = written / volume + 1;
                Vector3 target = new Vector3(
                    coordinate(minX, width, dx, copy, index % width),
                    coordinate(minY, height, dy, copy, index / width / length),
                    coordinate(minZ, length, dz, copy, index / width % length));
                if (!registeredUndo) {
                    player.addUndoBlocks(undo);
                    registeredUndo = true;
                }
                undo.blocks.add(level.getBlock(target).clone());
                level.setBlock(target, source.get(index).clone(), true, false);
                written++;
            }
            while (written == volume * count && refreshed < written && budget-- > 0) {
                int index = refreshed % volume;
                int copy = refreshed / volume + 1;
                Vector3 target = new Vector3(
                    coordinate(minX, width, dx, copy, index % width),
                    coordinate(minY, height, dy, copy, index / width / length),
                    coordinate(minZ, length, dz, copy, index / width % length));
                Connections.refresh(level, target, side++);
                if (side == 7) { side = 0; refreshed++; }
            }
            if (written == volume * count && refreshed == written) future.complete(written);
            else schedule.accept(this);
        } catch (RuntimeException e) {
            future.completeExceptionally(e); // Partial changes remain available to //undo.
        }
    }
}
