package com.pikle6.splitfire;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Splits files into checksummed chunks and reassembles them.
 *
 * <p>Splitting {@code movie.mp4} writes the chunks {@code movie.mp4.001}, {@code movie.mp4.002}, ... plus a
 * {@link Manifest} called {@code movie.mp4.splitfire} that records the size and SHA-256 checksum of every chunk and of
 * the whole file. Chunks hold the raw bytes of the file, so they can be moved around independently; reassembly checks
 * every checksum and only produces the output file once all of them match.
 *
 * <p>Data is streamed with a small fixed-size buffer, so files and chunks of any size can be processed.
 */
public final class SplitFire {

    private static final int BUFFER_SIZE = 64 * 1024;

    private SplitFire() {
    }

    /** Splits {@code file} into {@code outputDirectory}, refusing to overwrite existing files. */
    public static Manifest split(Path file, Path outputDirectory, Chunking chunking) throws IOException {
        return split(file, outputDirectory, chunking, false);
    }

    /**
     * Splits {@code file} into chunk files plus a manifest in {@code outputDirectory}, creating the directory if
     * needed.
     *
     * @param overwrite whether existing chunk or manifest files may be replaced; if {@code false} and any of them
     *                  exists, nothing is written and {@link FileAlreadyExistsException} is thrown
     * @return the manifest that was written
     * @throws IllegalArgumentException if {@code chunking} cannot be applied to a file of this size
     */
    public static Manifest split(Path file, Path outputDirectory, Chunking chunking, boolean overwrite) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(chunking, "chunking");
        if (!Files.isRegularFile(file)) {
            throw Files.exists(file)
                    ? new IOException("not a regular file: " + file)
                    : new NoSuchFileException(file.toString());
        }
        String fileName = FileNames.requirePlainFileName(file.getFileName().toString(), "file name");
        long fileSize = Files.size(file);
        List<Long> chunkSizes = chunking.chunkSizes(fileSize);

        Path directory = createDirectories(outputDirectory);
        Path manifestPath = directory.resolve(Manifest.fileNameFor(fileName));
        List<Path> chunkPaths = new ArrayList<>(chunkSizes.size());
        for (int i = 0; i < chunkSizes.size(); i++) {
            chunkPaths.add(directory.resolve(FileNames.chunkFileName(fileName, i, chunkSizes.size())));
        }
        if (!overwrite) {
            requireAbsent(manifestPath);
            for (Path chunkPath : chunkPaths) {
                requireAbsent(chunkPath);
            }
        }

        OpenOption[] writeOptions = overwrite ? new OpenOption[0] : new OpenOption[] {StandardOpenOption.CREATE_NEW};
        List<Path> written = new ArrayList<>();
        boolean complete = false;
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest fileDigest = Hashing.sha256();
            byte[] buffer = new byte[BUFFER_SIZE];
            List<Chunk> chunks = new ArrayList<>(chunkSizes.size());
            for (int i = 0; i < chunkSizes.size(); i++) {
                Path chunkPath = chunkPaths.get(i);
                long size = chunkSizes.get(i);
                MessageDigest chunkDigest = Hashing.sha256();
                try (OutputStream out = Files.newOutputStream(chunkPath, writeOptions)) {
                    written.add(chunkPath);
                    if (copy(in, out, size, buffer, chunkDigest, fileDigest) != size) {
                        throw new IOException("file shrank while it was being split: " + file);
                    }
                }
                chunks.add(new Chunk(i, chunkPath.getFileName().toString(), size, Hashing.hex(chunkDigest)));
            }
            if (in.read() != -1) {
                throw new IOException("file grew while it was being split: " + file);
            }

