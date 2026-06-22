/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;

public abstract class AbstractSyncToolHandler extends AbstractToolHandler implements SyncToolHandler<Object, Object> {

    public AbstractSyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractSyncToolHandler(String name) {
        super(name);
    }

    @Override
    public abstract Object handle(McpContext context, Object arguments) throws Exception;
}
