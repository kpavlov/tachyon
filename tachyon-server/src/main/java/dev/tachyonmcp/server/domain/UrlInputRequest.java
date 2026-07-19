/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;

/** Requests user input by opening a URL (e.g. for OAuth or form fill). */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface UrlInputRequest extends InputRequest {

    /** Prompt message shown to the user. */
    String message();

    /** Identifier linking the response back to the elicitation context. */
    String elicitationId();

    /** URL to open for user input. */
    String url();

    @Value.Check
    default void check() {
        if (message().isBlank()) throw new IllegalArgumentException("message must not be blank");
        if (elicitationId().isBlank()) throw new IllegalArgumentException("elicitationId must not be blank");
        if (url().isBlank()) throw new IllegalArgumentException("url must not be blank");
    }

    static Builder builder() {
        return DefaultUrlInputRequest.builder();
    }

    static UrlInputRequest of(String message, String elicitationId, String url) {
        return DefaultUrlInputRequest.of(message, elicitationId, url);
    }

    interface Builder {
        Builder message(String message);

        Builder elicitationId(String elicitationId);

        Builder url(String url);

        UrlInputRequest build();
    }
}
