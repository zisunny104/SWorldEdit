package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.IOException;

/** Sequential, bounded pages of detached data. Implementations never access a live world. */
public interface PageStore extends AutoCloseable {
    long pageCount();
    long byteCount();
    Reader reader() throws IOException;
    @Override void close() throws IOException;

    interface Writer extends AutoCloseable {
        void append(byte[] page) throws IOException;
        PageStore finish() throws IOException;
        @Override void close() throws IOException;
    }

    interface Reader extends AutoCloseable {
        /** Returns a fresh page, or null after the validated end marker. */
        byte[] next() throws IOException;
        @Override void close() throws IOException;
    }
}
