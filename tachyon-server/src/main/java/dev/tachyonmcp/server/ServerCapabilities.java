/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

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

    public record Prompts(boolean listChanged) {}

    public record Tools(boolean listChanged) {}

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
