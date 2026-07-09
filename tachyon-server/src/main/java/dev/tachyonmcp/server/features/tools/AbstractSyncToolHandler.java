/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import java.util.function.Consumer;

public abstract class AbstractSyncToolHandler extends AbstractToolHandler implements SyncToolHandler {

    public AbstractSyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractSyncToolHandler(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        super(descriptorConfigurer);
    }

    public AbstractSyncToolHandler(String name) {
        super(name);
    }
}
