/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * An audio content block provided to or from an LLM.
 *
 * <p>The audio data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the encoding (e.g. {@code audio/mp3}).
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        typeImmutable = "Default*",
        visibility = Value.Style.ImplementationVisibility.PACKAGE)
public non-sealed interface AudioContent extends ContentBlock, HasMeta {

    /**
     * Returns the base64-encoded audio data.
     *
     * @return the audio data as a base64 string
     */
    @Value.Redacted
    String data();

    /**
     * Returns the MIME type of the audio content.
     *
     * @return the audio MIME type
     */
    String mimeType();

    /**
     * Returns the annotations for this content, or {@code null} if none.
     *
     * @return the annotations, or {@code null}
     */
    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, Object> meta();

    @Override
    default Type type() {
        return Type.AUDIO;
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
     * Creates a new builder for constructing {@code AudioContent} instances.
     *
     * @return a new builder
     */
    static Builder builder() {
        return DefaultAudioContent.builder();
    }

    /**
     * Creates an audio content block with no metadata or annotations.
     *
     * @param data     the base64-encoded audio data
     * @param mimeType the audio MIME type
     * @return a new audio content
     */
    static AudioContent base64(String data, String mimeType) {
        return DefaultAudioContent.of(data, mimeType, null, null);
    }

    /**
     * Creates audio content from base64-encoded data.
     *
     * @param data the base64-encoded audio data
     * @param mimeType the audio MIME type
     * @return a new audio content
     * @deprecated use {@link #base64(String, String)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static AudioContent of(String data, String mimeType) {
        return base64(data, mimeType);
    }

    /**
     * Creates an audio content block with given annotations and no metadata.
     *
     * @param data        the base64-encoded audio data
     * @param mimeType    the audio MIME type
     * @param annotations the annotations, or {@code null}
     * @return a new audio content
     */
    static AudioContent base64(String data, String mimeType, @Nullable Annotations annotations) {
        return DefaultAudioContent.of(data, mimeType, annotations, null);
    }

    /**
     * Creates audio content from base64-encoded data with annotations.
     *
     * @param data the base64-encoded audio data
     * @param mimeType the audio MIME type
     * @param annotations the annotations, or {@code null}
     * @return a new audio content
     * @deprecated use {@link #base64(String, String, Annotations)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static AudioContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return base64(data, mimeType, annotations);
    }

    /**
     * Creates an audio content block with metadata and optional annotations.
     *
     * @param data        the base64-encoded audio data
     * @param mimeType    the audio MIME type
     * @param annotations the annotations, or {@code null}
     * @param meta        the metadata entries, or {@code null}
     * @return a new audio content
     */
    static AudioContent base64(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return DefaultAudioContent.of(data, mimeType, annotations, meta);
    }

    /**
     * Creates audio content from base64-encoded data with annotations and metadata.
     *
     * @param data the base64-encoded audio data
     * @param mimeType the audio MIME type
     * @param annotations the annotations, or {@code null}
     * @param meta the metadata entries, or {@code null}
     * @return a new audio content
     * @deprecated use {@link #base64(String, String, Annotations, Map)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static AudioContent of(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return base64(data, mimeType, annotations, meta);
    }

    /**
     * Builder for {@link AudioContent}.
     */
    interface Builder {
        /**
         * Sets the base64-encoded audio data.
         *
         * @param data the audio data
         * @return this builder
         */
        Builder data(String data);

        /**
         * Sets the MIME type of the audio content.
         *
         * @param mimeType the audio MIME type
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
         * Builds the {@code AudioContent} instance.
         *
         * @return a new audio content
         */
        AudioContent build();
    }
}
