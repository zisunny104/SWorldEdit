package ru.SocialMoods.SWorldEdit.repository.impl;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import java.util.HashMap;
import java.util.Map;
import ru.SocialMoods.SWorldEdit.data.Schematic;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SchematicRepositoryImpl implements SchematicRepository {
    private final File baseDir;

    public SchematicRepositoryImpl(File baseDir) {
        this.baseDir = baseDir;
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void save(Schematic schematic, String name) throws IOException {
        CompoundTag tag = new CompoundTag()
                .putShort("Width", schematic.getWidth())
                .putShort("Height", schematic.getHeight())
                .putShort("Length", schematic.getLength())
                .putIntArray("BlockIds", schematic.getBlocks())
                .putIntArray("BlockData", schematic.getData());

        ListTag<CompoundTag> entities = new ListTag<>("BlockEntities");
        schematic.getBlockEntities().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                entities.add(new CompoundTag().putInt("Index", entry.getKey())
                        .putCompound("Data", entry.getValue().clone())));
        tag.putList(entities);

        File file = new File(baseDir, name + ".schem");
        try (FileOutputStream fos = new FileOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            NBTIO.write(tag, gzos);
        }
    }

    private static int[] readValues(CompoundTag tag, String modern, String legacy) {
        if (tag.contains(modern)) return tag.getIntArray(modern);
        byte[] bytes = tag.getByteArray(legacy);
        int[] values = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) values[i] = bytes[i] & 0xff;
        return values;
    }

    @Override
    public Schematic load(String name) throws IOException {
        File file = new File(baseDir, name + ".schem");
        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gzis = new GZIPInputStream(fis)) {
            CompoundTag tag = NBTIO.read(gzis);
            if (tag.contains("BlockIds") != tag.contains("BlockData")) {
                throw new IOException("Incomplete schematic block arrays");
            }
            try {
                Map<Integer, CompoundTag> entities = new HashMap<>();
                if (tag.contains("BlockEntities")) {
                    if (!(tag.get("BlockEntities") instanceof ListTag<?> list))
                        throw new IllegalArgumentException("Invalid block entity list");
                    for (var value : list.getAll()) {
                        if (!(value instanceof CompoundTag entry) ||
                                !(entry.get("Index") instanceof cn.nukkit.nbt.tag.IntTag) ||
                                !(entry.get("Data") instanceof CompoundTag data))
                            throw new IllegalArgumentException("Invalid block entity entry");
                        if (entities.putIfAbsent(entry.getInt("Index"), data) != null)
                            throw new IllegalArgumentException("Duplicate block entity index");
                    }
                }
                return new Schematic(
                        (short) tag.getShort("Width"),
                        (short) tag.getShort("Height"),
                        (short) tag.getShort("Length"),
                        readValues(tag, "BlockIds", "Blocks"),
                        readValues(tag, "BlockData", "Data"), entities
                );
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new IOException("Invalid schematic", e);
            }
        }
    }
}

