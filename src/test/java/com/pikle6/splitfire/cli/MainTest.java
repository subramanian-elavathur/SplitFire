package com.pikle6.splitfire.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static com.pikle6.splitfire.cli.Main.EXIT_FAILURE;
import static com.pikle6.splitfire.cli.Main.EXIT_OK;
import static com.pikle6.splitfire.cli.Main.EXIT_USAGE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return new Main(new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8))
                .run(args);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void splitVerifyAndAssembleEndToEnd() throws IOException {
        Path file = randomFile("movie.mp4", 5_000_000);
        Path chunks = tempDir.resolve("chunks");
        Path restored = tempDir.resolve("restored");

        assertEquals(EXIT_OK, run("split", file.toString(), "--size", "2M", "-o", chunks.toString()), err());
        assertTrue(out().contains("Split movie.mp4 (4.8 MiB) into 3 chunks"), out());
        assertTrue(Files.exists(chunks.resolve("movie.mp4.003")));

        assertEquals(EXIT_OK, run("verify", chunks.resolve("movie.mp4.splitfire").toString()), err());
        assertTrue(out().contains("OK movie.mp4 (4.8 MiB, 3 chunks)"), out());

        assertEquals(EXIT_OK, run("assemble", chunks.toString(), "--output=" + restored), err());
        assertTrue(out().contains("Assembled " + restored.resolve("movie.mp4")), out());
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(restored.resolve("movie.mp4")));
    }

    @Test
    void assemblesAndVerifiesEveryManifestInADirectory() throws IOException {
        Path chunks = tempDir.resolve("chunks");
        assertEquals(EXIT_OK, run("split", randomFile("a.bin", 100).toString(), "-n", "4", "-o", chunks.toString()));
        assertEquals(EXIT_OK, run("split", randomFile("b.bin", 100).toString(), "-n", "4", "-o", chunks.toString()));

        assertEquals(EXIT_OK, run("verify", chunks.toString()), err());
        assertTrue(out().contains("OK a.bin") && out().contains("OK b.bin"), out());

        assertEquals(EXIT_OK, run("assemble", chunks.toString(), "-o", tempDir.resolve("restored").toString()), err());
        assertArrayEquals(Files.readAllBytes(tempDir.resolve("a.bin")), Files.readAllBytes(tempDir.resolve("restored/a.bin")));
        assertArrayEquals(Files.readAllBytes(tempDir.resolve("b.bin")), Files.readAllBytes(tempDir.resolve("restored/b.bin")));
    }

    @Test
    void reportsCorruptChunks() throws IOException {
        Path chunks = tempDir.resolve("chunks");
        run("split", randomFile("a.bin", 100).toString(), "-n", "2", "-o", chunks.toString());
        Files.write(chunks.resolve("a.bin.002"), new byte[50]);

        assertEquals(EXIT_FAILURE, run("verify", chunks.toString()));
        assertTrue(err().startsWith("FAILED " + chunks.resolve("a.bin.splitfire") + ": chunk 2 of 2"), err());

        assertEquals(EXIT_FAILURE, run("assemble", chunks.toString(), "-o", tempDir.resolve("restored").toString()));
        assertTrue(err().contains("is corrupt"), err());
        assertTrue(Files.notExists(tempDir.resolve("restored/a.bin")));
    }

    @Test
    void refusesToOverwriteWithoutForce() throws IOException {
        Path file = randomFile("a.bin", 100);
        Path chunks = tempDir.resolve("chunks");
        assertEquals(EXIT_OK, run("split", file.toString(), "-o", chunks.toString()));

        assertEquals(EXIT_FAILURE, run("split", file.toString(), "-o", chunks.toString()));
        assertTrue(err().contains("file already exists") && err().contains("use --force to overwrite"), err());

        assertEquals(EXIT_OK, run("split", file.toString(), "-o", chunks.toString(), "--force"), err());
    }

    @Test
    void reportsMissingFilesClearly() {
        Path missing = tempDir.resolve("missing.bin");
        assertEquals(EXIT_FAILURE, run("split", missing.toString()));
        assertEquals("splitfire: no such file: " + missing + System.lineSeparator(), err());
    }

    @Test
    void reportsDirectoriesWithoutManifests() {
        assertEquals(EXIT_FAILURE, run("verify", tempDir.toString()));
        assertTrue(err().contains("no manifests (*.splitfire) found in"), err());
    }

    @Test
    void reportsImpossibleChunkCountsAsUsageErrors() throws IOException {
        assertEquals(EXIT_USAGE, run("split", randomFile("a.bin", 10).toString(), "--chunks", "0"));
        assertTrue(err().contains("chunk count must be between 1 and"), err());
    }

    @Test
    void reportsChunkCountsLargerThanTheFile() throws IOException {
        assertEquals(EXIT_FAILURE, run("split", randomFile("a.bin", 10).toString(), "--chunks", "11"));
        assertTrue(err().contains("cannot split a 10-byte file into 11 chunks"), err());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--help", "-h", "help --help", "split --help"})
    void printsHelp(String args) {
        assertEquals(EXIT_OK, run(args.split(" ")));
        assertEquals(Main.USAGE, out());
    }

    @Test
    void printsVersion() {
        assertEquals(EXIT_OK, run("--version"));
        assertTrue(out().startsWith("splitfire "), out());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''                                  | missing command",
            "frobnicate                          | unknown command \"frobnicate\"",
            "split --bogus x                     | unknown option --bogus",
            "split                               | split expects one file, got 0 arguments",
            "split a b                           | split expects one file, got 2 arguments",
            "split a -n 2 -s 1M                  | use either --chunks or --size, not both",
            "split a -n two                      | invalid chunk count \"two\"",
            "split a --size 10Q                  | invalid size \"10Q\"",
            "split a --output                    | --output requires a value",
            "split a --force=yes                 | --force does not take a value",
            "assemble a -n 2                     | --chunks cannot be used with assemble",
            "verify a -o out                     | --output cannot be used with verify",
            "verify a -f                         | --force cannot be used with verify",
    })
    void rejectsInvalidCommandLines(String args, String message) {
        assertEquals(EXIT_USAGE, run(args.isEmpty() ? new String[0] : args.split(" +")));
        assertTrue(err().startsWith("splitfire: " + message), err());
        assertTrue(err().contains("Run 'splitfire --help' for usage."), err());
    }

    @Test
    void doubleDashEndsOptions() throws IOException {
        Path file = randomFile("-dash.bin", 10);
        assertEquals(EXIT_OK, run("split", "-o", tempDir.resolve("chunks").toString(), "--", file.toString()), err());
    }

    @ParameterizedTest
    @CsvSource({
            "4096, 4096",
            "1b, 1",
            "512K, 524288",
            "512k, 524288",
            "10M, 10485760",
            "10MB, 10485760",
            "10MiB, 10485760",
            "'1 G', 1073741824",
            "2T, 2199023255552",
    })
    void parsesSizes(String text, long expected) throws Main.UsageException {
        assertEquals(expected, Main.parseSize(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "0M", "-1", "1.5M", "M", "10X", "10 M B", "9999999999999T", "99999999999999999999"})
    void rejectsInvalidSizes(String text) {
        assertThrows(Main.UsageException.class, () -> Main.parseSize(text));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0 bytes",
            "1, 1 byte",
            "1023, 1023 bytes",
            "1024, 1.0 KiB",
            "1536, 1.5 KiB",
            "1048576, 1.0 MiB",
            "5000000, 4.8 MiB",
            "9223372036854775807, 8.0 EiB",
    })
    void formatsByteCounts(long bytes, String expected) {
        assertEquals(expected, Main.formatBytes(bytes));
    }

    private Path randomFile(String name, int size) throws IOException {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return Files.write(tempDir.resolve(name), data);
    }
}
