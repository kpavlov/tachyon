/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Request parameters for a prompt invocation.
 *
 * @param arguments JSON-serialized argument string, or null
 * @param inputResponses client's input responses for input-required prompts, or null
 * @param requestState opaque state token for input-required prompts, or null
 */
public record PromptRequest(
        @Nullable String arguments,
        @Nullable Map<String, JsonNode> inputResponses,
        @Nullable String requestState) {}
