/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.McpResourceType;

record PromptEntry(PromptDescriptor descriptor, PromptHandler handler) implements McpResourceType {

    @Override
    public String name() {
        return descriptor.name();
    }
}
