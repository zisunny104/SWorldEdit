package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import java.util.Map;
/** Read-only query. A suggested input is returned only after parser round-trip validation. */
public final class BlockInfo {
    private BlockInfo() { }
    public static String input(Block block, String identifier) {
        if(identifier != null) {
            String value=identifier+(block.getDamage()==0 ? "" : ":"+block.getDamage());
            if(matches(block,Utils.fromString(value,null))) return value;
        }
        String value=block.getId()+":"+block.getDamage();
        return matches(block,Utils.fromString(value,null)) ? value : null;
    }
    private static boolean matches(Block a,Block b) { return b!=null && a.getId()==b.getId() && a.getDamage()==b.getDamage(); }
    public static String identifier(Block block) {
        try { return (String)block.getClass().getMethod("getIdentifier").invoke(block); }
        catch(ReflectiveOperationException | LinkageError ignored) { }
        try {
            Object type=block.getClass().getMethod("getBlockType").invoke(block);
            if(type!=null) return (String)Class.forName("cn.nukkit.utils.material.MaterialType").getMethod("getIdentifier").invoke(type);
        } catch(ReflectiveOperationException | LinkageError ignored) { }
        return null;
    }
    private static String states(Block block) {
        try {
            Class<?> type=Class.forName("cn.nukkit.level.format.leveldb.BlockStateMapping");
            Object mapping=type.getMethod("get").invoke(null);
            if(mapping!=null) {
                Object snapshot=type.getMethod("getState",int.class,int.class).invoke(mapping,block.getId(),block.getDamage());
                if(snapshot!=null && (int)snapshot.getClass().getMethod("getLegacyId").invoke(snapshot)==block.getId() &&
                        (int)snapshot.getClass().getMethod("getLegacyData").invoke(snapshot)==block.getDamage()) {
                    Object state=snapshot.getClass().getMethod("getVanillaState").invoke(snapshot);
                    if(state instanceof Map<?,?> map && map.get("states") instanceof Map<?,?> values) return values.toString();
                }
            }
        } catch(ReflectiveOperationException | LinkageError ignored) { }
        return "data="+block.getDamage();
    }
    public static String describe(Block block) {
        String identifier=identifier(block), input=input(block,identifier);
        var entity=block.getLevel().getBlockEntity(block);
        String nbt=entity==null ? "none" :
                entity.namedTag==null ? entity.getClass().getSimpleName() : entity.namedTag.getString("id");
        return String.format("World: %1$s | Position: %2$s, %3$s, %4$s",block.getLevel().getName(),block.getFloorX(),block.getFloorY(),block.getFloorZ())+"\n"+
                String.format("Block: %1$s | Identifier: %2$s | ID/data: %3$s:%4$s",block.getName(),identifier==null ? "unavailable" : identifier,block.getId(),block.getDamage())+"\n"+
                String.format("States: %1$s | Emitted light: %2$s",states(block),block.getLightLevel())+"\n"+
                String.format("Verified block input: %1$s",input==null ? "unavailable" : input)+"\n"+
                String.format("Block entity: %1$s (NBT is not included in block input)",nbt);
    }
}
