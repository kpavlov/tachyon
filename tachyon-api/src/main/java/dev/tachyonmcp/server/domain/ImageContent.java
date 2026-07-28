/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * An image provided to or from an LLM.
 *
 * <p>The image data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the format (e.g. {@code image/png}, {@code image/jpeg}).
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface ImageContent extends ContentBlock {

    @Value.Redacted
    String data();

    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, Object> meta();

    @Override
    default Type type() {
        return Type.IMAGE;
    }

    @Value.Check
    default void check() {
        if (data().isBlank()) throw new IllegalArgumentException("data must not be blank");
        if (mimeType().isBlank()) throw new IllegalArgumentException("mimeType must not be blank");
    }

    static Builder builder() {
        return DefaultImageContent.builder();
    }

    /**
     * Creates an image content block from base64-encoded data, with no metadata or annotations.
     *
     * @param data     the base64-encoded image data
     * @param mimeType the image MIME type (e.g. {@code image/png})
     */
    static ImageContent base64(String data, String mimeType) {
        return DefaultImageContent.of(data, mimeType, null, null);
    }

    /**
     * Creates an image content block from base64-encoded data.
     *
     * @param data the base64-encoded image data
     * @param mimeType the image MIME type
     * @return a new image content block
     * @deprecated use {@link #base64(String, String)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static ImageContent of(String data, String mimeType) {
        return base64(data, mimeType);
    }

    /** Creates an image content block from base64-encoded data with given annotations and no metadata. */
    static ImageContent base64(String data, String mimeType, @Nullable Annotations annotations) {
        return DefaultImageContent.of(data, mimeType, annotations, null);
    }

    /**
     * Creates an image content block from base64-encoded data with annotations.
     *
     * @param data the base64-encoded image data
     * @param mimeType the image MIME type
     * @param annotations the annotations, or {@code null}
     * @return a new image content block
     * @deprecated use {@link #base64(String, String, Annotations)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static ImageContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return base64(data, mimeType, annotations);
    }

    /** Creates an image content block from base64-encoded data with metadata and optional annotations. */
    static ImageContent base64(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return DefaultImageContent.of(data, mimeType, annotations, meta);
    }

    /**
     * Creates an image content block from base64-encoded data with annotations and metadata.
     *
     * @param data the base64-encoded image data
     * @param mimeType the image MIME type
     * @param annotations the annotations, or {@code null}
     * @param meta the metadata, or {@code null}
     * @return a new image content block
     * @deprecated use {@link #base64(String, String, Annotations, Map)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static ImageContent of(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return base64(data, mimeType, annotations, meta);
    }

    interface Builder {
        Builder data(String data);

        Builder mimeType(String mimeType);

        Builder annotations(@Nullable Annotations annotations);

        Builder meta(@Nullable Map<String, ?> entries);

        ImageContent build();
    }
}
