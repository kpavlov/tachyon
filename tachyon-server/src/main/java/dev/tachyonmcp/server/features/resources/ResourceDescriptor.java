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

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceDescriptor extends ServerFeature {

    String name();

    @Nullable
    String title();

    @Nullable
    String description();

    String uri();

    @Nullable
    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Long size();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uri().isBlank()) throw new IllegalArgumentException("uri must not be blank");
        if (size() != null && size() < 0) throw new IllegalArgumentException("size must be >= 0, got: " + size());
    }

    static ResourceDescriptor.Builder builder() {
        return DefaultResourceDescriptor.builder();
    }

    static ResourceDescriptor of(String name, String uri, @Nullable String description, @Nullable String mimeType) {
        return DefaultResourceDescriptor.of(name, null, description, uri, mimeType, null, null, null, null);
    }

    static ResourceDescriptor of(
            String name,
            String uri,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable Long size,
            @Nullable List<Icon> icons) {
        return DefaultResourceDescriptor.of(name, title, description, uri, mimeType, annotations, size, icons, null);
    }

    interface Builder {

        Builder name(String name);

        Builder title(@Nullable String title);

        Builder description(@Nullable String description);

        Builder uri(String uri);

        Builder mimeType(@Nullable String mimeType);

        Builder annotations(@Nullable Annotations annotations);

        Builder size(@Nullable Long size);

        Builder icons(@Nullable Iterable<? extends Icon> elements);

        Builder extensionId(@Nullable String extensionId);

        ResourceDescriptor build();
    }
}
