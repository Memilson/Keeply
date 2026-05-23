package com.keeply.agent.daemon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DaemonInstanceLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private DaemonInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static DaemonInstanceLock acquire(Path lockPath) throws IOException {
        FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        FileLock lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            throw new IllegalStateException("Daemon já está em execução.");
        }

        String pid = Long.toString(ProcessHandle.current().pid());
        channel.position(0);
        channel.write(ByteBuffer.wrap(pid.getBytes(StandardCharsets.UTF_8)));
        channel.force(true);

        return new DaemonInstanceLock(channel, lock);
    }

    @Override
    public void close() throws IOException {
        lock.release();
        channel.close();
    }
}
