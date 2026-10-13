package com.pikle6.splitfire;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Describes a split file: its name, size and checksum, and the chunks it was cut into.
 *
 * <p>Manifests are stored as UTF-8 {@link Properties} text next to the chunk files, named after the original file
 * with a {@value #EXTENSION} extension:
 *
 * <pre>{@code
 * splitfire.format=1
 * file.name=movie.mp4
 * file.size=2500000
 * file.sha256=...
 * chunk.count=3
 * chunk.0.file=movie.mp4.001
 * chunk.0.size=1048576
 * chunk.0.sha256=...
 * ...
 * }</pre>
 *
 * @param fileName name of the original file
 * @param fileSize size of the original file in bytes; always the sum of the chunk sizes
 * @param sha256   lowercase hex SHA-256 digest of the original file
 * @param chunks   the chunks in order; {@code chunks.get(i).index() == i}
 */
public record Manifest(String fileName, long fileSize, String sha256, List<Chunk> chunks) {

    /** File extension of manifest files. */
    public static final String EXTENSION = ".splitfire";

    /** Version of the manifest format written by this release. */
    public static final int FORMAT_VERSION = 1;

    /** Manifests larger than this are rejected before parsing, so a wrong file can't exhaust memory. */
    private static final long MAX_MANIFEST_BYTES = 64L * 1024 * 1024;

    public Manifest {
        FileNames.requirePlainFileName(fileName, "file name");
        if (fileSize < 0) {
            throw new IllegalArgumentException("file size must not be negative, got " + fileSize);
        }
        sha256 = Hashing.requireSha256Hex(sha256, "file checksum");
        chunks = List.copyOf(chunks);

        long total = 0;
        Set<String> chunkFileNames = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            if (chunk.index() != i) {
                throw new IllegalArgumentException("chunk at position %d has index %d".formatted(i, chunk.index()));
            }
            if (!chunkFileNames.add(chunk.fileName())) {
                throw new IllegalArgumentException("chunk file name \"%s\" is used more than once".formatted(chunk.fileName()));
            }
            total = Math.addExact(total, chunk.size());
        }
        if (total != fileSize) {
            throw new IllegalArgumentException("chunk sizes add up to %d bytes but the file size is %d".formatted(total, fileSize));
        }
    }

    /** Name of the manifest file for an original file called {@code fileName}. */
    public static String fileNameFor(String fileName) {
        return fileName + EXTENSION;
    }

    /**
     * Reads and validates a manifest file.
     *
     * @throws InvalidManifestException if the file is not a valid manifest
     */
    public static Manifest read(Path path) throws IOException {
        if (Files.size(path) > MAX_MANIFEST_BYTES) {
            throw new InvalidManifestException("not a SplitFire manifest (too large): " + path);
        }
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new InvalidManifestException("not a SplitFire manifest (not UTF-8 text): " + path, e);
        }
        return parse(text, path.toString());
    }

    /**
     * Writes this manifest to {@code path}. Without options, an existing file is replaced; pass
     * {@link java.nio.file.StandardOpenOption#CREATE_NEW} to refuse that.
     */
    public void write(Path path, OpenOption... options) throws IOException {
        Files.writeString(path, format(), StandardCharsets.UTF_8, options);
    }

    static Manifest parse(String text, String source) throws InvalidManifestException {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidManifestException("invalid manifest " + source + ": " + e.getMessage(), e);
        }

        String format = properties.getProperty("splitfire.format");
        if (format == null) {
            throw new InvalidManifestException("not a SplitFire manifest: " + source);
        }
        if (!format.equals(Integer.toString(FORMAT_VERSION))) {
            throw new InvalidManifestException("unsupported manifest format \"%s\" in %s (this version of SplitFire reads format %d)"
                    .formatted(format, source, FORMAT_VERSION));
        }

        try {
            long chunkCount = number(properties, "chunk.count", source);
            if (chunkCount < 0 || chunkCount > Chunking.MAX_CHUNKS) {
                throw new InvalidManifestException("invalid manifest %s: chunk.count must be between 0 and %d"
                        .formatted(source, Chunking.MAX_CHUNKS));
            }
            List<Chunk> chunks = new ArrayList<>((int) chunkCount);
            for (int i = 0; i < chunkCount; i++) {
                String prefix = "chunk." + i + ".";
                chunks.add(new Chunk(
                        i,
                        required(properties, prefix + "file", source),
                        number(properties, prefix + "size", source),
                        required(properties, prefix + "sha256", source)));
            }
            return new Manifest(
                    required(properties, "file.name", source),
                    number(properties, "file.size", source),
                    required(properties, "file.sha256", source),
                    chunks);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new InvalidManifestException("invalid manifest " + source + ": " + e.getMessage(), e);
        }
    }

    String format() {
        StringBuilder text = new StringBuilder("# SplitFire manifest\n");
        appendEntry(text, "splitfire.format", Integer.toString(FORMAT_VERSION));
        appendEntry(text, "file.name", fileName);
        appendEntry(text, "file.size", Long.toString(fileSize));
        appendEntry(text, "file.sha256", sha256);
        appendEntry(text, "chunk.count", Integer.toString(chunks.size()));
        for (Chunk chunk : chunks) {
            String prefix = "chunk." + chunk.index() + ".";
            appendEntry(text, prefix + "file", chunk.fileName());
            appendEntry(text, prefix + "size", Long.toString(chunk.size()));
            appendEntry(text, prefix + "sha256", chunk.sha256());
        }
        return text.toString();
    }

    private static String required(Properties properties, String key, String source) throws InvalidManifestException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new InvalidManifestException("invalid manifest " + source + ": missing " + key);
        }
        return value;
    }

    private static long number(Properties properties, String key, String source) throws InvalidManifestException {
        String value = required(properties, key, source);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidManifestException("invalid manifest %s: %s is not a number: \"%s\"".formatted(source, key, value), e);
        }
    }

    /** Appends {@code key=value}, escaping the value so that {@link Properties#load} reads it back unchanged. */
    private static void appendEntry(StringBuilder text, String key, String value) {
        text.append(key).append('=');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> text.append("\\\\");
                case '\t' -> text.append("\\t");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\f' -> text.append("\\f");
                case ' ' -> text.append(i == 0 ? "\\ " : " ");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        text.append("\\u%04x".formatted((int) c));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        text.append('\n');
    }
}
