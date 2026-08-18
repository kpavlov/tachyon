/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.extensions;

/**
 * Controls whether a {@link ServerExtension} is listed in the {@code initialize} response's
 * {@code capabilities.extensions}.
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
    NEVER
}
