/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface InputRequiredPromptHandler {

    PromptHandlerResult handle(
            @Nullable String arguments, @Nullable Map<String, JsonNode> inputResponses, @Nullable String requestState)
            throws Exception;
}
