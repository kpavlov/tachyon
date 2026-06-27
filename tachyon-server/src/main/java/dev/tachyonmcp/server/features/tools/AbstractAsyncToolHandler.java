/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

public abstract class AbstractAsyncToolHandler<R extends ToolResult> extends AbstractToolHandler<R>
        implements AsyncToolHandler<R> {

    public AbstractAsyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractAsyncToolHandler(String name) {
        super(name);
    }
}
