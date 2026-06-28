/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import java.util.List;

/**
 * Demonstrates prompt descriptor and handler patterns.
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
}
