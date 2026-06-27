/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.Session;
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
     * Returns {@code true} when this implementation can handle the given HTTP request.
     * POST requests are matched by endpoint AND {@code MCP-Protocol-Version} header compatibility;
     * other methods (GET for SSE, DELETE for session close, OPTIONS) are matched by endpoint only.
     */
    boolean matches(HttpRequest request);

    /** Response mapper for this protocol version. */
    ProtocolResponseMapper responseMapper();

    /**
     * Creates the per-channel {@link InteractionContext} for this protocol.
     *
     * <ul>
     *   <li>MCP: {@code DefaultMcpContext}
     *   <li>Future A2A: {@code DefaultInteractionContext<A2ASession>}
     * </ul>
     *
     * @param provider to obtain server-level dependencies (e.g. {@code McpServer})
     */
    InteractionContext<Session> createInteractionContext(ContextProvider provider);
}
