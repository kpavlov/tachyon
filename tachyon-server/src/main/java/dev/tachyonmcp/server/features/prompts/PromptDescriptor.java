/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.ServerResourceType;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.json.JsonSchemaUtils;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface PromptDescriptor extends ServerResourceType {

    String name();

    @Nullable
    String description();

    @Nullable
    String title();

    @Nullable
    List<PromptArgument> arguments();

    @Nullable
    JsonNode inputSchema();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    static Builder builder() {
        return DefaultPromptDescriptor.builder();
    }

    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema) {
        return DefaultPromptDescriptor.of(name, description, title, arguments, inputSchema, null, null);
    }

    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema,
            @Nullable List<Icon> icons) {
        return DefaultPromptDescriptor.of(name, description, title, arguments, inputSchema, icons, null);
    }

    static PromptDescriptor of(String name, String description) {
        return DefaultPromptDescriptor.of(name, description, null, null, null, null, null);
    }

    interface Builder {
        Builder name(String name);

        Builder description(@Nullable String description);

        Builder title(@Nullable String title);

        Builder addArguments(PromptArgument... elements);

        Builder arguments(@Nullable Iterable<? extends PromptArgument> elements);

        Builder inputSchema(@Nullable JsonNode inputSchema);

        default Builder inputSchema(@Nullable String inputSchema) {
            return inputSchema(JsonSchemaUtils.parseSchema(inputSchema));
        }

        Builder icons(@Nullable Iterable<? extends Icon> elements);

        Builder extensionId(@Nullable String extensionId);

        PromptDescriptor build();
    }
}
