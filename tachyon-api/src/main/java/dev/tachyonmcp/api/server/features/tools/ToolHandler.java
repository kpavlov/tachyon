/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.ServerFeature;
import java.util.concurrent.CompletionStage;

/**
 * Experimental class-based tool handler escape hatch.
 *
 * <p>Prefer {@link Tools#register(ToolDescriptor, ToolFn)} or {@link
 * Tools#registerAsync(ToolDescriptor, AsyncToolFn)}. Implement this interface only when a
 * class-based handler is required.
 */
@ExperimentalApi
public interface ToolHandler extends ServerFeature<ToolDescriptor> {

    /**
     * Returns the tool descriptor.
     *
     * @return the tool descriptor
     */
    @Override
    ToolDescriptor descriptor();

    /**
     * Handles a tool request asynchronously.
     *
     * @param context the interaction context
     * @param request the tool request
     * @return the pending tool result
     */
    CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request);
}
