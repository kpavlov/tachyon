/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base {@link ToolHandler} implementation
 *
 * @author Konstantin Pavlov
 */
public abstract class AbstractToolHandler implements ToolHandler {

    private final ToolDescriptor descriptor;

    public AbstractToolHandler(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        this.descriptor = descriptor;
    }

    public AbstractToolHandler(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        this(configure(descriptorConfigurer));
    }

    private static ToolDescriptor configure(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        final var builder = ToolDescriptor.builder();
        descriptorConfigurer.accept(builder);
        return builder.build();
    }

    public AbstractToolHandler(String name) {
        this(ToolDescriptor.builder().name(name).build());
    }

    public ToolDescriptor descriptor() {
        return descriptor;
    }
}
