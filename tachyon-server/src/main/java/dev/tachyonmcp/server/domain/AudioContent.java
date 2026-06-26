/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * An audio content block provided to or from an LLM.
 *
 * <p>The audio data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the encoding (e.g. {@code audio/mp3}).
 */
@Value.Immutable
@Value.Builder
@Value.Style(allParameters = true, typeImmutable = "Default*")
public non-sealed interface AudioContent extends ContentBlock, HasMeta {

    String data();

    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, JsonNode> meta();

    @Override
    default Type type() {
        return Type.AUDIO;
    }

    static DefaultAudioContent.Builder builder() {
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
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, JsonNode> meta) {
        return DefaultAudioContent.of(data, mimeType, annotations, meta);
    }
}
