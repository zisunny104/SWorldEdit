package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.TextPacket;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Rich text lets each client translate vanilla names while retaining the plugin's surrounding text. */
public final class ClientNames {
    private static final String NAME_MARKER = "\u0000SWORLDEDIT_NAME\u0000";
    private static final Pattern NAME_MARKERS = Pattern.compile("\u0000SWORLDEDIT_NAME(?:_[0-9]+)?\u0000");
    private record Catalogue(Map<String, List<String>> blocks, Map<String, List<String>> items) { }
    private static volatile Catalogue catalogue = new Catalogue(Map.of(), Map.of());

    public static void configure(Path jar, Consumer<String> warning) {
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            var entry = archive.getEntry("client-names.properties");
            if (entry == null) throw new java.io.IOException("Missing client-names.properties");
            try (var input = archive.getInputStream(entry)) { load(input); }
        } catch (Exception error) {
            catalogue = new Catalogue(Map.of(), Map.of());
            warning.accept("Client name catalogue unavailable; using core names: " + error);
            BlockEntities.report("Client name catalogue could not be loaded", error);
        }
    }

    static void load(java.io.InputStream input) throws java.io.IOException {
        Properties names = new Properties();
        names.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        Map<String, List<String>> blocks = new HashMap<>(), items = new HashMap<>();
        names.forEach((key, value) -> {
            String translation = (String) key;
            Map<String, List<String>> target = translation.startsWith("tile.") ? blocks : items;
            target.computeIfAbsent(normalize((String) value), ignored -> new ArrayList<>()).add(translation);
        });
        blocks.replaceAll((name, keys) -> List.copyOf(keys));
        items.replaceAll((name, keys) -> List.copyOf(keys));
        catalogue = new Catalogue(Map.copyOf(blocks), Map.copyOf(items));
    }

    private ClientNames() { }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static String blockKey(Block block) {
        String identifier = BlockInfo.identifier(block);
        if (identifier != null && !identifier.startsWith("minecraft:")) return null;
        List<String> candidates = catalogue.blocks.getOrDefault(normalize(block.getName()), List.of());
        if (identifier != null) {
            String direct = "tile." + identifier.substring("minecraft:".length()) + ".name";
            if (candidates.contains(direct)) return direct;
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    static String itemKey(Item item) {
        if (item.hasCustomName()) return null;
        String name = normalize(item.getName());
        Catalogue names = catalogue;
        List<String> candidates = names.items.getOrDefault(name, List.of());
        if (candidates.isEmpty()) candidates = names.blocks.getOrDefault(name, List.of());
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    public static void sendBlock(Player player, Block block, Function<String, String> message) {
        send(player, blockKey(block), block.getName(), message);
    }

    public static void sendItem(Player player, Item item, Function<String, String> message) {
        send(player, itemKey(item), item.getName(), message);
    }

    public static void sendBlocks(Player player, Block before, Block after, BiFunction<String, String, String> message) {
        String beforeKey = blockKey(before);
        String afterKey = blockKey(after);
        if (beforeKey == null && afterKey == null) {
            player.sendMessage(message.apply(before.getName(), after.getName()));
            return;
        }
        String beforeMarker = "\u0000SWORLDEDIT_NAME_0\u0000";
        String afterMarker = "\u0000SWORLDEDIT_NAME_1\u0000";
        sendRich(player, message.apply(beforeMarker, afterMarker), Map.of(
                beforeMarker, nameNode(beforeKey, before.getName()),
                afterMarker, nameNode(afterKey, after.getName())));
    }

    private static Map<String, String> nameNode(String key, String fallback) {
        return key == null ? Map.of("text", String.valueOf(fallback)) : Map.of("translate", key);
    }

    private static void send(Player player, String key, String fallback, Function<String, String> message) {
        if (key == null) {
            player.sendMessage(message.apply(fallback));
            return;
        }
        sendRich(player, message.apply(NAME_MARKER), Map.of(NAME_MARKER, nameNode(key, fallback)));
    }

    private static void sendRich(Player player, String text, Map<String, Map<String, String>> names) {
        List<Map<String, String>> parts = new ArrayList<>();
        int start = 0;
        var markers = NAME_MARKERS.matcher(text);
        while (markers.find()) {
            if (markers.start() > start) parts.add(Map.of("text", text.substring(start, markers.start())));
            parts.add(names.getOrDefault(markers.group(), Map.of("text", markers.group())));
            start = markers.end();
        }
        if (start < text.length()) parts.add(Map.of("text", text.substring(start)));
        TextPacket packet = new TextPacket();
        packet.type = TextPacket.TYPE_OBJECT;
        StringBuilder json = new StringBuilder("{\"rawtext\":[");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) json.append(',');
            var node = parts.get(index).entrySet().iterator().next();
            json.append('{');
            quote(json, node.getKey());
            json.append(':');
            quote(json, node.getValue());
            json.append('}');
        }
        packet.message = json.append("]}").toString();
        player.dataPacket(packet);
    }

    private static void quote(StringBuilder output, String text) {
        output.append('"');
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"' || value == '\\') output.append('\\').append(value);
            else if (value < 32) {
                output.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) {
                    output.append(Character.forDigit((value >> shift) & 15, 16));
                }
            } else output.append(value);
        }
        output.append('"');
    }
}
