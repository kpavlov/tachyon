/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

public class CancellationException extends RuntimeException {

    public CancellationException() {
        super("Handler cancelled");
    }

    public CancellationException(String message) {
        super(message);
    }
}
