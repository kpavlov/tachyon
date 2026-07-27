/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.ServerFeature;
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptHandler;

@InternalApi
record PromptEntry(PromptDescriptor descriptor, PromptHandler handler) implements ServerFeature<PromptDescriptor> {

    static PromptEntry of(PromptDescriptor descriptor, PromptHandler handler) {
        return new PromptEntry(descriptor, handler);
    }
}
