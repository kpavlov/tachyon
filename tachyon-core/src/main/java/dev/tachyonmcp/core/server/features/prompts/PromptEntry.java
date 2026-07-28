/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.prompts;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.features.prompts.AsyncPromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;

@InternalApi
record PromptEntry(PromptDescriptor descriptor, AsyncPromptFn fn) implements ServerFeature<PromptDescriptor> {

    static PromptEntry of(PromptDescriptor descriptor, AsyncPromptFn fn) {
        return new PromptEntry(descriptor, fn);
    }
}
