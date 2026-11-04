package ru.SocialMoods.SWorldEdit.clipboard;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** A process lock distinguishes abandoned session files from another running test/server. */
public final class ClipboardCache implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    private ClipboardCache(Path directory, FileChannel channel, FileLock lock) {
        this.directory=directory;this.channel=channel;this.lock=lock;
    }

    public static ClipboardCache open(Path root, Path session, Consumer<String> warn) throws IOException {
        Files.createDirectories(root);
        try(var directories=Files.newDirectoryStream(root,"session-*")) {
            for(Path old:directories) {
                if(!Files.isDirectory(old,LinkOption.NOFOLLOW_LINKS))continue;
                try{UUID.fromString(old.getFileName().toString().substring(8));}
                catch(IllegalArgumentException e){continue;}
                try{cleanAbandoned(old);}catch(IOException e){warn.accept("Cannot clean old clipboard session: "+old.getFileName()+": "+e.getMessage());}
            }
        }
        Files.createDirectory(session);
        FileChannel channel=FileChannel.open(session.resolve("owner.lock"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try{return new ClipboardCache(session,channel,channel.lock());}
        catch(IOException|RuntimeException e){channel.close();throw e;}
    }

    private static void cleanAbandoned(Path old)throws IOException {
        Path marker=old.resolve("owner.lock");
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;
        List<Path> files=new ArrayList<>();
        try(var entries=Files.newDirectoryStream(old)) {
            for(Path path:entries) {
                if(path.equals(marker))continue;
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                        || !path.getFileName().toString().matches("swe-clipboard-[A-Za-z0-9-]+\\.(partial|ready)"))return;
                files.add(path);
            }
        }
        try(FileChannel channel=FileChannel.open(marker,StandardOpenOption.WRITE)) {
            FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException e){return;}
            if(lock==null)return;
            try(lock){for(Path file:files)Files.deleteIfExists(file);}
        }
        Files.deleteIfExists(marker);Files.deleteIfExists(old);
    }

    @Override public void close()throws IOException {
        try{lock.release();}finally{channel.close();}
        Files.deleteIfExists(directory.resolve("owner.lock"));Files.deleteIfExists(directory);
    }
}
