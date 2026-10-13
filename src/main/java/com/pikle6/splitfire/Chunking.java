package com.pikle6.splitfire;

import java.util.List;
import java.util.stream.LongStream;

/**
 * Decides how a file is cut into chunks: either by a maximum chunk size or by a fixed number of chunks.
 */
public sealed interface Chunking {

    /** Chunk size used by {@link #defaults()}: 1 MiB. */
    long DEFAULT_CHUNK_SIZE = 1024 * 1024;

    /** Upper bound on the number of chunks a single file may be split into. */
    int MAX_CHUNKS = 100_000;

    /** Chunks of at most {@code chunkSize} bytes; only the last chunk may be smaller. */
    static Chunking ofSize(long chunkSize) {
        return new BySize(chunkSize);
    }

    /** Exactly {@code chunkCount} chunks whose sizes differ by at most one byte. */
    static Chunking ofCount(int chunkCount) {
        return new ByCount(chunkCount);
    }

    /** Chunks of at most {@link #DEFAULT_CHUNK_SIZE} bytes. */
    static Chunking defaults() {
        return ofSize(DEFAULT_CHUNK_SIZE);
    }

    /**
     * Returns the size of each chunk, in order, for a file of {@code fileSize} bytes. An empty file has no chunks.
     *
     * @throws IllegalArgumentException if the file cannot be split this way
     */
    List<Long> chunkSizes(long fileSize);

    record BySize(long chunkSize) implements Chunking {

        public BySize {
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("chunk size must be positive, got " + chunkSize);
            }
        }

        @Override
        public List<Long> chunkSizes(long fileSize) {
            requireValidFileSize(fileSize);
            long count = Math.ceilDiv(fileSize, chunkSize);
            if (count > MAX_CHUNKS) {
                throw new IllegalArgumentException("a chunk size of %d bytes would produce %d chunks (maximum is %d); use a larger chunk size"
                        .formatted(chunkSize, count, MAX_CHUNKS));
            }
            return LongStream.range(0, count)
                    .map(i -> Math.min(chunkSize, fileSize - i * chunkSize))
                    .boxed()
                    .toList();
        }
    }

    record ByCount(int chunkCount) implements Chunking {

        public ByCount {
            if (chunkCount <= 0 || chunkCount > MAX_CHUNKS) {
                throw new IllegalArgumentException("chunk count must be between 1 and %d, got %d".formatted(MAX_CHUNKS, chunkCount));
            }
        }

        @Override
        public List<Long> chunkSizes(long fileSize) {
            requireValidFileSize(fileSize);
            if (fileSize == 0) {
                return List.of();
            }
            if (fileSize < chunkCount) {
                throw new IllegalArgumentException("cannot split a %d-byte file into %d chunks".formatted(fileSize, chunkCount));
            }
            long base = fileSize / chunkCount;
            long remainder = fileSize % chunkCount;
            return LongStream.range(0, chunkCount)
                    .map(i -> i < remainder ? base + 1 : base)
                    .boxed()
                    .toList();
        }
    }

    private static void requireValidFileSize(long fileSize) {
        if (fileSize < 0) {
            throw new IllegalArgumentException("file size must not be negative, got " + fileSize);
        }
    }
}
