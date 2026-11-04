package ru.SocialMoods.SWorldEdit.clipboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardCacheTest {
    @TempDir Path root;
    @Test void cleansOnlyAbandonedOwnedSessionsAndKeepsActiveOrUnknownFiles()throws Exception{
        Path abandoned=root.resolve("session-"+UUID.randomUUID());Files.createDirectory(abandoned);
        Files.writeString(abandoned.resolve("owner.lock"),"");Files.writeString(abandoned.resolve("swe-clipboard-123.partial"),"incomplete");
        Path foreign=root.resolve("session-"+UUID.randomUUID());Files.createDirectory(foreign);
        Files.writeString(foreign.resolve("owner.lock"),"");Files.writeString(foreign.resolve("important.txt"),"keep");
        Path first=root.resolve("session-"+UUID.randomUUID());
        try(var one=ClipboardCache.open(root,first,ignored->{})){
            assertFalse(Files.exists(abandoned));assertTrue(Files.exists(foreign.resolve("important.txt")));
            Path second=root.resolve("session-"+UUID.randomUUID());
            try(var two=ClipboardCache.open(root,second,ignored->{})){assertTrue(Files.exists(first.resolve("owner.lock")));}
            assertFalse(Files.exists(second));
        }
        assertFalse(Files.exists(first));
    }
}
