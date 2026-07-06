/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import java.util.Objects;

public abstract class AbstractToolHandler implements ToolHandler {

    private final ToolDescriptor descriptor;

    public AbstractToolHandler(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        this.descriptor = descriptor;
    }

    public AbstractToolHandler(String name) {
        this(ToolDescriptor.builder().name(name).build());
    }

    public ToolDescriptor descriptor() {
        return descriptor;
    }

    public String name() {
        return descriptor.name();
    }
}
