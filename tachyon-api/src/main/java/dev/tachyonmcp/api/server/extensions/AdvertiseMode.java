/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.extensions;

/**
 * Controls whether a {@link ServerExtension} is listed in {@code capabilities.extensions} —
 * advertised to clients via {@code initialize} (MCP 2025-11-25) or {@code server/discover} (MCP
 * 2026-07-28 and later).
 */
public enum AdvertiseMode {
    /** Always listed in {@code capabilities.extensions}. */
    ALWAYS,

    /**
     * Never listed in {@code capabilities.extensions}. The extension is still registered and
     * fully usable — a client that already knows its ID can still negotiate it and call its
     * methods — this only hides it from capability discovery, e.g. for internal-only extensions
     * clients aren't expected to know about.
     */
    NEVER,

    /**
     * Listed in {@code capabilities.extensions} only when the client also declares this
     * extension's ID in the same request — {@code capabilities.extensions} on an {@code
     * initialize} request (2025-11-25), or {@code _meta."io.modelcontextprotocol/clientCapabilities".extensions}
     * on any per-request declaration including {@code server/discover} (2026-07-28 and later).
     * Useful for extensions that should stay invisible to clients that don't already know to ask
     * for them.
     */
    NEGOTIATED
}
