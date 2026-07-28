/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

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

    /**
     * Returns the base64-encoded image data.
     *
     * @return the image data as a base64 string
     */
    @Value.Redacted
    String data();

    /**
     * Returns the MIME type of the image content.
     *
     * @return the image MIME type
     */
    String mimeType();

    /**
     * Returns the annotations for this content, or {@code null} if none.
     *
     * @return the annotations, or {@code null}
     */
    @Nullable
    Annotations annotations();

    /**
     * Returns the request-level metadata for this content, or {@code null} if none.
     *
     * @return the metadata entries, or {@code null}
     */
    @Nullable
    Map<String, Object> meta();

    @Override
    default Type type() {
        return Type.IMAGE;
    }

    /**
     * Validates that required fields are not blank.
     *
     * @throws IllegalArgumentException if {@code data} or {@code mimeType} is blank
     */
    @Value.Check
    default void check() {
        if (data().isBlank()) throw new IllegalArgumentException("data must not be blank");
        if (mimeType().isBlank()) throw new IllegalArgumentException("mimeType must not be blank");
    }

    /**
     * Creates a new builder for constructing {@code ImageContent} instances.
     *
     * @return a new builder
     */
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

    /**
     * Builder for {@link ImageContent}.
     */
    interface Builder {
        /**
         * Sets the base64-encoded image data.
         *
         * @param data the image data
         * @return this builder
         */
        Builder data(String data);

        /**
         * Sets the MIME type of the image content.
         *
         * @param mimeType the image MIME type
         * @return this builder
         */
        Builder mimeType(String mimeType);

        /**
         * Sets the annotations.
         *
         * @param annotations the annotations, or {@code null}
         * @return this builder
         */
        Builder annotations(@Nullable Annotations annotations);

        /**
         * Sets the metadata entries.
         *
         * @param entries the metadata map, or {@code null}
         * @return this builder
         */
        Builder meta(@Nullable Map<String, ?> entries);

        /**
         * Builds the {@code ImageContent} instance.
         *
         * @return a new image content
         */
        ImageContent build();
    }
}
