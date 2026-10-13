package com.pikle6.splitfire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitFireTest {

    @TempDir
    Path tempDir;

    static Stream<Arguments> roundTrips() {
        return Stream.of(
                Arguments.of(0, Chunking.defaults()),
                Arguments.of(1, Chunking.defaults()),
                Arguments.of(1, Chunking.ofCount(1)),
                Arguments.of(1023, Chunking.ofSize(1024)),
                Arguments.of(1024, Chunking.ofSize(1024)),
                Arguments.of(1025, Chunking.ofSize(1024)),
                Arguments.of(2_000, Chunking.ofSize(1)),
                Arguments.of(10, Chunking.ofCount(3)),
                Arguments.of(10, Chunking.ofCount(10)),
                Arguments.of(200_003, Chunking.ofCount(7)),
                // Larger than the internal copy buffer, so chunks are copied in several pieces.
                Arguments.of(3 * 1024 * 1024 + 17, Chunking.defaults()),
                Arguments.of(3 * 1024 * 1024 + 17, Chunking.ofCount(2)));
    }

    @ParameterizedTest(name = "{0} bytes, {1}")
    @MethodSource("roundTrips")
    void splitThenAssembleRestoresTheOriginalBytes(int size, Chunking chunking) throws IOException {
        Path file = randomFile("data.bin", size);
        Path chunks = tempDir.resolve("chunks");

        Manifest manifest = SplitFire.split(file, chunks, chunking);
        assertEquals(chunking.chunkSizes(size).size(), manifest.chunks().size());
        assertEquals(manifest, SplitFire.verify(chunks.resolve("data.bin.splitfire")));

        Path restored = SplitFire.assemble(chunks.resolve("data.bin.splitfire"), tempDir.resolve("restored"));
        assertEquals(tempDir.resolve("restored").resolve("data.bin"), restored);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(restored));
    }

    @Test
    void chunksAreRawSlicesOfTheFileWithSortableNames() throws IOException {
        Path file = randomFile("photo.jpeg", 2500);
        Path chunks = tempDir.resolve("chunks");

        Manifest manifest = SplitFire.split(file, chunks, Chunking.ofSize(1000));

        assertEquals(List.of("photo.jpeg.001", "photo.jpeg.002", "photo.jpeg.003"),
                manifest.chunks().stream().map(Chunk::fileName).toList());
        assertEquals(List.of("photo.jpeg.001", "photo.jpeg.002", "photo.jpeg.003", "photo.jpeg.splitfire"), list(chunks));
        // Concatenating the chunks in name order gives back the original file.
        ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
        for (Chunk chunk : manifest.chunks()) {
            concatenated.writeBytes(Files.readAllBytes(chunks.resolve(chunk.fileName())));
        }
        assertArrayEquals(Files.readAllBytes(file), concatenated.toByteArray());
    }

    @Test
    void chunkNumbersAreWideEnoughForTheChunkCount() throws IOException {
        Manifest manifest = SplitFire.split(randomFile("f", 1200), tempDir.resolve("chunks"), Chunking.ofSize(1));
        assertEquals("f.0001", manifest.chunks().getFirst().fileName());
        assertEquals("f.1200", manifest.chunks().getLast().fileName());
    }

    @Test
    void manifestRecordsChecksums() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");

        Manifest manifest = SplitFire.split(file, tempDir.resolve("chunks"), Chunking.ofCount(1));

        String sha256OfHelloWorld = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertEquals(sha256OfHelloWorld, manifest.sha256());
        assertEquals(sha256OfHelloWorld, manifest.chunks().getFirst().sha256());
        assertEquals(manifest, Manifest.read(tempDir.resolve("chunks").resolve("hello.txt.splitfire")));
    }

    @Test
    void splitCanWriteNextToTheOriginalFile() throws IOException {
        Path file = randomFile("data.bin", 5000);
        byte[] original = Files.readAllBytes(file);

        SplitFire.split(file, tempDir, Chunking.ofCount(2));
        Files.delete(file);
        SplitFire.assemble(tempDir.resolve("data.bin.splitfire"), tempDir);

        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void assembledFilesGetTheSamePermissionsAsOtherNewFiles() throws IOException {
        Path chunks = splitSample();
        Path restored = assembleSample();
        assertEquals(Files.getPosixFilePermissions(chunks.resolve("data.bin.001")), Files.getPosixFilePermissions(restored));
    }

    @Test
    void splitRefusesToOverwriteUnlessAsked() throws IOException {
        Path file = randomFile("data.bin", 3000);
        Path chunks = tempDir.resolve("chunks");
        SplitFire.split(file, chunks, Chunking.ofSize(1000));
        Files.delete(chunks.resolve("data.bin.splitfire"));

        // A leftover chunk is enough to refuse, and nothing new is written.
        assertThrows(FileAlreadyExistsException.class, () -> SplitFire.split(file, chunks, Chunking.ofSize(1000)));
        assertEquals(List.of("data.bin.001", "data.bin.002", "data.bin.003"), list(chunks));

        Manifest manifest = SplitFire.split(file, chunks, Chunking.ofSize(1000), true);
        assertEquals(manifest, SplitFire.verify(chunks.resolve("data.bin.splitfire")));
    }

    @Test
    void splitRejectsMissingFilesAndDirectories() {
        assertThrows(NoSuchFileException.class,
                () -> SplitFire.split(tempDir.resolve("missing"), tempDir, Chunking.defaults()));
        IOException e = assertThrows(IOException.class, () -> SplitFire.split(tempDir, tempDir, Chunking.defaults()));
        assertTrue(e.getMessage().startsWith("not a regular file"), e.getMessage());
    }

    @Test
    void splitRejectsImpossibleChunkingBeforeWritingAnything() throws IOException {
        Path file = randomFile("tiny", 3);
        assertThrows(IllegalArgumentException.class,
                () -> SplitFire.split(file, tempDir.resolve("chunks"), Chunking.ofCount(5)));
        assertFalse(Files.exists(tempDir.resolve("chunks")));
    }

    @Test
    void assembleDetectsCorruptedChunks() throws IOException {
        Path chunks = splitSample();
        Path chunk = chunks.resolve("data.bin.002");
        byte[] bytes = Files.readAllBytes(chunk);
        bytes[123] ^= 1;
        Files.write(chunk, bytes);

        IntegrityException e = assertThrows(IntegrityException.class, this::assembleSample);
        assertTrue(e.getMessage().startsWith("chunk 2 of 3"), e.getMessage());
        assertTrue(e.getMessage().contains("is corrupt"), e.getMessage());
        assertNothingAssembled();
        assertThrows(IntegrityException.class, () -> SplitFire.verify(chunks.resolve("data.bin.splitfire")));
    }

    @Test
    void assembleDetectsTruncatedChunks() throws IOException {
        Path chunks = splitSample();
        Path chunk = chunks.resolve("data.bin.003");
        byte[] bytes = Files.readAllBytes(chunk);
        Files.write(chunk, Arrays.copyOf(bytes, bytes.length - 1));

        IntegrityException e = assertThrows(IntegrityException.class, this::assembleSample);
        assertTrue(e.getMessage().contains("has %d bytes, expected %d".formatted(bytes.length - 1, bytes.length)), e.getMessage());
        assertNothingAssembled();
    }

    @Test
    void assembleReportsMissingChunks() throws IOException {
        Path chunks = splitSample();
        Files.delete(chunks.resolve("data.bin.001"));

        NoSuchFileException e = assertThrows(NoSuchFileException.class, this::assembleSample);
        assertEquals("missing chunk 1 of 3", e.getReason());
        assertNothingAssembled();
    }

    @Test
    void assembleChecksTheWholeFileChecksum() throws IOException {
        Path chunks = splitSample();
        Path manifestFile = chunks.resolve("data.bin.splitfire");
        Manifest manifest = Manifest.read(manifestFile);
        new Manifest(manifest.fileName(), manifest.fileSize(), "0".repeat(64), manifest.chunks()).write(manifestFile);

        IntegrityException e = assertThrows(IntegrityException.class, this::assembleSample);
        assertTrue(e.getMessage().startsWith("reassembled data.bin is corrupt"), e.getMessage());
        assertNothingAssembled();
    }

    @Test
    void assembleRefusesToOverwriteUnlessAsked() throws IOException {
        splitSample();
        Path output = tempDir.resolve("restored");
        Files.createDirectories(output);
        Files.writeString(output.resolve("data.bin"), "existing");

        assertThrows(FileAlreadyExistsException.class, this::assembleSample);
        assertEquals("existing", Files.readString(output.resolve("data.bin")));

        SplitFire.assemble(tempDir.resolve("chunks").resolve("data.bin.splitfire"), output, true);
        assertArrayEquals(Files.readAllBytes(tempDir.resolve("data.bin")), Files.readAllBytes(output.resolve("data.bin")));
    }

    @Test
    void assembleRejectsManifestsThatPointOutsideTheirDirectory() throws IOException {
        Path chunks = splitSample();
        Path manifestFile = chunks.resolve("data.bin.splitfire");
        Files.writeString(manifestFile, Files.readString(manifestFile).replace("file.name=data.bin", "file.name=../data.bin"));

        assertThrows(InvalidManifestException.class, this::assembleSample);
        assertNothingAssembled();
    }

    @Test
    void findManifestsListsOnlyManifests() throws IOException {
        Path chunks = tempDir.resolve("chunks");
        SplitFire.split(randomFile("b.bin", 10), chunks, Chunking.defaults());
        SplitFire.split(randomFile("a.bin", 10), chunks, Chunking.defaults());
        Files.createDirectories(chunks.resolve("dir.splitfire"));

        assertEquals(List.of(chunks.resolve("a.bin.splitfire"), chunks.resolve("b.bin.splitfire")),
                SplitFire.findManifests(chunks));
    }

    /** Splits a 2500-byte {@code data.bin} into three chunks in {@code chunks/}. */
    private Path splitSample() throws IOException {
        Path chunks = tempDir.resolve("chunks");
        SplitFire.split(randomFile("data.bin", 2500), chunks, Chunking.ofSize(1000));
        return chunks;
    }

    private Path assembleSample() throws IOException {
        return SplitFire.assemble(tempDir.resolve("chunks").resolve("data.bin.splitfire"), tempDir.resolve("restored"));
    }

    /** A failed assembly must leave neither the output file nor temporary files behind. */
    private void assertNothingAssembled() throws IOException {
        Path output = tempDir.resolve("restored");
        assertTrue(!Files.exists(output) || list(output).isEmpty(), () -> "unexpected files: " + list(output));
    }

    private Path randomFile(String name, int size) throws IOException {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return Files.write(tempDir.resolve(name), data);
    }

    private static List<String> list(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
