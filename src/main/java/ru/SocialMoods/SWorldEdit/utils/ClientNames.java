package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.TextPacket;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Rich text lets each client translate vanilla names while retaining the plugin's surrounding text. */
public final class ClientNames {
    private static final String NAME_MARKER = "\u0000SWORLDEDIT_NAME\u0000";
    private static final Gson JSON = new Gson();
    private static final Map<String, List<String>> BLOCKS = new HashMap<>();
    private static final Map<String, List<String>> ITEMS = new HashMap<>();

    static {
        Properties names = new Properties();
        try (var input = ClientNames.class.getResourceAsStream("/client-names.properties")) {
            if (input == null) throw new IllegalStateException("Missing vanilla name catalogue");
            names.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
        names.forEach((key, value) -> {
            String translation = (String) key;
            Map<String, List<String>> target = translation.startsWith("tile.") ? BLOCKS : ITEMS;
            target.computeIfAbsent(normalize((String) value), ignored -> new ArrayList<>()).add(translation);
        });
    }

    private ClientNames() { }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static String blockKey(Block block) {
        String identifier = BlockInfo.identifier(block);
        if (identifier != null && !identifier.startsWith("minecraft:")) return null;
        List<String> candidates = BLOCKS.getOrDefault(normalize(block.getName()), List.of());
        if (identifier != null) {
            String direct = "tile." + identifier.substring("minecraft:".length()) + ".name";
            if (candidates.contains(direct)) return direct;
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    static String itemKey(Item item) {
        if (item.hasCustomName()) return null;
        String name = normalize(item.getName());
        List<String> candidates = ITEMS.getOrDefault(name, List.of());
        if (candidates.isEmpty()) candidates = BLOCKS.getOrDefault(name, List.of());
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    public static void sendBlock(Player player, Block block, Function<String, String> message) {
        send(player, blockKey(block), block.getName(), message);
    }

    public static void sendItem(Player player, Item item, Function<String, String> message) {
        send(player, itemKey(item), item.getName(), message);
    }

    private static void send(Player player, String key, String fallback, Function<String, String> message) {
        if (key == null) {
            player.sendMessage(message.apply(fallback));
            return;
        }
        List<Map<String, String>> parts = new ArrayList<>();
        String text = message.apply(NAME_MARKER);
        int start = 0;
        int marker;
        while ((marker = text.indexOf(NAME_MARKER, start)) >= 0) {
            if (marker > start) parts.add(Map.of("text", text.substring(start, marker)));
            parts.add(Map.of("translate", key));
            start = marker + NAME_MARKER.length();
        }
        if (start < text.length()) parts.add(Map.of("text", text.substring(start)));
        TextPacket packet = new TextPacket();
        packet.type = TextPacket.TYPE_OBJECT;
        packet.message = JSON.toJson(Map.of("rawtext", parts));
        player.dataPacket(packet);
    }
}
