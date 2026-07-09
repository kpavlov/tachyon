/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.ServerResourceType;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface TaskDescriptor extends ServerResourceType {

    String name();

    @Nullable
    String description();

    @Nullable
    String title();

    List<Icon> icons();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    static Builder builder() {
        return DefaultTaskDescriptor.builder();
    }

    static Builder builder(String name) {
        return builder().name(name);
    }

    interface Builder {
        Builder name(String name);

        Builder description(@Nullable String description);

        Builder title(@Nullable String title);

        Builder icons(Iterable<? extends Icon> icons);

        TaskDescriptor build();
    }
}
