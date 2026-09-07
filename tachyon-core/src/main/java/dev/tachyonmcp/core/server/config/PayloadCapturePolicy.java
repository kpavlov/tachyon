/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import dev.tachyonmcp.api.annotations.ExperimentalApi;

/**
 * Opt-in policy controlling whether — and how much — request/response content observation
 * listeners may capture. All toggles default to {@code false}; capture only runs when a toggle is
 * enabled <em>and</em> a listener is registered, so metrics-only and disabled-observability
 * configurations pay nothing for it.
 *
 * @param requestArgs      capture the request's params/arguments
 * @param responseContent  capture the encoded response content
 * @param rawMessage       capture the raw JSON-RPC envelope, independent of {@link #requestArgs}/{@link #responseContent}
 * @param exceptionDetail  capture a redacted exception message on handler/serialization failure
 * @param maxBytes         truncation limit, in UTF-8 bytes, applied to every captured value
 */
@ExperimentalApi
public record PayloadCapturePolicy(
        boolean requestArgs, boolean responseContent, boolean rawMessage, boolean exceptionDetail, int maxBytes) {

    /** Default byte limit applied to a captured value before truncation. */
    public static final int DEFAULT_MAX_BYTES = 4096;

    public static final PayloadCapturePolicy DISABLED =
            new PayloadCapturePolicy(false, false, false, false, DEFAULT_MAX_BYTES);

    public PayloadCapturePolicy {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link PayloadCapturePolicy}. */
    public static final class Builder {

        private boolean requestArgs;
        private boolean responseContent;
        private boolean rawMessage;
        private boolean exceptionDetail;
        private int maxBytes = DEFAULT_MAX_BYTES;

        private Builder() {}

        /** Enables capturing the request's params/arguments. */
        public Builder requestArgs(boolean enabled) {
            this.requestArgs = enabled;
            return this;
        }

        /** Enables capturing the encoded response content. */
        public Builder responseContent(boolean enabled) {
            this.responseContent = enabled;
            return this;
        }

        /** Enables capturing the raw JSON-RPC envelope. */
        public Builder rawMessage(boolean enabled) {
            this.rawMessage = enabled;
            return this;
        }

        /** Enables capturing a redacted exception message on failure. */
        public Builder exceptionDetail(boolean enabled) {
            this.exceptionDetail = enabled;
            return this;
        }

        /** Sets the truncation limit, in UTF-8 bytes, applied to every captured value. */
        public Builder maxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public PayloadCapturePolicy build() {
            return new PayloadCapturePolicy(requestArgs, responseContent, rawMessage, exceptionDetail, maxBytes);
        }
    }
}
