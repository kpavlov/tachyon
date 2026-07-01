/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

public abstract class AbstractSyncToolHandler extends AbstractToolHandler implements SyncToolHandler {

    public AbstractSyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractSyncToolHandler(String name) {
        super(name);
    }
}
