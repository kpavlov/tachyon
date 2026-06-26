/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
@Value.Builder
public interface ToolDescriptor {

    String name();

    @Nullable
    String title();

    @Nullable
    String description();

    @Nullable
    JsonNode inputSchema();

    @Nullable
    JsonNode outputSchema();

    @Nullable
    TaskSupport taskSupport();

    @Nullable
    ToolAnnotations annotations();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    static DefaultToolDescriptor.Builder builder() {
        return DefaultToolDescriptor.builder();
    }

    static DefaultToolDescriptor.Builder builder(String name) {
        return builder().name(name);
    }
}
