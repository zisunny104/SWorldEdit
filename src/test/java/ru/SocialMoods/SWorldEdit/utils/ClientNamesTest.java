package ru.SocialMoods.SWorldEdit.utils;

import cn.nukkit.Player;
import cn.nukkit.block.BlockStone;
import cn.nukkit.block.BlockWool;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.TextPacket;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientNamesTest {
    @TempDir java.nio.file.Path directory;
    @BeforeEach void loadCatalogue() throws Exception {
        try (var input = ClientNames.class.getResourceAsStream("/client-names.properties")) { ClientNames.load(input); }
    }

    @Test void missingCatalogueFallsBackAndCanRecoverFromTheCurrentJar() throws Exception {
        var jar = directory.resolve("plugin.jar");
        var warnings = new java.util.ArrayList<String>();
        try (var out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) { }
        ClientNames.configure(jar, warnings::add);
        assertNull(ClientNames.blockKey(new BlockStone()));assertFalse(warnings.isEmpty());
        try (var out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar));
             var input = ClientNames.class.getResourceAsStream("/client-names.properties")) {
            out.putNextEntry(new java.util.zip.ZipEntry("client-names.properties"));input.transferTo(out);out.closeEntry();
        }
        ClientNames.configure(jar, message -> fail(message));
        assertEquals("tile.stone.stone.name", ClientNames.blockKey(new BlockStone()));
    }
    @Test void stoneVariantsAndWoolColorsHaveSeparateKeys() {
        assertEquals("tile.stone.stone.name", ClientNames.blockKey(new BlockStone(0)));
        assertEquals("tile.stone.granite.name", ClientNames.blockKey(new BlockStone(1)));
        assertEquals("tile.stone.graniteSmooth.name", ClientNames.blockKey(new BlockStone(2)));
        assertEquals("tile.wool.red.name", ClientNames.blockKey(new BlockWool(14)));
    }

    @Test void packetContainsTranslatableNameAndLiteralSurroundingText() {
        Player player = mock(Player.class);
        ClientNames.sendBlock(player, new BlockStone(1), name -> "Block <" + name + "> 1:1\n%tile.other");
        var captor = ArgumentCaptor.forClass(TextPacket.class);
        verify(player).dataPacket(captor.capture());
        TextPacket packet = captor.getValue();
        assertEquals(TextPacket.TYPE_OBJECT, packet.type);
        var parts = JsonParser.parseString(packet.message).getAsJsonObject().getAsJsonArray("rawtext");
        assertEquals("Block <", parts.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("tile.stone.granite.name", parts.get(1).getAsJsonObject().get("translate").getAsString());
        assertEquals("> 1:1\n%tile.other", parts.get(2).getAsJsonObject().get("text").getAsString());
        assertDoesNotThrow(packet::encode);
        verify(player, never()).sendMessage(anyString());
    }

    @Test void unknownAndRenamedItemsKeepTheirOriginalNames() {
        Player player = mock(Player.class);
        Item custom = mock(Item.class);
        when(custom.getName()).thenReturn("Stick");when(custom.hasCustomName()).thenReturn(true);
        ClientNames.sendItem(player, custom, name -> "Tool " + name);
        verify(player).sendMessage("Tool Stick");verify(player, never()).dataPacket(any(TextPacket.class));
        Item unknown = mock(Item.class);when(unknown.getName()).thenReturn("My new block");
        assertNull(ClientNames.itemKey(unknown));
    }

    @Test void vanillaToolsHaveClientKeys() {
        Item axe = mock(Item.class);when(axe.getName()).thenReturn("Wooden Axe");
        assertEquals("item.wooden_axe.name", ClientNames.itemKey(axe));
    }

    @Test void replacementKeepsBothNamesInTheirOwnTranslationNodes() {
        Player player = mock(Player.class);
        ClientNames.sendBlocks(player, new BlockStone(1), new BlockWool(14),
                (before, after) -> "Replaced 12 | " + before + " → " + after);
        var captor = ArgumentCaptor.forClass(TextPacket.class);
        verify(player).dataPacket(captor.capture());
        var parts = JsonParser.parseString(captor.getValue().message).getAsJsonObject().getAsJsonArray("rawtext");
        assertEquals("Replaced 12 | ", parts.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("tile.stone.granite.name", parts.get(1).getAsJsonObject().get("translate").getAsString());
        assertEquals(" → ", parts.get(2).getAsJsonObject().get("text").getAsString());
        assertEquals("tile.wool.red.name", parts.get(3).getAsJsonObject().get("translate").getAsString());
    }

    @Test void replacementSupportsOneUnknownNameWithoutLosingTheKnownTranslation() {
        Player player = mock(Player.class);
        var unknown = mock(cn.nukkit.block.Block.class);when(unknown.getName()).thenReturn("Custom <block>");
        ClientNames.sendBlocks(player, unknown, new BlockStone(), (before, after) -> before + " → " + after);
        var captor = ArgumentCaptor.forClass(TextPacket.class);verify(player).dataPacket(captor.capture());
        var parts = JsonParser.parseString(captor.getValue().message).getAsJsonObject().getAsJsonArray("rawtext");
        assertEquals("Custom <block>", parts.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("tile.stone.stone.name", parts.get(2).getAsJsonObject().get("translate").getAsString());
    }
}
