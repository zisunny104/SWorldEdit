package ru.SocialMoods.SWorldEdit.clipboard;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class MemoryPageStoreTest {
    @Test void boundsEmptyPageOverheadAndDoesNotShareArrays() throws Exception {
        try (var writer = new MemoryPageStore.Writer(32, 64)) {
            byte[] page = {7};writer.append(page);page[0] = 99;
            assertThrows(IOException.class, () -> writer.append(new byte[0]));
            try (var store = writer.finish()) {
                var first = store.reader();assertEquals(7, first.next()[0]);store.close();
                assertNull(first.next());first.close();assertThrows(IOException.class, store::reader);
            }
        }
    }
}
