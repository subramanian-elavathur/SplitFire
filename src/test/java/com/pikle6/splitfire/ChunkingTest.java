package com.pikle6.splitfire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingTest {

    @Test
    void bySizeCutsFullChunksAndOneRemainder() {
        assertEquals(List.of(3L, 3L, 3L, 1L), Chunking.ofSize(3).chunkSizes(10));
        assertEquals(List.of(3L, 3L, 3L), Chunking.ofSize(3).chunkSizes(9));
        assertEquals(List.of(1L), Chunking.ofSize(1024).chunkSizes(1));
    }

    @Test
    void byCountProducesExactlyThatManyNearlyEqualChunks() {
        assertEquals(List.of(4L, 3L, 3L), Chunking.ofCount(3).chunkSizes(10));
        assertEquals(List.of(3L, 3L, 3L), Chunking.ofCount(3).chunkSizes(9));
        assertEquals(List.of(1L, 1L, 1L), Chunking.ofCount(3).chunkSizes(3));
    }

    @Test
    void emptyFileHasNoChunks() {
        assertEquals(List.of(), Chunking.ofSize(10).chunkSizes(0));
        assertEquals(List.of(), Chunking.ofCount(10).chunkSizes(0));
    }

    @Test
    void handlesFilesLargerThanTwoGigabytes() {
        long fileSize = 10L * 1024 * 1024 * 1024 + 1;
        List<Long> sizes = Chunking.ofCount(3).chunkSizes(fileSize);
        assertEquals(3, sizes.size());
        assertEquals(fileSize, sizes.stream().mapToLong(Long::longValue).sum());
        assertTrue(sizes.getFirst() > Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 7, 1000, 1023, 1024, 1025, 99_999})
    void chunkSizesAlwaysAddUpToTheFileSize(long fileSize) {
        for (Chunking chunking : List.of(Chunking.ofSize(1), Chunking.ofSize(7), Chunking.ofSize(1024),
                Chunking.ofCount(1), Chunking.ofCount((int) Math.min(fileSize, 13)))) {
            List<Long> sizes = chunking.chunkSizes(fileSize);
            assertEquals(fileSize, sizes.stream().mapToLong(Long::longValue).sum(), chunking.toString());
            assertTrue(sizes.stream().allMatch(size -> size > 0), chunking.toString());
        }
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> Chunking.ofSize(0));
        assertThrows(IllegalArgumentException.class, () -> Chunking.ofSize(-1));
        assertThrows(IllegalArgumentException.class, () -> Chunking.ofCount(0));
        assertThrows(IllegalArgumentException.class, () -> Chunking.ofCount(Chunking.MAX_CHUNKS + 1));
        assertThrows(IllegalArgumentException.class, () -> Chunking.defaults().chunkSizes(-1));
    }

    @Test
    void rejectsMoreChunksThanBytes() {
        var e = assertThrows(IllegalArgumentException.class, () -> Chunking.ofCount(5).chunkSizes(3));
        assertEquals("cannot split a 3-byte file into 5 chunks", e.getMessage());
    }

    @Test
    void rejectsTooManyChunks() {
        assertEquals(Chunking.MAX_CHUNKS, Chunking.ofSize(1).chunkSizes(Chunking.MAX_CHUNKS).size());
        assertThrows(IllegalArgumentException.class, () -> Chunking.ofSize(1).chunkSizes(Chunking.MAX_CHUNKS + 1));
    }
}
