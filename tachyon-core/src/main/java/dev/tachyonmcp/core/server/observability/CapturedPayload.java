/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** A payload captured for observation purposes, subject to {@code PayloadCapturePolicy} limits. */
@InternalApi
public sealed interface CapturedPayload {

    /** Captured content, already redacted/truncated to policy limits. */
    record Value(String json) implements CapturedPayload {}

    /** Content that could not be captured, with a bounded, non-sensitive reason. */
    record Omitted(String reason) implements CapturedPayload {}

    /** Appended to a truncated payload; its own byte length is reserved out of {@code maxBytes}. */
    String TRUNCATION_MARKER = "…(truncated)";

    int TRUNCATION_MARKER_BYTES = TRUNCATION_MARKER.getBytes(StandardCharsets.UTF_8).length;

    /**
     * Captures {@code json}, truncating to at most {@code maxBytes} UTF-8 bytes on a
     * character-boundary-safe cut. Never falls back to the unredacted original on truncation
     * failure — an {@link Omitted} is returned instead.
     */
    static CapturedPayload capture(String json, int maxBytes) {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return new Value(json);
        }
        if (TRUNCATION_MARKER_BYTES > maxBytes) {
            return new Omitted("truncation failed");
        }
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            var decoded = decoder.decode(ByteBuffer.wrap(bytes, 0, maxBytes - TRUNCATION_MARKER_BYTES));
            return new Value(decoded + TRUNCATION_MARKER);
        } catch (CharacterCodingException e) {
            return new Omitted("truncation failed");
        }
    }
}
