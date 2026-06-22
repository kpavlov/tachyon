/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletionStage;

public abstract class AbstractAsyncToolHandler extends AbstractToolHandler implements AsyncToolHandler<Object, Object> {

    public AbstractAsyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractAsyncToolHandler(String name) {
        super(name);
    }

    @Override
    public abstract CompletionStage<Object> handleAsync(McpContext context, Object arguments) throws Exception;
}
