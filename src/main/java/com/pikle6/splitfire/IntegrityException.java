package com.pikle6.splitfire;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown when chunk data does not match its manifest: a chunk or the reassembled file has the wrong size or checksum.
 */
public class IntegrityException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IntegrityException(String message) {
        super(message);
    }
}
