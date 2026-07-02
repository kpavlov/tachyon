/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.ServerResourceType;
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
public interface ResourceDescriptor extends ServerResourceType {

    String name();

    String uri();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    @Nullable
    Double size();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uri().isBlank()) throw new IllegalArgumentException("uri must not be blank");
        if (size() != null && (Double.isNaN(size()) || size() < 0))
            throw new IllegalArgumentException("size must be >= 0, got: " + size());
    }

    static DefaultResourceDescriptor.Builder builder() {
        return DefaultResourceDescriptor.builder();
    }

    static ResourceDescriptor of(String name, String uri, @Nullable String description, @Nullable String mimeType) {
        return DefaultResourceDescriptor.of(name, uri, description, mimeType, null, null, null, null, null);
    }

    static ResourceDescriptor of(
            String name,
            String uri,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable Double size,
            @Nullable List<Icon> icons) {
        return DefaultResourceDescriptor.of(name, uri, description, mimeType, title, annotations, size, icons, null);
    }
}
