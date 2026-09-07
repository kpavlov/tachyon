/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.RequestId;
import org.jspecify.annotations.Nullable;

/**
 * Identity facts for one inbound MCP operation (request, notification, or {@code initialize}),
 * independent of JSON-RPC id so repeated ids across sessions never collide.
 *
 * <p>Created once, as early as {@code method} (and {@code id}, if any) are known. {@link
 * #sessionId(String)} and {@link #traceparent(String)} are set at most once, as that information
 * becomes available, and are safe to read from a different thread once set: every write here
 * happens-before the {@link ObservationListener#complete} call that follows it, via the same
 * {@code CompletableFuture} chain the dispatcher already uses.
 */
@InternalApi
public final class OperationInfo {

    private final OperationKind kind;
    private final String method;
    private final @Nullable RequestId requestId;

    private @Nullable String sessionId;
    private @Nullable String traceParent;
    private @Nullable CapturedPayload requestPayload;
    private @Nullable CapturedPayload responsePayload;
    private @Nullable String target;

    public OperationInfo(OperationKind kind, String method, @Nullable RequestId requestId) {
        this.kind = kind;
        this.method = method;
        this.requestId = requestId;
    }

    public OperationKind kind() {
        return kind;
    }

    public String method() {
        return method;
    }

    public @Nullable RequestId requestId() {
        return requestId;
    }

    public @Nullable String sessionId() {
        return sessionId;
    }

    public void sessionId(@Nullable String sessionId) {
        this.sessionId = sessionId;
    }

    public @Nullable String traceparent() {
        return traceParent;
    }

    public void traceparent(@Nullable String traceparent) {
        this.traceParent = traceparent;
    }

    public @Nullable CapturedPayload requestPayload() {
        return requestPayload;
    }

    public void requestPayload(@Nullable CapturedPayload requestPayload) {
        this.requestPayload = requestPayload;
    }

    public @Nullable CapturedPayload responsePayload() {
        return responsePayload;
    }

    public void responsePayload(@Nullable CapturedPayload responsePayload) {
        this.responsePayload = responsePayload;
    }

    /**
     * The resolved target name for {@code tools/call}/{@code prompts/get} (a {@link
     * dev.tachyonmcp.api.server.features.tools.ToolDescriptor}/{@link
     * dev.tachyonmcp.api.server.features.prompts.PromptDescriptor} name), or {@code null} when
     * not applicable or not yet resolved. Always the resolved descriptor's own name — never the
     * raw wire parameter — so a client cannot pump arbitrary, unbounded values into it.
     */
    public @Nullable String target() {
        return target;
    }

    public void target(@Nullable String target) {
        this.target = target;
    }
}
