/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.McpResourceType;

record PromptEntry(PromptDescriptor descriptor, InputRequiredPromptHandler handler) implements McpResourceType {

    static PromptEntry of(PromptDescriptor descriptor, PromptHandler simple) {
        return new PromptEntry(
                descriptor,
                (args, inputResponses, requestState) -> PromptHandlerResult.messages(simple.getMessages(args)));
    }

    @Override
    public String name() {
        return descriptor.name();
    }
}
