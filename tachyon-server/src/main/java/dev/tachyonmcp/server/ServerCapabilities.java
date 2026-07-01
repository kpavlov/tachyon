/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Capabilities the server advertises to the client during initialization.
 *
 * @param prompts      prompt capabilities ({@code null} = not supported)
 * @param resources    resource capabilities ({@code null} = not supported)
 * @param tools        tool capabilities ({@code null} = not supported)
 * @param logging      whether logging is supported
 * @param completions  whether completion is supported
 * @param tasks        task capabilities ({@code null} = not supported)
 * @param experimental experimental capability extensions
 */
@Value.Builder
public record ServerCapabilities(
        @Nullable Prompts prompts,
        @Nullable Resources resources,
        @Nullable Tools tools,
        boolean logging,
        boolean completions,
        @Nullable Tasks tasks,
        @Nullable Map<String, JsonNode> experimental) {

    static ServerCapabilitiesBuilder builder() {
        return new ServerCapabilitiesBuilder();
    }

    public ServerCapabilities {
        experimental = experimental == null ? null : Map.copyOf(experimental);
    }

    /** Prompt capabilities. */
    public record Prompts(boolean listChanged) {}

    /** Tool capabilities. */
    public record Tools(boolean listChanged) {}

    /** Resource capabilities. */
    public record Resources(boolean subscribe, boolean listChanged) {}

    /**
     * Server task capabilities
     *
     * @param list             Server supports the `tasks/list` operation
     * @param cancel           Server supports the `tasks/cancel` operation
     * @param toolCallRequests Server supports task-augmented `tools/call` requests
     */
    public record Tasks(boolean list, boolean cancel, boolean toolCallRequests) {}
}
