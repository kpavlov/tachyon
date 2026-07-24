/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.UriTemplate;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Descriptor for a resource URI template.
 * <p>
 * A template describes a family of resources matching a URI pattern (e.g.
 * {@code file:///{path}}). Each concrete URI within the template is resolved
 * at request time via a {@link ResourceRequest}.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceTemplateDescriptor extends ServerFeature.Descriptor {

    @Override
    String name();

    /** The URI template pattern (e.g. {@code file:///{path}}). */
    String uriTemplate();

    /** Optional description of the resource family. */
    @Nullable
    String description();

    /** Optional MIME type that all matching resources share. */
    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    /** Optional identifier of the extension that owns this template. */
    @Nullable
    String extensionId();

    @Nullable
    List<Icon> icons();

    /**
     * Validates the resource template descriptor's name and URI template.
     *
     * @throws IllegalArgumentException if the name or URI template is blank or invalid
     */
    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uriTemplate().isBlank()) throw new IllegalArgumentException("uriTemplate must not be blank");
        UriTemplate.create(uriTemplate());
    }

    /**
     * Creates a builder for constructing resource template descriptors.
     *
     * @return a new resource template descriptor builder
     */
    static Builder builder() {
        return DefaultResourceTemplateDescriptor.builder();
    }

    /** Creates a template descriptor from a name and URI template pattern. */
    static ResourceTemplateDescriptor of(String name, String uriTemplate) {
        return ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(uriTemplate)
                .build();
    }

    /** Creates a fully specified resource template descriptor. */
    static ResourceTemplateDescriptor of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable List<Icon> icons) {
        return ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(uriTemplate)
                .description(description)
                .mimeType(mimeType)
                .title(title)
                .annotations(annotations)
                .icons(icons)
                .build();
    }

    /** Builder for {@link ResourceTemplateDescriptor}. */
    interface Builder {
        Builder name(String name);

        Builder uriTemplate(String uriTemplate);

        Builder description(@Nullable String description);

        Builder mimeType(@Nullable String mimeType);

        Builder title(@Nullable String title);

        Builder annotations(@Nullable Annotations annotations);

        Builder icons(@Nullable Iterable<? extends Icon> elements);

        ResourceTemplateDescriptor build();
    }
}
