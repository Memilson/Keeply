package com.keeply.agent.daemon;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DaemonLogStreamer implements Runnable {
    private static final long READ_LIMIT_BYTES = 256L * 1024;

    private final Path path;
    private final Consumer<String> output;
    private final AtomicLong offset = new AtomicLong();

    public DaemonLogStreamer(Path path, Consumer<String> output) {
        this.path = path;
        this.output = output;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                streamNewContent();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // Log streaming is best effort and cannot terminate the UI.
            }
        }
    }

    private void streamNewContent() throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        long size = Files.size(path);
        long previous = offset.get();
        if (size < previous) {
            previous = 0;
            offset.set(0);
        }
        if (size <= previous) {
            return;
        }
        long start = Math.max(previous, size - READ_LIMIT_BYTES);
        String chunk;
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(size - start));
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
            }
            buffer.flip();
            chunk = StandardCharsets.UTF_8.decode(buffer).toString();
        }
        offset.set(size);
        StringBuilder visible = new StringBuilder();
        if (start > previous) {
            visible.append("[daemon] ... eventos anteriores omitidos da visualizacao ...\n");
        }
        for (String line : chunk.split("\\R")) {
            if (!line.isBlank()) {
                visible.append("[daemon] ").append(line.trim()).append('\n');
            }
        }
        if (!visible.isEmpty()) {
            output.accept(visible.toString());
        }
    }
}
