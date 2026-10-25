package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockResolutionTest {
    @BeforeAll static void initializeBlocks() throws Exception { try { Block.class.getMethod("init").invoke(null); } catch (NoSuchMethodException ignored) { Object registry = Class.forName("cn.nukkit.registry.Registries").getField("BLOCK").get(null); registry.getClass().getMethod("init").invoke(registry); } }
    @Test void namespacedAndLegacyNamesAgree() {
        assertEquals(1, Utils.fromString("minecraft:stone", null).getId());
        assertEquals(1, Utils.fromString("STONE", null).getId());
        assertEquals(0, Utils.fromString("minecraft:air", null).getId());
        assertEquals(0, Utils.fromString("air", null).getId());
    }
    @Test void newerRegisteredBlocksUseCoreIds() {
        for (String name : new String[]{"cherry_planks", "copper_bulb"}) {
            Block block = Utils.fromString("minecraft:" + name, null);
            assertNotNull(block, name);
            assertTrue(block.getId() > 255, name);
        }
    }
    @Test void preservesExplicitMetadata() {
        Block raw = Block.get(5, 2);
        Block parsed = Utils.fromString("5:2", null);
        if (raw.getDamage() != 2 || raw.getClass().getSimpleName().equals("BlockUnknown")) {
            assertNull(parsed); // Flattened cores cannot represent this legacy variant.
        } else {
            assertNotNull(parsed);
            assertEquals(2, parsed.getDamage());
            assertEquals(2, Utils.fromString("minecraft:planks:2", null).getDamage());
        }
    }
    @Test void invalidInputsDoNotBecomeAirOrDisableResolution() {
        for (String input : new String[]{"", "minecraft:missing_block", "-1", "1:-1", "1:65536", "2147483647", "stone:bad", "other:stone"}) {
            assertNull(Utils.fromString(input, null), input);
        }
        assertNotNull(Utils.fromString("minecraft:stone", null));
    }
}



