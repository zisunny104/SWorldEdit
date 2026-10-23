package ru.SocialMoods.SWorldEdit.data;

import lombok.Value;

@Value
public class Schematic {
    short width;
    short height;
    short length;
    int[] blocks;
    int[] data;

    public Schematic(short width, short height, short length, int[] blocks, int[] data) {
        int volume = volume(width, height, length);
        if (blocks.length != volume || data.length != volume) {
            throw new IllegalArgumentException("Schematic array lengths do not match dimensions");
        }
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
    }

    public static int volume(int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0 ||
                width > Short.MAX_VALUE || height > Short.MAX_VALUE || length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid schematic dimensions");
        }
        return Math.multiplyExact(Math.multiplyExact(width, height), length);
    }
}
