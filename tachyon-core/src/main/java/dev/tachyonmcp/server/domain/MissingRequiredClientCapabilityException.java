/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;

/**
 * Thrown by a handler when processing a request requires a client capability the client did not
 * declare in {@code _meta.io.modelcontextprotocol/clientCapabilities}. Mirrors
 * {@link InvalidArgumentException}: the feature registry that owns the request/response cycle
 * catches this and maps it to a {@code MissingRequiredClientCapabilityError} (-32021, MCP
 * 2026-07-28 SEP-2575), rather than the handler encoding the wire error itself.
 */
public final class MissingRequiredClientCapabilityException extends RuntimeException {

    private final Map<String, Object> requiredCapabilities;

    public MissingRequiredClientCapabilityException(String message, Map<String, Object> requiredCapabilities) {
        super(message);
        this.requiredCapabilities = requiredCapabilities;
    }

    /** The missing capabilities, keyed by name (e.g. {@code {"sampling": {}}}). */
    public Map<String, Object> requiredCapabilities() {
        return requiredCapabilities;
    }
}
