/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * An audio content block provided to or from an LLM.
 *
 * <p>The audio data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the encoding (e.g. {@code audio/mp3}).
 */
public non-sealed interface AudioContent extends ContentBlock, HasMeta {

    String data();

    String mimeType();

    @Nullable
    Annotations annotations();

    /** Creates an audio content block with no metadata or annotations. */
    static AudioContent of(String data, String mimeType) {
        return new DefaultAudioContent(data, mimeType, null, null);
    }

    /** Creates an audio content block with given annotations and no metadata. */
    static AudioContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return new DefaultAudioContent(data, mimeType, null, annotations);
    }

    /** Creates an audio content block with metadata and optional annotations. */
    static AudioContent of(
            String data, String mimeType, @Nullable Map<String, JsonNode> meta, @Nullable Annotations annotations) {
        return new DefaultAudioContent(data, mimeType, meta, annotations);
    }
}
