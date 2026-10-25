package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLightBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.task.BlockOperationTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldUtils {

    private static final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
    );

    public static CompletableFuture<Integer> setAsync(PlayerData dat, Position pos1, Position pos2, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            Level level = pos1.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            v.setComponents(x, y, z);
                            undo.blocks.add(level.getBlock(v));
                            BlockSnapshots.place(level, v, b, false);
                            blocks.incrementAndGet();
                        }
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> wallsAsync(Position pos1, Position pos2, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            Level level = pos1.level;
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        level.setBlock(v.setComponents(x, y, minZ), b, true, false);
                        level.setBlock(v.setComponents(x, y, maxZ), b, true, false);
                        blocks.addAndGet(2);
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        level.setBlock(v.setComponents(minX, y, z), b, true, false);
                        level.setBlock(v.setComponents(maxX, y, z), b, true, false);
                        blocks.addAndGet(2);
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> copyAsync(Position pos1, Position pos2, Vector3 center, BlocksArray copy) {
        SWorldEdit plugin = SWorldEdit.get();
        return ClipboardOperation.copy(pos1, pos2, center, copy,
                plugin.getConfig().getInt("clipboard.max-blocks", 100000),
                plugin.getConfig().getInt("clipboard.blocks-per-tick", 512),
                task -> plugin.getServer().getScheduler().scheduleDelayedTask(plugin, task, 1));
    }

    public static CompletableFuture<Integer> pasteAsync(PlayerData dat, Position center, BlocksArray copy) {
        return pasteAsync(dat, center, copy, false);
    }

    public static CompletableFuture<Integer> pasteAsync(PlayerData dat, Position center, BlocksArray copy, boolean skipAir) {
        SWorldEdit plugin = SWorldEdit.get();
        return ClipboardOperation.paste(dat, center, copy, skipAir,
                plugin.getConfig().getInt("clipboard.max-blocks", 100000),
                plugin.getConfig().getInt("clipboard.blocks-per-tick", 512),
                task -> plugin.getServer().getScheduler().scheduleDelayedTask(plugin, task, 1));
    }

    public static CompletableFuture<Integer> rotateYAsync(PlayerData dat, Position center, Selection sel, int degrees) {
        return CompletableFuture.supplyAsync(() -> {
            double rad = Math.toRadians(degrees);
            Level level = center.level;
            AtomicInteger blocks = new AtomicInteger(0);
            int diffX = center.getFloorX();
            int diffZ = center.getFloorZ();
            int minX = Math.min(sel.getPosOne().getFloorX(), sel.getPosTwo().getFloorX());
            int minY = Math.min(sel.getPosOne().getFloorY(), sel.getPosTwo().getFloorY());
            int minZ = Math.min(sel.getPosOne().getFloorZ(), sel.getPosTwo().getFloorZ());
            int maxX = Math.max(sel.getPosOne().getFloorX(), sel.getPosTwo().getFloorX());
            int maxY = Math.max(sel.getPosOne().getFloorY(), sel.getPosTwo().getFloorY());
            int maxZ = Math.max(sel.getPosOne().getFloorZ(), sel.getPosTwo().getFloorZ());

            BlocksArray undo = new BlocksArray();

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            int id = sel.getPosOne().level.getBlockIdAt(x, y, z);
                            if (id == 0) continue;

                            int damage = level.getBlockDataAt(x, y, z);
                            Block block = Block.get(id, damage);
                            if (block == null) continue;

                            int newX = x - diffX;
                            int newZ = z - diffZ;
                            double distance = Math.sqrt(newX * newX + newZ * newZ);
                            double angle = Math.atan2(newZ, newX) + rad;

                            int targetX = (int) Math.round(distance * Math.cos(angle)) + diffX;
                            int targetZ = (int) Math.round(distance * Math.sin(angle)) + diffZ;

                            v.setComponents(targetX, y, targetZ);
                            undo.blocks.add(level.getBlock(new Vector3(x, y, z)));
                            level.setBlock(new Vector3(x, y, z), Block.get(0));
                            level.setBlock(v, block);
                            blocks.incrementAndGet();
                        }
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.getUndoSteps().add(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> cylAsync(PlayerData dat, Position center, int radius, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            Level level = center.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + z * z > radius * radius) continue;
                        v.setComponents(center.x + x, center.y, center.z + z);
                        undo.blocks.add(level.getBlock(v));
                        BlockSnapshots.place(level, v, b, false);
                        blocks.incrementAndGet();
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> hcylAsync(PlayerData dat, Position center, int radius, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            Level level = center.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        int distanceSquared = x * x + z * z;
                        if (distanceSquared > radius * radius || distanceSquared <= (radius - 1) * (radius - 1)) continue;
                        v.setComponents(center.x + x, center.y, center.z + z);
                        undo.blocks.add(level.getBlock(v));
                        BlockSnapshots.place(level, v, b, false);
                        blocks.incrementAndGet();
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> sphereAsync(PlayerData dat, Position center, int radius, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            Level level = center.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (x * x + y * y + z * z > radius * radius) continue;
                            v.setComponents(center.x + x, center.y + y, center.z + z);
                            undo.blocks.add(level.getBlock(v));
                            BlockSnapshots.place(level, v, b, false);
                            blocks.incrementAndGet();
                        }
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> hsphereAsync(PlayerData dat, Position center, int radius, Block b) {
        return CompletableFuture.supplyAsync(() -> {
            Level level = center.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            int distanceSquared = x * x + y * y + z * z;
                            if (distanceSquared > radius * radius || distanceSquared <= (radius - 1) * (radius - 1)) continue;
                            v.setComponents(center.x + x, center.y + y, center.z + z);
                            undo.blocks.add(level.getBlock(v));
                            BlockSnapshots.place(level, v, b, false);
                            blocks.incrementAndGet();
                        }
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> replaceAsync(PlayerData dat, Position pos1, Position pos2, Block original, Block replacement) {
        return CompletableFuture.supplyAsync(() -> {
            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            Level level = pos1.level;
            BlocksArray undo = new BlocksArray();
            AtomicInteger blocks = new AtomicInteger(0);

            BlockOperationTask task = new BlockOperationTask(() -> {
                Vector3 v = new Vector3();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            v.setComponents(x, y, z);
                            Block currentBlock = level.getBlock(v);

                            // better BlockLightBlock removal
                            if (currentBlock instanceof BlockLightBlock) {
                                undo.blocks.add(currentBlock);
                                level.setBlock(v, replacement, true, false);
                                blocks.incrementAndGet();
                                continue;
                            }

                            if (original != null &&
                                    (level.getBlockIdAt(x, y, z) != original.getId() ||
                                            level.getBlockDataAt(x, y, z) != original.getDamage())) {
                                continue;
                            }

                            undo.blocks.add(currentBlock);
                            level.setBlock(v, replacement, true, false);
                            blocks.incrementAndGet();
                        }
                    }
                }
            });

            level.getServer().getScheduler().scheduleAsyncTask(SWorldEdit.get(), task);
            try {
                task.waitForCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }

            dat.addUndoBlocks(undo);
            return blocks.get();
        }, executor);
    }

    public static CompletableFuture<Integer> undoAsync(PlayerData dat, int step) {
        SWorldEdit plugin = SWorldEdit.get();
        return ClipboardOperation.undo(dat, step, plugin.getConfig().getInt("clipboard.blocks-per-tick", 512),
                task -> plugin.getServer().getScheduler().scheduleDelayedTask(plugin, task, 1));
    }
}
