/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;

@InternalApi
public record PromptEntry(PromptDescriptor descriptor, InputRequiredPromptHandler handler) implements ServerFeature {

    static PromptEntry of(PromptDescriptor descriptor, PromptHandler simple) {
        return new PromptEntry(
                descriptor, (ctx, request) -> PromptHandlerResult.messages(simple.getMessages(request.arguments())));
    }

    @Override
    public String name() {
        return descriptor.name();
    }
}
