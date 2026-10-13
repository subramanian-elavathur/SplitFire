package com.pikle6.splitfire;

/**
 * One piece of a split file, as recorded in its {@link Manifest}.
 *
 * @param index    zero-based position of the chunk within the original file
 * @param fileName name of the chunk file, which lives next to the manifest
 * @param size     number of bytes in the chunk
 * @param sha256   lowercase hex SHA-256 digest of the chunk's bytes
 */
public record Chunk(int index, String fileName, long size, String sha256) {

    public Chunk {
        if (index < 0) {
            throw new IllegalArgumentException("chunk index must not be negative, got " + index);
        }
        FileNames.requirePlainFileName(fileName, "chunk file name");
        if (size <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, got " + size);
        }
        sha256 = Hashing.requireSha256Hex(sha256, "chunk checksum");
    }
}
