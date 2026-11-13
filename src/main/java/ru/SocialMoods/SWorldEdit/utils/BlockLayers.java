package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Bridges the numeric layer API and the BlockLayer API used by newer Nukkit builds. */
final class BlockLayers {
    static final int UNKNOWN = -1;
    static final int EMPTY = -2;
    private record Access(MethodHandle read, MethodHandle write) { }
    private static final Access ACCESS = discover();

    private BlockLayers() { }

    private static Access discover() {
        try {
            Class<?> layerType = Class.forName("cn.nukkit.block.BlockLayer");
            return access(layerType, layerType.getField("WATERLOGGED").get(null));
        } catch (ReflectiveOperationException ignored) {
            try { return access(int.class, 1); }
            catch (ReflectiveOperationException unsupported) { return null; }
        }
    }

    private static Access access(Class<?> layerType, Object layer) throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        var getter = lookup.unreflect(Level.class.getMethod("getBlock", Vector3.class, layerType, boolean.class));
        var setter = lookup.unreflect(Level.class.getMethod("setBlock", Vector3.class, layerType, Block.class, boolean.class, boolean.class));
        getter = MethodHandles.insertArguments(getter, 2, layer, true);
        setter = MethodHandles.insertArguments(setter, 2, layer);
        return new Access(getter, MethodHandles.insertArguments(setter, 3, true, false));
    }

    static Block read(Level level, Vector3 point) {
        if (ACCESS == null) return null;
        try { return (Block) ACCESS.read.invokeExact(level, point); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot read secondary block layer", failure); }
    }

    static void write(Level level, Vector3 point, Block block) {
        if (block.getId() == 0) {
            Block current = read(level, point);
            if (current == null || current.getId() == 0) return;
        }
        if (ACCESS == null) throw new IllegalStateException("Core does not support writing the secondary block layer");
        try {
            boolean changed = (boolean) ACCESS.write.invokeExact(level, point, block.clone());
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot write secondary block layer", failure); }
        Block stored = read(level, point);
        if (stored == null || stored.getId() != block.getId() || stored.getDamage() != block.getDamage()) {
            throw new IllegalStateException("World did not store the secondary layer at " + point);
        }
    }

    static void requireWritable(Level level) {
        if (ACCESS != null) {
            var provider = level.getProvider();
            if (provider == null) return;
            String type = provider.getClass().getName();
            if (!type.startsWith("cn.nukkit.level.format.anvil.") && !type.startsWith("cn.nukkit.level.format.mcregion.")) return;
        }
        throw new IllegalArgumentException("Destination world does not support the secondary block layer");
    }

    static int capture(Level level, Vector3 point, PagedClipboard.Palette palette) {
        Block extra = read(level, point);
        if (extra == null) return UNKNOWN;
        return extra.getId() == 0 ? EMPTY : palette.capture(extra);
    }

    static boolean occupied(Level level, Vector3 point) {
        Block block = read(level, point);
        return block != null && block.getId() != 0;
    }
}
