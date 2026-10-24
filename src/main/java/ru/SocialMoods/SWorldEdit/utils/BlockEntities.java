package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;

import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.Map;
import java.util.logging.Logger;

/** Block-entity data is cloned on both capture and placement; never shared with a clipboard. */
final class BlockEntities {
    private static final Logger LOG = Logger.getLogger("SWorldEdit");
    private static final Map<String,String> TYPES = Map.ofEntries(
        Map.entry("BlockChest", "Chest"), Map.entry("BlockChestTrapped", "Chest"),
        Map.entry("BlockSignPost", "Sign"), Map.entry("BlockWallSign", "Sign"),
        Map.entry("BlockHangingSign", "HangingSign"),
        Map.entry("BlockFurnace", "Furnace"), Map.entry("BlockFurnaceBurning", "Furnace"),
        Map.entry("BlockBlastFurnace", "BlastFurnace"), Map.entry("BlockSmoker", "Smoker"),
        Map.entry("BlockHopper", "Hopper"), Map.entry("BlockDispenser", "Dispenser"),
        Map.entry("BlockDropper", "Dropper"), Map.entry("BlockShulkerBox", "ShulkerBox"),
        Map.entry("BlockEnderChest", "EnderChest"), Map.entry("BlockBrewingStand", "BrewingStand"),
        Map.entry("BlockEnchantingTable", "EnchantTable"), Map.entry("BlockFlowerPot", "FlowerPot"),
        Map.entry("BlockItemFrame", "ItemFrame"), Map.entry("BlockSkull", "Skull"),
        Map.entry("BlockBanner", "Banner"), Map.entry("BlockBed", "Bed"),
        Map.entry("BlockBeacon", "Beacon"), Map.entry("BlockJukebox", "Jukebox"),
        Map.entry("BlockLectern", "Lectern"), Map.entry("BlockCampfire", "Campfire"));
    private static final Map<String,String> CORE_NAMES = coreNames();
    private BlockEntities() { }

