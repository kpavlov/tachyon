/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.interceptor;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.RequestId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * View of the single MCP operation an {@link McpInterceptor} is wrapped around — the request or
 * notification as it arrived on the wire.
 *
 * <p><strong>One instance per dispatched operation, valid only for the duration of the
 * interception.</strong> Everything but {@link #sessionId()} and {@link #protocolVersion()} is
 * fixed by the inbound message; those two read through the live dispatch. Do not retain the
 * invocation past the {@link McpInterceptor#intercept} call; copy out what you need.
 *
 * <p>Per-operation is the <em>instance</em>, not the attribute space behind {@link #context()},
 * which every request on the connection shares — see that method.
 *
 * <p><strong>Not for implementation outside Tachyon.</strong> Methods may be added in any release,
 * so implementing it in application code will break on upgrade. Only {@link McpInterceptor} is
 * meant to be implemented.
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
public interface McpInvocation {

    /**
     * Returns the JSON-RPC method being dispatched, e.g. {@code tools/call}, {@code initialize} or
     * {@code notifications/cancelled}.
     *
     * @return the method name
     */
    String method();

    /**
     * Returns the JSON-RPC request id, or {@code null} when this operation is a notification.
     *
     * @return the request id, or {@code null} for notifications
     */
    @Nullable
    RequestId requestId();

    /**
     * Returns the MCP session id, or {@code null} in stateless mode.
     *
     * <p>Non-null for every operation on a stateful session, {@code initialize} included — the
     * dispatcher establishes the session before running the chain, so an interceptor wrapping
     * {@code initialize} already sees the id the response will carry.
     *
     * @return the session id, or {@code null} in stateless mode
     */
    @Nullable
    String sessionId();

    /**
     * Returns the negotiated MCP protocol version for this channel.
     *
     * @return the protocol version, e.g. {@code 2025-11-25}
     */
    String protocolVersion();

    /**
     * Returns the value of the top-level {@code name} parameter — the tool name for {@code
     * tools/call} and the prompt name for {@code prompts/get} — or empty when this operation names
     * no target.
     *
     * <p>Read straight off the wire params, so it costs no decode and no parse of {@link #params()}.
     * This is what an authorization interceptor gates on and a tracing interceptor names spans by.
     *
     * @return the targeted tool or prompt name, or empty
     */
    Optional<String> targetName();

    /**
     * Returns the value of the top-level {@code uri} parameter — the resource URI for {@code
     * resources/read}, {@code resources/subscribe} and {@code resources/unsubscribe} — or empty
     * when this operation names no resource.
     *
     * @return the targeted resource URI, or empty
     */
    Optional<String> resourceUri();

    /**
     * Returns the raw JSON-RPC {@code params} of this operation, or empty when it carried none.
     *
     * <p>Encoded lazily on first call and cached: interceptors that never inspect the payload pay
     * nothing. The document describes the wire params exactly as received — the decoded,
     * protocol-neutral request is not exposed here.
     *
     * <p><strong>Handle as untrusted, potentially confidential input.</strong> Tool arguments
     * routinely carry credentials and personal data; log them at {@code TRACE} only, or not at all.
     *
     * @return the raw params document, or empty when the operation had no params
     */
    Optional<JsonDocument> params();

    /**
     * Returns the handler-facing interaction context backing this operation — extension checks,
     * outbound notifications, and the attribute space.
     *
     * <p>🔴 That attribute space ({@link InteractionContext#get}/{@link InteractionContext#set}) is
     * scoped to the <em>connection</em>, not to this operation: concurrent requests share it and a
     * value survives into later ones. Not a place for per-request state such as a timer or a span —
     * {@link McpInterceptor#intercept} is ordinary blocking code, so keep that in locals.
     *
     * @return the interaction context
     */
    InteractionContext context();
}
