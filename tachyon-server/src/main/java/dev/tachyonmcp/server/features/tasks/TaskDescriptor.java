/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true, typeImmutable = "Default*")
@Value.Builder
public interface TaskDescriptor extends McpResourceType {

    String name();

    @Nullable
    String description();

    @Nullable
    String title();

    @Nullable
    List<Icon> icons();

    static DefaultTaskDescriptor.Builder builder() {
        return DefaultTaskDescriptor.builder();
    }

    static DefaultTaskDescriptor.Builder builder(String name) {
        return builder().name(name);
    }

    static TaskDescriptor of(String name, @Nullable String description) {
        return DefaultTaskDescriptor.of(name, description, null, null);
    }
}
