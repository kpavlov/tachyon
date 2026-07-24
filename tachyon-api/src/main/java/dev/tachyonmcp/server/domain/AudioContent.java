/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

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

    @Value.Redacted
    String data();

    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, Object> meta();

    @Override
    default Type type() {
        return Type.AUDIO;
    }

    @Value.Check
    default void check() {
        if (data().isBlank()) throw new IllegalArgumentException("data must not be blank");
        if (mimeType().isBlank()) throw new IllegalArgumentException("mimeType must not be blank");
    }

    static Builder builder() {
        return DefaultAudioContent.builder();
    }

    /** Creates an audio content block with no metadata or annotations. */
    static AudioContent of(String data, String mimeType) {
        return DefaultAudioContent.of(data, mimeType, null, null);
    }

    /** Creates an audio content block with given annotations and no metadata. */
    static AudioContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return DefaultAudioContent.of(data, mimeType, annotations, null);
    }

    /** Creates an audio content block with metadata and optional annotations. */
    static AudioContent of(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return DefaultAudioContent.of(data, mimeType, annotations, meta);
    }

    interface Builder {
        Builder data(String data);

        Builder mimeType(String mimeType);

        Builder annotations(@Nullable Annotations annotations);

        Builder meta(@Nullable Map<String, ?> entries);

        AudioContent build();
    }
}
