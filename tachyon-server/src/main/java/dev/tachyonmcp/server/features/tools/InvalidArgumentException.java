/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

public final class InvalidArgumentException extends RuntimeException {

    private final String argName;

    public InvalidArgumentException(String argName, String message) {
        super(message);
        this.argName = argName;
    }

    public InvalidArgumentException(String argName, String message, Throwable cause) {
        super(message, cause);
        this.argName = argName;
    }

    public String argName() {
        return argName;
    }
}
