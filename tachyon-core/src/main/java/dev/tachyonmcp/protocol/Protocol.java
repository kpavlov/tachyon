/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.runtime.ChannelContext;
import dev.tachyonmcp.runtime.DefaultChannelContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * SPI for protocol versions, loadable via {@link java.util.ServiceLoader}.
 *
 * <p>Each implementation represents one negotiated server version (e.g. MCP 2025-11-25, MCP 2026-07-28)
 * and encapsulates the endpoint path, request matching predicate, response mapper,
 * and per-channel context factory.
 *
 * <p>To register an implementation, add its fully-qualified class name to
 * {@code META-INF/services/dev.tachyonmcp.protocol.Protocol}.
 */
public interface Protocol {

    /** HTTP endpoint path this protocol is served on, e.g. {@code "/mcp"}. */
    String endpoint();

    /** Protocol family name, e.g. {@code "mcp"}. */
    String familyName();

    /**
     * Server's negotiated version string, e.g. {@code "2025-11-25"}.
     * Used for version comparison: ISO-date format ensures lexicographic order is chronological.
     */
    String versionString();

    default int priority() {
        return 0;
    }

    /**
     * Whether this protocol version supports a server-side session established via
     * {@code initialize}. {@code true} for versions where sessions are part of the protocol (e.g.
     * MCP 2025-11-25) — a deployment can still choose to run those statelessly via
     * {@link dev.tachyonmcp.server.ServerBuilder} session config, that's an orthogonal server
     * choice, not a protocol trait. {@code false} for fully stateless, per-request protocol
     * versions (e.g. MCP 2026-07-28), which removed sessions from the protocol entirely — every
     * request self-describes via {@code _meta}, and no {@code initialize} handshake exists.
     */
    default boolean supportsSessions() {
        return true;
    }

    /**
     * Returns {@code true} when this implementation can handle the given HTTP request.
     * POST requests are matched by endpoint AND {@code MCP-Protocol-Version} header compatibility;
     * other methods (GET for SSE, DELETE for session close, OPTIONS) are matched by endpoint only.
     */
    boolean matches(HttpRequest request);

    /** Response mapper for this protocol version. */
    ProtocolResponseMapper responseMapper();

    default ChannelContext createInteractionContext() {
        return new DefaultChannelContext(this);
    }
}
