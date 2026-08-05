/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A server-to-client MCP notification (a JSON-RPC envelope with a {@code method} and no {@code id})
 * captured by a {@link McpClient}.
 *
 * @param method the notification method, e.g. {@code notifications/progress}
 * @param params the notification params, or {@code null} when the envelope has none
 */
public record Notification(String method, @Nullable JsonNode params) {

    /**
     * Runs {@code assertion} against this notification's params.
     *
     * @param assertion the assertion consumer
     * @return {@code this}
     */
    public Notification satisfies(Consumer<JsonNode> assertion) {
        assertion.accept(params);
        return this;
    }

    static Notification from(JsonNode envelope) {
        var params = envelope.path("params");
        return new Notification(envelope.path("method").asString(), params.isMissingNode() ? null : params);
    }
}
