/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.session.McpSession;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/** Server-level operations available within a handler dispatch context. */
public interface ServerContext {

    /** Returns the current session ID. */
    String sessionId();

    /** Sets the logging level for the current session. */
    void setLoggingLevel(LoggingLevel level);

    /** Returns the logging level for the current session. */
    @Nullable
    LoggingLevel getLoggingLevel();

    /** Sends a request to the client and returns a future that completes with the response. */
    CompletableFuture<String> sendRequest(String method, Object params);

    /** Returns the current session, or {@code null} in stateless mode. */
    @Nullable
    McpSession session();

    /** Returns the owning {@link McpServer}. */
    McpServer mcpServer();
}