    private static Map<String,String> coreNames() {
        Map<String,String> names = new java.util.HashMap<>();
        for (var field : BlockEntity.class.getFields()) {
            if (field.getType() == String.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    String id = (String) field.get(null);
                    if (id != null) names.put(id.replace("_", "").toLowerCase(java.util.Locale.ROOT), id);
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        return names;
    }

    private static String type(Block block) {
        if (block == null) return null;
        for (Class<?> c = block.getClass(); c != null; c = c.getSuperclass()) {
            String type = TYPES.get(c.getSimpleName());
            if (type != null) return type;
            String simple = c.getSimpleName();
            if (simple.startsWith("Block")) {
                type = CORE_NAMES.get(simple.substring(5).replace("_", "").toLowerCase(java.util.Locale.ROOT));
                if (type != null) return type;
            }
        }
        return null;
    }

    static CompoundTag capture(Level level, Vector3 position) {
        BlockEntity entity = level.getBlockEntity(position);
        String expected = type(level.getBlock(position));
        if (entity == null) return expected == null ? null : clean(position, expected);
        try {
            if (!entity.isBlockEntityValid()) {
                warn(position, expected == null ? "BlockEntity" : expected, "invalid source entity; copying a clean block");
                return expected == null ? null : clean(position, expected);
            }
            entity.saveNBT(); // Flush inventory/sign runtime data into its tag first.
            CompoundTag copy = entity.namedTag.clone();
            if (copy.getString("id").isEmpty() && expected != null) copy.putString("id", expected);
            if (copy.getString("id").isEmpty()) throw new IllegalArgumentException("Missing block entity type");
            return copy;
        } catch (RuntimeException | LinkageError e) {
            if (expected == null) throw new IllegalStateException("Cannot capture block entity at " + position, e);
            warn(position, expected, "capture failed; preserving a clean usable block instead");
            return clean(position, expected);
        }
    }

    private static CompoundTag clean(Vector3 position, String type) {
        return new CompoundTag().putString("id", type).putInt("x", position.getFloorX())
                .putInt("y", position.getFloorY()).putInt("z", position.getFloorZ());
    }

    static CompoundTag at(CompoundTag source, Vector3 target) {
        CompoundTag tag = source.clone();
        tag.putInt("x", target.getFloorX()).putInt("y", target.getFloorY()).putInt("z", target.getFloorZ());
        // A copied half must never point to a chest in the source world (or an unrelated destination).
        tag.remove("pairx").remove("pairz").remove("pairlead");
        tag.remove("isOpened");
        return tag;
    }

    private static void close(Level level, Vector3 target) {
        BlockEntity old = level.getBlockEntity(target);
        if (old != null) {
            try { old.getClass().getMethod("unpair").invoke(old); }
            catch (NoSuchMethodException ignored) { }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot detach paired container", e); }
        }
        if (old != null) old.close(); // No onBreak(): do not spill/duplicate inventory contents.
    }

    private static boolean create(Level level, Vector3 target, CompoundTag tag) {
        try {
            BlockEntity entity = BlockEntity.createBlockEntity(tag.getString("id"),
                    level.getChunk(target.getFloorX() >> 4, target.getFloorZ() >> 4, true), tag);
            if (entity == null) return false;
            if (entity.closed || !entity.isBlockEntityValid()) { entity.close(); return false; }
            entity.setDirty();
            if (entity instanceof BlockEntitySpawnable) ((BlockEntitySpawnable)entity).spawnToAll();
            return true;
        } catch (RuntimeException | LinkageError e) { report("Block entity creation failed at " + target, e); return false; }
    }

    static void place(Level level, Vector3 target, Block block, CompoundTag source) {
        Block previous = level.getBlock(target);
        if (previous != null) previous = previous.clone();
        CompoundTag previousTag = previous == null ? null : capture(level, target);
        close(level, target);
        BlockSnapshots.place(level, target, block, false);
        String expected = type(block);
        String id = source != null && !source.getString("id").isEmpty() ? source.getString("id") : expected;
        if (id == null || id.isEmpty()) return;
        CompoundTag tag = source == null ? clean(target, id) : at(source, target).putString("id", id);
        if (create(level, target, tag)) return;
        close(level, target); // Clean any partially registered instance before retrying.
        boolean initialized = create(level, target, clean(target, id));
        if (!initialized && expected != null && !expected.equals(id)) {
            close(level, target);
            initialized = create(level, target, clean(target, expected));
        }
        if (initialized) {
            warn(target, id, "incompatible NBT; initialized a clean usable block");
            return;
        }
        close(level, target);
        boolean restored = previous != null;
        if (previous != null) {
            level.setBlock(target, previous, true, false);
            String previousType = previousTag == null ? type(previous) : previousTag.getString("id");
            if (previousType != null) {
                restored = create(level, target, previousTag == null ? clean(target, previousType) : at(previousTag, target));
            }
        }
        if (!restored) { close(level, target); level.setBlock(target, Block.get(0), true, false); }
        throw new IllegalStateException("Cannot initialize " + id + " at " + target +
                (restored ? "; restored the previous destination block." : "; cleared the unusable block. Use //undo to restore the destination."));
    }

    static void verify(Level level, Vector3 target, Block block, CompoundTag source) {
        String expected = source == null ? type(block) : source.getString("id");
        if (expected == null || expected.isEmpty()) return;
        BlockEntity actual = level.getBlockEntity(target);
        if (actual == null || actual.closed || actual.namedTag == null ||
                !expected.equalsIgnoreCase(actual.namedTag.getString("id")) || !actual.isBlockEntityValid())
            throw new IllegalStateException("Missing or invalid registered " + expected + " after paste at " + target + "; use //undo to restore the destination");
        // Resend only after all blocks and chest links are in place, on a later tick.
        if (actual instanceof BlockEntitySpawnable) ((BlockEntitySpawnable) actual).spawnToAll();
    }

    static void report(String message, Throwable error) {
        var plugin = ru.SocialMoods.SWorldEdit.SWorldEdit.get();
        if (plugin != null) {
            if (error == null) plugin.getLogger().info(message);
            else plugin.getLogger().warning(message, error);
        } else if (error == null) LOG.info(message);
        else LOG.log(java.util.logging.Level.WARNING, message, error);
    }

    private static void warn(Vector3 position, String type, String reason) {
        report(type + " at " + position.getFloorX() + "," + position.getFloorY() + "," + position.getFloorZ() + ": " + reason, null);
    }
}


