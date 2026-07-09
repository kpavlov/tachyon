/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import java.util.function.Consumer;

public abstract class AbstractAsyncToolHandler extends AbstractToolHandler implements AsyncToolHandler {

    public AbstractAsyncToolHandler(ToolDescriptor descriptor) {
        super(descriptor);
    }

    public AbstractAsyncToolHandler(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        super(descriptorConfigurer);
    }

    public AbstractAsyncToolHandler(String name) {
        super(name);
    }
}
