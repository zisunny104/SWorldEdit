package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.item.Item;
import java.util.HashMap;
import java.util.Map;

/** Per-player, session-only bindings by held item type; never edits item NBT. */
public final class ToolBindings {
    public enum Mode { SELECTION, INFO, NONE }
    private final Map<String,Mode> modes = new HashMap<>();
    public void bind(Item item, Mode mode) {
        if (item == null || item.getId() == 0) throw new IllegalArgumentException("Hold an item first");
        modes.put(key(item),mode);
    }
    public Mode mode(Item item, boolean defaultWand) {
        return item == null ? Mode.NONE : modes.getOrDefault(key(item),defaultWand ? Mode.SELECTION : Mode.NONE);
    }
    private static String key(Item item) {
        try {
            Object id=item.getClass().getMethod("getNamespaceId").invoke(item);
            if(id instanceof String value && !value.isEmpty()) return value;
        } catch(ReflectiveOperationException | LinkageError ignored) { }
        return "legacy:"+item.getId();
    }
    public void clear() { modes.clear(); }
    public void restoreWand(Item item) { modes.remove(key(item)); }
}
