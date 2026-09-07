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
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            var decoded = decoder.decode(ByteBuffer.wrap(bytes, 0, maxBytes));
            return new Value(decoded + "…(truncated)");
        } catch (CharacterCodingException e) {
            return new Omitted("truncation failed");
        }
    }
}
