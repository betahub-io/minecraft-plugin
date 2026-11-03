package io.betahub.minecraft.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Reads the last lines of a (possibly large) UTF-8 text file without loading all of it. */
public final class LogTail {

    private static final int CHUNK_BYTES = 8 * 1024;
    private static final byte NEWLINE = '\n';

    private LogTail() {
    }

    /**
     * @param maxLines how many trailing lines to return
     * @param maxBytes hard cap on bytes read, so one huge line cannot blow up memory
     */
    public static String read(Path file, int maxLines, int maxBytes) throws IOException {
        if (maxLines < 1 || maxBytes < 1) {
            throw new IllegalArgumentException("maxLines and maxBytes must be positive");
        }
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long size = channel.size();
            long end = size;
            // A trailing newline ends the last line; it does not start a new one.
            if (size > 0 && byteAt(channel, size - 1) == NEWLINE) {
                end = size - 1;
            }
            long limit = Math.max(0, end - maxBytes);
            long start = findStart(channel, end, limit, maxLines);
            if (start == limit && limit > 0 && byteAt(channel, limit - 1) != NEWLINE) {
                // The cap cut into a line; a partial first line could hold half an IP address that redaction misses.
                start = nextLineStart(channel, limit, end);
            }

            ByteBuffer buffer = ByteBuffer.allocate((int) (end - start));
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // keep reading until the buffer is full
            }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        }
    }

    /** Offset of the first byte of the {@code maxLines}-th line counted back from {@code end}. */
    private static long findStart(SeekableByteChannel channel, long end, long limit, int maxLines) throws IOException {
        int newlines = 0;
        long chunkEnd = end;
        ByteBuffer chunk = ByteBuffer.allocate(CHUNK_BYTES);
        while (chunkEnd > limit) {
            long chunkStart = Math.max(limit, chunkEnd - CHUNK_BYTES);
            chunk.clear().limit((int) (chunkEnd - chunkStart));
            channel.position(chunkStart);
            while (chunk.hasRemaining() && channel.read(chunk) > 0) {
                // fill the chunk
            }
            for (int i = chunk.position() - 1; i >= 0; i--) {
                if (chunk.get(i) == NEWLINE) {
                    newlines++;
                    if (newlines == maxLines) {
                        return chunkStart + i + 1;
                    }
                }
            }
            chunkEnd = chunkStart;
        }
        return limit;
    }

    /** Offset after the first newline in [from, end), or {@code from} when there is none (one huge line). */
    private static long nextLineStart(SeekableByteChannel channel, long from, long end) throws IOException {
        ByteBuffer chunk = ByteBuffer.allocate(CHUNK_BYTES);
        for (long chunkStart = from; chunkStart < end; chunkStart += CHUNK_BYTES) {
            chunk.clear().limit((int) Math.min(CHUNK_BYTES, end - chunkStart));
            channel.position(chunkStart);
            while (chunk.hasRemaining() && channel.read(chunk) > 0) {
                // fill the chunk
            }
            for (int i = 0; i < chunk.position(); i++) {
                if (chunk.get(i) == NEWLINE) {
                    return chunkStart + i + 1;
                }
            }
        }
        return from;
    }

    private static byte byteAt(SeekableByteChannel channel, long position) throws IOException {
        ByteBuffer one = ByteBuffer.allocate(1);
        channel.position(position);
        channel.read(one);
        return one.get(0);
    }
}
