package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import org.junit.jupiter.api.Test;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.utils.WorkTracker;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleCommandTest {
    @Test void normalEntryRetainsSubmittedSelectionAndRestoresLaterSelection() {
        var plugin=mock(SWorldEdit.class);var server=mock(Server.class);var map=mock(SimpleCommandMap.class);
        var player=mock(Player.class);var level=mock(Level.class);var owner=new PlayerData(player);
        when(plugin.getServer()).thenReturn(server);when(server.getCommandMap()).thenReturn(map);
        when(plugin.getWEPlayer(player)).thenReturn(owner);when(player.hasPermission("we.command")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);when(player.getLevel()).thenReturn(level);player.level=level;
        when(server.getLevel(level.getId())).thenReturn(level);
        var ticks=new ArrayDeque<Runnable>();var clock=new AtomicLong();int[] calls={0};
        try(var tracker=new WorkTracker(ticks::add,(p,message)->{},5,clock::get);
            var singleton=mockStatic(SWorldEdit.class)) {
            singleton.when(SWorldEdit::get).thenReturn(plugin);when(plugin.getWorkTracker()).thenReturn(tracker);
            WECommand edit=new WECommand("/set","set") {
                @Override public void execute(Player p,PlayerData data,String[] args) {
                    assertEquals(1,data.getSelection().getPosOne().getFloorX());
                    assertArrayEquals(new String[]{"stone"},args);calls[0]++;
                }
            };
            when(map.getCommand("/set")).thenReturn(edit);
            Position submitted=new Position(1,70,1,level);
            owner.getSelection().setPosOne(submitted);owner.getSelection().setPosTwo(new Position(2,70,2,level));
            assertTrue(new ScheduleCommand().execute(player,"/schedule",new String[]{"5","//set","stone"}));
            assertEquals(0,calls[0]);submitted.x=100;
            Position later=new Position(10,70,10,level);owner.getSelection().setPosOne(later);
            clock.set(TimeUnit.SECONDS.toNanos(5));ticks.remove().run();
            assertEquals(1,calls[0]);assertSame(later,owner.getSelection().getPosOne());
        }
    }
}
