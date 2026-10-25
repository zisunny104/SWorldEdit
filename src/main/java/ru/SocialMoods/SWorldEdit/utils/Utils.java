package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Utils {
    private static final Map<String, Integer> IDS = new HashMap<>();
    static {
        for (Field field : BlockID.class.getFields()) {
            if (field.getType() == int.class) {
                try {
                    IDS.put(field.getName().toLowerCase(Locale.ROOT), field.getInt(null));
                } catch (IllegalAccessException ignored) { }
            }
        }
    }

    public static Block fromString(String input, Player player) {
        Block result = resolve(input);
        if (result == null && player != null) {
            player.sendMessage(String.format("Unknown or unsupported block: %1$s", input));
        }
        return result;
    }

    private static Block resolve(String input) {
        if (input == null) return null;
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring(10);
        if (!value.matches("[a-z0-9_]+(?::[0-9]+)?")) return null;
        String[] parts = value.split(":");
        try {
            int data = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            if (data > 65535) return null;
            if (parts[0].matches("[0-9]+")) {
                return checkedBlock(Integer.parseInt(parts[0]), data);
            }
            // Registry identifiers can differ from legacy BlockID constant names.
            Block registered = registryBlock("minecraft:" + parts[0], data, parts.length == 2);
            if (registered != null) return registered;
            Integer id = IDS.get(parts[0]);
            return id == null ? null : checkedBlock(id, data);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static Block registryBlock(String identifier, int data, boolean explicitData) {
        for (String registry : new String[]{"cn.nukkit.block.material.BlockTypes", "cn.nukkit.block.BlockTypes"}) {
            try {
                Class<?> type = Class.forName(registry);
                Object entry = type.getMethod("get", String.class).invoke(null, identifier);
                if (entry == null) continue;
                if (registry.equals("cn.nukkit.block.material.BlockTypes")) {
                    Class<?> api = Class.forName("cn.nukkit.block.material.BlockType");
                    Block block = (Block) api.getMethod("createBlock").invoke(entry);
                    return explicitData ? checkedBlock(block.getId(), data) : supported(block);
                }
                // Invoke the public interface: the registry implementation may be private.
                Class<?> api = Class.forName("cn.nukkit.utils.material.MaterialType");
                return checkedBlock((Integer) api.getMethod("getLegacyId").invoke(entry), data);
            } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
                // One absent/incompatible registry must not disable other cores or future lookups.
            }
        }
        return null;
    }

    private static Block checkedBlock(int id, int data) {
        if (id < 0) return null;
        try {
            Block block = supported(Block.get(id, data));
            return block != null && block.getId() == id && block.getDamage() == data ? block : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Block supported(Block block) {
        return block == null || block.getClass().getSimpleName().equals("BlockUnknown") ? null : block;
    }
}
