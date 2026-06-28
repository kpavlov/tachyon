/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface FormInputRequest extends InputRequest {

    String message();

    Map<String, JsonNode> requestedSchema();

    static DefaultFormInputRequest.Builder builder() {
        return DefaultFormInputRequest.builder();
    }

    static FormInputRequest of(String message, Map<String, JsonNode> requestedSchema) {
        return DefaultFormInputRequest.of(message, requestedSchema);
    }
}
