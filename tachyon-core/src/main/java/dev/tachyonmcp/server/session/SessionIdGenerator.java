/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
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
    SessionIdGenerator DEFAULT = new SessionIdGenerator() {
        @Override
        public String generate(HttpRequest request) {
            return "sess_" + UUID.randomUUID().toString().replace("-", "");
        }

        @Override
        public boolean readsRequest() {
            return false;
        }
    };

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

    /**
     * Whether {@link #generate} inspects the request (headers, URI, …). When {@code false}, the
     * transport skips detaching a per-request snapshot for the async session-creation dispatch.
     *
     * <p>Defaults to {@code true} so every custom generator is handed a valid request; override to
     * {@code false} for a request-independent id (like {@link #DEFAULT}) to opt into the fast path.
     */
    default boolean readsRequest() {
        return true;
    }
}
