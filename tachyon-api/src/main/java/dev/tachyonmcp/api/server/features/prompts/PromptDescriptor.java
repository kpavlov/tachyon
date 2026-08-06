/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.domain.HasMeta;
import dev.tachyonmcp.api.server.domain.Icon;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Descriptor for a server-provided prompt template.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface PromptDescriptor extends ServerFeature.Descriptor, HasMeta {

    /** The prompt name, unique within the server. */
    String name();

    /** Optional human-readable title. */
    @Nullable
    String title();

    /** Optional description of this prompt. */
    @Nullable
    String description();

    /** Optional arguments accepted by this prompt. */
    @Nullable
    List<PromptArgument> arguments();

    /** Optional JSON schema describing the prompt's arguments. */
    @Nullable
    JsonSchema inputSchema();

    /** Optional icons for this prompt. */
    @Nullable
    List<Icon> icons();

    /** Optional identifier of the extension that owns this prompt. */
    @Nullable
    String extensionId();

    /** Optional protocol extension metadata. */
    @Nullable
    @Override
    Map<String, Object> meta();

    /**
     * Validates the prompt descriptor's name.
     *
     * @throws IllegalArgumentException if the name is blank
     */
    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    /** Creates a new builder for {@link PromptDescriptor}. */
    static Builder builder() {
        return DefaultPromptDescriptor.builder();
    }

    /** Creates a prompt descriptor with the given fields. */
    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonSchema inputSchema) {
        return DefaultPromptDescriptor.of(name, title, description, arguments, inputSchema, null, null, null);
    }

    /** Creates a prompt descriptor with the given fields, including icons. */
    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonSchema inputSchema,
            @Nullable List<Icon> icons) {
        return DefaultPromptDescriptor.of(name, title, description, arguments, inputSchema, icons, null, null);
    }

    /** Creates a prompt descriptor with just a name and description. */
    static PromptDescriptor of(String name, String description) {
        return DefaultPromptDescriptor.of(name, null, description, null, null, null, null, null);
    }

    /** Builder for {@link PromptDescriptor}. */
    interface Builder {

        /** Fills this builder with the attribute values from {@code instance}. */
        Builder from(PromptDescriptor instance);

        /** Sets the prompt name, unique within the server. */
        Builder name(String name);

        /** Sets the optional human-readable title. */
        Builder title(@Nullable String title);

        /** Sets the optional description of this prompt. */
        Builder description(@Nullable String description);

        /** Appends arguments accepted by this prompt. */
        Builder addArguments(PromptArgument... elements);

        /** Sets the arguments accepted by this prompt. */
        Builder arguments(@Nullable Iterable<? extends PromptArgument> elements);

        /** Sets the optional JSON schema describing the prompt's arguments. */
        Builder inputSchema(@Nullable JsonSchema inputSchema);

        /** Sets the optional JSON schema describing the prompt's arguments, parsed from a string. */
        default Builder inputSchema(@Nullable String inputSchema) {
            return inputSchema(inputSchema != null ? JsonSchema.of(inputSchema) : null);
        }

        /** Sets the optional icons for this prompt. */
        Builder icons(@Nullable Iterable<? extends Icon> elements);

        /** Sets the optional identifier of the extension that owns this prompt. */
        Builder extensionId(@Nullable String extensionId);

        /**
         * Sets optional protocol extension metadata.
         *
         * @param entries metadata entries, or {@code null} for none
         */
        Builder meta(@Nullable Map<String, ?> entries);

        /** Builds the {@link PromptDescriptor}. */
        PromptDescriptor build();
    }
}
