/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface UrlInputRequest extends InputRequest {

    String message();

    String elicitationId();

    String url();

    static DefaultUrlInputRequest.Builder builder() {
        return DefaultUrlInputRequest.builder();
    }

    static UrlInputRequest of(String message, String elicitationId, String url) {
        return DefaultUrlInputRequest.of(message, elicitationId, url);
    }
}
