package io.betahub.minecraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogTailTest {

    private static final int LARGE = 1_000_000;

    @TempDir
    Path dir;

    @Test
    void returnsLastLinesWithoutTrailingNewline() throws IOException {
        Path log = write("one\ntwo\nthree\nfour\n");

        assertEquals("three\nfour", LogTail.read(log, 2, LARGE));
    }

    @Test
    void returnsWholeFileWhenShorterThanRequested() throws IOException {
        Path log = write("one\ntwo");

        assertEquals("one\ntwo", LogTail.read(log, 10, LARGE));
    }

    @Test
    void handlesEmptyFile() throws IOException {
        assertEquals("", LogTail.read(write(""), 5, LARGE));
    }

    @Test
    void readsAcrossChunkBoundaries() throws IOException {
        String content = IntStream.range(0, 5_000).mapToObj(i -> "line " + i + " żółć")
                .collect(Collectors.joining("\n", "", "\n"));
        Path log = write(content);

        String tail = LogTail.read(log, 3, LARGE);

        assertEquals("line 4997 żółć\nline 4998 żółć\nline 4999 żółć", tail);
    }

    @Test
    void byteCapLimitsOutput() throws IOException {
        Path log = write("a".repeat(100) + "\n" + "b".repeat(100) + "\n");

        assertEquals("b".repeat(50), LogTail.read(log, 10, 50));
    }

    @Test
    void cappedExcerptStartsAtALineBoundary() throws IOException {
        Path log = write("first 192.168.1.10 line\nsecond\nthird\n");

        assertEquals("second\nthird", LogTail.read(log, 10, 15));
    }

    @Test
    void rejectsNonPositiveLimits() throws IOException {
        Path log = write("x");
        assertThrows(IllegalArgumentException.class, () -> LogTail.read(log, 0, LARGE));
    }

    private Path write(String content) throws IOException {
        Path file = dir.resolve("latest.log");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
