/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.domain.Args;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Request parameters for a prompt invocation.
 *
 * @param arguments the prompt arguments, or empty if none were provided
 * @param inputResponses client's input responses for input-required prompts, or null
 * @param requestState opaque state token for input-required prompts, or null
 * @param meta protocol extension metadata, or null
 */
public record PromptRequest(
        Args arguments,
        @Nullable Map<String, Object> inputResponses,
        @Nullable String requestState,
        @Nullable Map<String, Object> meta)
        implements ServerFeature.Request {

    public PromptRequest {
        if (arguments == null) arguments = Args.empty();
    }

    public PromptRequest(Args arguments, @Nullable Map<String, Object> inputResponses, @Nullable String requestState) {
        this(arguments, inputResponses, requestState, null);
    }
}
