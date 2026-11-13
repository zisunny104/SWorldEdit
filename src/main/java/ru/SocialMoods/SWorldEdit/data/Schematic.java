package ru.SocialMoods.SWorldEdit.data;

import lombok.Value;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

@Value
public class Schematic {
    short width;
    short height;
    short length;
    int[] blocks;
    int[] data;
    Map<Integer, CompoundTag> blockEntities;
    int[] secondaryIds;
    int[] secondaryData;

    public Schematic(short width, short height, short length, int[] blocks, int[] data) {
        this(width, height, length, blocks, data, Map.of());
    }

    public Schematic(short width, short height, short length, int[] blocks, int[] data,
                     Map<Integer, CompoundTag> blockEntities) {
        this(width, height, length, blocks, data, blockEntities, null, null);
    }

    public Schematic(short width, short height, short length, int[] blocks, int[] data,
                     Map<Integer, CompoundTag> blockEntities, int[] secondaryIds, int[] secondaryData) {
        int volume = volume(width, height, length);
        if (blocks.length != volume || data.length != volume) {
            throw new IllegalArgumentException("Schematic array lengths do not match dimensions");
        }
        if ((secondaryIds == null) != (secondaryData == null) || secondaryIds != null &&
                (secondaryIds.length != volume || secondaryData.length != volume)) {
            throw new IllegalArgumentException("Invalid schematic secondary layer arrays");
        }
        if (secondaryIds != null) for (int i = 0; i < volume; i++) {
            if (secondaryIds[i] < -1 || secondaryData[i] < 0 || secondaryData[i] > 65535 ||
                    (secondaryIds[i] <= 0 && secondaryData[i] != 0)) {
                throw new IllegalArgumentException("Invalid secondary layer state at index " + i);
            }
        }
        this.secondaryIds = secondaryIds;
        this.secondaryData = secondaryData;
        for (int i = 0; i < volume; i++) {
            if (blocks[i] < 0 || data[i] < 0 || data[i] > 65535) {
                throw new IllegalArgumentException("Invalid block ID or metadata at index " + i);
            }
        }
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.data = data;
        Map<Integer, CompoundTag> tags = new HashMap<>();
        blockEntities.forEach((index, tag) -> {
            if (index == null || index < 0 || index >= volume || tag == null || blocks[index] == 0 ||
                    tag.getString("id").isEmpty())
                throw new IllegalArgumentException("Invalid schematic block entity index or type: " + index);
            tags.put(index, tag.clone());
        });
        this.blockEntities = Collections.unmodifiableMap(tags);
    }

    public static int volume(int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0 ||
                width > Short.MAX_VALUE || height > Short.MAX_VALUE || length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid schematic dimensions");
        }
        return Math.multiplyExact(Math.multiplyExact(width, height), length);
    }
}
