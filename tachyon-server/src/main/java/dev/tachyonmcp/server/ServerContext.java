/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.server.domain.LoggingLevel;
import org.jspecify.annotations.Nullable;

/**
 * Server-level operations reachable from the internal MCP dispatch surface
 * ({@link dev.tachyonmcp.server.session.DispatchContext}). Not part of the tool-author API.
 */
public interface ServerContext {

    /** Returns the current session ID, or {@code null} when no session is bound (stateless mode). */
    @Nullable
    String sessionId();

    /** Sets the logging level for the current session. */
    void setLoggingLevel(LoggingLevel level);

    /** Returns the logging level for the current session. */
    @Nullable
    LoggingLevel getLoggingLevel();

    /** Returns the current session, or {@code null} in stateless mode. */
    @Nullable
    Session session();

    /** Returns the owning {@link Server}. */
    Server mcpServer();
}
