/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.ServerFeature;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface TaskDescriptor extends ServerFeature.Descriptor {

    String id();

    @Value.Derived
    default String name() {
        return id();
    }

    @Value.Check
    default void check() {
        if (id().isBlank()) throw new IllegalArgumentException("id must not be blank");
    }

    static Builder builder() {
        return DefaultTaskDescriptor.builder();
    }

    interface Builder {
        Builder id(String id);

        TaskDescriptor build();
    }
}
