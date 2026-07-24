/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Descriptor for a static (non-template) resource.
 * <p>
 * A resource is a named, URI-addressable piece of content such as a file, database
 * record, or API response.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceDescriptor extends ServerFeature.Descriptor {

    /** The resource name, unique within the server. */
    String name();

    /** Optional human-readable title. */
    @Nullable
    String title();

    /** Optional description of this resource. */
    @Nullable
    String description();

    /** The URI that identifies this resource. */
    String uri();

    /** Optional MIME type of the resource content. */
    @Nullable
    String mimeType();

    /** Optional annotations for this resource. */
    @Nullable
    Annotations annotations();

    /** Optional size of the resource in bytes. */
    @Nullable
    Long size();

    /** Optional icons for this resource. */
    @Nullable
    List<Icon> icons();

    /** Optional identifier of the extension that owns this resource. */
    @Nullable
    String extensionId();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uri().isBlank()) throw new IllegalArgumentException("uri must not be blank");
        Long size = size();
        if (size != null && size < 0) throw new IllegalArgumentException("size must be >= 0, got: " + size);
    }

    /** Creates a new builder for {@link ResourceDescriptor}. */
    static ResourceDescriptor.Builder builder() {
        return DefaultResourceDescriptor.builder();
    }

    /** Creates a resource descriptor with the given fields. */
    static ResourceDescriptor of(String name, String uri, @Nullable String description, @Nullable String mimeType) {
        return DefaultResourceDescriptor.builder()
                .name(name)
                .uri(uri)
                .description(description)
                .mimeType(mimeType)
                .build();
    }

    /** Creates a fully specified resource descriptor. */
    static ResourceDescriptor of(
            String name,
            String uri,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable Long size,
            @Nullable List<Icon> icons) {
        return ResourceDescriptor.builder()
                .name(name)
                .uri(uri)
                .description(description)
                .mimeType(mimeType)
                .title(title)
                .annotations(annotations)
                .size(size)
                .icons(icons)
                .build();
    }

    /** Builder for {@link ResourceDescriptor}. */
    interface Builder {

        Builder name(String name);

        Builder title(@Nullable String title);

        Builder description(@Nullable String description);

        Builder uri(String uri);

        Builder mimeType(@Nullable String mimeType);

        Builder annotations(@Nullable Annotations annotations);

        Builder size(@Nullable Long size);

        default Builder size(int size) {
            return size((long) size);
        }

        Builder icons(@Nullable Iterable<? extends Icon> elements);

        Builder extensionId(@Nullable String extensionId);

        ResourceDescriptor build();
    }
}
