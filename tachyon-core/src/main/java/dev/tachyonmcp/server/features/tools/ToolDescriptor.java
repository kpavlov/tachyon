/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema;

import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.json.JsonSchema;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ToolDescriptor extends ServerFeature.Descriptor {

    String name();

    @Nullable
    String title();

    @Nullable
    String description();

    @Nullable
    JsonSchema inputSchema();

    @Nullable
    JsonSchema outputSchema();

    @Nullable
    TaskSupport taskSupport();

    @Nullable
    ToolAnnotations annotations();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    static Builder builder() {
        return DefaultToolDescriptor.builder();
    }

    static ToolDescriptor of(String name) {
        return DefaultToolDescriptor.of(name, null, null, null, null, null, null, null, null);
    }

    static ToolDescriptor of(String name, @Nullable String description) {
        return DefaultToolDescriptor.of(name, null, description, null, null, null, null, null, null);
    }

    interface Builder {
        Builder name(String name);

        Builder title(@Nullable String title);

        Builder description(@Nullable String description);

        Builder inputSchema(@Nullable JsonSchema inputSchema);

        Builder outputSchema(@Nullable JsonSchema outputSchema);

        default Builder inputSchema(@Nullable String inputSchema) {
            return inputSchema(parseSchema(inputSchema));
        }

        default Builder outputSchema(@Nullable String outputSchema) {
            return outputSchema(parseSchema(outputSchema));
        }

        Builder taskSupport(@Nullable TaskSupport taskSupport);

        Builder annotations(@Nullable ToolAnnotations annotations);

        Builder icons(@Nullable Iterable<? extends Icon> icons);

        Builder extensionId(@Nullable String extensionId);

        ToolDescriptor build();
    }
}
