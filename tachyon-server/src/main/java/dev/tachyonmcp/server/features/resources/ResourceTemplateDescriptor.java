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

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceTemplateDescriptor extends ServerFeature.Descriptor {

    @Override
    String name();

    String uriTemplate();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    @Nullable
    List<Icon> icons();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uriTemplate().isBlank()) throw new IllegalArgumentException("uriTemplate must not be blank");
        UriTemplate.create(uriTemplate());
    }

    static Builder builder() {
        return DefaultResourceTemplateDescriptor.builder();
    }

    static ResourceTemplateDescriptor of(String name, String uriTemplate) {
        return ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(uriTemplate)
                .build();
    }

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
