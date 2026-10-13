package com.pikle6.splitfire;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown when a manifest file is malformed, uses an unsupported format version, or describes inconsistent chunks.
 */
public class InvalidManifestException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidManifestException(String message) {
        super(message);
    }

    public InvalidManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
