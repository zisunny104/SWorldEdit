package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import ru.SocialMoods.SWorldEdit.clipboard.PageStore;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;

/** Session clipboard. Native block prototypes stay bounded; bulk coordinates and NBT are paged. */
public final class PagedClipboard implements AutoCloseable {
    static final int PAGE_BYTES = 4 * 1024 * 1024;
    final PageStore store;
    final Palette palette;
    final SelectionBounds bounds;
    final int count;

    PagedClipboard(PageStore store, Palette palette, SelectionBounds bounds, int count) {
        this.store = store; this.palette = palette; this.bounds = bounds; this.count = count;
    }

    @Override public void close() throws IOException { store.close(); }

    record Cell(int x, int y, int z, int state, CompoundTag tag, boolean pair) { }

    /** Do not reconstruct native state through Block.get(id, meta): some cores truncate metadata. */
    static final class Palette {
        private final int limit;
        private final Map<List<Object>, Integer> ids = new HashMap<>();
        private final List<Block> prototypes = new ArrayList<>();
        private final Map<Class<?>, List<Field>> fields = new HashMap<>();
        Palette(int limit) { this.limit = limit; }

        int capture(Block source) {
            List<Object> key = new ArrayList<>();
            key.add(source.getClass()); key.add(source.getId()); key.add(source.getDamage());
            for (Field field : fields.computeIfAbsent(source.getClass(), type -> {
                List<Field> result = new ArrayList<>();
                for (Class<?> c = type; c != null && c != cn.nukkit.level.Position.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                        // Core Block cache does not define state. Layer does, and is retained separately.
                        if (c == Block.class && !f.getName().equals("layer")) continue;
                        if (!f.trySetAccessible()) throw new IllegalStateException("Cannot snapshot native field " + f);
                        result.add(f);
                    }
                }
                return result;
            })) {
                try {
                    Object value = field.get(source);
                    // Unknown mutable state cannot safely be deduplicated by id/meta or toString().
                    key.add(value == null || field.getType().isPrimitive() || value instanceof String || value instanceof Enum<?>
                            ? value : new Object());
                } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            }
            Integer existing = ids.get(key);
            if (existing != null) return existing;
            if (prototypes.size() >= limit) throw new IllegalArgumentException("Native state palette exceeds clipboard.streaming.max-states=" + limit);
            Block detached = source.clone();
            detached.level = null;
            detached.setComponents(0, 0, 0);
            int id = prototypes.size();
            prototypes.add(detached); ids.put(key, id);
            return id;
        }

        Block block(Cell cell) {
            if (cell.state < 0 || cell.state >= prototypes.size()) throw new IllegalArgumentException("Invalid clipboard state index");
            Block result = prototypes.get(cell.state).clone();
            result.setComponents(cell.x, cell.y, cell.z);
            return result;
        }
    }

    static byte[] encode(List<Cell> cells) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(cells.size());
            for (Cell cell : cells) {
                out.writeInt(cell.x); out.writeInt(cell.y); out.writeInt(cell.z); out.writeInt(cell.state);
                out.writeBoolean(cell.pair);
                byte[] tag = cell.tag == null ? null : NBTIO.write(cell.tag);
                if (tag != null && tag.length > PAGE_BYTES - 25 - raw.size()) throw new IOException("Block entity exceeds clipboard page byte budget");
                out.writeInt(tag == null ? -1 : tag.length);
                if (tag != null) out.write(tag);
                if (raw.size() > PAGE_BYTES) throw new IOException("Clipboard page exceeds byte budget");
            }
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { raw.writeTo(gzip); }
        if (compressed.size() > PAGE_BYTES) throw new IOException("Compressed clipboard page exceeds byte budget");
        return compressed.toByteArray();
    }

    static List<Cell> decode(byte[] page, int maxCells) throws IOException {
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(page))) {
            raw = gzip.readNBytes(PAGE_BYTES + 1);
            if (raw.length > PAGE_BYTES) throw new IOException("Clipboard page expands beyond byte budget");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            int count = in.readInt();
            if (count < 0 || count > maxCells || count > (raw.length - 4) / 21) throw new IOException("Invalid clipboard page count");
            List<Cell> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = in.readInt(), y = in.readInt(), z = in.readInt(), state = in.readInt();
                boolean pair = in.readBoolean(); int size = in.readInt();
                if (size < -1 || size > in.available()) throw new IOException("Invalid clipboard NBT length");
                CompoundTag tag = size == -1 ? null : NBTIO.read(new ByteArrayInputStream(in.readNBytes(size)));
                cells.add(new Cell(x, y, z, state, tag, pair));
            }
            if (in.available() != 0) throw new IOException("Trailing clipboard bytes");
            return cells;
        }
    }
}
