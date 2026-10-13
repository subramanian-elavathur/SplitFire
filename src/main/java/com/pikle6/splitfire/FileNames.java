package com.pikle6.splitfire;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Rules for the file names stored in manifests. */
final class FileNames {

    private FileNames() {
    }

    /**
     * Requires {@code name} to be a single path element that is valid on every platform: not empty, not {@code .} or
     * {@code ..}, and free of {@code /}, {@code \} and NUL.
     */
    static String requirePlainFileName(String name, String description) {
        Objects.requireNonNull(name, description);
        if (name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid " + description + ": \"" + name + "\"");
        }
        return name;
    }

    /**
     * Resolves a file name read from a manifest against {@code directory}, refusing anything that would land outside
     * it (for example a drive-relative name such as {@code C:evil} on Windows).
     */
    static Path resolveChild(Path directory, String name) throws InvalidManifestException {
        Path parent = directory.toAbsolutePath();
        try {
            Path child = parent.resolve(name);
            if (parent.equals(child.getParent()) && child.getFileName().toString().equals(name)) {
                return child;
            }
        } catch (InvalidPathException e) {
            throw new InvalidManifestException("file name \"" + name + "\" is not valid on this platform", e);
        }
        throw new InvalidManifestException("file name \"" + name + "\" points outside " + parent);
    }

    /** Name of the {@code index}-th (zero-based) chunk of {@code fileName}, e.g. {@code movie.mp4.001}. */
    static String chunkFileName(String fileName, int index, int chunkCount) {
        int width = Math.max(3, Integer.toString(chunkCount).length());
        return String.format(Locale.ROOT, "%s.%0" + width + "d", fileName, index + 1);
    }
}
