package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Server;
import cn.nukkit.Player;
import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.command.data.CommandParamType;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandRegistrationTest {
    @Test void everyCommandAndAliasResolvesWithoutOverridingVanillaLabels() throws Exception {
        var map=mock(SimpleCommandMap.class,CALLS_REAL_METHODS);
        var known=SimpleCommandMap.class.getDeclaredField("knownCommands");
        known.setAccessible(true);known.set(map,new HashMap<>());
        map.register("nukkit",new cn.nukkit.command.defaults.HelpCommand("help"));
        var vanillaHelp=map.getCommand("help");
        var commands=CommandRegistry.create(mock(SchematicService.class));
        assertEquals(18,commands.size());Set<String> labels=new HashSet<>();
        for(var command:commands) {
            assertTrue(labels.add(command.getName()));
            assertTrue(map.register("worldedit",command),command.getName());
            assertSame(command,map.getCommand(command.getName()));
            assertSame(command,map.getCommand("worldedit:"+command.getName()));
            for(var alias:command.getAliases()) {
                assertTrue(labels.add(alias),alias);
                assertSame(command,map.getCommand(alias),alias);
            }
            assertEquals("we.command",command.getPermission());
            assertNotNull(command.getCommandParameters("default"));
            assertEquals(command.syntax(),command.getUsage());
        }
        assertSame(vanillaHelp,map.getCommand("help"));
        assertNull(map.getCommand("/redo"));
        assertSame(map.getCommand("/schem"),map.getCommand("/schematic"));
    }
    @Test void metadataMatchesSupportedArgumentsAndPermissionVisibility() {
        var commands=CommandRegistry.create(mock(SchematicService.class));
        for(var command:commands) {
            var args=command.getCommandParameters("default");
            for(var arg:args) assertTrue(arg.type!=CommandParamType.RAWTEXT || arg.enumData!=null,command.getName());
            if(Set.of("/copy","/cut","/undo","/wand","/desel","/pos1","/pos2").contains(command.getName())) assertEquals(0,args.length);
            if(Set.of("/sphere","/hsphere","/cyl","/hcyl").contains(command.getName())) {
                assertEquals(CommandParamType.STRING,args[0].type); assertEquals(CommandParamType.INT,args[1].type);
            }
            if(command.getName().equals("/rotate")) {
                assertEquals(List.of("y"),args[0].enumData.getValues());assertEquals(CommandParamType.INT,args[1].type);
            }
            if(command.getName().equals("/schem")) {
                assertEquals(List.of("save","paste"),args[0].enumData.getValues());assertEquals(2,args.length);
            }
            Player denied=mock(Player.class);assertFalse(command.testPermissionSilent(denied));
            Player allowed=mock(Player.class);when(allowed.hasPermission("we.command")).thenReturn(true);
            assertTrue(command.testPermissionSilent(allowed));
        }
    }
    @Test void noArgumentCommandsRejectUnexpectedArgumentsBeforeStartingWork() {
        Player allowed=mock(Player.class);when(allowed.hasPermission("we.command")).thenReturn(true);
        assertFalse(new CopyCommand().execute(allowed,"/copy",new String[]{"unexpected"}));
        verify(allowed).sendMessage(String.format("Usage: %1$s","//copy"));
    }
    @Test void bedrockCommandMetadataCanBeGeneratedAndEncoded() {
        Player player=mock(Player.class);Server server=mock(Server.class);
        var language=mock(cn.nukkit.lang.BaseLang.class);
        when(player.hasPermission("we.command")).thenReturn(true);
        when(player.getServer()).thenReturn(server);when(server.getLanguage()).thenReturn(language);
        when(language.translateString(anyString())).thenAnswer(call->call.getArgument(0));
        var packet=new cn.nukkit.network.protocol.AvailableCommandsPacket();packet.commands=new LinkedHashMap<>();
        for(var command:CommandRegistry.create(mock(SchematicService.class))) {
            var data=command.generateCustomCommandData(player);assertNotNull(data);
            packet.commands.put(command.getName(),data);
        }
        assertDoesNotThrow(packet::encode);assertTrue(packet.getBuffer().length>0);
        when(player.hasPermission("we.command")).thenReturn(false);
        assertNull(new HelpCommand().generateCustomCommandData(player));
    }
}
