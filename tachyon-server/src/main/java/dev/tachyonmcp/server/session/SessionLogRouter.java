/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.io.Closeable;
import java.util.List;
import java.util.function.Predicate;

public interface SessionLogRouter extends Closeable {
    void append(SessionEvent event);

    List<SessionEvent> replay(String sessionId, long lastSeq);

    long pump(String sessionId, long cursor, Predicate<SessionEvent> processor);
}
