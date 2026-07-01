/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

/** Thrown when a cancelled handler is detected. */
public class CancellationException extends RuntimeException {

    public CancellationException() {
        super("Handler cancelled");
    }

    public CancellationException(String message) {
        super(message);
    }
}
