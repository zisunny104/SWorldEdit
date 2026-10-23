package ru.SocialMoods.SWorldEdit.service.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.data.Schematic;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;

import java.util.concurrent.CompletableFuture;

public class SchematicServiceImpl implements SchematicService {
    private final SchematicRepository repository;

    public SchematicServiceImpl(SchematicRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<Void> save(PlayerData dat, String name) {
        return CompletableFuture.runAsync(() -> {
            Position p1 = dat.getSelection().getPosOne();
            Position p2 = dat.getSelection().getPosTwo();
            Level level = p1.level;

            int minX = Math.min(p1.getFloorX(), p2.getFloorX());
            int minY = Math.min(p1.getFloorY(), p2.getFloorY());
            int minZ = Math.min(p1.getFloorZ(), p2.getFloorZ());
            int maxX = Math.max(p1.getFloorX(), p2.getFloorX());
            int maxY = Math.max(p1.getFloorY(), p2.getFloorY());
            int maxZ = Math.max(p1.getFloorZ(), p2.getFloorZ());

            int width = Math.addExact(Math.subtractExact(maxX, minX), 1);
            int height = Math.addExact(Math.subtractExact(maxY, minY), 1);
            int length = Math.addExact(Math.subtractExact(maxZ, minZ), 1);

            int[] blocks = new int[Schematic.volume(width, height, length)];
            int[] data = new int[blocks.length];

            int i = 0;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int id = level.getBlockIdAt(x, y, z);
                        int dmg = level.getBlockDataAt(x, y, z);
                        blocks[i] = id;
                        data[i] = dmg;
                        i++;
                    }
                }
            }

            try {
                repository.save(new Schematic((short) width, (short) height, (short) length, blocks, data), name);
            } catch (Exception e) {
                throw new RuntimeException("Ошибка сохранения схематики", e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> load(PlayerData dat, Position center, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Schematic schematic = repository.load(name);
                for (int index = 0; index < schematic.getBlocks().length; index++) {
                    int id = schematic.getBlocks()[index];
                    int meta = schematic.getData()[index];
                    Block block = Block.get(id, meta);
                    if (block.getId() != id || block.getDamage() != meta ||
                            block.getClass().getSimpleName().equals("BlockUnknown")) {
                        throw new IllegalArgumentException("Core does not support schematic block " + id + ":" + meta);
                    }
                }
                Level level = center.level;
                BlocksArray undo = new BlocksArray();
                int placed = 0;

                int i = 0;
                for (int y = 0; y < schematic.getHeight(); y++) {
                    for (int z = 0; z < schematic.getLength(); z++) {
                        for (int x = 0; x < schematic.getWidth(); x++) {
                            int id = schematic.getBlocks()[i];
                            int dmg = schematic.getData()[i];
                            if (id != 0) {
                                Block block = Block.get(id, dmg);
                                int bx = center.getFloorX() + x;
                                int by = center.getFloorY() + y;
                                int bz = center.getFloorZ() + z;
                                undo.blocks.add(level.getBlock(bx, by, bz));
                                level.setBlock(bx, by, bz, block, true, false);
                                placed++;
                            }
                            i++;
                        }
                    }
                }
                dat.addUndoBlocks(undo);
                return placed;
            } catch (Exception e) {
                throw new RuntimeException("Ошибка загрузки схематики", e);
            }
        });
    }
}