            Manifest manifest = new Manifest(fileName, fileSize, Hashing.hex(fileDigest), chunks);
            manifest.write(manifestPath, writeOptions);
            complete = true;
            return manifest;
        } finally {
            if (!complete) {
                deleteQuietly(written);
            }
        }
    }

    /** Reassembles the file described by {@code manifestFile} into {@code outputDirectory}, refusing to overwrite. */
    public static Path assemble(Path manifestFile, Path outputDirectory) throws IOException {
        return assemble(manifestFile, outputDirectory, false);
    }

    /**
     * Reassembles the file described by {@code manifestFile} from the chunk files next to it, writing it into
     * {@code outputDirectory} (created if needed) under its original name.
     *
     * <p>The file is first written to a temporary file in {@code outputDirectory} and only moved into place once the
     * size and checksum of every chunk and of the whole file have been verified, so a failed assembly never leaves a
     * partial file behind.
     *
     * @param overwrite whether an existing file with the same name may be replaced
     * @return the path of the reassembled file
     * @throws InvalidManifestException if the manifest is malformed
     * @throws NoSuchFileException      if a chunk file is missing
     * @throws IntegrityException       if a chunk or the reassembled file fails verification
     */
    public static Path assemble(Path manifestFile, Path outputDirectory, boolean overwrite) throws IOException {
        Objects.requireNonNull(manifestFile, "manifestFile");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Manifest manifest = Manifest.read(manifestFile);

        Path directory = createDirectories(outputDirectory);
        Path target = FileNames.resolveChild(directory, manifest.fileName());
        if (!overwrite) {
            requireAbsent(target);
        }

        // Not Files.createTempFile: that restricts permissions to the owner, which would carry over to the result.
        Path temp = directory.resolve(".splitfire-" + UUID.randomUUID() + ".tmp");
        boolean complete = false;
        try {
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {
                copyChunks(manifest, chunkDirectory(manifestFile), out);
            }
            CopyOption[] moveOptions = overwrite ? new CopyOption[] {StandardCopyOption.REPLACE_EXISTING} : new CopyOption[0];
            Files.move(temp, target, moveOptions);
            complete = true;
            return target;
        } finally {
            if (!complete) {
                deleteQuietly(List.of(temp));
            }
        }
    }

    /**
     * Checks that every chunk described by {@code manifestFile} is present and intact, without writing anything.
     *
     * @return the verified manifest
     * @throws InvalidManifestException if the manifest is malformed
     * @throws NoSuchFileException      if a chunk file is missing
     * @throws IntegrityException       if a chunk fails verification
     */
    public static Manifest verify(Path manifestFile) throws IOException {
        Objects.requireNonNull(manifestFile, "manifestFile");
        Manifest manifest = Manifest.read(manifestFile);
        copyChunks(manifest, chunkDirectory(manifestFile), OutputStream.nullOutputStream());
        return manifest;
    }

    /** Lists the manifest files ({@code *.splitfire}) directly inside {@code directory}, sorted by name. */
    public static List<Path> findManifests(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(Manifest.EXTENSION))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }

    /** Streams every chunk of {@code manifest} into {@code out}, verifying sizes and checksums along the way. */
    private static void copyChunks(Manifest manifest, Path chunkDirectory, OutputStream out) throws IOException {
        MessageDigest fileDigest = Hashing.sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        int chunkCount = manifest.chunks().size();
        for (Chunk chunk : manifest.chunks()) {
            Path path = FileNames.resolveChild(chunkDirectory, chunk.fileName());
            String label = "chunk %d of %d (%s)".formatted(chunk.index() + 1, chunkCount, path);
            if (!Files.isRegularFile(path)) {
                throw new NoSuchFileException(path.toString(), null, "missing chunk %d of %d".formatted(chunk.index() + 1, chunkCount));
            }
            long actualSize = Files.size(path);
            if (actualSize != chunk.size()) {
                throw new IntegrityException("%s has %d bytes, expected %d".formatted(label, actualSize, chunk.size()));
            }
            MessageDigest chunkDigest = Hashing.sha256();
            try (InputStream in = Files.newInputStream(path)) {
                if (copy(in, out, chunk.size(), buffer, chunkDigest, fileDigest) != chunk.size()) {
                    throw new IntegrityException(label + " was truncated while it was being read");
                }
            }
            String actual = Hashing.hex(chunkDigest);
            if (!actual.equals(chunk.sha256())) {
                throw new IntegrityException("%s is corrupt: SHA-256 is %s, expected %s".formatted(label, actual, chunk.sha256()));
            }
        }
        String actual = Hashing.hex(fileDigest);
        if (!actual.equals(manifest.sha256())) {
            throw new IntegrityException("reassembled %s is corrupt: SHA-256 is %s, expected %s"
                    .formatted(manifest.fileName(), actual, manifest.sha256()));
        }
    }

    /**
     * Copies up to {@code length} bytes from {@code in} to {@code out}, feeding them to each digest.
     *
     * @return the number of bytes copied, which is less than {@code length} only if {@code in} ended early
     */
    private static long copy(InputStream in, OutputStream out, long length, byte[] buffer, MessageDigest... digests)
            throws IOException {
        long copied = 0;
        while (copied < length) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, length - copied));
            if (read < 0) {
                break;
            }
            for (MessageDigest digest : digests) {
                digest.update(buffer, 0, read);
            }
            out.write(buffer, 0, read);
            copied += read;
        }
        return copied;
    }

    private static Path chunkDirectory(Path manifestFile) {
        return manifestFile.toAbsolutePath().getParent();
    }

    private static Path createDirectories(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath();
        // Files.createDirectories rejects an existing symlink to a directory, so check first.
        if (!Files.isDirectory(absolute)) {
            Files.createDirectories(absolute);
        }
        return absolute;
    }

    private static void requireAbsent(Path path) throws FileAlreadyExistsException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(path.toString());
        }
    }

    private static void deleteQuietly(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best-effort cleanup; the original failure is more useful to the caller.
            }
        }
    }
}
