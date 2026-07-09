/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.prompts.AsyncPromptHandler;
import dev.tachyonmcp.server.features.prompts.InputRequiredPromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptHandlerResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates prompt descriptor and handler patterns.
 *
 * <p>Three handler flavors: {@link PromptHandler} (simple, messages only),
 * {@link InputRequiredPromptHandler} (sync, returns {@link PromptHandlerResult} — messages
 * or an input-required/MRTR round-trip), and {@link AsyncPromptHandler} (non-blocking).
 */
final class PromptHandlerExample {

    /** Simplest — name + description, handler returns fixed messages. */
    static PromptHandler simpleHandler() {
        return args -> List.of(PromptMessage.user("Rewrite this in a pirate style."));
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

    /** Handler that reads the argument. */
    static PromptHandler argHandler() {
        return args -> {
            var text = args != null ? args : "default text";
            return List.of(PromptMessage.user("Rewrite this: " + text));
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

    /** Sync handler with request context — returns a PromptHandlerResult. */
    static InputRequiredPromptHandler syncHandler() {
        return (ctx, request) ->
                PromptHandlerResult.messages(List.of(PromptMessage.user("Rewrite: " + request.arguments())));
    }

    /** Async handler — returns a CompletionStage for non-blocking backends. */
    static AsyncPromptHandler asyncHandler() {
        return (ctx, request) -> CompletableFuture.supplyAsync(
                () -> PromptHandlerResult.messages(List.of(PromptMessage.user("Rewrite: " + request.arguments()))));
    }
}
