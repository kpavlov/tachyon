/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.features.prompts.AsyncPromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates prompt descriptor and handler patterns.
 *
 * <p>Two handler flavors: {@link PromptFn} (sync, returns a {@link PromptResult} — messages
 * or an input-required/MRTR round-trip) and {@link AsyncPromptFn} (non-blocking).
 */
final class PromptFnExample {

    /** Simplest — name + description, handler returns fixed messages. */
    static PromptFn simpleHandler() {
        return (ctx, request) -> PromptResult.messages(List.of(PromptMessage.user("Rewrite this in a pirate style.")));
    }

    static PromptDescriptor simpleDescriptor() {
        return PromptDescriptor.of("rewrite-forecast", "Rewrites a weather forecast in a given style");
    }

    /** With typed arguments. */
    static PromptDescriptor argDescriptor() {
        return PromptDescriptor.of(
                "rewrite",
                "Rewrites text in a style",
                "Rewrite Tool",
                List.of(
                        PromptArgument.of("text", null, "Original text", true),
                        PromptArgument.of("style", null, "Desired writing style", false)),
                null);
    }

    /** Handler that reads a typed argument. */
    static PromptFn argHandler() {
        return (ctx, request) -> {
            var text = request.arguments().stringOr("text", "default text");
            return PromptResult.messages(List.of(PromptMessage.user("Rewrite this: " + text)));
        };
    }

    /** Using builder for full descriptor. */
    static PromptDescriptor builtDescriptor() {
        return PromptDescriptor.builder()
                .name("format")
                .description("Formats input data")
                .title("Format Tool")
                .addArguments(PromptArgument.of("input", null, "Input data", true))
                .build();
    }

    /** Async handler — returns a CompletionStage for non-blocking backends. */
    static AsyncPromptFn asyncHandler() {
        return (ctx, request) -> CompletableFuture.supplyAsync(
                () -> PromptResult.messages(List.of(PromptMessage.user("Rewrite: " + request.arguments().json()))));
    }
}
