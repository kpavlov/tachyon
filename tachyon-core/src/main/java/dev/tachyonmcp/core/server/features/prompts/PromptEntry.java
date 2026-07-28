/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.prompts;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptHandler;

@InternalApi
record PromptEntry(PromptDescriptor descriptor, PromptHandler handler) implements ServerFeature<PromptDescriptor> {

    static PromptEntry of(PromptDescriptor descriptor, PromptHandler handler) {
        return new PromptEntry(descriptor, handler);
    }
}
