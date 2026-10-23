package ru.SocialMoods.SWorldEdit.service.impl;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

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

            short width = (short) (maxX - minX + 1);
            short height = (short) (maxY - minY + 1);
            short length = (short) (maxZ - minZ + 1);

            byte[] blocks = new byte[width * height * length];
            byte[] data = new byte[blocks.length];

            int i = 0;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int id = level.getBlockIdAt(x, y, z);
                        int dmg = level.getBlockDataAt(x, y, z);
                        blocks[i] = (byte) id;
                        data[i] = (byte) dmg;
                        i++;
                    }
                }
            }

            try {
                repository.save(new Schematic(width, height, length, blocks, data), name);
            } catch (Exception e) {
                throw new RuntimeException(Messages.text("schematic.save.failed"), e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> load(PlayerData dat, Position center, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Schematic schematic = repository.load(name);
                Level level = center.level;
                BlocksArray undo = new BlocksArray();
                int placed = 0;

                int i = 0;
                for (int y = 0; y < schematic.getHeight(); y++) {
                    for (int z = 0; z < schematic.getLength(); z++) {
                        for (int x = 0; x < schematic.getWidth(); x++) {
                            int id = schematic.getBlocks()[i] & 0xFF;
                            int dmg = schematic.getData()[i] & 0xFF;
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
                throw new RuntimeException(Messages.text("schematic.load.failed"), e);
            }
        });
    }
}
