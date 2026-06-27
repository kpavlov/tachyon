/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.session.McpSession;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public interface ServerContext {

    String sessionId();

    void setLoggingLevel(LoggingLevel level);

    @Nullable
    LoggingLevel getLoggingLevel();

    CompletableFuture<String> sendRequest(String method, Object params);

    @Nullable
    McpSession session();

    McpServer mcpServer();
}
