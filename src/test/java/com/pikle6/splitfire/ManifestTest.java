package com.pikle6.splitfire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    private static Manifest sample(String fileName) {
        return new Manifest(fileName, 15, HASH_A, List.of(
                new Chunk(0, fileName + ".001", 10, HASH_B),
                new Chunk(1, fileName + ".002", 5, HASH_C)));
    }

    @Test
    void formatIsStableAndHumanReadable() {
        assertEquals("""
                # SplitFire manifest
                splitfire.format=1
                file.name=notes.txt
                file.size=15
                file.sha256=%s
                chunk.count=2
                chunk.0.file=notes.txt.001
                chunk.0.size=10
                chunk.0.sha256=%s
                chunk.1.file=notes.txt.002
                chunk.1.size=5
                chunk.1.sha256=%s
                """.formatted(HASH_A, HASH_B, HASH_C), sample("notes.txt").format());
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.txt", " leading space", "trailing space ", "tab\there", "new\nline",
            "carriage\rreturn", "bell\u0007", "a=b:c#d!e", "#comment", "!bang", "équipe ☃ 日本.txt", "x"})
    void roundTripsUnusualFileNames(String fileName) throws Exception {
        Manifest manifest = sample(fileName);
        assertEquals(manifest, Manifest.parse(manifest.format(), "test"));
    }

    @Test
    void writesAndReadsFiles(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("notes.txt.splitfire");
        Manifest manifest = sample("notes.txt");
        manifest.write(path);
        assertEquals(manifest, Manifest.read(path));

        assertThrows(FileAlreadyExistsException.class, () -> manifest.write(path, StandardOpenOption.CREATE_NEW));
    }

    @Test
    void normalizesUppercaseChecksums() {
        Chunk chunk = new Chunk(0, "x.001", 1, "ABCDEF".repeat(10) + "ABCD");
        assertEquals("abcdef".repeat(10) + "abcd", chunk.sha256());
    }

    @Test
    void emptyFileHasNoChunks() throws Exception {
        Manifest manifest = new Manifest("empty", 0, HASH_A, List.of());
        assertEquals(manifest, Manifest.parse(manifest.format(), "test"));
    }

    @Test
    void rejectsInconsistentManifests() {
        assertThrows(IllegalArgumentException.class,
                () -> new Manifest("x", 16, HASH_A, sample("x").chunks()), "sizes don't add up");
        assertThrows(IllegalArgumentException.class,
                () -> new Manifest("x", 2, HASH_A, List.of(new Chunk(1, "x.001", 2, HASH_B))), "wrong index");
        assertThrows(IllegalArgumentException.class,
                () -> new Manifest("x", 2, HASH_A, List.of(new Chunk(0, "x.001", 1, HASH_B), new Chunk(1, "x.001", 1, HASH_B))),
                "duplicate chunk file");
        assertThrows(IllegalArgumentException.class, () -> new Chunk(0, "x.001", 0, HASH_B), "empty chunk");
        assertThrows(IllegalArgumentException.class, () -> new Chunk(0, "x.001", 1, "not-a-hash"), "bad hash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "..", "../evil", "dir/file", "dir\\file", "/etc/passwd", "C:\\evil", "nul\u0000"})
    void rejectsFileNamesThatAreNotPlainNames(String fileName) {
        assertThrows(IllegalArgumentException.class, () -> new Manifest(fileName, 0, HASH_A, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(0, fileName, 1, HASH_A));
    }

    @Test
    void parseRejectsPathTraversal() {
        String text = sample("x").format().replace("chunk.1.file=x.002", "chunk.1.file=../../x.002");
        var e = assertThrows(InvalidManifestException.class, () -> Manifest.parse(text, "test"));
        assertTrue(e.getMessage().contains("../../x.002"), e.getMessage());
    }

    @Test
    void parseRejectsOtherFilesAndVersions() {
        var notManifest = assertThrows(InvalidManifestException.class, () -> Manifest.parse("hello=world\n", "test"));
        assertEquals("not a SplitFire manifest: test", notManifest.getMessage());

        String future = sample("x").format().replace("splitfire.format=1", "splitfire.format=2");
        var unsupported = assertThrows(InvalidManifestException.class, () -> Manifest.parse(future, "test"));
        assertTrue(unsupported.getMessage().contains("unsupported manifest format \"2\""), unsupported.getMessage());
    }

    @Test
    void parseExplainsWhatIsWrong() {
        String valid = sample("x").format();
        assertParseError(valid.replace("chunk.1.size=5\n", ""), "missing chunk.1.size");
        assertParseError(valid.replace("file.size=15", "file.size=lots"), "file.size is not a number: \"lots\"");
        assertParseError(valid.replace("file.size=15", "file.size=16"), "chunk sizes add up to 15 bytes but the file size is 16");
        assertParseError(valid.replace("chunk.count=2", "chunk.count=-1"), "chunk.count must be between 0");
        assertParseError(valid.replace("chunk.count=2", "chunk.count=999999999"), "chunk.count must be between 0");
        assertParseError(valid.replace("chunk.count=2", "chunk.count=3"), "missing chunk.2.file");
        assertParseError(valid.replace(HASH_C, "123"), "\"123\" is not a SHA-256 hex digest");
    }

    @Test
    void readRejectsBinaryFiles(@TempDir Path dir) throws Exception {
        Path binary = dir.resolve("random.splitfire");
        Files.write(binary, new byte[] {(byte) 0xff, (byte) 0xfe, 0, 1, 2, (byte) 0xc3});
        var e = assertThrows(InvalidManifestException.class, () -> Manifest.read(binary));
        assertTrue(e.getMessage().startsWith("not a SplitFire manifest"), e.getMessage());
    }

    private static void assertParseError(String text, String expectedMessagePart) {
        var e = assertThrows(InvalidManifestException.class, () -> Manifest.parse(text, "test"));
        assertTrue(e.getMessage().contains(expectedMessagePart),
                () -> "expected \"" + expectedMessagePart + "\" in: " + e.getMessage());
    }
}
