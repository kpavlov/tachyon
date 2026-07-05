/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema;

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

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    static Builder builder() {
        return DefaultToolDescriptor.builder();
    }

    static Builder builder(String name) {
        return builder().name(name);
    }

    /**
     * @deprecated Use {@link #builder()}
     */
    @Deprecated
    static Builder builder(String name, @Nullable String inputSchemaJson, @Nullable String outputSchemaJson) {
        return builder(name)
                .inputSchema(parseSchema(inputSchemaJson, name))
                .outputSchema(parseSchema(outputSchemaJson, name));
    }

    interface Builder {
        Builder name(String name);

        Builder title(@Nullable String title);

        Builder description(@Nullable String description);

        Builder inputSchema(@Nullable JsonNode inputSchema);

        Builder outputSchema(@Nullable JsonNode outputSchema);

        Builder taskSupport(@Nullable TaskSupport taskSupport);

        Builder annotations(@Nullable ToolAnnotations annotations);

        //        Builder icons(@Nullable Iterable<? extends Icon> icons);

        Builder icons(@Nullable Iterable<? extends Icon> icons);

        Builder extensionId(@Nullable String extensionId);

        ToolDescriptor build();
    }
}
