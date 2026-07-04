/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import io.netty.handler.codec.http.HttpRequest;
import java.util.UUID;

/**
 * Derives the session id for a newly initialized session from the incoming {@code initialize}
 * request. Lets operators base the id on request headers (tenant id, auth subject, a
 * client-supplied id, …) or the request URI.
 *
 * <p>Only consulted when sessions are enabled ({@code session(s -> s.enabled(true))});
 * stateless servers never create a session and never call this.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface SessionIdGenerator {

    /**
     * Default generator: {@code sess_<UUID>}, ignoring the request.
     */
    SessionIdGenerator DEFAULT =
            request -> "sess_" + UUID.randomUUID().toString().replace("-", "");

    /**
     * Derives a session id from the initialize request (headers, URI, …).
     *
     * <p>Contract for the <em>initialize</em> flow:
     * <ul>
     *   <li>A {@code null} return value or any thrown exception causes the server to respond
     *       with an {@code internal-error} and abort session creation.
     *   <li>A blank string ({@code ""} or whitespace-only) is accepted as-is and used directly
     *       as the session id — the generator must produce a non-blank value if a valid id is
     *       required.
     * </ul>
     */
    String generate(HttpRequest request);
}
