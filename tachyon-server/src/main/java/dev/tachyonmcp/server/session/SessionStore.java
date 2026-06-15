/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public interface SessionStore extends AutoCloseable {

    @Nullable
    McpSession put(String sessionId, McpSession session);

    Optional<McpSession> get(String sessionId);

    McpSession computeIfAbsent(String sessionId, Function<String, McpSession> factory);

    Collection<McpSession> values();

    @Nullable
    McpSession remove(String sessionId);
}
