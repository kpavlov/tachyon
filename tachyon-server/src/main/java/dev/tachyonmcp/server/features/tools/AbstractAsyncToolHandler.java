/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

public abstract class AbstractAsyncToolHandler extends AbstractToolHandler implements AsyncToolHandler {

    public AbstractAsyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractAsyncToolHandler(String name) {
        super(name);
    }
}
