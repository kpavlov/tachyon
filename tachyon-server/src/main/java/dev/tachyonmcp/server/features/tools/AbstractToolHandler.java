/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import java.util.Objects;

public abstract class AbstractToolHandler {

    private final ToolDescriptor descriptor;

    private static void validateName(String name) {
        Objects.requireNonNull(name, "Tool name must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("Tool name must not be blank");
        if (name.length() > 128) throw new IllegalArgumentException("Tool name must not exceed 128 characters");
    }

    public AbstractToolHandler(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        validateName(descriptor.name());
        this.descriptor = descriptor;
    }

    public AbstractToolHandler(String name) {
        this(ToolDescriptor.builder(name).build());
    }

    public ToolDescriptor descriptor() {
        return descriptor;
    }

    public String name() {
        return descriptor.name();
    }
}
