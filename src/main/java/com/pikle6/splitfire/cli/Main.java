package com.pikle6.splitfire.cli;

import com.pikle6.splitfire.Chunking;
import com.pikle6.splitfire.Manifest;
import com.pikle6.splitfire.SplitFire;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Command-line entry point: {@code splitfire split|assemble|verify ...}. */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_USAGE = 2;

    static final String USAGE = """
            SplitFire - split files into checksummed chunks and reassemble them.

            Usage:
              splitfire split <file> [--chunks N | --size SIZE] [--output DIR] [--force]
              splitfire assemble <manifest | directory> [--output DIR] [--force]
              splitfire verify <manifest | directory>

            Commands:
              split      Cut <file> into chunks and write them, plus a <file>.splitfire
                         manifest, to the output directory.
              assemble   Rebuild the original file from a manifest and the chunks next to
                         it. Given a directory, rebuilds every manifest in it.
              verify     Check that all chunks of a manifest are present and intact.

            Options:
              -n, --chunks N     split into N chunks of (nearly) equal size
              -s, --size SIZE    maximum chunk size, e.g. 4096, 512K, 10M, 2G (default: 1M)
              -o, --output DIR   directory to write to (default: current directory)
              -f, --force        overwrite existing files
              -h, --help         show this help
              -V, --version      show the version
            """;

    private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*(?:([kmgt])i?)?b?", Pattern.CASE_INSENSITIVE);

    private final PrintStream out;
    private final PrintStream err;

    Main(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        System.exit(new Main(System.out, System.err).run(args));
    }

    int run(String... args) {
        Invocation invocation;
        try {
            invocation = Invocation.parse(args);
        } catch (UsageException e) {
            err.println("splitfire: " + e.getMessage());
            err.println("Run 'splitfire --help' for usage.");
            return EXIT_USAGE;
        }

        try {
            return switch (invocation.command()) {
                case "help" -> {
                    out.print(USAGE);
                    yield EXIT_OK;
                }
                case "version" -> {
                    out.println("splitfire " + version());
                    yield EXIT_OK;
                }
                case "split" -> split(invocation);
                case "assemble" -> assemble(invocation);
                case "verify" -> verify(invocation);
                default -> throw new IllegalStateException("unhandled command " + invocation.command());
            };
        } catch (IOException e) {
            err.println("splitfire: " + describe(e));
            return EXIT_FAILURE;
        } catch (UncheckedIOException e) {
            err.println("splitfire: " + describe(e.getCause()));
            return EXIT_FAILURE;
        } catch (IllegalArgumentException e) {
            err.println("splitfire: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    private int split(Invocation invocation) throws IOException {
        Manifest manifest = SplitFire.split(invocation.operand(), invocation.output(), invocation.chunking(), invocation.force());
        out.printf("Split %s (%s) into %d %s in %s%n",
                manifest.fileName(), formatBytes(manifest.fileSize()), manifest.chunks().size(),
                manifest.chunks().size() == 1 ? "chunk" : "chunks", invocation.output());
        out.println("Manifest: " + invocation.output().resolve(Manifest.fileNameFor(manifest.fileName())));
        return EXIT_OK;
    }

    private int assemble(Invocation invocation) throws IOException {
        int failures = 0;
        for (Path manifestFile : manifests(invocation.operand())) {
            try {
                Path assembled = SplitFire.assemble(manifestFile, invocation.output(), invocation.force());
                out.printf("Assembled %s (%s, checksums verified)%n", assembled, formatBytes(Files.size(assembled)));
            } catch (IOException | IllegalArgumentException e) {
                err.println("splitfire: failed to assemble " + manifestFile + ": " + describe(e));
                failures++;
            }
        }
        return failures == 0 ? EXIT_OK : EXIT_FAILURE;
    }

    private int verify(Invocation invocation) throws IOException {
        int failures = 0;
        for (Path manifestFile : manifests(invocation.operand())) {
            try {
                Manifest manifest = SplitFire.verify(manifestFile);
                out.printf("OK %s (%s, %d %s)%n", manifest.fileName(), formatBytes(manifest.fileSize()),
                        manifest.chunks().size(), manifest.chunks().size() == 1 ? "chunk" : "chunks");
            } catch (IOException | IllegalArgumentException e) {
                err.println("FAILED " + manifestFile + ": " + describe(e));
                failures++;
            }
        }
        return failures == 0 ? EXIT_OK : EXIT_FAILURE;
    }

    /** A manifest file is used as-is; a directory expands to the manifests inside it. */
    private static List<Path> manifests(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return List.of(path);
        }
        List<Path> manifests = SplitFire.findManifests(path);
        if (manifests.isEmpty()) {
            throw new IOException("no manifests (*" + Manifest.EXTENSION + ") found in " + path.toAbsolutePath());
        }
        return manifests;
    }

    static String describe(Exception e) {
        return switch (e) {
            case NoSuchFileException x when x.getReason() == null -> "no such file: " + x.getFile();
            case FileAlreadyExistsException x -> "file already exists: " + x.getFile() + " (use --force to overwrite)";
            case AccessDeniedException x -> "permission denied: " + x.getFile();
            case NotDirectoryException x -> "not a directory: " + x.getFile();
            default -> e.getMessage() != null ? e.getMessage() : e.toString();
        };
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /** Parses sizes such as {@code 4096}, {@code 512K}, {@code 10MB} or {@code 2GiB}; units are powers of 1024. */
    static long parseSize(String text) throws UsageException {
        Matcher matcher = SIZE.matcher(text.strip());
        if (!matcher.matches()) {
            throw new UsageException("invalid size \"" + text + "\" (examples: 4096, 512K, 10M, 2G)");
        }
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
        int shift = switch (unit) {
            case "" -> 0;
            case "k" -> 10;
            case "m" -> 20;
            case "g" -> 30;
            default -> 40;
        };
        try {
            long value = Long.parseLong(matcher.group(1));
            if (value == 0) {
                throw new UsageException("size must be greater than zero");
            }
            if (value > Long.MAX_VALUE >> shift) {
                throw new NumberFormatException();
            }
            return value << shift;
        } catch (NumberFormatException e) {
            throw new UsageException("size \"" + text + "\" is too large");
        }
    }

    private static String version() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version != null ? version : "(development build)";
    }

    /** A parsed command line; {@code command} is one of help, version, split, assemble or verify. */
    record Invocation(String command, Path operand, Chunking chunking, Path output, boolean force) {

        private static final Set<String> FLAGS = Set.of("--help", "--version", "--force");

        static Invocation parse(String... args) throws UsageException {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();
            boolean optionsEnded = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (optionsEnded || arg.equals("-") || !arg.startsWith("-")) {
                    positional.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsEnded = true;
                    continue;
                }
                String name = arg;
                String value = null;
                int equals = arg.indexOf('=');
                if (arg.startsWith("--") && equals > 0) {
                    name = arg.substring(0, equals);
                    value = arg.substring(equals + 1);
                }
                String option = switch (name) {
                    case "-h", "--help" -> "--help";
                    case "-V", "--version" -> "--version";
                    case "-f", "--force" -> "--force";
                    case "-n", "--chunks" -> "--chunks";
                    case "-s", "--size" -> "--size";
                    case "-o", "--output" -> "--output";
                    default -> throw new UsageException("unknown option " + arg);
                };
                if (FLAGS.contains(option)) {
                    if (value != null) {
                        throw new UsageException(option + " does not take a value");
                    }
                } else if (value == null) {
                    if (++i == args.length) {
                        throw new UsageException(option + " requires a value");
                    }
                    value = args[i];
                }
                options.put(option, value);
            }

            if (options.containsKey("--help")) {
                return new Invocation("help", null, null, null, false);
            }
            if (options.containsKey("--version")) {
                return new Invocation("version", null, null, null, false);
            }
            if (positional.isEmpty()) {
                throw new UsageException("missing command");
            }

            String command = positional.getFirst();
            Set<String> allowed = switch (command) {
                case "split" -> Set.of("--chunks", "--size", "--output", "--force");
                case "assemble" -> Set.of("--output", "--force");
                case "verify" -> Set.of();
                default -> throw new UsageException("unknown command \"" + command + "\"");
            };
            for (String option : options.keySet()) {
                if (!allowed.contains(option)) {
                    throw new UsageException(option + " cannot be used with " + command);
                }
            }
            if (options.containsKey("--chunks") && options.containsKey("--size")) {
                throw new UsageException("use either --chunks or --size, not both");
            }
            List<String> operands = positional.subList(1, positional.size());
            if (operands.size() != 1) {
                String expected = command.equals("split") ? "one file" : "one manifest or directory";
                throw new UsageException(command + " expects " + expected + ", got " + operands.size() + " arguments");
            }

            return new Invocation(
                    command,
                    path(operands.getFirst()),
                    chunking(options.get("--chunks"), options.get("--size")),
                    path(options.getOrDefault("--output", "")).toAbsolutePath(),
                    options.containsKey("--force"));
        }

        private static Chunking chunking(String chunks, String size) throws UsageException {
            try {
                if (chunks != null) {
                    return Chunking.ofCount(Integer.parseInt(chunks.strip()));
                }
                return size != null ? Chunking.ofSize(parseSize(size)) : Chunking.defaults();
            } catch (NumberFormatException e) {
                throw new UsageException("invalid chunk count \"" + chunks + "\"");
            } catch (IllegalArgumentException e) {
                throw new UsageException(e.getMessage());
            }
        }

        private static Path path(String text) throws UsageException {
            try {
                return Path.of(text);
            } catch (InvalidPathException e) {
                throw new UsageException("invalid path \"" + text + "\": " + e.getReason());
            }
        }
    }

    /** A problem with the command line itself. */
    static final class UsageException extends Exception {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
