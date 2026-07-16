/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;

@InternalApi
public record PromptEntry(PromptDescriptor descriptor, PromptHandler handler)
        implements ServerFeature<PromptDescriptor> {

    static PromptEntry of(PromptDescriptor descriptor, PromptHandler handler) {
        return new PromptEntry(descriptor, handler);
    }
}
